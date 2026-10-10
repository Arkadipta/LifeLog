# FND — Foundations

Infrastructure that more than one feature needs. Read [README.md](README.md) §1 and §8 first.
Paths are relative to `app/src/main/kotlin/com/lifelog/app/` unless they start with `app/` or
`gradle/`.

---

## FND-01 — Room migration harness

### 1. Objective
Make schema changes possible, registered and testable, without changing the schema. After this
spec a later spec adds a migration by writing one `Migration` object and one test method.

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

### 3. Required behaviour
- The app behaves exactly as before. No schema change, no version bump.
- A registry of migrations exists and is passed to the database builder.
- An instrumented test can create a database at any exported schema version, run the
  registered migrations up to the current version, and have Room validate the result against
  the entities.
- **Edge cases**: the registry is empty today — the test must still pass at version 1 (it
  proves the wiring: schema assets are found, the helper opens, validation runs).
  The registry must be contiguous: for every `v` in `1 until SCHEMA_VERSION` there is exactly
  one migration `v → v+1`.

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
     the data after migrating.
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

### 5. Data model / persistence changes
None. This spec adds the *means* of changing the schema.

### 6. UI/UX changes
None.

### 7. Interactions with existing functionality
- **SQLite restore** (`SqliteRestore`): unchanged code; its "older backups are migrated on
  open" claim becomes true for future versions because the migrations are now registered on
  the one builder the app uses.
- **Auto-backup / Google Auto Backup restore**: same path, same benefit.
- **`EventTypeFieldsQueryTest`** and the other in-memory tests: unaffected
  (`inMemoryDatabaseBuilder` creates the current schema directly).
- **R8**: `room-testing` is androidTest-only; nothing reaches the release APK.

### 8. Testing requirements
- Instrumented: the two `MigrationTest` methods above.
- Mutation check: temporarily remove the `assets.srcDir` line and confirm
  `baseline_opensAndValidates` fails with a missing-schema error (proves the test exercises
  the wiring rather than passing vacuously).
- Emulator: install the baseline (`main` @ `b16dda8`) build, log data, install this build over it, launch
  — data intact, no crash.
- `./gradlew :app:testDebugUnitTest` stays green.

### 9. Dependencies
None.

### 10. Open questions / product decisions
None.

### 11. Acceptance criteria
- [ ] `Migrations.kt` exists with the rules comment and an empty `ALL_MIGRATIONS`.
- [ ] `DatabaseModule` registers `ALL_MIGRATIONS`; still no destructive fallback.
- [ ] `room-testing` is an androidTest dependency; schemas are an androidTest asset directory.
- [ ] `MigrationTest` passes on the emulator; the mutation check fails as described.
- [ ] No change to `1.json`, to `SCHEMA_VERSION`, or to app behaviour.

### 12. Note for the owner — do this once, before the first schema-changing spec
After FND-01 the next spec to land that changes the schema (ARC-01 or CF-03) makes the
database unreadable by any older build (README §5 R1). **Export a SQLite backup from the
current build first** and keep it; it is the only way back to a build from before the migration.
