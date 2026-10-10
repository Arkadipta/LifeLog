# CAL — One-way calendar sync

Push an event's entries into a device calendar of the user's choice, through Android's
`CalendarContract`. The calendar is a second view; LifeLog is the single source of truth.
Creating or editing an entry writes its calendar event, deleting an entry deletes it, and
nothing ever flows back.

Added 2026-10-10. Read [README.md](README.md) §1, §5 and §8 first. Paths are relative to
`app/src/main/kotlin/com/lifelog/app/` unless they start with `app/`.

---

## Shared design

### Fixed by the brief (owner)

- Strictly one-way. Calendar-side changes are never read back into LifeLog.
- Triggers: entry created or edited → upsert its calendar event; entry deleted → delete it.
- A calendar-side edit or delete is overwritten (re-created if needed) by the next push.
- `CalendarContract` directly, with `READ_CALENDAR` + `WRITE_CALENDAR`. No Google Calendar
  API, no network code.
- The mapping lives in LifeLog's own database. `SYNC_DATA*` and `ExtendedProperties` are not
  used (they are for sync adapters).
- Off by default; turned on per event from Event Detail's overflow menu.
- No reminders.
- Reference for ideas only: `github.com/dmi3dmi3/saywhen` (GPL-3.0). **Nothing is copied.**

### Decided 2026-10-10 (README §6, P-17 … P-22)

| ID | Decision |
|---|---|
| P-17 | The mapping is a **side table**, one row per entry — not columns on `event_entries`. |
| P-18 | An update **keeps the calendar event's current length**; the default duration applies on insert and re-create only. |
| P-19 | Turning sync off asks **Keep** (default) or **Remove** the existing calendar events. |
| P-20 | Archiving an event leaves its calendar events alone. |
| P-21 | An event-level change marks that event's calendar entries out of date and **offers** "Update N calendar events now"; it pushes nothing by itself. |
| P-22 | On a new phone or after a restore, duplicates are **accepted, with a warning**; no visible marker is added to descriptions. |

Applied from the viability report without a separate question (say so if any is unwanted):
events are marked **Free**; every event is **stamped** as LifeLog's; links are written
**dirty-first**; **past entries** can be added when sync is turned on.

### What exists today

- **Entries** — `event_entries(id, eventTypeId, fieldValuesJson, note, createdAt, updatedAt)`.
  One user-editable timestamp (`createdAt`); **no end time**, so the brief's "DTEND = end if
  the entry has one" never applies. Values are one JSON map, field id → typed value; field
  definitions are in `event_fields`.
- **Write paths** — every create/edit/delete of an entry goes through
  `EventRepository.saveEntry` (`data/repository/EventRepository.kt:176`) and `deleteEntry`
  (:185): the entry form, the quick-add widget (`widget/QuickAddActivity.kt`), Event Detail and
  the Timeline. Three things bypass them: the CSV wizard inserts directly
  (`csv/CsvImportEngine.kt:128,170` — always a brand-new event); deleting an event
  (`deleteEventType`, :113) removes its entries through the foreign-key cascade, with no
  per-entry call; and a restore swaps the whole database file.
- **`saveEntry` is a full-row update** built from an `EventEntry` (:180), and Room invalidates
  per table: any write to `event_entries` re-runs the Timeline, the Event Detail list and every
  chart on screen.
- **Permissions** — none for calendars (`app/src/main/AndroidManifest.xml:5–17`). The one
  runtime-permission flow in the app is notifications on the Reminders screen
  (`ui/reminders/RemindersScreen.kt:86–113`, `:228–261`): a launcher, a banner, a Settings
  fallback once the system dialog is spent, and a re-check on resume.
- **Backup** — Android backup and device transfer include `lifelog.db`
  (`app/src/main/res/xml/backup_rules.xml`), so whatever the database says about calendars
  travels to another phone.
- **saywhen** — three small files: a writable-calendar list with a default-picking order, an
  insert and a delete, and a sync request that skips local calendars. It has no update, no
  stored mapping and no handling of deleted rows; those parts are designed here.
- **Calendar provider facts, read from the AOSP source** (`CalendarProvider2`):
  a delete by an ordinary app removes the row outright when the event was never synced, and
  otherwise sets `DELETED = 1` until the sync adapter purges it;
  `CUSTOM_APP_PACKAGE` / `CUSTOM_APP_URI` are ordinary, writable event columns.
  **Not verifiable from source** (they depend on the account's sync adapter): whether those two
  columns survive a Google sync round trip; whether a provider-inserted event picks up the
  calendar's default notifications; how promptly an expedited sync request is honoured.
  CAL-02 checks all three on a real account before anything is built on them.

### Decisions

| # | Decision | Why |
|---|---|---|
| CAL-D1 | **Two tables of their own** — per-event settings and per-entry links. `event_entries` and `event_types` are not altered. | (a) `saveEntry`'s full-row update would wipe mapping columns on every edit (the ADH-01 bug class). (b) Link writes would invalidate `event_entries` and recompute lists and charts a second time per save, and thousands of times in a re-push. (c) A delete that cannot be carried out yet needs somewhere to wait after the entry row is gone. `chart_configs` is the precedent for a per-event side table. |
| CAL-D2 | **The database is the queue.** The entry write and its link's state change are one transaction; the calendar is written afterwards. | A killed process (the quick-add activity finishes immediately after saving) cannot lose a push — the link is still marked and is picked up later. |
| CAL-D3 | **Four link states**: `SYNCED`; `PENDING` (must be pushed — retried automatically); `STALE` (out of date after an event-level change — pushed only when the user asks, or when that entry is next saved); `PENDING_DELETE` (the calendar event must go — retried automatically). | P-21 needs "out of date but wait for the user" to be different from "failed, try again". |
| CAL-D4 | **LifeLog never updates or deletes a calendar row it cannot prove it created.** Proof = the row is in the calendar the link recorded **and** carries LifeLog's stamp for that entry. Anything else is left alone and a new event is inserted. | Event ids are local to one device's provider and are reassigned if the provider's data is reset. Overwriting an unrelated event is the one unacceptable failure. |
| CAL-D5 | **Settings made on another install are not trusted.** Each settings row records the install it was made on; a device-local marker (in `noBackupFilesDir`, which Android never backs up) identifies this install. On a mismatch the event's sync is switched off, its links are dropped, and the user is asked to choose a calendar again. The chosen calendar is also re-validated by account and name before every push. | The database travels by backup, transfer and restore; calendar ids do not mean the same thing elsewhere. Pushing into "calendar 3" on a new phone could put health entries into a shared work calendar. |
| CAL-D6 | **LifeLog owns start, title and description; the calendar owns the length** once the event exists (P-18). | LifeLog has no duration field. A length corrected in the calendar app is the only place that fact lives. |
| CAL-D7 | **Plan, then apply.** A pure planner turns (link, what is in the calendar, what should be there) into operations; all provider access is behind one small interface, `CalendarStore`. | The decision logic is the risky part and becomes unit-testable with a fake; the Android part stays thin and is tested once, on a local calendar. |
| CAL-D8 | Events are **Free** (they do not block availability), carry **no reminders**, and use the device's time zone at the moment of the push. | A six-hour health log must not make the user look busy. |
| CAL-D9 | **No new background machinery.** One app-scoped coroutine on `Dispatchers.IO`; no WorkManager job, no foreground service. Unfinished work resumes on the next app start. | Matches the app (`UserPreferencesRepository.kt:57`, `LifeLogApp.kt:25`); the queue in the database makes resumption free. |
| CAL-D10 | **Calendar state is device-local.** It is not added to the JSON export. The SQLite backup carries the two tables, where CAL-D5 makes them inert on any other install. | A calendar id is meaningless in a portable export. |

### Link lifecycle

```
                    entry saved (event has sync on)
   (no link) ───────────────────────────────────────▶ PENDING ──push ok──▶ SYNCED
                                                        ▲   │                │  │
                              entry saved again ────────┘   │ push failed    │  │ event-level change,
                                                            ▼ (stays)        │  │ calendar changed,
                                                         PENDING             │  │ same-device restore
                                                                             │  ▼
   entry deleted / event deleted / sync turned off with "Remove"             │ STALE ──user: "Update"──▶ push ──▶ SYNCED
        any state ──▶ PENDING_DELETE ──calendar event removed──▶ (row gone)  │        or entry saved ──▶ PENDING
        (a link that was never pushed is simply dropped)                     │
   sync turned off with "Keep", or settings from another install ────────────┴──▶ (rows dropped; calendar untouched)
```

### What a calendar event contains

| Calendar column | Value |
|---|---|
| `CALENDAR_ID` | the calendar chosen for the event |
| `TITLE` | the chosen field's displayed value for this entry (with its unit, for a number); when that field is empty on the entry, or no field is chosen, the event's name |
| `DESCRIPTION` | every other field that has a value, one per line as `Name: value unit`, in field order; then, after a blank line, the note. Left out: the title field; history-dependent computed fields (they change when *other* entries change, and would silently go stale) |
| `DTSTART` | the entry's `createdAt` |
| `DTEND` | on insert / re-create: `DTSTART` + the event's default duration. On update: `DTSTART` + the calendar event's current length (CAL-D6) |
| `EVENT_TIMEZONE` | `ZoneId.systemDefault().id` at push time |
| `AVAILABILITY` | `AVAILABILITY_FREE` |
| `CUSTOM_APP_PACKAGE`, `CUSTOM_APP_URI` | the stamp: LifeLog's package name and `lifelog://entry/<entryId>` (an identifier only — no intent filter is added) |
| reminders | none (no `Reminders` rows) |

### Components

| File | Spec | Contents |
|---|---|---|
| `data/db/entity/CalendarSyncConfigEntity.kt`, `CalendarLinkEntity.kt`; `data/db/dao/CalendarSyncConfigDao.kt`, `CalendarLinkDao.kt` | CAL-01 | tables and queries |
| `domain/calendar/CalendarModels.kt` | CAL-02 | `DeviceCalendar`, `EventContent`, `StoredEvent`, `CalendarOp`, `LinkState` |
| `domain/calendar/CalendarEventMapper.kt` | CAL-02 | entry → `EventContent` (pure) |
| `domain/calendar/CalendarSyncPlanner.kt` | CAL-02 | the insert / update / re-create / delete decision (pure) |
| `calendar/CalendarStore.kt`, `calendar/AndroidCalendarStore.kt` | CAL-02 | the only code that touches `ContentResolver` |
| `calendar/CalendarSyncCoordinator.kt`, `calendar/InstallMarker.kt` | CAL-03 | the engine |
| hooks in `EventRepository`, `LifeLogApp` | CAL-04 | |
| `ui/events/CalendarSyncSheet.kt`, `CalendarSyncViewModel.kt` | CAL-05 | per-event settings |
| Settings item | CAL-06 | re-push all |

The pure / Android split follows `domain/csv/*` + `csv/*`.

---

## CAL-01 — Calendar-sync persistence

### 1. Objective
Create the two tables the feature keeps its state in, with their queries and the migration.
No behaviour changes.

### 2. Current implementation
- Five tables, schema version as left by the previous migration; migrations follow FND-01's
  rules (`data/db/Migrations.kt`).
- Precedent for a per-event side table with a cascading foreign key:
  `data/db/entity/ChartConfigEntity.kt`.
- Entities are listed in `data/db/LifeLogDatabase.kt` (:16); DAOs are provided one per line in
  `di/DatabaseModule.kt` (:28–32).
- `export/SqliteRestore.kt` (:46) lists the tables a backup must contain.
- `EventEntryDao` has no "by ids" query.

### 3. Required behaviour
**`calendar_sync_configs`** — at most one row per event.

| Column | Type | Meaning |
|---|---|---|
| `eventTypeId` | INTEGER, primary key, FK → `event_types(id)` ON DELETE CASCADE | |
| `enabled` | INTEGER NOT NULL | 0 = off (settings remembered) |
| `calendarId` | INTEGER NOT NULL | the provider's calendar id on the install that chose it |
| `accountName`, `accountType`, `calendarName` | TEXT NOT NULL | the calendar's identity, for re-validation (CAL-D5) |
| `titleFieldId` | INTEGER NULL | NULL = use the event's name |
| `durationMinutes` | INTEGER NOT NULL | default length of a new calendar event |
| `installId` | TEXT NOT NULL | the install that made these settings (CAL-D5) |

**`calendar_links`** — at most one row per entry.

| Column | Type | Meaning |
|---|---|---|
| `entryId` | INTEGER, primary key | **no foreign key**: a `PENDING_DELETE` link must outlive its entry |
| `eventTypeId` | INTEGER NOT NULL, indexed | no foreign key, for the same reason |
| `calEventId` | INTEGER NULL | NULL until the first successful insert |
| `calCalendarId` | INTEGER NULL | the calendar that event was written to |
| `state` | TEXT NOT NULL, indexed | `SYNCED`, `PENDING`, `STALE`, `PENDING_DELETE` |

- A `state` that is not one of the four decodes as `PENDING` (the safe reading: push again).
  Reading these tables never throws (the policy at `data/db/Mappers.kt:26`).
- Deleting an event removes its settings row (cascade) and leaves its links (they are turned
  into `PENDING_DELETE` by CAL-04 before the delete).
- A backup from before this spec migrates on open and gets two empty tables. A backup is not
  required to contain them.
- Nothing reads or writes the tables yet.

### 4. Technical design
- Two entities and two DAOs as named in *Components*; register them in `LifeLogDatabase` and
  `DatabaseModule`.
- `CalendarSyncConfigDao`: `get(eventTypeId)`, `observe(eventTypeId): Flow<…?>`, `getAll()`,
  `observeAll()`, `upsert(entity)`, `setEnabled(eventTypeId, enabled)`.
- `CalendarLinkDao`:
  - `get(entryId)`, `getBatch(states, eventTypeId: Long?, afterEntryId, limit)` (ordered by
    `entryId`, for chunked draining);
  - `markPending(entryId, eventTypeId)` — a `@Transaction`: insert `PENDING` if absent, else set
    `PENDING` keeping the calendar ids, unless the row is `PENDING_DELETE`;
  - `markPendingDelete(entryId)` and `markPendingDeleteForEventType(eventTypeId)` — set
    `PENDING_DELETE`, then delete the rows whose `calEventId` is NULL (never pushed: nothing to
    remove);
  - `markStaleForEventType(eventTypeId)` — `SYNCED` → `STALE` only;
  - `markAllSyncedStale()` (same-device restore);
  - `addPendingForUnlinkedEntries(eventTypeId)` —
    `INSERT INTO calendar_links (entryId, eventTypeId, state) SELECT id, eventTypeId, 'PENDING' FROM event_entries WHERE eventTypeId = :id AND id NOT IN (SELECT entryId FROM calendar_links)`;
  - `markSynced(entryId, calEventId, calCalendarId)`, `delete(entryId)`,
    `deleteForEventType(eventTypeId)`;
  - `observeCount(states, eventTypeId: Long?): Flow<Int>`;
  - `calEventIdsForCalendar(calendarId): List<Long>` (CAL-06's sweep).
- `EventEntryDao.getByIds(ids: List<Long>)` (callers pass at most 50 ids).
- Migration (take `SCHEMA_VERSION + 1`): two `CREATE TABLE` and two `CREATE INDEX` statements,
  **copied from the `createSql` of the exported schema JSON** after the entities compile, so
  the migrated schema is exactly what Room expects.

### 5. Data model / persistence changes
| Change | Detail |
|---|---|
| Tables | `calendar_sync_configs`, `calendar_links` (above) |
| Indexes | `calendar_links(eventTypeId)`, `calendar_links(state)` |
| Room version | `+1`, one migration, schema JSON committed |
| JSON export | **unchanged** (CAL-D10) — no `EXPORT_SCHEMA_VERSION` bump |
| `event_entries`, `event_types` | not altered; one read-only query added to `EventEntryDao` |

### 6. UI/UX changes
None.

### 7. Interactions with existing functionality
- `SqliteRestore.validate`: unchanged; the required-table list stays at the original five.
- `EventTypeDeleteDbTest` pins which tables cascade on event delete — extend it (settings row
  goes, links stay).
- R8: both entities sit under `data.db.entity`, which the existing keep rule covers.
- Nothing observes these tables from a list screen, so writing to them never re-runs the
  Timeline, Event Detail or charts (the point of CAL-D1).

### 8. Testing requirements
- Instrumented `MigrationTest`: migrate seeded data; both tables exist and are empty; existing
  rows intact.
- Instrumented `CalendarLinkDaoTest` (in-memory Room): every DAO operation above, in
  particular — `markPending` keeps ids and does not resurrect a `PENDING_DELETE`;
  `markPendingDelete` drops never-pushed links; `markStaleForEventType` touches `SYNCED` only;
  `addPendingForUnlinkedEntries` is idempotent; an unknown `state` string reads as `PENDING`;
  `getBatch` pages in `entryId` order.
- `EventTypeDeleteDbTest` addition as in §7.

### 9. Dependencies
FND-01.

### 10. Open questions / product decisions
None (P-17 decided).

### 11. Acceptance criteria
- [ ] Schema JSON committed; migration test green; upgrade over real data verified on device.
- [ ] `event_entries` and `event_types` schemas are byte-identical to before.
- [ ] Every DAO operation is covered by a test.
- [ ] No visible behaviour change.

---

## CAL-02 — Calendar layer

### 1. Objective
Everything that talks to, or reasons about, the device calendar — with no dependency on
LifeLog's database: list calendars and pick a default; turn an entry into event content; decide
between insert, update, re-create and delete; carry operations out; request a sync. It starts
by checking, on a real account, the provider behaviours the design relies on.

### 2. Current implementation
- No calendar code and no calendar permissions exist.
- Instrumented tests already exercise real system services (`ReminderCoordinatorTest` uses the
  real `AlarmManager`), which is the precedent for testing against the real calendar provider.
- Ideas taken from saywhen (re-implemented, not copied): which calendar columns to read; a
  default-calendar preference order; skipping the sync request for local calendars.

### 3. Required behaviour
**Provider check — do this first, on the owner's phone, against a Google calendar.**
A throw-away debug action or instrumented test that inserts one stamped event, requests a
sync, waits until the row has a `_SYNC_ID`, and reports:

| # | Question | Design depends on it for |
|---|---|---|
| a | Are `CUSTOM_APP_PACKAGE` and `CUSTOM_APP_URI` still on the row after the round trip? | CAL-D4 |
| b | Does the event fire a notification, or show one in the calendar app? | "no reminders" |
| c | Does deleting the synced event leave `DELETED = 1`, later purged? | planner rule 2 |
| d | Is the event uploaded within about a minute of the sync request? | requirement 10 of the brief |

If (a) fails, **stop and report** (README §8). Without a surviving stamp, CAL-D4 cannot be
met as written; the options to put to the owner are a visible marker line in the description
(declined once, P-22) or accepting calendar-id equality alone as proof. If (b) fails, add an
explicit "no reminder" write and re-check. (c) and (d) are recorded for the commit message.

**Calendars**
- `CalendarStore.calendars()` returns every calendar with access level ≥
  `CAL_ACCESS_CONTRIBUTOR`: id, display name, account name and type, owner account, primary
  flag, colour, and whether it is local (`ACCOUNT_TYPE_LOCAL`).
- `pickDefault(calendars)`: the primary calendar of a non-local account; else any primary
  calendar; else a calendar whose owner is its own account; else the first; else none.

**Mapping** (pure) — `CalendarEventMapper.toContent(entry, eventType, titleFieldId,
durationMinutes, zoneId, excludedFieldIds)` produces the table in *What a calendar event
contains*. Edge cases: the title field is empty on this entry, or was deleted → the event's
name; a Multi-select title joins with ", "; an entry with no values and no note → an empty
description; a value stored under an older field type (legacy mismatch) is shown as stored;
values of fields that no longer exist are left out.

**Planner** (pure) — for one link:

| # | Situation | Operation |
|---|---|---|
| 1 | no `calEventId` | insert into the target calendar |
| 2 | row missing, or `DELETED = 1` | insert (nothing to delete) |
| 3 | row exists but is **not ours** — in a different calendar from the one the link recorded, or its stamp is absent or names something else | insert; **leave that row untouched** |
| 4 | ours, and the link's calendar is the target | update: title, description, start, time zone, availability; end = new start + the row's current length (the default duration if that length is not positive) |
| 5 | ours, but the target calendar is different | delete the old event, insert into the target |
| 6 | removal requested, row is ours and not deleted | delete |
| 7 | removal requested, row missing / deleted / not ours | nothing to do |

`DELETED` is examined before anything else: an update on a `DELETED = 1` row reports success
and does nothing.

**Store** — `events(ids)` reads the rows the planner needs in one query;
`apply(operations)` carries a batch out and returns, per operation, the new event id or a
failure; `requestSync(calendars)` asks each non-local calendar's account for a manual,
expedited sync. A missing permission and a failed write are distinct, typed failures — never
an uncaught exception.

### 4. Technical design
```kotlin
// domain/calendar/CalendarModels.kt (pure)
data class DeviceCalendar(val id: Long, val name: String, val accountName: String, val accountType: String,
                          val ownerAccount: String, val isPrimary: Boolean, val color: Int, val isLocal: Boolean)
data class EventContent(val title: String, val description: String, val startMs: Long,
                        val defaultEndMs: Long, val timeZoneId: String)
data class StoredEvent(val id: Long, val calendarId: Long, val deleted: Boolean,
                       val startMs: Long, val endMs: Long, val appPackage: String?, val appUri: String?)
sealed interface CalendarOp {
    data class Insert(val entryId: Long, val calendarId: Long, val content: EventContent) : CalendarOp
    data class Update(val entryId: Long, val eventId: Long, val content: EventContent, val endMs: Long) : CalendarOp
    data class Delete(val entryId: Long, val eventId: Long) : CalendarOp
}

// calendar/CalendarStore.kt
interface CalendarStore {
    fun calendars(): List<DeviceCalendar>
    fun events(ids: Collection<Long>): Map<Long, StoredEvent>
    fun apply(ops: List<CalendarOp>): ApplyResult        // per-op new id, or a typed failure for the batch
    fun requestSync(calendars: Collection<DeviceCalendar>)
}
```
- `CalendarSyncPlanner.plan(link, existing: StoredEvent?, targetCalendarId, content, ourPackage)`
  returns `List<CalendarOp>` (0, 1 or 2 operations); `planRemoval(link, existing, ourPackage)`
  likewise. `stampUri(entryId) = "lifelog://entry/$entryId"`.
- `AndroidCalendarStore(contentResolver, packageName)`: one `query` with
  `_ID IN (…)`; `ContentResolver.applyBatch(CalendarContract.AUTHORITY, …)` built from
  `ContentProviderOperation`s (a batch is all-or-nothing, which is what the engine wants); new
  ids from the result URIs. `SecurityException` → permission failure; anything else → write
  failure. `EventContent` → `ContentValues` lives here, not in the mapper, so the mapper stays
  testable on the JVM.
- `app/src/main/AndroidManifest.xml`: declare `READ_CALENDAR` and `WRITE_CALENDAR` (needed by
  this spec's instrumented test; they are requested from the user only in CAL-05).

### 5. Data model / persistence changes
None.

### 6. UI/UX changes
None. The two permissions appear in the app's system info page but are not requested yet.

### 7. Interactions with existing functionality
- The app still requests no internet permission.
- Package visibility: the calendar provider needs no `<queries>` entry.
- Declaring the permissions changes the Play listing's data-safety answers (owner task; see
  README R15).

### 8. Testing requirements
- JVM `CalendarEventMapperTest`: title from a chosen field / fallback to the event name /
  deleted title field; description order, units, note, omitted empties, excluded ids; start and
  default end; time zone passed through.
- JVM `CalendarSyncPlannerTest`: one test per planner row, plus — `DELETED` checked before the
  ownership test; stamp absent ⇒ not ours; calendar mismatch ⇒ not ours; length preserved on
  update; zero or negative stored length ⇒ default duration.
- JVM `pickDefault` cases.
- A `FakeCalendarStore` (in `app/src/androidTest` or shared test sources) that implements the
  interface in memory, including `DELETED = 1` rows — used here and by CAL-03.
- Instrumented `AndroidCalendarStoreTest`: grants the two permissions with
  `UiAutomation.grantRuntimePermission` (no new test dependency); creates its own **local**
  calendar under a test account name (inserted through the sync-adapter URI with
  `ACCOUNT_TYPE_LOCAL`); insert → read back every column including the stamp, `FREE`, and no
  `Reminders` rows; update; delete (the row is gone for a local calendar); a batch of 60;
  removes its calendar afterwards. It must never read or write any other calendar.
- Mutation checks: treat "stamp absent" as ours → planner test fails; drop the `DELETED` check
  → planner test fails.

### 9. Dependencies
None (it does not use the database; CAL-01 is not required).

### 10. Open questions / product decisions
The outcome of provider check (a). Everything else is decided.

### 11. Acceptance criteria
- [ ] The provider check was run on a Google calendar and its four answers are recorded.
- [ ] The planner implements the seven rows and nothing else; tests green and mutation-checked.
- [ ] Only `AndroidCalendarStore` touches `ContentResolver` or `CalendarContract`.
- [ ] The instrumented test passes against its own local calendar and leaves nothing behind.
- [ ] No code was copied from saywhen.

---

## CAL-03 — Sync engine

### 1. Objective
The coordinator that turns link rows into calendar operations and operation results back into
link state — plus the operations the UI will call to turn sync on, change it and turn it off,
and the status it will display.

### 2. Current implementation
- After CAL-01 and CAL-02 there are tables and a calendar layer; nothing connects them.
- The house pattern for "one object owns every state transition, serialised by a mutex" is
  `notifications/ReminderCoordinator.kt`. App-scoped work runs on
  `CoroutineScope(SupervisorJob() + Dispatchers.IO)` (`UserPreferencesRepository.kt:57`).

### 3. Required behaviour
**Draining**
- `requestDrain()` — start a drain of `PENDING` and `PENDING_DELETE` links if none is running;
  calls made while one runs are coalesced into one follow-up. Returns immediately.
- `drainNow(includeStale: Boolean, eventTypeId: Long? = null)` — the same work, awaited, for
  the UI ("Update N calendar events now", "Re-push all"), optionally limited to one event and
  optionally including `STALE` links.
- One drain at a time, app-wide.
- An event's `PENDING` / `STALE` links are processed only when **all** of these hold; otherwise
  they are left exactly as they are and the event's status says why:
  calendar permission granted · the event's settings are enabled · they were made on this
  install · a writable calendar with the recorded id still exists and its account and name
  match.
- `PENDING_DELETE` links need only the permission: they carry their own calendar and event
  ids, and CAL-D4's ownership test protects everything else.
- Links are taken in chunks of 50. For each chunk: load the entries, their events and fields;
  read the calendar rows; map and plan (CAL-02); apply; then, in one database transaction,
  record the outcome — insert / re-create → `SYNCED` with the new ids; update → `SYNCED`;
  delete done or nothing to delete → the link row is removed.
- A link whose entry no longer exists is handled as `PENDING_DELETE`.
- A failed chunk leaves its links untouched and ends the drain. Nothing is thrown to callers.
- After the last chunk, one sync request covers every non-local calendar that was written to.
- Progress (`done`, `total`) is observable while a drain runs, and a running drain can be
  cancelled; cancelling leaves the remaining links in their states.

**Settings operations**
- `enable(eventTypeId, calendar, titleFieldId, durationMinutes, includeExisting)` — writes the
  settings row stamped with this install; when `includeExisting`, adds a `PENDING` link for
  every entry of the event that has none; then drains.
- `update(…)` — a changed calendar or title source turns the event's `SYNCED` links `STALE`
  (a changed default duration does not: existing events keep their length, P-18). Pushes
  nothing (P-21).
- `disable(eventTypeId, removeEvents)` — P-19. *Keep*: drop the event's links; the calendar is
  not touched and LifeLog stops managing those events. *Remove*: turn every link
  `PENDING_DELETE` and drain. Either way the settings row stays, with `enabled = 0`, so turning
  sync on again starts from the previous choices.

**Status**
- Per event: `Off`, `InSync`, `Waiting(count)` (links in `PENDING` or `STALE`), or
  `Paused(reason)` with reason `PERMISSION`, `CALENDAR_UNAVAILABLE`, or `CHOOSE_AGAIN`
  (settings from another install).
- App-wide: the number of links not `SYNCED`.

**Install marker**
- A random id in `noBackupFilesDir`, created on first use.
- `reconcileInstall()`: for every settings row whose `installId` is not this install's — set
  `enabled = 0` and drop that event's links. Idempotent; cheap enough to run on every start.

**Edge cases**: an entry saved twice before the first drain (one insert, not two); an entry
edited while its link is `STALE` (becomes `PENDING` and is pushed alone); a calendar event
deleted in the calendar app (re-created on the next push); the calendar removed with its
account (`CALENDAR_UNAVAILABLE`, links wait); permission revoked mid-drain (the chunk fails,
links wait); 5 000 links in one drain (chunked, cancellable, progress reported).

### 4. Technical design
```kotlin
@Singleton
class CalendarSyncCoordinator @Inject constructor(
    @ApplicationContext context: Context,
    private val db: LifeLogDatabase,
    private val configDao: CalendarSyncConfigDao, private val linkDao: CalendarLinkDao,
    private val entryDao: EventEntryDao, private val typeDao: EventTypeDao, private val fieldDao: EventFieldDao,
    private val store: CalendarStore, private val installMarker: InstallMarker
)
```
- It reads entries and fields through the DAOs directly, **not** through `EventRepository`
  (which will depend on this class in CAL-04).
- `private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)`; `private val mutex`;
  a conflated "drain requested" signal so `requestDrain()` never queues more than one
  follow-up.
- Hilt: bind `CalendarStore` to `AndroidCalendarStore` (a small `@Module`, or an `@Inject`
  constructor on the implementation plus a `@Binds`).
- Description content: stored values through the mapper. If CF-04 has landed, add the event's
  **pointwise** computed values with `ComputedFields.evaluateRow` and pass the ids of
  history-dependent fields as `excludedFieldIds`; format with CF-07's `FieldValueFormat` if it
  exists, else `displayString()` plus the unit as `EntryCards.fieldDisplayValue` does today.
- Permission: `ContextCompat.checkSelfPermission` for both calendar permissions, read at the
  start of every drain.
- `InstallMarker`: `File(context.noBackupFilesDir, "calendar_install_id")`.

### 5. Data model / persistence changes
None in the database. One small file outside the backup set.

### 6. UI/UX changes
None.

### 7. Interactions with existing functionality
- **Lists and charts are not re-run by a drain**: it writes only `calendar_links`. Pin this
  with a query-callback test (the technique in `EventTypeFieldsQueryTest`).
- **Computed fields**: as §4. A formula change is an event-level change (CAL-04 marks links
  `STALE`).
- **Archiving** (P-20): nothing special — an archived event's links behave like any other's.
- **Process death** mid-drain: applied chunks are recorded; the rest are still marked and
  resume on the next start (`PENDING`, `PENDING_DELETE`) or on request (`STALE`).

### 8. Testing requirements
Instrumented, in-memory Room + `FakeCalendarStore` (the repo's no-mocks convention):
- insert; update with length preserved; calendar changed (delete + insert); event deleted in
  the calendar (re-created); `DELETED = 1` row (re-created, the dead row not updated); a row
  that is not ours (left untouched, new event inserted); pending delete; pending delete of a
  row that is not ours (untouched, link removed); orphaned link;
- two saves before one drain → one event; a chunk failure leaves every link in that chunk
  unchanged; permission missing → nothing changes and status is `Paused(PERMISSION)`;
  calendar identity mismatch → `Paused(CALENDAR_UNAVAILABLE)`; settings from another install →
  disabled, links dropped, `Paused(CHOOSE_AGAIN)`;
- `enable` with and without `includeExisting`; `update` (calendar change → `STALE`; duration
  change → not); `disable` keep / remove;
- the sync request is made once per drain and only for non-local calendars;
- `event_entries` is never written by a drain.
Mutation checks: skip the identity validation; record `SYNCED` before applying; drain `STALE`
links in an automatic drain — each must fail a test.

### 9. Dependencies
CAL-01, CAL-02.

### 10. Open questions / product decisions
None (P-18, P-19, P-21, P-22 decided).

### 11. Acceptance criteria
- [ ] Every scenario in §8 passes; the mutation checks fail as described.
- [ ] No drain can update or delete a calendar row that fails the ownership test.
- [ ] A drain never throws to its caller and never writes `event_entries`.
- [ ] `STALE` links move only on request or when their own entry is saved.

---

## CAL-04 — Repository hooks and lifecycle

### 1. Objective
Make LifeLog's own writes drive the engine: saving and deleting entries, deleting and editing
events, and app start (including after a restore). After this spec, a settings row seeded by
hand makes an event's entries appear in the calendar with no UI.

### 2. Current implementation
- `data/repository/EventRepository.kt`: `saveEntry` (:176) — insert, or full-row update;
  `deleteEntry` (:185); `deleteEventType` (:113) — one DAO delete, entries go by cascade;
  `saveEventType` (:97) — one transaction, field set replaced.
- `LifeLogApp.onCreate` (`LifeLogApp.kt:33–50`) applies a staged restore, then starts the
  reminder check with `databaseRestored`. Its comment records that R8 removes an injected
  field that is never read.
- `EventTypeFieldsQueryTest.kt:62` constructs `EventRepository(db, …)` by hand.
- The trigger must sit in the data layer, not in view models (brief, requirement 7).

### 3. Required behaviour
- **`saveEntry`** — in one transaction with the entry write: if the entry's event has enabled
  settings, its link becomes `PENDING` (created if absent). After the transaction,
  `requestDrain()`. The save returns as soon as the database write is done; it never waits
  for, and never fails because of, the calendar.
- **`deleteEntry`** — in one transaction: the entry's link (if any) becomes `PENDING_DELETE`
  (or is dropped if it was never pushed), then the entry is deleted. Then `requestDrain()`.
- **`deleteEventType`** — in one transaction: every link of the event becomes
  `PENDING_DELETE`, then the event is deleted (the cascade removes its entries and its
  settings row). Then `requestDrain()`. Deleting a LifeLog event therefore removes its
  calendar events.
- **`saveEventType`** — when the event has enabled settings and the edit changed something a
  calendar event shows — the event's name; a field's name, unit, type or formula; a field
  added or removed — the event's `SYNCED` links become `STALE`. Nothing is pushed (P-21). An
  edit that changes only description, tag, colour or icon marks nothing.
- **App start** — `reconcileInstall()`; if the database was just restored in-app, every
  remaining `SYNCED` link becomes `STALE` (the calendar may no longer match); then
  `requestDrain()`.
- **Untouched**: the CSV wizard (a new event has no settings); archiving (P-20).
- **Edge cases**: an entry saved for an event without settings → no link row, no drain work;
  save → edit → delete before the first drain → the link ends up dropped and the calendar is
  never written; the quick-add activity finishing right after the save → the link is already
  `PENDING` and is drained now or on the next start.

### 4. Technical design
- `EventRepository` gains `CalendarSyncConfigDao`, `CalendarLinkDao` and
  `CalendarSyncCoordinator`. `saveEntry`, `deleteEntry` and `deleteEventType` wrap their
  writes in `db.withTransaction { … }` together with the link change; `requestDrain()` is
  called after the block returns.
- Pure `domain/calendar/CalendarRelevantChange.kt`:
  `fun calendarRelevantChange(before: EventType, after: EventType): Boolean` — compares the
  name and, per field id, name / unit / type / `computedSpecJson` (if CF-03 has landed), and
  the set of field ids. `saveEventType` reads the stored event before writing and calls
  `linkDao.markStaleForEventType` inside its transaction when it returns true.
- `LifeLogApp`: inject the coordinator and add
  `appScope.launch { calendarSyncCoordinator.onAppStart(databaseRestored = restored) }` —
  a real call, which is also what stops R8 from dropping the field.
- Update `EventTypeFieldsQueryTest`'s construction of the repository.

### 5. Data model / persistence changes
None.

### 6. UI/UX changes
None.

### 7. Interactions with existing functionality
- **`saveEventType` is also edited by ADH-01** (targeted update) and **CF-05** (temporary
  field ids). The three changes touch different lines; land them one at a time. The
  "before" snapshot for `calendarRelevantChange` must be read before the field set is
  replaced.
- **Entry saves stay as fast as they are**: one primary-key lookup in a tiny table is added to
  the transaction.
- **Reminders and widgets**: the calls in `EntryViewModel.save` and the two delete view models
  are untouched.
- **Event deletion**: `EventDetailViewModel.deleteEventType` still detaches reminders first and
  then calls the repository.
- **Restore**: the staged-restore path already tells `onCreate` it ran; Android backup and
  device transfer do not, which is why `reconcileInstall()` runs on every start.

### 8. Testing requirements
- JVM `CalendarRelevantChangeTest`: each relevant change is detected; description / tag /
  colour / icon changes and a pure field reorder are not.
- Instrumented repository tests (in-memory Room + `FakeCalendarStore`): each hook's link
  outcome; an entry of an event without settings creates no link; the entry write and the link
  change are atomic (fail the link write → the entry write is rolled back, and the reverse);
  event deletion turns every link into `PENDING_DELETE` and the drain removes the calendar
  events; a relevant event edit marks `STALE` and pushes nothing; app start after a simulated
  restore marks `STALE`; settings from another install are reconciled at start.
- Emulator, end to end against a local calendar with a seeded settings row: log an entry (it
  appears), edit it (title/time follow; a length changed in the calendar app is kept), delete
  it (gone), delete the event (all gone), kill the app right after a quick-add save and
  relaunch (the entry appears).

### 9. Dependencies
CAL-03.

### 10. Open questions / product decisions
None (P-20, P-21 decided).

### 11. Acceptance criteria
- [ ] The three entry/event write paths change link state in the same transaction as the data.
- [ ] No view model or screen calls the coordinator to push an entry.
- [ ] A save never waits for the calendar and cannot fail because of it.
- [ ] Deleting an event removes its calendar events; editing it relevantly marks them out of date and pushes nothing.
- [ ] After a restore, nothing is pushed automatically except what was already `PENDING`.

---

## CAL-05 — Per-event calendar settings

### 1. Objective
The user-facing switch: turn calendar sync on for an event from its overflow menu, choose the
calendar, the title and the default length, optionally add past entries, see when something
needs attention, and turn it off again.

### 2. Current implementation
- Event Detail's overflow menu (`ui/events/EventDetailScreen.kt:206–263`): Jump to date, Jump
  to top, Edit, Delete, Add Chart, Export to CSV. A `SnackbarHostState` exists (:105).
- The permission pattern to copy: `ui/reminders/RemindersScreen.kt:86–113` (launcher, state
  re-read on resume, "must use Settings" once the dialog is spent) and `:228–261` (state
  function, settings intent).
- A duration picker already exists but is private to the reminder editor:
  `PickerCard` (`ui/reminders/CreateReminderScreen.kt:412`) and `SnoozeDurationSheet` (:451),
  with presets and labels in `util/SnoozeDuration.kt`.
- Sheets are `ModalBottomSheet` + `Column(verticalScroll)`; a sheet's view model is obtained
  with `hiltViewModel()` as `EntryFormSheet` does.
- `README.md:130–131` states there is "no cloud to sync to".

### 3. Required behaviour
**Entry point** — a **Calendar sync** item in Event Detail's overflow menu, above Export to
CSV. It opens the Calendar sync sheet.

**The sheet, sync off**
1. A short explanation and the disclosure: *"Entries of this event are copied to the calendar
   you choose. If that calendar syncs with an account, the copies leave this device."*
2. If calendar access is not granted: an **Allow calendar access** button (requests both
   permissions). After a refusal that the system will not ask about again, the button becomes
   **Open settings**. The state is re-read when the app resumes.
3. With access: **Calendar** — the writable calendars, each with its colour, name and account;
   the default from CAL-02 preselected. No writable calendar → a message saying so and no way
   to proceed.
4. **Title** — "Event name" or one of the event's entered fields (Text, Number, Single choice,
   Multi-select; not Yes/No, not computed).
5. **Default length** — a duration picker; presets 5, 10, 15, 30 min, 1 h, 2 h, 6 h, 1 day,
   and a custom value from 1 minute to 7 days; initially 30 minutes. A caption explains P-18:
   *"Used when an entry is first added. If you change an event's length in your calendar,
   LifeLog keeps it."*
6. **Existing entries** — "From now on" (default) or "Also add the existing N entries", the
   latter with the caution *"Entries already in this calendar from an earlier sync will be
   added again."* (P-22).
7. **Turn on**.

**The sheet, sync on** — the same controls showing the current settings, **Save**, and:
- when some entries have no calendar event: **Add N past entries** (same caution);
- **Turn off calendar sync** → a dialog (P-19): "Keep the events in the calendar" (default) or
  "Remove them from the calendar".
- Changing the calendar or the title source and saving does not push anything; the banner
  below then offers the update (P-21).

**On Event Detail** — one banner when sync is on and not `InSync`:
- `Waiting(n)`: "n calendar events are out of date" with **Update** (runs the event's drain,
  including `STALE`, showing progress);
- `Paused(PERMISSION)`: "Calendar access is off" with **Fix** (opens the sheet);
- `Paused(CALENDAR_UNAVAILABLE)`: "The calendar for this event is no longer available" with
  **Choose**;
- `Paused(CHOOSE_AGAIN)`: "Calendar sync was set up on another device. Choose a calendar to
  turn it on here." with **Choose**.

**Edge cases**: permission revoked while the sheet is open; the chosen calendar disappears
between opening the sheet and saving; an event with no eligible title field (only "Event
name"); the title field later deleted (titles fall back to the event's name; the sheet shows
"Event name"); rotation with the sheet open; `font_scale 1.30`; the two-pane layout; a long
drain started from the banner while the user navigates away (it continues; the banner shows
the remaining count on return).

### 4. Technical design
- `ui/events/CalendarSyncSheet.kt` + `CalendarSyncViewModel` (Hilt): loads the event, its
  settings, the calendar list (off the main thread — it is a provider query) and the count of
  unlinked entries; calls the coordinator's `enable` / `update` / `disable` / `drainNow`.
  Keep the form state a plain data class with a pure `validate()`, as `EntryFormState` is.
- Permission: `rememberLauncherForActivityResult(RequestMultiplePermissions())`, plus the
  resume re-check and settings fallback of `RemindersScreen`.
- Duration picker: move `PickerCard` and the duration sheet out of `CreateReminderScreen.kt`
  into `ui/components/` with the presets and title as parameters; the reminder editor keeps
  its behaviour. Reuse the label helpers in `util/SnoozeDuration.kt`.
- `EventDetailViewModel`: `calendarStatus: StateFlow<CalendarSyncStatus>` from the
  coordinator, and `updateCalendarNow()`. `EventDetailScreen`: the menu item, the banner, the
  progress line.
- `README.md`: reword the privacy bullets (lines 130–131) — still no internet permission and
  no LifeLog server; calendar sync is optional, per event, and hands copies to a calendar the
  user picks, which may itself sync.

### 5. Data model / persistence changes
None.

### 6. UI/UX changes
As §3: one overflow item, one sheet, one dialog, one banner on Event Detail, a runtime
permission request, and a README paragraph.

### 7. Interactions with existing functionality
- **ARC-03**: an archived event's banner and this banner can both show; this one sits below.
  An archived event keeps syncing edits to existing entries (P-20) and can be switched off
  from the sheet.
- **Reminder editor**: uses the extracted duration picker with unchanged presets and copy —
  verify by screenshot.
- **Settings**: nothing here; the app-wide action is CAL-06.
- **Computed fields**: not offered as title sources; pointwise ones appear in descriptions
  (CAL-03).

### 8. Testing requirements
- JVM: form validation; the banner text for each status (pure function); the default duration
  and its bounds.
- Emulator (a local calendar created for the purpose, and the owner's Google calendar on a
  phone): turn on with and without past entries; every permission path including "Open
  settings"; change calendar → banner → Update moves the events; change title source →
  Update; change default length → existing events unchanged, new ones use it; edit a length in
  the calendar app, then edit the entry in LifeLog → length kept; turn off with Keep and with
  Remove; rotation; `font_scale 1.30`; the reminder editor's snooze picker unchanged.

### 9. Dependencies
CAL-04.

### 10. Open questions / product decisions
None open. Defaults introduced here, listed in README §6 for the owner to veto: the initial
length of 30 minutes and its range (P-23); which field types can be a title (P-24).

### 11. Acceptance criteria
- [ ] Sync can be turned on, changed and turned off for an event entirely from its overflow menu.
- [ ] The disclosure is shown before the first calendar is chosen.
- [ ] Nothing is pushed by a settings change until the user taps Update.
- [ ] Every paused state is visible on Event Detail with a way to fix it.
- [ ] The reminder editor's duration picker looks and behaves as before.
- [ ] README's privacy section describes the feature accurately.

---

## CAL-06 — Re-push all

### 1. Objective
One place to bring every calendar back in line: push everything that is waiting, across all
events, and clear out calendar events LifeLog created that no longer correspond to an entry.

### 2. Current implementation
- After CAL-05, out-of-date links can be pushed one event at a time from Event Detail.
- `ui/settings/SettingsScreen.kt` has sections Appearance, Data & Backup and Import;
  `SettingsViewModel` injects the application context, preferences and the export/import
  engines. Long-running settings actions report through a snackbar (`:112–120`).

### 3. Required behaviour
- **Settings → Calendar → Re-push all.** Subtitle: "Everything is in sync", "37 entries
  waiting", or "Calendar sync is not turned on for any event". Shown always; disabled when no
  event has sync on.
- Tapping it runs, in order:
  1. a full drain — `PENDING`, `STALE` and `PENDING_DELETE`, for every event whose
     preconditions hold (CAL-03);
  2. an **orphan sweep**: in each calendar used by an enabled event, delete the events that
     carry LifeLog's stamp but that no link points to. (They arise when an in-app restore
     brings back an older database: entries logged after that backup are gone from LifeLog but
     their calendar events remain.)
- A dialog shows progress ("412 of 1 240") with **Cancel**. Cancelling stops after the current
  chunk; what remains stays marked.
- On finish: "Calendar is up to date", or "n entries could not be pushed" with the reason
  (access off, calendar unavailable, write failed).
- Events whose sync is paused are skipped and counted in the result.
- **Edge cases**: nothing waiting (the sweep still runs); permission missing (nothing runs;
  the message says so and offers the system settings page); several events sharing one
  calendar (swept once); a calendar used by no enabled event (never swept); the provider
  check in CAL-02 found that stamps do not survive sync (**the sweep is left out** — say so in
  the commit, and the acceptance box below is struck).

### 4. Technical design
- `CalendarSyncCoordinator.repushAll(): RepushResult` — `drainNow(includeStale = true)`, then
  the sweep: for each distinct enabled `calendarId`, `store.stampedEventIds(calendarId)`
  (add to `CalendarStore`: non-deleted events in that calendar whose `CUSTOM_APP_PACKAGE` is
  ours) minus `linkDao.calEventIdsForCalendar(calendarId)`, deleted in chunks; then one sync
  request. Runs under the same mutex as every other drain.
- `SettingsViewModel`: inject the coordinator; expose the waiting count and the progress;
  `repushAll()` and `cancelRepush()`.
- `SettingsScreen`: a "Calendar" section with one item and the progress dialog.

### 5. Data model / persistence changes
None.

### 6. UI/UX changes
One Settings section with one item, a progress dialog, a result message.

### 7. Interactions with existing functionality
- The sweep deletes only rows that carry LifeLog's stamp, in calendars an enabled event is
  using on this install — it cannot remove anything the user or another app created.
- It does not remove the duplicates that P-22 accepts on a new phone: those copies arrive from
  the account without a stamp and are indistinguishable from the user's own events.
- Auto-backup, export and restore are unaffected.

### 8. Testing requirements
- Instrumented (in-memory Room + `FakeCalendarStore`): re-push drains all three states; the
  sweep deletes a stamped event with no link and keeps — a linked event, an unstamped event, an
  event stamped by another package, and anything in a calendar no enabled event uses; cancel
  mid-run leaves the rest marked; paused events are skipped and counted.
- `AndroidCalendarStoreTest`: `stampedEventIds` on the test's local calendar.
- Emulator: restore an older backup in-app, then Re-push all — entries that existed at the
  backup are re-created or updated as needed, and events for entries that no longer exist are
  removed; progress and cancel behave on a few thousand entries.
- Mutation check: sweep without the stamp filter → the "keeps an unstamped event" test fails.

### 9. Dependencies
CAL-04. (CAL-05 is not required, but without it settings exist only by seeding.)

### 10. Open questions / product decisions
None.

### 11. Acceptance criteria
- [ ] Re-push all pushes every waiting link for every event that can sync, with progress and cancel.
- [ ] The orphan sweep removes only stamped, unlinked events in calendars in use (or is omitted per the provider check).
- [ ] The result message states what could not be pushed and why.
- [ ] Nothing in this spec runs without the user tapping the action.
