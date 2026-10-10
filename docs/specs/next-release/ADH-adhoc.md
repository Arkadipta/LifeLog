# ADH — Ad-hoc tasks

Small, self-contained requests that arrive during the release and belong to none of the three
features. This file is the place to put them so they never disturb the CF / ARC / AGG
numbering.

## How to add one

1. Take the next free id (`ADH-02`, `ADH-03`, …). Ids are never reused or renumbered.
2. Copy the template below, fill every heading (write "None" rather than deleting one).
3. Add a row to the index in [README.md](README.md) §3.
4. If the task turns out to need a schema change, it takes `SCHEMA_VERSION + 1` like any other
   spec and follows the rules in `data/db/Migrations.kt` (FND-01).
5. If it grows past one session or starts to depend on feature specs in both directions, it is
   not ad hoc — promote it into the feature file it belongs to.

### Template

```markdown
## ADH-NN — <short, unambiguous title>

### 1. Objective
### 2. Current implementation      (files, functions, line refs as of the commit you read)
### 3. Required behaviour          (exact behaviour, edge cases)
### 4. Technical design
### 5. Data model / persistence changes
### 6. UI/UX changes
### 7. Interactions with existing functionality
### 8. Testing requirements
### 9. Dependencies
### 10. Open questions / product decisions
### 11. Acceptance criteria
```

Paths are relative to `app/src/main/kotlin/com/lifelog/app/` unless they start with `app/`.

---

## ADH-01 — Event edits must not rewrite `createdAt` (targeted update)

*Found while inspecting the save path for ARC-01. Not requested; listed as decision P-15.*

### 1. Objective
Editing an event must change only what the editor edits. Today it also rewrites the event's
creation timestamp, and the same mechanism would silently wipe any column added to
`event_types` later (ARC-01's `archivedAt`).

### 2. Current implementation
- `ui/events/CreateEventViewModel.persist` (:207–225) builds
  `EventType(id = existingId, name, description, category, colorArgb, iconName, fields)` —
  `createdAt` is not passed, so it takes the data-class default `System.currentTimeMillis()`
  (`domain/model/EventType.kt:11`).
- `data/repository/EventRepository.saveEventType` (:97–111), for an existing id, calls
  `eventTypeDao.update(eventType.copy(updatedAt = now).toEntity())` — a full-row `@Update`
  (`data/db/dao/EventTypeDao.kt:38`), and `EventType.toEntity()` (`data/db/Mappers.kt:53`)
  copies `createdAt` across.
- Net effect: every edit sets `event_types.createdAt = now`.
  `EventFilterUseCase.sortEventTypes` (`domain/EventFilterUseCase.kt:67–68`) sorts
  `CREATED_NEWEST` / `CREATED_OLDEST` on that column, so an edited event jumps to "newest".
- Verified by reading the full path. Repro: create events A then B; sort *Newest created* →
  B, A; edit A's description and save → A, B.

### 3. Required behaviour
- Saving an existing event updates `name`, `description`, `category`, `colorArgb`, `iconName`
  and `updatedAt`, replaces the field set as today, and leaves every other column of the row
  untouched — `createdAt` now, and any column added later.
- Creating a new event is unchanged (`createdAt = updatedAt = now`).
- Edge cases: an edit that changes nothing still advances `updatedAt` (as today); the CSV
  wizard's event creation (`csv/CsvImportEngine.import`, :128) is an insert and is unaffected.

### 4. Technical design
- `EventTypeDao`: add
  ```kotlin
  @Query("""UPDATE event_types SET name = :name, description = :description,
            category = :category, colorArgb = :colorArgb, iconName = :iconName,
            updatedAt = :updatedAt WHERE id = :id""")
  suspend fun updateDetails(id: Long, name: String, description: String, category: String,
                            colorArgb: Int, iconName: String, updatedAt: Long)
  ```
- `EventRepository.saveEventType`: the `else` branch calls `updateDetails(…)` instead of
  `update(…)`. Stay inside the existing `db.withTransaction`.
- Delete `EventTypeDao.update` if it has no other caller (it has none at the baseline).
- Add one sentence to `saveEventType`'s KDoc: writes are targeted so they never touch columns
  the editor does not own.

### 5. Data model / persistence changes
None (no schema change). Existing rows whose `createdAt` was already rewritten by past edits
cannot be recovered and are left as they are.

### 6. UI/UX changes
None. The created-date sorts stop moving edited events.

### 7. Interactions with existing functionality
- `observeEventType` / `observeAllEventTypes` re-emit as before (`updatedAt` still changes).
- JSON export, SQLite backup: unaffected.
- ARC-01 relies on this: with a full-row update, editing an archived event would un-archive it.

### 8. Testing requirements
- Instrumented (in-memory Room, alongside `EventTypeFieldsQueryTest`):
  `saveEventType_edit_keepsCreatedAt_advancesUpdatedAt` — insert with a fixed old `createdAt`,
  edit through the repository, assert `createdAt` unchanged, `updatedAt` larger, name changed,
  field set replaced with ids preserved.
- Mutation check: restore the full-row update in place → the test fails.
- Emulator: the repro in §2 no longer reorders.

### 9. Dependencies
None. (ARC-01 depends on this.)

### 10. Open questions / product decisions
P-15 — confirm the fix is wanted as specified. No alternative behaviour is proposed.

### 11. Acceptance criteria
- [ ] Editing an event leaves `createdAt` unchanged (test + emulator repro).
- [ ] `saveEventType` no longer performs a full-row update of `event_types`.
- [ ] New-event creation and field replacement behave as before; unit and instrumented suites green.
