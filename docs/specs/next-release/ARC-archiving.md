# ARC — Archiving events

Archive = put an event out of sight without deleting it, and bring it back intact.

**Revised 2026-10-10 (owner decisions).** Archiving applies to **events only** — archiving
individual entries was withdrawn. The action lives in **Event Detail's three-dot overflow
menu**; there is no long-press anywhere. Consequently **ARC-02** (long-press action menu) and
**ARC-04** (entry archiving) are withdrawn; their ids are retired, not reused. Two specs
remain: ARC-01 and ARC-03.

Read [README.md](README.md) §1, §5 and §8 first. Paths are relative to
`app/src/main/kotlin/com/lifelog/app/` unless they start with `app/`.

**Screen names.** The brief's "EventScreen" is `ui/events/EventsScreen.kt` (the Events list);
"EventDetailsScreen" is `ui/events/EventDetailScreen.kt` (one event's entries and charts).

---

## Shared design

### What exists today
- **No archived / hidden / soft-deleted state exists.** `reminders.isActive` is the only on/off
  flag in the schema and means something else (whether the alarm is armed).
- An event is deleted — permanently — only from Event Detail's overflow menu
  (`EventDetailScreen.kt:234`), which also detaches linked reminders and unbinds quick-add
  widgets (`EventDetailViewModel.deleteEventType`, :139). The Events list deliberately has no
  per-event actions (owner call, audit D8).
- There is no undo anywhere.

### Decisions

| # | Decision | Why |
|---|---|---|
| ARC-D1 | **One nullable timestamp**: `event_types.archivedAt`. `NULL` = active. | One column answers "is it archived" and "when" (the archived list sorts and labels by it). |
| ARC-D2 | **Archiving touches the event row only.** Entries, fields and chart configs are left exactly as they are. | Unarchiving restores the event completely, with nothing to rebuild. |
| ARC-D3 | **Active is the default.** Every existing query that feeds a screen returns active events only; a query that includes archived events says so in its name. | An archived event must not leak into a surface because someone reused a query. |
| ARC-D4 | **Unarchive ships with archive.** The archive action offers Undo, and there is always a permanent place to unarchive from. | A hide with no way back is a delete with extra steps. |
| ARC-D5 | **Writes are targeted** — they update the columns they own. | A full-row update from a domain object that does not carry `archivedAt` would silently un-archive (README S2; fixed for events by ADH-01). |
| ARC-D6 | **All event actions stay in Event Detail's overflow menu** — Archive joins Edit and Delete there. The Events list stays action-free. | Owner decisions P-09 (2026-10-10) and audit D8 (2026-08-28). |

### Behaviour of an archived event, surface by surface

| Surface | Archived event |
|---|---|
| Events list, its search, tags, sort | hidden |
| Timeline, its search | its entries hidden |
| Its own detail screen | opens (from Archived events) with a banner; entries browsable; **no add-entry button; no charts** |
| Charts | not rendered, not computed; the chart configurations are kept and return on unarchive |
| Computed fields (CF) | evaluated as normal on its detail screen — it still has all its entries |
| Timeline widget | its entries hidden; a widget bound to it shows its empty state |
| Quick-add widget, reminder "Add entry" | the widget stays bound; opening the form says "This event is archived" |
| Event pickers (reminder editor, widget setup, Log Entry shortcut) | not offered |
| Reminders linked to it | turned off on archive; still linked; event name still shown |
| CSV export | "Export to CSV" on its own screen works; skipped in the ZIP-of-CSV (P-10) |
| SQLite / JSON export, restore | included (raw tables) |

---

## ARC-01 — Archive data layer

### 1. Objective
Store an event's archived state, expose archive/unarchive in the repository, and make every
existing read path return active events only. After this spec, setting the column by hand
(seed recipe) makes an event vanish correctly from every surface — before any UI exists.

### 2. Current implementation
- `data/db/entity/EventTypeEntity.kt` — `id, name, description, category, colorArgb, iconName,
  createdAt, updatedAt`.
- `data/db/dao/EventTypeDao.kt`, `EventEntryDao.kt` — no query filters on any state.
- `EventRepository.observeAllEventTypes` (`data/repository/EventRepository.kt:46`) feeds the
  Events list, both tag rows, the Timeline's field map, the reminder editor's picker, both
  widget pickers and the Log Entry shortcut's picker. `observeAllEntryRows` (:165) feeds the
  Timeline. `getRecentEntries*` (:189, :214, :220) feed the Timeline widget.
  `getAllCategories` (:244), `getAllEventTypesForExport` (:196).
- `ReminderRepository.observeAll` (`data/repository/ReminderRepository.kt:23`) resolves event
  names from `eventTypeDao.observeAll()`.
- `saveEventType` (:97) — full-row update today; targeted after ADH-01.
- Export: `EventTypeRow` (`export/ExportData.kt`), mapped at `export/ExportEngine.kt:340`;
  format descriptions at `ui/settings/SettingsScreen.kt:269–273`.
- `EventEntryDao.getLatestForEventType` (:42) has no caller.

### 3. Required behaviour
**State**
- `event_types.archivedAt: Long?` — `NULL` = active; otherwise the epoch milliseconds at which
  the event was archived.
- Archiving sets it to now; unarchiving sets it to `NULL`. Both are idempotent.
- No other write may change it: editing an archived event leaves it archived.
- Nothing else changes when an event is archived — not its entries, fields or chart configs.

**Reads** — every query, and what it returns after this spec:

| DAO method | After |
|---|---|
| `EventTypeDao.observeAll()` → rename **`observeActive()`** | `WHERE archivedAt IS NULL ORDER BY name` |
| *new* `observeAllIncludingArchived()` | every event (reminder name resolution only) |
| *new* `observeArchived()` | `WHERE archivedAt IS NOT NULL ORDER BY archivedAt DESC` |
| *new* `observeArchivedCount(): Flow<Int>` | |
| `getById`, `observeById` | unchanged — by id, archived or not |
| `getEntryCount`, `observeEntryCounts`, `getEntryCounts`, `observeLatestEntryTimes` | unchanged (they are joined to whichever event list asked) |
| `getAll()` | unchanged (JSON export). *new* `getAllActive()` for `getAllCategories`, `getRecentEntriesByCategory` and `getAllEventTypesForExport` (P-10) |
| `EventEntryDao.observeAll()` → rename **`observeForActiveEvents()`** | `SELECT e.* FROM event_entries e JOIN event_types t ON t.id = e.eventTypeId WHERE t.archivedAt IS NULL ORDER BY e.createdAt DESC` |
| `getRecent(limit)` | the same join and filter, **then** `LIMIT` |
| `getRecentByEventType(id, limit)` | query unchanged; the repository returns an empty list when that event is archived |
| `getRecentByEventTypes(ids, limit)` | unchanged (its ids come from `getAllActive`) |
| `observeByEventType`, `getById`, `getAllEntries`, `getAllForExport` | unchanged — an archived event's own screen and its CSV export still read all its entries |
| `getLatestForEventType` | delete (dead) |

**Repository API** (`EventRepository`)
- `suspend fun archiveEventType(id: Long)`, `suspend fun unarchiveEventType(id: Long)`.
- `fun observeArchivedEventTypes(): Flow<List<EventType>>` (with entry counts, the same join
  as `observeAllEventTypes`), `fun observeArchivedEventCount(): Flow<Int>`.
- `observeAllEventTypes()` keeps its name and now means active events — which is what every
  one of its callers wants.
- `ReminderRepository.observeAll` switches to `observeAllIncludingArchived()` so a reminder
  linked to an archived event still shows that event's name.

**Edge cases**
- `LIMIT` applies after filtering: if the three newest entries belong to an archived event,
  `getRecent(2)` returns the two newest entries of active events.
- A tag used only by archived events disappears from the tag rows.
- A Timeline widget bound to an archived event renders its empty state, not another event's
  entries.
- Restoring an older SQLite backup (no column) migrates on open; every event is active.

### 4. Technical design
- `EventTypeEntity`: `val archivedAt: Long? = null` (last property).
- Migration (take `SCHEMA_VERSION + 1`, FND-01 rules):
  `ALTER TABLE event_types ADD COLUMN archivedAt INTEGER`. No `DEFAULT` clause, no index (the
  table holds a handful of rows).
- Domain: `EventType.archivedAt: Long? = null`, `val isArchived get() = archivedAt != null`;
  mapped in `EventTypeEntity.toDomain`. `EventType.toEntity()` passes it through (a new event
  is inserted with `NULL`).
- `EventTypeDao.setArchivedAt(id: Long, archivedAt: Long?)` —
  `@Query("UPDATE event_types SET archivedAt = :archivedAt WHERE id = :id")`.
- `saveEventType` must already be the targeted update of ADH-01 (ARC-D5). If ADH-01 has not
  landed, do it as part of this spec.
- Rename the two DAO methods and their call sites (`EventRepository.kt:48`, `:166`).
- Export: `EventTypeRow.archivedAt: Long? = null`, mapped in `ExportEngine`; bump
  `EXPORT_SCHEMA_VERSION`. Change the ZIP-of-CSV description to
  "Human-readable. One CSV per event type. Archived events are not included."

### 5. Data model / persistence changes
| Change | Detail |
|---|---|
| Column | `event_types.archivedAt INTEGER NULL` |
| Room version | `+1`, one migration, schema JSON committed |
| JSON export | `EventTypeRow` gains the property; `EXPORT_SCHEMA_VERSION` `+1` |
| Indexes | none |

`event_entries` is not changed.

### 6. UI/UX changes
One line of copy in the export-format dialog. Nothing else — but the Events list, Timeline,
tags, pickers and widgets now honour the column.

### 7. Interactions with existing functionality
- **`observeAllEventTypes`' query count** is pinned by `EventTypeFieldsQueryTest` (four
  queries per emission); the added `WHERE` does not change it.
- **Room invalidation**: the joined entry query observes `event_types` as well, so archiving
  an event re-emits the Timeline (wanted).
- **Reminders**: `rescheduleTimeSinceLast` and `detachFromEventType` look events up by id —
  unaffected.
- **SQLite restore**: `SqliteRestore`'s table list and row counts are raw SQL over all rows —
  unchanged; the counts shown after a restore include archived events.
- **Computed fields**: unaffected — `observeByEventType` and `getAllForExport` still return
  every entry of an event (README §2).
- **Entry form**: `getEventType(id)` returns an archived event (by id); refusing new entries is
  ARC-03's job.

### 8. Testing requirements
- Instrumented `MigrationTest`: migrate seeded data; `archivedAt` is `NULL` on every event; all
  other columns intact.
- Instrumented `ArchiveDbTest` (in-memory Room, pattern of `EventTypeDeleteDbTest`): one test
  per changed row of the read table; the `LIMIT`, tag and bound-widget edge cases;
  `saveEventType` on an archived event leaves `archivedAt` set (mutation check: restore the
  full-row update → fails); archive/unarchive idempotence; unarchive restores the event with
  its entries, fields and chart configs untouched; `ReminderRepository.observeAll` still
  resolves an archived event's name.
- JVM: `MappersDecodeTest` passthrough for `EventType.archivedAt`.
- Emulator, by seed: set `archivedAt` on one event via `run-as`; verify every row of the
  surface table in *Shared design* that needs no UI (Events list, Timeline, tags, Timeline
  widget, pickers, ZIP export). Upgrade check over real data (README R1).

### 9. Dependencies
FND-01, ADH-01.

### 10. Open questions / product decisions
P-10 (the ZIP-of-CSV export skips archived events) — on its default.

### 11. Acceptance criteria
- [ ] Schema JSON committed; migration test green; upgrade over real data verified.
- [ ] Every query in §3's table behaves as listed (tests).
- [ ] No write path other than `setArchivedAt` can change `archivedAt` (test, mutation-checked).
- [ ] A hand-archived event disappears from every surface listed in §8 and nothing crashes.
- [ ] JSON export carries the property; SQLite backup/restore round-trips an archived event.
- [ ] `event_entries` and its queries by event are unchanged.

---

## ARC-02 — *withdrawn (2026-10-10)*

Was: a shared long-press action menu for event and entry cards. Withdrawn by owner decision
P-09 — archive lives in Event Detail's overflow menu and nothing in this release uses a
long-press. The id is retired.

---

## ARC-03 — Event archiving

### 1. Objective
Archive an event from its detail screen's overflow menu, undo it, find archived events again,
and unarchive them — with reminders, widgets and charts behaving as decided.

### 2. Current implementation
- After ARC-01: the repository can archive and unarchive; archived events are filtered out of
  every list.
- `ui/events/EventDetailScreen.kt` — overflow menu (:206–263): Jump to date, Jump to top,
  Edit, Delete, Add Chart, Export to CSV. Add-entry FAB (:272). Chart carousel shown when the
  event has any chart config (`showChartCarousel`, :139). A `SnackbarHostState` exists (:105),
  fed by a local `exportResult` string (:107).
- `ui/events/EventDetailViewModel.kt` — `chartDataMap` (:112) runs `ChartDataProcessor` for
  every readable chart; `deleteEventType` (:139) detaches reminders
  (`ReminderCoordinator.detachFromEventType`, `notifications/ReminderCoordinator.kt:198`:
  unlink + deactivate + cancel alarms), unbinds quick-add widgets and refreshes Timeline
  widgets. No one-shot event channel.
- `ui/events/EntryViewModel.loadEventType` (:94) sets `eventTypeMissing` when the event is
  gone; `widget/QuickAddActivity` then toasts "This event no longer exists" and finishes.
  One-shot precedent: `EntryViewModel._dismiss`, a `Channel` exposed with `receiveAsFlow()` (:91).
- `ui/settings/SettingsScreen.kt` — sections Appearance, Data & Backup, Import; the CSV import
  item navigates through an `onNavigateToImport` callback wired in
  `ui/navigation/AppNavigation.kt` (:405–411).
- `EventTypeCard` is private to `ui/events/EventsScreen.kt` (:232).

### 3. Required behaviour
**Archive (P-09)**
- Event Detail's overflow menu gains **Archive**, placed directly above Delete, for an active
  event.
- Choosing it: turns off the event's active linked reminders (they stay linked and keep their
  settings — P-08), archives the event, refreshes Timeline widgets. The screen stays open and
  switches to its archived presentation (below).
- A snackbar confirms: "“Blood Pressure” archived" — with " · 2 reminders turned off" when any
  were — and **Undo**. Undo unarchives the event and re-enables exactly those reminders.

**An archived event's detail screen (P-07)**
- A banner under the app bar: "This event is archived" with an **Unarchive** button.
- **No add-entry button.**
- **No charts**: the chart carousel is not shown and chart data is not computed. Chart
  configurations are kept untouched and reappear on unarchive.
- Overflow menu: Jump to date, Jump to top, Edit, **Unarchive**, Delete, Export to CSV.
  (No Archive, no Add Chart.)
- The entry list is otherwise unchanged: browse, search, filter, sort, and the existing swipe
  actions on existing entries.
- **Unarchive** (banner or menu) returns the screen to normal at once. Reminders stay off; the
  user turns them back on from Reminders.

**New entries are refused**
- The entry form opened for a *new* entry on an archived event closes immediately. From the
  quick-add widget or a reminder's "Add entry" the user sees the toast "This event is
  archived".
- Quick-add widgets stay bound to the event (archiving is reversible; deletion is not).

**Finding archived events again (P-06 — on its default)**
- **Settings → Archived events**, a new item (section "Events") whose subtitle is the count
  ("3 archived" / "None"). It opens the **Archived events** screen: back arrow, title, the
  archived events newest-archived first, drawn with the Events list's card plus an
  "Archived Oct 3" chip; an empty state when there are none.
- Tapping a card opens that event's detail screen (archived presentation), where Unarchive
  and Delete live. The list itself has no per-card actions.

**Edge cases**
- Archiving the last active event: the Events list shows its normal empty state.
- Two-pane layout (≥ 840 dp): the Events list pane drops the event as soon as it is archived;
  the detail pane keeps showing it, now with the banner. No navigation happens. An archived
  event opened from the Archived screen shows beside the (active) list with a back arrow.
- An archived event reached from outside (widget "History" → `EXTRA_OPEN_EVENT_ID`): opens
  with the banner.
- Undo after the snackbar has gone: Unarchive from the banner or from Settings → Archived
  events (reminders are not re-enabled on that path).
- A reminder linked to an archived event: listed in Reminders, off, with the event's name; its
  editor still shows the event name; the picker does not offer archived events.
- Rotation while the snackbar is showing must not replay the archive.
- Deleting an archived event works exactly as deleting an active one.

### 4. Technical design
- **Reminders** — `ReminderDao`: `getActiveIdsByEventType(id)`,
  `deactivateByEventType(id)` (`UPDATE reminders SET isActive = 0 WHERE eventTypeId = :id AND isActive = 1`),
  and a `@Transaction deactivateForEventType(id): List<Long>` returning the ids it turned off
  (the shape of `detachFromEventType`, `data/db/dao/ReminderDao.kt:57`).
  `ReminderCoordinator.deactivateForEventType(id): List<Long>` — under `rearmMutex`, calls it
  and cancels each alarm. Undo re-enables through the existing
  `ReminderCoordinator.setActive(reminder, true)`, per id.
- **`EventDetailViewModel`**
  - `fun archiveEvent()`: coordinator → `repository.archiveEventType` →
    `widgetUpdater.refreshTimeline()` → emit `EventDetailUiEvent.Archived(name, reminderIds)`
    on a new `Channel`-backed one-shot flow (the `EntryViewModel._dismiss` pattern).
  - `fun undoArchive(reminderIds: List<Long>)`, `fun unarchiveEvent()`.
  - `chartDataMap`: emit an empty map when `type?.isArchived == true` (do not run the
    processor).
- **`EventDetailScreen`** — collect the event and show the snackbar with an action label
  through the existing `snackbarHostState`; banner, FAB, `showChartCarousel` and the overflow
  items keyed on `eventType?.isArchived`. `leadingItemCount` already follows
  `showChartCarousel` (:144), so jump-to-date keeps working.
- **`EntryViewModel.loadEventType`** — when the loaded event `isArchived`, set a new
  `eventTypeArchived` flag; `EntryFormSheet` gains `onEventArchived: () -> Unit = onDismiss`;
  `QuickAddActivity` passes a toast-and-finish beside `finishMissingEvent`. `loadEntry`
  (editing an existing entry) does not check.
- **Archived events screen** — new `ui/events/ArchivedEventsScreen.kt` with a small
  `ArchivedEventsViewModel` (`repository.observeArchivedEventTypes()`); make `EventTypeCard`
  `internal` and give it an optional trailing chip rather than duplicating it. Route
  `Screen.ArchivedEvents("settings/archived-events")`, a normal `NavHost` destination wrapped in
  `ReadableWidth`. `SettingsScreen` gains `onNavigateToArchivedEvents` (wired like
  `onNavigateToImport`) and `SettingsViewModel` an `archivedEventCount` flow.
- Icons: `Icons.Rounded.Archive`, `Icons.Rounded.Unarchive` (both present in the bundled
  material-icons-extended 1.7.5).

### 5. Data model / persistence changes
None beyond ARC-01. Three reminder DAO queries, no schema change.

### 6. UI/UX changes
- Event Detail: one overflow item (Archive / Unarchive), a banner, a snackbar; FAB and chart
  carousel hidden while archived.
- Settings: one new item. New screen: Archived events.
- A toast in `QuickAddActivity`.
- The Events list and the Timeline gain no controls.

### 7. Interactions with existing functionality
- **Delete from Event Detail** is unchanged and works for archived events (it *detaches* —
  unlinks — reminders, as today).
- **"Time since last entry" reminders** linked to the event are turned off like the others;
  nothing re-arms them while archived because no entry can be logged.
- **Log Entry shortcut, reminder editor picker, both widget pickers** are built on
  `observeAllEventTypes()` → archived events are absent with no further work.
- **`EventsViewModel` is not touched** — it stays free of the reminder and widget
  dependencies removed in audit D8.
- **Charts / AGG**: nothing is processed for an archived event; AGG specs need no special case.
- **Computed fields**: shown on the archived event's entry cards as normal.
- **`owningTabRoute`**: `settings/archived-events` belongs to no tab, like Settings; the
  archived event's detail route lights Events, as any event's does.
- **Export to CSV** from the archived event's own menu still works (ARC-01 leaves
  `getAllForExport` alone).

### 8. Testing requirements
- Instrumented (pattern and id-range convention of `DetachFromEventTypeTest` /
  `ReminderCoordinatorTest`: test reminder ids in their own high block, real `AlarmManager`):
  `deactivateForEventType` turns off and cancels only the active linked reminders, returns
  their ids, leaves `eventTypeId` set; undo re-arms exactly those.
- JVM: the snackbar message for 0 / 1 / n reminders (extract as a pure function);
  `chartDataMap`'s "archived → empty" decision if extracted; an `OwningTabRouteTest` case for
  the new route (→ no tab).
- Emulator: archive from the overflow → banner, no FAB, no charts, snackbar → Undo (event
  normal again, reminders on, `dumpsys alarm` shows them armed); archive → back → gone from
  Events and Timeline → Settings → Archived events → open → Unarchive → back in Events with
  its charts; quick-add widget tap on an archived event → toast, widget still bound; Timeline
  widget drops its entries; two-pane case; rotation during the snackbar; `font_scale 1.30`.

### 9. Dependencies
ARC-01.

### 10. Open questions / product decisions
P-06 — where archived events are reached from (default: Settings → Archived events).
Decided and applied: P-07, P-08, P-09.

### 11. Acceptance criteria
- [ ] Archive and Unarchive are in Event Detail's overflow menu; nothing was added to the Events list or the Timeline.
- [ ] An archived event shows a banner, no add-entry button and no charts; its chart configurations survive a round trip.
- [ ] Linked active reminders are turned off on archive with their alarms cancelled; Undo restores exactly them.
- [ ] An archived event receives no new entries from any entry point; quick-add widgets stay bound.
- [ ] Archived events can be found and reopened from Settings → Archived events.
- [ ] No long-press was introduced.

---

## ARC-04 — *withdrawn (2026-10-10)*

Was: archiving individual entries (long-press on the Timeline and in Event Detail, an
"Archived entries" view). Withdrawn by the owner. With it went the
`event_entries.archivedAt` column, the entry-level query filters and the targeted `saveEntry`
update that ARC-01 originally carried. The id is retired.
