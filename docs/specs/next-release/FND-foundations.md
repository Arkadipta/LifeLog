# FND — Foundations

Infrastructure that more than one feature needs. Read [README.md](README.md) §1 and §8 first.
Paths are relative to `app/src/main/kotlin/com/lifelog/app/` unless they start with `app/` or
`gradle/`.

---

## FND-01 — Room migration harness and restore hardening

*Revised 2026-11-16 (spec review, finding 1): the restore path is hardened in the same spec,
because registering migrations is what makes "an older backup is migrated on open" a real code
path for the first time — and the restore's safety guarantee stops one step short of it.*

### 1. Objective
Make schema changes possible, registered and testable, without changing the schema. After this
spec a later spec adds a migration by writing one `Migration` object, one schema hash and one
test method. And make sure a restore can never swap in a database file that this build cannot
open.

### 2. Current implementation
- `data/db/LifeLogDatabase.kt:24,38` — `version = 1`, `exportSchema = true`,
  `SCHEMA_VERSION = 1` (a const that must equal the annotation; `export/SqliteRestore.kt:36`
  reads it as `CURRENT_SCHEMA_VERSION`).
- `di/DatabaseModule.kt:25` — `Room.databaseBuilder(…).build()`: no migrations, and
  deliberately no `fallbackToDestructiveMigration*`.
- `data/db/Migrations.kt` does not exist (deleted at the 1.0 schema reset).
- `app/build.gradle.kts` — schemas exported through the KSP argument
  `room.schemaLocation = $projectDir/schemas`; no Room Gradle plugin.
  `app/schemas/com.lifelog.app.data.db.LifeLogDatabase/1.json` is committed
  (identity hash `2a727314c2739a8a814b461b6f56aebb`).
- `gradle/libs.versions.toml` — no `room-testing`. androidTest dependencies are ext-junit and
  the runner only; tests use `Room.inMemoryDatabaseBuilder` (e.g.
  `app/src/androidTest/kotlin/com/lifelog/app/data/db/EventTypeDeleteDbTest.kt`).
- `export/SqliteRestore.validate` (:90, version check :121) accepts a backup whose
  `user_version` is in `1..CURRENT_SCHEMA_VERSION` on the stated assumption that "Room
  migrates older ones on open". With no migrations registered that assumption is false the
  moment the version is bumped.
- **The version number is the only thing `validate` knows about the schema**, and the number
  is not unique. The comment at `SqliteRestore.kt:125–128` says so: pre-1.0 builds used
  `user_version` 2, 3 and 4 before the schema reset restarted numbering at 1, "so legacy
  backups carry higher numbers that future releases will reuse". This release reuses exactly
  those three numbers (ARC-01, CF-03, CAL-01). A pre-1.0 file would then pass the range check.
- **Nothing proves Room can open the file before the swap.**
  `applyStagedRestoreIfPresent` (:197) re-validates, copies the live database to a rollback
  file, copies the staged file over the live one, and deletes the rollback at once (:226) —
  before Room has opened anything. If Room then rejects the file (identity-hash mismatch, or a
  migration that throws or leaves the wrong shape), the first query throws; the first one runs
  in `LifeLogApp.onCreate`'s `appScope.launch { reminderCoordinator.ensureArmedOnAppStart(…) }`
  (`LifeLogApp.kt:43`), which has no handler, so the process dies on every launch.
- **The way out destroys the backups.** Auto-backups default to app-private storage
  (`File(context.filesDir, "backups")`, `export/ExportEngine.kt:175`); clearing app data to
  escape the crash loop deletes them with everything else.
- `app/src/androidTest/kotlin/com/lifelog/app/export/SqliteRestoreValidateTest.kt` builds its
  test files with raw SQL (`createDbFile`, :42) and does not write `room_master_table`.

### 3. Required behaviour
- No schema change, no version bump. The app behaves as before except in the restore path
  (below).
- A registry of migrations exists and is passed to the database builder.
- An instrumented test can create a database at any exported schema version, run the
  registered migrations up to the current version, and have Room validate the result against
  the entities.
- **Edge cases**: the registry is empty today — the test must still pass at version 1 (it
  proves the wiring: schema assets are found, the helper opens, validation runs).
  The registry must be contiguous: for every `v` in `1 until SCHEMA_VERSION` there is exactly
  one migration `v → v+1`.

**Restore**
- A backup is accepted only when its schema is one this build knows: its `user_version` is in
  `1..SCHEMA_VERSION` **and** the identity hash Room wrote into it
  (`room_master_table.identity_hash`) equals the hash recorded for that version. A file with
  the right number and the wrong hash is rejected at the picker, with the existing
  "can't read this format" message — the live database and the staged file are never touched.
- A staged restore is swapped in only after this build has **actually opened it**: the staged
  copy is opened through Room with the registered migrations (which migrates it to the current
  version and runs Room's own validation) before the live database is replaced. If that open
  fails, the staged file is discarded, the live database is left exactly as it was, and the
  user sees "The restore could not be completed. Your previous data was kept."
- The guarantee stated at the top of `SqliteRestore.kt` — the current database remains
  untouched if any validation or restore step fails — therefore now covers migration too.
- **Edge cases**: a pre-1.0 file numbered 2, 3 or 4 (rejected by hash); a file with no
  `room_master_table` (rejected: not a Room database); a current-version backup (accepted, the
  pre-open is a plain open); an older-version backup (accepted, migrated on the staged copy,
  then swapped in already at the current version); a migration that throws on a particular
  backup (staged copy discarded, live data kept); the on-device auto-backup picker
  (`ImportEngine.restoreFromFile`) — same checks, same code.

### 4. Technical design
1. **`data/db/Migrations.kt`** (new):
   ```kotlin
   /** Every schema step since the 1.0 baseline, in order. See the rules below. */
   val ALL_MIGRATIONS: Array<Migration> = arrayOf()
   ```
   with a file-level comment stating the rules every later spec follows:
   - one hand-written `Migration(n, n + 1)` per version step; never an auto-migration, never
     destructive;
   - `ALTER TABLE … ADD COLUMN` for a nullable column carries **no `DEFAULT` clause**, so the
     migrated table matches what Room generates for the entity;
   - bump `@Database(version)` **and** `SCHEMA_VERSION` together; commit the new
     `app/schemas/…/<n+1>.json`;
   - add a test method to `MigrationTest` that seeds the previous version with data and asserts
     the data after migrating;
   - add the new version's identity hash to `KNOWN_SCHEMA_HASHES` (below), copied from
     `database.identityHash` in the exported `<n+1>.json`;
   - **a migration is frozen once a build containing it has opened a real database** (the
     owner's phone counts). A mistake found afterwards is fixed by a further version step,
     never by editing the migration: a device already at that version would be left with a
     shape no later build expects.

   In the same file:
   ```kotlin
   /** Room's identity hash for every schema version this app has shipped, by version. */
   val KNOWN_SCHEMA_HASHES: Map<Int, String> = mapOf(
       1 to "2a727314c2739a8a814b461b6f56aebb"
   )
   ```
2. **`di/DatabaseModule.kt`**: `.addMigrations(*ALL_MIGRATIONS)` before `.build()`.
3. **`gradle/libs.versions.toml`**: `androidx-room-testing = { group = "androidx.room",
   name = "room-testing", version.ref = "room" }`.
4. **`app/build.gradle.kts`**:
   - `androidTestImplementation(libs.androidx.room.testing)`
   - inside `android { }`: `sourceSets["androidTest"].assets.srcDir("$projectDir/schemas")`
     (this is how `MigrationTestHelper` finds the exported JSON).
5. **`app/src/androidTest/kotlin/com/lifelog/app/data/db/MigrationTest.kt`** (new):
   - `@get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),
     LifeLogDatabase::class.java)`.
   - `internal fun seedV1(db: SupportSQLiteDatabase)`: raw `INSERT`s giving one event type, two
     fields (one NUMERIC, one CHOICE with options), three entries with real
     `fieldValuesJson`, one reminder linked to the event, one chart config. Raw SQL, not
     entities — entities describe the *current* schema, the seed must describe version 1.
   - `baseline_opensAndValidates()`: `helper.createDatabase(DB, 1)` → `seedV1` → close →
     `helper.runMigrationsAndValidate(DB, LifeLogDatabase.SCHEMA_VERSION, true,
     *ALL_MIGRATIONS)` → assert the seeded row counts are intact.
   - `registryIsContiguous()`: assert `ALL_MIGRATIONS` covers `1..SCHEMA_VERSION` with no gap
     or duplicate (`startVersion`/`endVersion`).
   - `opensThroughTheRealBuilder()`: after `runMigrationsAndValidate`, open the same file with
     `Room.databaseBuilder(context, LifeLogDatabase::class.java, DB).addMigrations(*ALL_MIGRATIONS)`
     and force the open (`openHelper.writableDatabase`). `runMigrationsAndValidate` checks the
     result against the **exported schema JSON**, not against the compiled entities; this
     second open is the check against the entities, and it is the same call the restore
     pre-open below makes.
   - `knownHashesMatchExportedSchemas()`: for every `v` in `1..SCHEMA_VERSION`, read
     `<v>.json` from the test assets and assert `KNOWN_SCHEMA_HASHES[v]` equals its
     `database.identityHash`; assert the map has no other keys.
6. **`export/SqliteRestore.kt`**
   - `validate`: after the version range check, read
     `SELECT identity_hash FROM room_master_table WHERE id = 42` (Room's fixed master row).
     Table or row absent, or the hash different from `KNOWN_SCHEMA_HASHES[version]` →
     `Validation.Invalid` with the message already used for an unreadable format (:129–134).
     Correct the comment at :121 ("Room migrates older ones on open" is now true) and the one
     at :125–128 (the number is no longer trusted on its own).
   - `applyStagedRestoreIfPresent`: between the re-validation and the swap, **pre-open the
     staged file**:
     ```kotlin
     val probe = Room.databaseBuilder(context, LifeLogDatabase::class.java, staged.absolutePath)
         .addMigrations(*ALL_MIGRATIONS)
         .build()
     try {
         probe.openHelper.writableDatabase                 // migrates + validates, or throws
             .query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
     } finally { probe.close() }
     ```
     wrapped in the function's existing `try`; any exception → discard the staged file and its
     sidecars, write the failure outcome, return `false`, **before** the live file is touched.
     The checkpoint (the statement `ExportEngine.checkpointWal` uses, `ExportEngine.kt:329`)
     folds the migrated pages back into the main file, so the plain file copy that follows
     carries them. This runs in `Application.onCreate` before `super.onCreate()`, where the
     file copy already runs; it needs only a `Context`, not Hilt.
   - Verify while implementing: that an absolute path is accepted as the database name (the
     framework's `getDatabasePath` passes absolute paths through); if it is not, stage the file
     under `context.getDatabasePath(STAGED_DB_NAME)` instead. Either way the probe must never
     be given `LifeLogDatabase.DATABASE_NAME`.
   - The rollback copy keeps its present job (a failed file copy). It no longer has to cover a
     failed open, because a file that fails to open is never copied.
7. **`SqliteRestoreValidateTest`**: `createDbFile` gains an `identityHash: String?` parameter
   and writes `room_master_table` when it is non-null (default: the hash for the version it
   is asked to create).

### 5. Data model / persistence changes
None. This spec adds the *means* of changing the schema.

### 6. UI/UX changes
None new. Two existing messages are reached in more cases: the "can't read this format"
message at the picker (wrong hash) and the "previous data was kept" outcome after restart
(the staged file would not open).

### 7. Interactions with existing functionality
- **SQLite restore** (`SqliteRestore`): its "older backups are migrated on open" claim becomes
  true for future versions because the migrations are now registered — and the two checks in
  §4.6 are what make that safe. A backup that validates today still validates.
- **Restoring an on-device auto-backup**: same path, same checks.
- **Android backup / device transfer**: these restore `lifelog.db` outside the app's code, so
  neither check runs; Room migrates the file on first open as for any upgrade. Android does
  not restore a backup made by a newer app version, so only the upgrade direction arises.
- **Pre-1.0 backups** stay unrestorable, as decided at the schema reset — now rejected by
  hash rather than by a number that is about to be reused.
- **Every later schema-changing spec** (ARC-01, CF-03, CAL-01) adds one line to
  `KNOWN_SCHEMA_HASHES`; `knownHashesMatchExportedSchemas` fails until it does.
- **`EventTypeFieldsQueryTest`** and the other in-memory tests: unaffected
  (`inMemoryDatabaseBuilder` creates the current schema directly).
- **R8**: `room-testing` is androidTest-only; nothing reaches the release APK.

### 8. Testing requirements
- Instrumented: the four `MigrationTest` methods above.
- Instrumented `SqliteRestoreValidateTest` additions: right version + right hash → valid;
  right version + wrong hash → invalid (the pre-1.0 case: a five-table file with
  `user_version` in range and a foreign hash); no `room_master_table` → invalid; the existing
  cases still pass with the updated `createDbFile`.
- Instrumented `SqliteRestoreApplyTest` (new): stage a valid current-version file → applied,
  live database replaced, counts match; stage a file that passes `validate` but that Room
  cannot open (right hash row, one table altered by raw SQL) → not applied, the live database
  is byte-for-byte what it was, the failure outcome is written, no staged or sidecar files
  remain.
- Mutation checks: temporarily remove the `assets.srcDir` line and confirm
  `baseline_opensAndValidates` fails with a missing-schema error (proves the test exercises
  the wiring rather than passing vacuously); remove the hash comparison → the wrong-hash test
  fails; remove the pre-open → the cannot-open test fails.
- Emulator: install the baseline (`main` @ `b16dda8`) build, log data, install this build over it, launch
  — data intact, no crash. Then export a SQLite backup, restore it in-app → applied, data
  intact; restore a file whose hash row was edited by hand → refused at the picker.
- `./gradlew :app:testDebugUnitTest` stays green.

### 9. Dependencies
None.

### 10. Open questions / product decisions
None.

### 11. Acceptance criteria
- [ ] `Migrations.kt` exists with the rules comment, an empty `ALL_MIGRATIONS` and `KNOWN_SCHEMA_HASHES` for version 1.
- [ ] `DatabaseModule` registers `ALL_MIGRATIONS`; still no destructive fallback.
- [ ] `room-testing` is an androidTest dependency; schemas are an androidTest asset directory.
- [ ] `MigrationTest` passes on the emulator; the mutation checks fail as described.
- [ ] A backup whose identity hash is not the one recorded for its version is refused at the picker.
- [ ] A staged restore that Room cannot open is discarded with the live database untouched (test).
- [ ] No change to `1.json` or to `SCHEMA_VERSION`; the only behaviour change is that restore refuses files it could not have opened.

### 12. Note for the owner — do this once, before the first schema-changing spec
After FND-01 the next spec to land that changes the schema (ARC-01 or CF-03) makes the
database unreadable by any older build (README §5 R1). **Export a SQLite backup from the
current build first** and keep it; it is the only way back to a build from before the migration.
Keep it somewhere outside the app (Downloads, or a backup folder chosen in Settings): the
default auto-backup folder is app-private and goes when app data is cleared.
