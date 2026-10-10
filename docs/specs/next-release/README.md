# LifeLog — Next Release Specification

| | |
|---|---|
| **Status** | Specification only. Nothing in this folder is implemented. |
| **Baseline** | `main` @ `b16dda8` (merge of PR #60, the audit fixes). The specs were written against `audit-fixes` @ `b31d8a9`; the merge produced an identical tree, so every file and line reference still holds. |
| **Written** | 2026-10-04, from a full read of the code at the baseline commit. |
| **Revised** | 2026-10-10, with the owner's decisions (§6): entry archiving and the long-press menu withdrawn; event archiving moved to Event Detail's overflow menu; formula editor settled; all-points/auto aggregation made conditional on a performance gate; bucket-size selection confirmed. The same day: **calendar sync (CAL-01 … CAL-06) added**, after a viability review and the owner's answers to its six questions (P-17 … P-22). |
| **Scope** | Computed fields (CF), event archiving (ARC), transparent/configurable chart aggregation (AGG), one-way calendar sync (CAL), plus foundations (FND) and a slot for ad-hoc tasks (ADH). |

## 0. How to use this folder

Each specification is one self-contained unit of work, sized for one session, in the same
working convention as the 2026-07 audit backlog: **one specification per session, one commit
per specification**. Say *"Implement specification CF-03"* and the implementing session should
need only:

1. this README (§1 architecture, §5 risks, §8 conventions), and
2. the spec's own section in its feature file (plus the feature file's *Shared design* section).

| File | Contents |
|---|---|
| [README.md](README.md) | Architecture assessment, dependency map, index, order, risks, open decisions |
| [FND-foundations.md](FND-foundations.md) | FND-01 |
| [CF-computed-fields-core.md](CF-computed-fields-core.md) | Shared CF design + language reference, CF-01 … CF-07 |
| [CF-computed-fields-windows.md](CF-computed-fields-windows.md) | CF-08 … CF-11 (cross-entry, rolling, time windows) |
| [ARC-archiving.md](ARC-archiving.md) | Shared ARC design, ARC-01 and ARC-03 (ARC-02 and ARC-04 are withdrawn) |
| [AGG-aggregation.md](AGG-aggregation.md) | Shared AGG design, AGG-01 … AGG-04 |
| [CAL-calendar-sync.md](CAL-calendar-sync.md) | Shared CAL design, CAL-01 … CAL-06 |
| [ADH-adhoc.md](ADH-adhoc.md) | Template for small tasks + ADH-01 |

**Path convention.** Source paths are relative to `app/src/main/kotlin/com/lifelog/app/` unless
they start with `app/` or `gradle/`. Line numbers are as of the baseline commit and will drift;
treat them as "look here", and re-verify before relying on them.

**Status column.** Update the index in §3 as specs land (`⬜` → `✅ <short-sha>`), exactly as
the audit backlog was tracked.

---

## 1. Architecture assessment (what this release builds on)

### 1.1 Shape of the app

Single-module Kotlin/Compose app. Hilt (`di/DatabaseModule.kt` is the only module; everything
else is constructor-injected `@Singleton`s). Layers:

```
ui/<feature>/*Screen.kt  ──collects──▶  *ViewModel (StateFlow)  ──▶  data/repository/*Repository
                                                │                         │
                                                ▼                         ▼
                                   domain/*  (pure Kotlin objects)   data/db (Room DAOs, entities, Mappers.kt)
```

- **Persistence** — Room 2.6.1, five tables (`event_types`, `event_fields`, `event_entries`,
  `reminders`, `chart_configs`), schema **version 1**, exported to
  `app/schemas/com.lifelog.app.data.db.LifeLogDatabase/1.json`. DataStore for preferences
  (`data/repository/UserPreferencesRepository.kt`).
- **Fields** — `EventFieldEntity` (`data/db/entity/EventFieldEntity.kt`): `id, eventTypeId, name,
  type (enum name as TEXT), optionsJson, unit, isRequired, sortOrder`. Domain `EventField`
  (`domain/model/EventField.kt`). `FieldType` = `NUMERIC, TEXT, BOOLEAN, CHOICE, MULTI_SELECT`.
- **Entry values** — one JSON column, `event_entries.fieldValuesJson`: a map
  `"<fieldId>" → FieldValue` (sealed, kotlinx-serialization polymorphic: `numeric`, `text`,
  `boolean`, `choice`, `multiselect`). A field with no value is simply **absent** from the map;
  there is no null-carrying value type. Decoded by the single function `decodeFieldValues`
  (`data/db/Mappers.kt:101`).
- **Two entry shapes** (introduced by audit item P2):
  `EventEntry` (values decoded eagerly — charts, filters, export, the entry editor) and the
  narrower `EntryRow` interface (`domain/model/EntryRow.kt`), implemented lazily by
  `LazyEntryRow` (`Mappers.kt:165`), which holds the raw JSON and decodes on first read of
  `fieldValues`. The Timeline is built entirely on lazy rows; **nothing may read
  `fieldValues` in a loop over a whole Timeline list**.
- **Read-side policy** (audit M8/A3, comment at `Mappers.kt:26`): mapping out of the database
  never throws; a bad row degrades only itself, visibly where possible.
- **Calculation today** — there is no arithmetic on stored values anywhere except chart
  aggregation (`ChartDataProcessor.aggregate`, `domain/ChartDataProcessor.kt:502`), the pie
  chart's per-category sum, and the tolerant numeric comparison in filters
  (`compareNumeric`, `domain/EntryQueryEngine.kt:152`).
- **Pure domain objects** — `ChartDataProcessor`, `ChartTickGenerator`, `LineChartAxisScale`,
  `EntryQueryEngine`, `RecurrenceCalculator`, `domain/csv/*`. Plain Kotlin `object`s, no Android
  imports, each with a JVM unit test. This is the house pattern for logic and the one every
  spec here follows.

### 1.2 How entries reach the screens (the seven read paths)

Every one of these is in `data/repository/EventRepository.kt`; a feature that changes what an
"entry" is must touch all of them.

| # | Method | Consumer | Shape |
|---|---|---|---|
| 1 | `observeEntriesForEventType` (:143) | Event Detail list **and** all its charts (`EventDetailViewModel._allEntries`, shared via `shareIn`) | `List<EventEntry>`, whole event, decoded |
| 2 | `observeAllEntryRows` (:165) | Timeline | `List<EntryRow>`, whole table, **lazy** |
| 3 | `getEntry` (:171) | Entry editor (`EntryViewModel.loadEntry`) — the only caller | one `EventEntry` |
| 4 | `getAllEntriesForEventType` (:209) | Per-event CSV export, ZIP-of-CSV export | whole event, decoded |
| 5 | `getRecentEntries` (:189) | Timeline widget (all) | `LIMIT 50` |
| 6 | `getRecentEntriesByEventType` (:214) | Timeline widget (one event) | `LIMIT 50` |
| 7 | `getRecentEntriesByCategory` (:220) | Timeline widget (one tag) | `LIMIT 50` |

The JSON export (`export/ExportEngine.buildExportData`, :282) bypasses the repository and dumps
raw DAO rows; the SQLite export copies the database file.

### 1.3 Charts

`EventDetailViewModel.chartDataMap` (`ui/events/EventDetailViewModel.kt:112`) runs
`ChartDataProcessor.process(config, entries, fields)` for every readable chart whenever the
entries, the fields or the chart list change, on `Dispatchers.Default`. Charts always see
**all** of the event's entries — the list's search/filter/sort does not affect them.

Pipeline inside `process` (`domain/ChartDataProcessor.kt:28`): stale-config check →
keep entries holding a chartable value → resolve the anchor (now, or the latest entry if the
now-anchored window would be empty) → time-window filter → build buckets → aggregate each
bucket with `config.aggregation`. Aggregation is **always on**; the bucket size is chosen by
the time range alone and is not shown anywhere:

| Range | Buckets (LINE/BAR) |
|---|---|
| Day | hourly, tiling the local day (23/24/25 on DST days) |
| Week | 7 daily |
| Month | **4 weekly** (28 days + the anchor day; the last bucket is 8 days) |
| Year | 12 calendar months |
| All | span-adaptive: hourly ≤ 26 h, daily ≤ 20 d, 7-day ≤ 140 d, 30-day ≤ 600 d, else 20 even bins |

Heatmaps always bucket per calendar day; pies always sum positive values per category.
The x-axis is **bucket-index space** (`ChartData.Cartesian.Point(bucketIndex, value)`), not
time. Rendering is Vico `2.0.0-beta.2`; chart cards are 280 dp × 224 dp, zoom pinned to
`Zoom.Content`, tap-to-inspect marker reads from `ChartData`.

### 1.4 UI conventions that matter here

- One entry-card system for Timeline and Event Detail: `ui/events/EntryCards.kt`
  (`entryCardItems` → `SwipeableEntryCard` → `EntryCard`). Tap expands; swipe start→end edits,
  end→start asks to delete. Lists are prepared off the main thread as an `EntryListModel`.
- Contextual actions today: swipe (entries, reminders), three-dot `DropdownMenu` (Event Detail
  app bar, chart cards), `ModalBottomSheet` for forms/pickers, `DeleteConfirmDialog`.
  **There is no long-press anywhere in the app** (no `combinedClickable`, no `onLongClick`).
- Bottom sheets are `Column(verticalScroll)` + `FlowRow`; never `LazyVerticalGrid` in a
  `ModalBottomSheet` (M3 1.3.1 gesture conflict).
- The Events list deliberately has **no delete affordance** (owner call, audit item D8,
  2026-08-28): deleting an event stays behind one navigation step, in Event Detail's overflow.
- Window-size layout (audit L14) is presentation-only: routes and the back stack are identical
  at every size; ≥ 840 dp × 480 dp the Events list is drawn once, outside the `NavHost`, beside
  `events` and `events/{eventId}`.

### 1.5 Tests

JVM unit tests (junit4 only — no mockk, no coroutines-test) for pure logic; view-model logic is
unit-tested only where it was **extracted into pure functions** (`EntryFormStateTest`).
Instrumented tests use in-memory Room + `runBlocking` (`app/src/androidTest`, 5 files).
`connectedDebugAndroidTest` reinstalls the app and **wipes the emulator's data** — run it last.

### 1.6 Shortcomings that materially affect this release

Documented rather than worked around; each is addressed by the spec named.

| # | Shortcoming | Affects | Handled in |
|---|---|---|---|
| S1 | **No migration infrastructure.** `Migrations.kt` was deleted at the 1.0 schema reset; `DatabaseModule` registers none; `room-testing` is not a dependency; schemas are not available to androidTest. There is no destructive fallback (by design), so the first schema change without a migration is a launch crash. | CF, ARC | FND-01 |
| S2 | **Full-row `@Update` clobbers columns the domain object does not carry.** `saveEventType` (:97) writes `eventType.toEntity()`; `CreateEventViewModel.persist` (:207) builds that `EventType` without `createdAt`, so **every event edit resets `createdAt` to now** (a live bug: it reshuffles the "Newest/Oldest created" sorts). Any new `event_types` column would be wiped the same way. | ARC | ADH-01, ARC-01 |
| S3 | **Field sets are replaced wholesale** (`deleteAllForEventType` + `insertAll`, ids preserved). Anything about a field that is not carried on `EventField` is destroyed by the next event save, and a new field has no id until the transaction runs. | CF | CF-03, CF-05 |
| S4 | **`EventEntry` knows field ids, not field definitions.** Anything derived per entry needs the field list joined in at the repository, on all seven read paths. | CF | CF-04, CF-11 |
| S5 | **Lazy Timeline rows cannot produce history-dependent values cheaply.** A rolling value needs every earlier entry of that event decoded. | CF | CF-11 |
| S6 | **Chart x-axis is bucket-index space**, and Vico's default x-step (GCD of x-deltas, rounded to 4 decimals) **throws** `IllegalArgumentException("The x values are too precise…")` for irregular fractional positions. | AGG | AGG-02 |
| S7 | **`ChartConfig` is one JSON column encoded without defaults** (`appJson` has `encodeDefaults = false`). A property's data-class default is therefore the permanent meaning of every stored chart that lacks the key. | AGG | AGG-02/03 |
| S8 | **Value formatting is duplicated** — `fieldDisplayValue` (`ui/events/EntryCards.kt:99`), `widgetFieldValue` (`widget/TimelineWidget.kt:228`), `toCsvCell` (`domain/csv/CsvWriter.kt:50`), and search via bare `displayString()` (`EventDetailViewModel.kt:81`). | CF | CF-07 |
| S9 | **No one-shot UI-event channel on `EventDetailViewModel`.** Results reach the screen through callbacks and local state (`exportResult`, `ui/events/EventDetailScreen.kt:107`); a snackbar with Undo needs an event that carries a payload. | ARC | ARC-03 |
| S10 | `ui/events/CreateEventScreen.kt` is 876 lines (audit A5, deferred). | CF | CF-06 puts the editor in a new file |
| S11 | **Entry writes are hard to hang anything on.** `saveEntry` (:176) is a full-row update from an object that carries only today's columns; any write to `event_entries` re-runs the Timeline, the Event Detail list and every chart (Room invalidates per table); and deleting an event removes its entries by cascade, with no per-entry call. | CAL | CAL-01 keeps calendar state in its own tables; CAL-04 hooks the three write paths |
| S12 | **Everything in the database travels** — Android backup and device transfer include `lifelog.db` (`app/src/main/res/xml/backup_rules.xml`), and an in-app restore swaps it — while anything that names a device resource (a calendar id, a calendar event id) is meaningful on one install only. | CAL | CAL-03 (install marker, calendar re-validation), CAL-02 (ownership test) |

---

## 2. Feature-level dependency map

```
Foundations
  FND-01  migration harness ─────────────▶ ARC-01, CF-03
  ADH-01  targeted event update ─────────▶ ARC-01

Archiving
  ARC-01  data layer ──▶ ARC-03  event archiving          (ARC-02 and ARC-04 are withdrawn)

Computed fields
  CF-01 parser ──▶ CF-02 evaluator ──┬──▶ CF-04 read paths ──┬──▶ CF-07 display + entry form
                                     │        ▲              ├──▶ CF-06 editor UI ◀── CF-05
  FND-01 ──▶ CF-03 column + model ───┼────────┘              └──▶ CF-11 history-aware read paths ◀── CF-08
                                     └──▶ CF-05 validation + save path   (needs CF-02 and CF-03)
  CF-02 ──▶ CF-08 cross-entry + expanding ──▶ CF-09 rolling (n) ──▶ CF-10 time windows

Aggregation
  AGG-01 disclosure ──▶ AGG-04 bucket size
  AGG-01 ──▶ AGG-02 all points (opens with a performance gate) ──▶ AGG-03 auto + threshold

Calendar sync
  FND-01 ──▶ CAL-01 tables ──┐
             CAL-02 calendar layer (opens with a provider check) ──┴──▶ CAL-03 engine ──▶ CAL-04 hooks ──┬──▶ CAL-05 per-event settings
                                                                                                        └──▶ CAL-06 re-push all
```

**Between features** (soft couplings — neither blocks the other, but the second to land must
honour the first; each spec's §7 says how):

- **ARC ↔ CF.** None. Archiving is per event and an archived event keeps every entry, so a
  computed field's table is unaffected; the Timeline and widgets simply do not list an archived
  event's entries.
- **CF ↔ AGG.** Computed values are produced before any chart processing, so charts need no
  change to plot them. A rolling or running field shown through bucket aggregation is smoothed
  twice; AGG-01's caption is what makes that visible and AGG-02's all-points mode is what
  shows it faithfully.
- **ARC ↔ AGG.** An archived event renders no charts (ARC-03). Nothing else.
- **CAL ↔ CF.** Calendar descriptions include an event's pointwise computed values when CF-04
  exists, and leave history-dependent ones out (they change when other entries change). CAL
  does not need CF; whichever lands second follows CAL-03 §4. A formula edit counts as an
  event-level change (CAL-04).
- **CAL ↔ ARC.** Archiving leaves calendar events alone (P-20); deleting an event removes them.
- **CAL ↔ ADH-01 / CF-05.** All three edit `saveEventType`; land them one at a time.
- **Schema versions** are allocated in implementation order: each schema-changing spec
  (ARC-01, CF-03, CAL-01) takes `LifeLogDatabase.SCHEMA_VERSION + 1` as it finds it.

---

## 3. Specification index

| ID | Title | Depends on | Schema | Status |
|---|---|---|---|---|
| **FND-01** | Room migration harness | — | none (infrastructure) | ⬜ |
| **ADH-01** | Event edits must not rewrite `createdAt` (targeted update) | — | none | ⬜ |
| **CF-01** | Expression parser | — | none | ⬜ |
| **CF-02** | Evaluator and dependency resolution (pointwise) | CF-01 | none | ⬜ |
| **CF-03** | Computed-field persistence and model | FND-01 | `event_fields.computedSpecJson` | ⬜ |
| **CF-04** | Read-path integration | CF-02, CF-03 | none | ⬜ |
| **CF-05** | Validation and save-path support | CF-02, CF-03 | none | ⬜ |
| **CF-06** | Formula editor UI | CF-04, CF-05 | none | ⬜ |
| **CF-07** | Display formatting and entry-form presentation | CF-04 | none | ⬜ |
| **CF-08** | Cross-entry evaluation and expanding functions | CF-02 | none | ⬜ |
| **CF-09** | Rolling count-window functions | CF-08 | none | ⬜ |
| **CF-10** | Time-based window functions | CF-09 | none | ⬜ |
| **CF-11** | History-aware read paths | CF-04, CF-08 | none | ⬜ |
| **ARC-01** | Archive data layer | FND-01, ADH-01 | `event_types.archivedAt` | ⬜ |
| ~~ARC-02~~ | ~~Long-press action menu foundation~~ | — | — | withdrawn 2026-10-10 |
| **ARC-03** | Event archiving | ARC-01 | none | ⬜ |
| ~~ARC-04~~ | ~~Entry archiving~~ | — | — | withdrawn 2026-10-10 |
| **AGG-01** | Aggregation disclosure | — | none | ⬜ |
| **AGG-02** | All-points mode for line charts *(opens with a performance gate; shelved if it fails)* | AGG-01 | none (`ChartConfig` JSON field) | ⬜ |
| **AGG-03** | Automatic aggregation with a global threshold *(shelved together with AGG-02)* | AGG-02 | none (DataStore key) | ⬜ |
| **AGG-04** | User-selectable bucket size | AGG-01 | none (`ChartConfig` JSON field) | ⬜ |
| **CAL-01** | Calendar-sync persistence | FND-01 | new tables `calendar_sync_configs`, `calendar_links` | ⬜ |
| **CAL-02** | Calendar layer *(opens with a provider check on a real account)* | — | none | ⬜ |
| **CAL-03** | Sync engine | CAL-01, CAL-02 | none | ⬜ |
| **CAL-04** | Repository hooks and lifecycle | CAL-03 | none | ⬜ |
| **CAL-05** | Per-event calendar settings | CAL-04 | none | ⬜ |
| **CAL-06** | Re-push all | CAL-04 | none | ⬜ |

25 active specifications (27 written, 2 withdrawn — ids are never reused). New small tasks are appended to [ADH-adhoc.md](ADH-adhoc.md) as `ADH-02`,
`ADH-03`, … and to this table; they never renumber the feature groups.

---

## 4. Recommended implementation order

| Step | Spec | Why here |
|---|---|---|
| 1 | FND-01 | Everything that touches the schema needs it; changes no behaviour, so it is the safest first commit. |
| 2 | ADH-01 | Small, fixes a live bug, and removes the hazard ARC-01 would otherwise have to work around. |
| 3 | AGG-01 | No dependencies, no schema, immediately visible. A good first user-facing commit. |
| 4 | AGG-04 | Confirmed (P-14). The direct fix for "a month shows four weekly points"; needs only AGG-01. |
| 5 | ARC-01 → ARC-03 | Small now that archiving is event-only. |
| 6 | CF-01 → CF-02 | Pure Kotlin, invisible to the app; can also be interleaved earlier. |
| 7 | CF-03 → CF-04 | After CF-04 a hand-seeded formula shows up in lists, charts, filters, widgets and CSV with no UI work — the checkpoint that proves the architecture. |
| 8 | CF-05 → CF-06 | The feature becomes usable end to end. |
| 9 | CF-07 | Polished display and the live preview in the entry form. |
| 10 | CAL-01 → CAL-02 → CAL-03 → CAL-04 → CAL-05 → CAL-06 | Depends only on FND-01, so it can move anywhere after step 1. Placed after CF-07 so calendar descriptions are built once, with computed values and the shared formatter; if calendar sync is wanted sooner, do it right after step 2 and CAL-03 §4 says what to add when CF lands. CAL-02 starts with a check on your phone (R14). |
| 11 | AGG-02 → AGG-03 | AGG-02 starts with a measured go/no-go (R13). If it fails, both are shelved and nothing else in the release is affected. |
| 12 | CF-08 → **CF-11** → CF-09 → CF-10 | CF-11 directly after CF-08: between them, history-dependent fields show on Event Detail but not on the Timeline or widgets. CF-09/10 are pure registry work after that. |

Minimum releasable cut, if the release needs trimming: steps 1–9 (pointwise computed fields,
event archiving, disclosed aggregation with selectable bucket size). Steps 10–12 are additive
and independent of one another.

---

## 5. Cross-cutting risks and architectural concerns

**R1 — First post-1.0 migrations, and no way back.** Room has no downgrade path here. Once a
build with schema v2 has opened the database on the daily-driver device, an older build
(including today's `main` build) crashes on launch, and `SqliteRestore.validate`
rejects a v2 backup in an older build. **Before installing the first schema-changing build
(ARC-01 or CF-03), take a manual SQLite export** (Settings → Export Now → SQLite) so there is a
v1 file to restore into an older build if needed. Every migration spec carries a
`MigrationTestHelper` test and an on-device upgrade check over real data.

**R2 — Baseline.** `audit-fixes` was merged to `main` by PR #60 and the branch deleted
(2026-10-10). `main` @ `b16dda8` has exactly the tree these specs were written against.
Release work accumulates on the **`next-release`** branch, cut from that commit, behind one
draft pull request to `main` — the arrangement the audit fixes used.

**R3 — The Timeline's laziness (P2) versus history-dependent fields.** Pointwise computed
values stay lazy (computed when a card is drawn). Rolling/running values cannot: CF-11 decodes
every entry of an event that defines such a field, on every `event_entries` change, inside the
Timeline's `flowOn(Dispatchers.Default)` pipeline. It is paid only for events that opted in,
but it is the one place this release spends the performance P2 bought. CF-11 must be measured
with the existing harness (5 000-entry dataset, `Choreographer: Skipped N frames`, three runs
per build) before it is called done.

**R4 — Defaults are forever (S7).** `ChartConfig` and the computed-field spec are JSON encoded
without defaults. A new property's default must be the *legacy* behaviour and must never be
changed afterwards; "new things default differently" is done by the UI writing an explicit
non-default value.

**R5 — Strict enum decoding.** kotlinx-serialization rejects an unknown enum constant, which
turns a whole `ChartConfig` into `StoredChartConfig.Unreadable`. Harmless going forward;
relevant only if a newer database is ever read by an older build (already impossible per R1).

**R6 — Vico is pinned to a beta.** AGG-02 depends on `rememberCartesianChart(getXStep = …)`
and on `LineCartesianLayer.pointProvider` being nullable — both verified present in
`2.0.0-beta.2` sources. Do not bump Vico inside an AGG spec.

**R7 — Floating point becomes routine.** Until now every stored `Double` was parsed from typed
text. Computed values are the first arithmetic results. Three guards already exist or are
specified: non-finite results never materialise (CF-02), filters compare with tolerance
(`compareNumeric`, audit L5 — written with this feature in mind), and display rounds to six
significant digits unless told otherwise (CF-07).

**R8 — Two notions of "a day".** Chart buckets are local-calendar days (DST-aware, audit L4).
Time windows in formulas (CF-10) are fixed elapsed durations, because a formula's result must
not depend on the device's time zone (a restored backup must compute the same numbers
anywhere). The difference is deliberate; it is up to an hour, twice a year.

**R9 — Shared files touched by several specs.** `data/repository/EventRepository.kt` (ARC-01,
CF-04, CF-05, CF-11), `ui/events/EventDetailScreen.kt` + `EventDetailViewModel.kt` (ARC-03,
AGG-03, CF-07), `domain/ChartDataProcessor.kt` + `ChartData.kt` (AGG-01/02/03/04),
`data/db/Mappers.kt` (ARC-01, CF-03, CF-04). One spec per session, in order, avoids conflicts; working two of these
in parallel worktrees will not.

**R10 — Test infrastructure is deliberately thin.** No mocking, no coroutine test dispatcher.
Follow the established pattern: put logic in pure functions/objects and unit-test those; test
DAOs and migrations on device; verify view-model wiring on the emulator. Do not add test
dependencies beyond `room-testing` (FND-01) without an owner call.

**R11 — Release (R8-minified) builds.** New `@Serializable` types rely on the library's own
consumer rules (audit L8). CF-03 includes one release-build round trip to prove the spec
codec survives minification.

**R12 — Widgets read through `LIMIT` queries.** Any filtering (archive) must be in SQL, before
the limit, and any history-dependent value needs the full table of its event fetched
separately (CF-11).

**R13 — Drawing many points is not free in this chart library.** On every draw, for every
series, Vico's line layer rebuilds the path, rasterises it into an off-screen bitmap in
software, and reads one pixel back per point for its marker targets
(`LineCartesianLayer.drawInternal`, Vico `2.0.0-beta.2`). Cost grows linearly with points and
is paid again whenever the chart is redrawn (opening the screen, the inspect marker, possibly
each frame of a carousel scroll). Today's charts never exceed about 27 points. The designs
bound the exposure — Auto never draws more than the threshold un-grouped, All-points has a
hard ceiling, AGG-04 caps buckets at 400 — and AGG-02 begins with a measured go/no-go. If the
gate fails, AGG-02 and AGG-03 are shelved (owner decision, 2026-10-10); AGG-01 and AGG-04
stand on their own.

**R14 — Calendar sync rests on provider behaviour that source code cannot confirm.** The AOSP
provider shows that the two stamp columns are ordinary writable columns and how deletes
behave; it cannot show what an account's sync adapter does to a row afterwards. CAL-02
therefore begins with a check on the owner's phone against a real Google calendar (stamp
survives a sync round trip; no notification is attached; delete semantics; upload latency).
If the stamp does not survive, CAL-D4's ownership test cannot be met as written — stop and
decide before CAL-03.

**R15 — Calendar sync changes what the app can claim.** The README says there is "no cloud to
sync to". That stays true of LifeLog itself — no internet permission, no server — but an
entry copied into an account-synced calendar leaves the device, and entries are often health
data. CAL-05 puts a disclosure in front of the first calendar choice and rewords the README;
declaring the calendar permissions also changes the Play listing's data-safety answers
(owner task).

**R16 — Ids that outlive their device.** A calendar id or calendar event id stored in the
database is carried to other installs by backup, transfer and restore (S12). The design never
trusts one without proof: settings from another install are switched off (CAL-D5), the chosen
calendar is re-validated by account and name before every push, and no calendar row is
updated or deleted unless it carries LifeLog's stamp for that entry (CAL-D4). The price,
accepted in P-22, is duplicates when history is pushed again on a new phone.

**Minor observations made during inspection** (not specs; fold into whichever spec touches the
file): `EventEntryDao.getLatestForEventType` has no caller (ARC-01 deletes it);
`ReminderEntity`'s comment "empty → migrate from legacy" describes code removed at the schema
reset.

---

## 6. Product decisions

### Decided by the owner (2026-10-10)

| ID | Decision | Outcome | Applied in |
|---|---|---|---|
| **P-01** | Formula editor style | **Plain text with `[Field]` references**, plus the type-ahead idea in the form that works on a phone keyboard: one *suggestion strip* under the formula that filters fields and functions as you type, offers fields and functions when a value is expected, and offers operator keys (`+ − × ÷ ^ ( ) ,`) when a value has just been completed. A strip rather than a floating dropdown: operators have no prefix to type ahead on, and a focusable popup anchored to a text field inside a bottom sheet with the keyboard open is fragile. | CF-06 |
| **P-02** | Meaning of `Log(x)` | **Natural log.** `log10(x)` and `log(x, base)` stay as conveniences (`log10(x) = log(x) / log(10)` either way). | CF-01 |
| **P-03** | Window semantics | **Skip missing values; partial windows produce a value.** | CF-08, CF-09 |
| **P-07** | What an archived event can do | Browsable; **no new entries until unarchived**; hidden from the Events list; **charts are not rendered**. | ARC-03 |
| **P-08** | Reminders of an archived event | **Turned off on archive.** | ARC-03 |
| **P-09** | Where the archive action lives | **Event Detail's three-dot overflow menu. No long-press.** ARC-02 is withdrawn. | ARC-03 |
| **P-11 / P-12** | Auto mode and the global threshold | **Accepted as specified, provided performance does not suffer; otherwise shelve.** AGG-02 therefore opens with a measured gate (R13). | AGG-02, AGG-03 |
| **P-14** | User-selectable bucket size | **Build it.** AGG-04 is no longer optional. | AGG-04 |
| — | Archiving individual entries | **Withdrawn.** ARC-04 and the `event_entries.archivedAt` column are removed. | ARC |

### Decided by the owner — calendar sync (2026-10-10)

The brief fixed the shape (one-way, `CalendarContract` only, mapping in LifeLog's database,
off by default, per event from Event Detail's overflow menu, no reminders). The six questions
from the viability review were answered "as recommended":

| ID | Decision | Outcome | Applied in |
|---|---|---|---|
| **P-17** | Where the entry ↔ calendar-event mapping lives | **A side table**, one row per entry — not columns on `event_entries`. | CAL-01 |
| **P-18** | A length changed in the calendar app | **Kept.** LifeLog sets the length on insert and re-create only. | CAL-02 |
| **P-19** | Turning sync off | **Ask**: keep the calendar events (default) or remove them. | CAL-03, CAL-05 |
| **P-20** | Archiving a synced event | **Its calendar events stay.** | CAL-04 |
| **P-21** | Event-level changes (rename, field changes, new calendar or title source) | **Mark out of date and offer "Update N calendar events now"**; push nothing unasked. | CAL-03, CAL-04, CAL-05 |
| **P-22** | Duplicates after moving to a new phone or restoring | **Accepted, with a warning**; no visible marker in descriptions. | CAL-05, CAL-06 |

### Still on their defaults (not yet answered — the specs assume the default)

| ID | Decision | Default in the specs | Blocks |
|---|---|---|---|
| **P-04** | Functions beyond the stated list: `prev(x)` / `delta(x)` and `coalesce(a, b, …)`. | Include all three. | CF-01, CF-08 |
| **P-05** | Yes/No fields in formulas as 1/0. | Not in this release; formulas use Number fields only. | CF-05 |
| **P-06** | **Where archived events are found again.** You said archived events should not show up on the Events screen, and that you prefer actions out of the way, so the earlier "Archived (N)" row in the Events list was dropped. | **Settings → Archived events** (the same reasoning that put CSV import in Settings: a rare action). Alternative if you want it closer: an "Archived (N)" row at the foot of the Events list. | ARC-03 |
| **P-10** | Does the ZIP-of-CSV export include archived events? (SQLite and JSON exports are full table dumps and always include them; exporting one archived event's CSV from its own screen always works.) | The ZIP skips archived events, and the export dialog says so. | ARC-01 |
| **P-13** | All-points mode for bar charts. | Not offered; bars stay grouped. | AGG-02 |
| **P-15** | ADH-01 — fix "editing an event resets its creation date". | Fix it; ARC-01 needs the same change regardless. | ADH-01 |
| **P-16** | The July design document is superseded by the CF files here (§7). | Leave it where it is; do not commit it. | — |
| **P-23** | Calendar sync: the default length of a new calendar event, before the user picks one. | 30 minutes; presets 5, 10, 15, 30 min, 1 h, 2 h, 6 h, 1 day; custom from 1 minute to 7 days. | CAL-05 |
| **P-24** | Calendar sync: which fields may supply the title. | Entered Text, Number, Single-choice and Multi-select fields; not Yes/No, not computed. Falls back to the event's name when empty. | CAL-05 |
| **P-25** | Calendar sync: four things proposed in the viability review and applied without a separate question — events are marked **Free**; each carries a hidden **stamp** naming LifeLog and the entry; history-dependent computed values are **left out** of descriptions; deleting a LifeLog event **deletes its calendar events**. | As stated. | CAL-02, CAL-03, CAL-04 |

---

## 7. Relationship to the July computed-fields design

A design for computed fields was approved on 2026-07-20 (version 1: per-entry expressions
only). This release re-derived the design from the current code, as asked. What survived, what
changed, and why:

**Carried forward unchanged** — compute on read, never persisted; columnar evaluation; stored
references by immutable field id; missing-in → missing-out with math errors as missing; table
order `createdAt` then `id`; pure and deterministic; numeric-only, same-event scope; versioned
spec that is salvaged, never fatal; pure-Kotlin engine; source text persisted, not the AST.

**Changed**

| July design | Now | Reason |
|---|---|---|
| New `FieldType.COMPUTED` | A computed field is a `NUMERIC` field with a formula | Nine places select fields with `type == NUMERIC` (`ChartConfigSheet.kt:68,136,137,210`, `ChartDataProcessor.kt:489,491`, `FilterSortSheet.kt:52`, `EventDetailScreen.kt:132`, `CreateEventScreen.kt:818`). Those are equality checks, not exhaustive `when`s, so a new enum value would be silently excluded from each until someone remembered it. As a numeric field, charts/filters/sort need **no** change. |
| Cross-entry operations deferred to a later version | In scope (CF-08 … CF-11) | Now a stated requirement. |
| Timeline/widgets augment per entry | Lazy per row for pointwise; eager per event for history-dependent | The Timeline became lazy (P2) after the July design was written. |
| CSV import skips columns that match a computed field | Not applicable | There is no import-into-existing-event path; the wizard only creates new events (audit M7/D2). |
| `getEntry` augments | `getEntry` returns stored values only | It has one caller, the editor; keeping computed values out of the edit path is simpler and safer than stripping them on the way back in (the strip stays, as a backstop). |
| Grammar `power := unary ('^' power)?` | `unary := '-' unary \| power` | The July grammar parses `-2^2` as `(-2)^2 = 4` while its own test table expected `-4`. |
| Tokenised chip editor, template gallery | Plain-text formula with `[Name]` references and a context-aware suggestion strip (P-01, decided) | Scope and risk; and the strip gives type-ahead without custom text-editing code. |
| CSV export formats computed values with display rounding | Full precision | CSV is an analysis artefact; rounding belongs to display. |

---

## 8. Conventions for every implementation session

- **Branch**: everything lands on `next-release` (one draft pull request to `main`, kept open
  until the release is done). A session starts from `origin/next-release`, adds the commit for
  its spec, pushes to that branch, and ticks the spec in the pull request's checklist and in
  §3. One spec → one commit, message in the existing style
  (`feat(scope): … (CF-03)`).
- **Do not** add dependencies, abstractions or files a spec does not name without saying so.
- **Pure first**: logic goes in `domain/…` objects with JVM tests; view models stay thin.
- **Never-throw-on-read** applies to everything new that is decoded from the database or
  DataStore.
- **Mutation-check** regression tests where a spec fixes or pins a behaviour (revert the fix
  in place, confirm the test fails), as the audit sessions did.
- **Verification on device** uses the established recipes: seed Room via `run-as` (no device
  `sqlite3`), open an event with `am start --el open_event_id <id>`, check clipping at
  `font_scale 1.30`; tablet emulation with `wm size 2560x1600; wm density 320` (reset after).
  `connectedDebugAndroidTest` wipes app data — run it last and re-seed.
- **Build environment**: JBR `JAVA_HOME`; a fresh worktree needs the Gradle wrapper jar and
  `local.properties` copied in.
- **When a spec's premise turns out wrong** against the code as it then is, stop and report
  rather than adapting silently — and correct the spec file in the same commit.
- **After landing**: tick §3's status column; note anything discovered for a later spec in
  that spec's section.
