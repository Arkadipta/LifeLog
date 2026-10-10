# CF — Computed fields (core: CF-01 … CF-07)

A computed field is a field whose value is derived, by a user-written formula, from other
fields of the same event. Example from the brief — two portfolio returns entered as
percentages, and their difference in log returns:

```
log(1 + [PORT1] / 100) - log(1 + [PORT2] / 100)
```

Read [README.md](README.md) §1, §5 and §8 first. Cross-entry functions (running, rolling,
time windows) are in [CF-computed-fields-windows.md](CF-computed-fields-windows.md).
Paths are relative to `app/src/main/kotlin/com/lifelog/app/` unless they start with `app/`.

---

## Shared design (applies to every CF spec)

### Decisions

| # | Decision | Why (grounded in the current code) |
|---|---|---|
| CF-D1 | **Compute on read. Computed values are never stored** — not in `fieldValuesJson`, not in a table. | Every consumer already holds the event's entries in memory (`_allEntries` for the list and charts; whole-table rows for the Timeline). Storing results would need invalidation on entry edit, entry delete, formula edit, CSV import and restore, and — for windows — rewriting every later entry of the event on each of those. Nothing to invalidate means nothing to get wrong. |
| CF-D2 | **A computed field is a `NUMERIC` field that has a formula** (`event_fields.computedSpecJson != null`). No new `FieldType`. | Charts, the stale-chart check, filters and sort select fields with `type == NUMERIC` in nine places (README §7). As a numeric field a computed field is chartable, filterable and sortable with zero changes there; only the places that *accept input* need to know the difference. "Computed" is where a value comes from, not what kind of value it is. |
| CF-D3 | **Stored formulas reference fields by id** (`{12}`); the editor shows and accepts names (`[PORT1]`). | `fieldValuesJson` is keyed by field id; names are neither unique nor stable (the editor only checks non-blank). A rename then needs no stored-data rewrite. |
| CF-D4 | **Missing in → missing out.** Any missing operand makes the result missing; a math domain error or a non-finite result (`1/0`, `ln(0)`, overflow) is missing. A missing computed value is the **absence of the key**, exactly like an empty entered field. | Matches how filters, sort and charts already treat absent values (`as? FieldValue.Numeric`), and JSON cannot carry NaN. No new "null" value type. |
| CF-D5 | **The table.** A formula is evaluated over all of the event's entries, ordered by `createdAt` ascending, ties broken by `id` ascending. UI sort order never affects a result. | Deterministic; matches the chart axis. `createdAt` is the user-editable entry time (entry form Timestamp chips; CSV import writes the source time into it). |
| CF-D6 | **Pure and deterministic.** No `now()`, no randomness, nothing that depends on locale or time zone. | That is what makes CF-D1 correct: the same database computes the same numbers on any device, after any restore. |
| CF-D7 | **Scope fence.** Numeric results from Number fields of the same event. No cross-event references, no text, no conditionals/comparisons. | Keeps the language from growing into a programming language. |
| CF-D8 | **The stored spec is versioned and salvaged, never fatal.** An unreadable spec (corrupt, or written by a newer app) leaves the field with no values, never throws, and is preserved byte-for-byte through an unrelated event edit. | Extends the read-side policy at `data/db/Mappers.kt:26`; needed because field sets are replaced wholesale on every event save (README S3). |
| CF-D9 | **The engine is pure Kotlin** in `domain/compute/`, no Android/Room/Compose imports. | House pattern (`ChartDataProcessor`, `EntryQueryEngine`); exhaustively unit-testable; callable from the Glance widget path. |
| CF-D10 | **Dependencies are derived, not stored.** The fields a formula uses are read off its parsed tree each time the field list is loaded. | One source of truth (the formula text). No dependency table to keep in sync. |
| CF-D11 | **Order of operations.** stored values → computed columns over the event's full history → search / filter / sort → chart time window → chart bucketing. | Computation happens in the repository, before any consumer. A filter is a view: it never changes a computed value. A chart over "last month" of a 7-entry rolling mean uses entries from before the month for its first points. |
| CF-D12 | **Two entry points**: `evaluateTable` (whole event in hand) and `evaluateRow` (one entry, no history). `evaluateRow` returns nothing for a field that needs history. | Maps onto the two entry shapes the app already has (decoded lists vs lazy rows / `LIMIT` queries). |

### Language reference

**Tokens**

| Token | Form | Notes |
|---|---|---|
| `NUMBER` | `[0-9]+ ('.' [0-9]+)?` or `'.' [0-9]+` | `.` is the only decimal separator. No exponent form, no thousands separator. |
| `DURATION` | `[0-9]+` immediately followed by `m`, `h`, `d` or `w` | Added by CF-10. Before CF-10, `7d` is a syntax error. |
| `FIELD_ID` | `'{' '-'? [0-9]+ '}'` | The stored form. A negative id is an editor-only temporary id (CF-05). |
| `FIELD_NAME` | `'[' <one or more chars, none of them '[' or ']'> ']'`, or a bare `IDENT` **not** followed by `(` | The editing form. Leading/trailing spaces inside brackets are trimmed. |
| `IDENT` | `[A-Za-z_][A-Za-z0-9_]*` | Function names. Case-insensitive; canonical form is lowercase. |
| operators | `+ - * / ^ ( ) ,` | `,` separates arguments only. |

Whitespace (space, tab, newline) is insignificant. Anything else is a syntax error carrying a
character position.

**Grammar** (precedence encoded; `^` is right-associative and binds tighter than unary minus)

```
expression     := additive
additive       := multiplicative (('+' | '-') multiplicative)*
multiplicative := unary (('*' | '/') unary)*
unary          := '-' unary | power
power          := primary ('^' unary)?
primary        := NUMBER | DURATION | FIELD_ID | FIELD_NAME | call | '(' expression ')'
call           := IDENT '(' [ expression (',' expression)* ] ')'
```

Therefore `-2^2 = -(2^2) = -4`, `2^3^2 = 2^(3^2) = 512`, `2^-1 = 0.5`.
Limits: source length ≤ 1 000 characters, nesting depth ≤ 50.

**Tree**

```kotlin
sealed interface Expr {
    data class Num(val value: Double) : Expr
    data class Duration(val millis: Long) : Expr            // CF-10
    data class FieldRef(val fieldId: Long) : Expr           // from FIELD_ID
    data class NameRef(val name: String) : Expr             // from FIELD_NAME; never evaluated
    data class Neg(val operand: Expr) : Expr
    data class Binary(val op: BinaryOp, val left: Expr, val right: Expr) : Expr   // ADD SUB MUL DIV POW
    data class Call(val function: String, val args: List<Expr>) : Expr            // lowercase name
}
```

**Functions** — one registry (`FunctionRegistry`) is the single source for the parser (names,
arity), the evaluator (implementation) and the editor's function list (signature, summary,
example). Adding a function is adding a registry entry.

| Function | Introduced | Meaning; when the result is missing |
|---|---|---|
| `abs(x)` | CF-01 | |
| `sqrt(x)` | CF-01 | missing if `x < 0` |
| `exp(x)` | CF-01 | missing on overflow |
| `ln(x)`, `log(x)` | CF-01 | natural logarithm (decided, P-02); missing if `x ≤ 0` |
| `log(x, base)` | CF-01 | missing if `x ≤ 0`, `base ≤ 0` or `base = 1` |
| `log10(x)` | CF-01 | missing if `x ≤ 0` |
| `pow(x, y)` | CF-01 | same as `x ^ y`; missing if not finite (`0^-1`, `(-2)^0.5`); `0^0 = 1` |
| `round(x)`, `round(x, n)` | CF-01 | half away from zero; `n` a whole-number literal `0..10` |
| `floor(x)`, `ceil(x)` | CF-01 | |
| `min(a, b, …)`, `max(a, b, …)` | CF-01 | two or more arguments; missing if any is missing |
| `coalesce(a, b, …)` | CF-01 | first argument that is not missing; missing only if all are (P-04). The one null-aware pointwise function. |
| `prev(x)`, `prev(x, n)`, `delta(x)` | CF-08 | see windows file |
| `expanding_<k>(x)` | CF-08 | `k` ∈ sum, mean, min, max, count, std, median, prod |
| `rolling_<k>(x, n)` | CF-09 | last `n` values |
| `rolling_<k>(x, 7d)` | CF-10 | values within a duration |

Operators `+ - * / ^` use `Double` arithmetic; any non-finite result is missing.

### Components (`domain/compute/`)

| File | Spec | Contents |
|---|---|---|
| `Expr.kt` | CF-01 | tree, `BinaryOp`, `Token` (kind, text, `start`, `endExclusive`) |
| `ExprLexer.kt`, `ExprParser.kt` | CF-01 | text → tokens → tree or positioned error |
| `FunctionRegistry.kt` | CF-01 | `FunctionDef`, the function table |
| `EntryTable.kt` | CF-02 | ordered view of an event's entries with numeric columns |
| `ExprEvaluator.kt` | CF-02 | tree + table → column |
| `ComputedGraph.kt` | CF-02 | evaluation order, cycle detection |
| `ComputedSpec.kt` | CF-03 | stored spec + codec |
| `ComputedFields.kt` | CF-04 | compile an event's field list; `evaluateTable` / `evaluateRow` |
| `FormulaText.kt` | CF-05 | names ↔ ids in formula text |
| `ComputedFieldValidator.kt` | CF-05 | save-time validation, typed errors |
| `WindowKernels.kt` | CF-08 | the eight window aggregates |

### Vocabulary
*Entered field* — a field the user types a value into. *Computed field* — a Number field with a
formula. *Pointwise* — a formula whose value for an entry depends only on that entry.
*History-dependent* — a formula that uses a cross-entry function (directly or through another
computed field). *Table* — CF-D5.

---

## CF-01 — Expression parser

### 1. Objective
Turn formula text into a tree, or into an error that says where the problem is. Deliver the
function registry with every pointwise function.

### 2. Current implementation
Nothing exists. The nearest house precedents are the hand-written tokenizer
`domain/csv/CsvParser.kt` (pure, tested by `app/src/test/kotlin/com/lifelog/app/domain/CsvImportLogicTest.kt`)
and `RecurrenceCalculator.validate` (`domain/RecurrenceCalculator.kt:22`), which returns a
message for an invalid rule.

### 3. Required behaviour
- `ExprParser.parse(source: String): ParseResult`
  - `ParseResult.Ok(expr: Expr, tokens: List<Token>)`
  - `ParseResult.Error(error: ParseError)` with
    `ParseError(kind, position: Int, detail: String)` and
    `kind ∈ { EMPTY, TOO_LONG, TOO_DEEP, UNEXPECTED_CHAR, UNEXPECTED_TOKEN, UNEXPECTED_END,
    UNKNOWN_FUNCTION, WRONG_ARITY, BAD_ARGUMENT }`. `position` is a 0-based index into `source`.
- `ExprLexer.tokenize(source)` is public (CF-05 rewrites reference tokens by span).
- Function names are matched case-insensitively and stored lowercase: `Log(` ≡ `log(`.
- A bare identifier followed by `(` is a call; otherwise it is a `NameRef`. `[log]` and a bare
  `log` not followed by `(` are both references to a field named "log".
- Unknown function and wrong argument count are reported by the parser, positioned at the
  function name.
- `round`'s second argument must be a `NUMBER` literal that is a whole number in `0..10`
  (`BAD_ARGUMENT` otherwise).
- **Edge cases** (each a test): blank source → `EMPTY`; `--x` parses (double negation); unary
  `+` is an error; implicit multiplication `2(3)` and `2[x]` are errors; `{}`, `{abc}`, `[]`,
  `[   ]`, unterminated `[` / `{` are errors; `1,5` outside a call is an error at the comma;
  `1.` and `1.2.3` are errors; trailing input after a complete expression is an error at the
  first extra token; 1 001 characters → `TOO_LONG`; 51 nested parentheses → `TOO_DEEP`;
  `{-3}` is a valid `FieldRef(-3)`.

### 4. Technical design
- Hand-written lexer and recursive-descent parser following the grammar above; one method per
  grammar rule; a depth counter incremented in `unary` and `primary`.
- `FunctionRegistry`:
  ```kotlin
  enum class FunctionKind { POINTWISE }            // CF-08 adds HISTORY
  class FunctionDef(
      val name: String,            // lowercase
      val minArgs: Int,
      val maxArgs: Int?,           // null = any number
      val kind: FunctionKind,
      val category: String,        // "Math", "Rounding", "Missing values", …
      val signature: String,       // "round(x, digits)"
      val summary: String,         // one plain-language line
      val example: String,
      val nullAware: Boolean = false,                  // true only for coalesce
      val pointwise: ((DoubleArray) -> Double)? = null // NaN result = missing
  )
  object FunctionRegistry { val all: List<FunctionDef>; fun find(name: String): FunctionDef? }
  ```
  A pointwise implementation receives its arguments for one row. Unless `nullAware`, it is
  never called with a `NaN` argument (the evaluator short-circuits, CF-02).
- The implementations of the CF-01 functions in the language reference live here; each returns
  `NaN` for its domain errors and for any non-finite result.
  `round`: `sign(x) * floor(abs(x) * 10^n + 0.5) / 10^n`.

### 5. Data model / persistence changes
None.

### 6. UI/UX changes
None. (`signature`, `summary`, `example` are written now because CF-06 renders them.)

### 7. Interactions with existing functionality
None — new package, no callers yet.

### 8. Testing requirements
JVM, `app/src/test/kotlin/com/lifelog/app/domain/compute/`:
- `ExprParserTest`: a table of source → expected tree (precedence: `2+3*4`, `(2+3)*4`,
  `-2^2`, `2^3^2`, `2^-1`, `a - -b`; calls with 0/1/2/n arguments; nested calls; both
  reference forms; case-insensitive function names); the brief's formula
  `Log(1 + (PORT1 / 100)) - Log(1 + (PORT2 / 100))` parses to two `Call("log", …)` over
  `NameRef("PORT1")` / `NameRef("PORT2")`; a table of invalid sources → expected `kind` and
  `position`; every edge case in §3.
- `ExprLexerTest`: token spans (`start`/`endExclusive`) for a formula containing every token
  kind, including whitespace between tokens.
- `FunctionRegistryTest`: every function at a normal value, at each domain boundary, and at
  overflow; `round` half cases (`2.5 → 3`, `-2.5 → -3`, `round(2.345, 2)`); names unique and
  lowercase; every entry has non-blank signature/summary/example.

### 9. Dependencies
None.

### 10. Open questions / product decisions
P-04 (`coalesce`) — on its default. P-02 is decided: `log` is the natural logarithm; `log10`
and `log(x, base)` stay as conveniences.

### 11. Acceptance criteria
- [ ] All three test classes pass under `./gradlew :app:testDebugUnitTest`.
- [ ] `domain/compute/` has no `android.*`, `androidx.*` or Room import.
- [ ] The brief's example formula parses exactly as typed.
- [ ] Parsing never throws for any input string (errors are values).

---

## CF-02 — Evaluator and dependency resolution (pointwise)

### 1. Objective
Evaluate a set of formulas over an event's entries, producing one column of values per
computed field, with the missing-value rules of CF-D4 and a safe answer for cycles.

### 2. Current implementation
No evaluator exists. Values are read as `(entry.fieldValues[id] as? FieldValue.Numeric)?.value`
throughout (`domain/ChartDataProcessor.kt:500`, `domain/EntryQueryEngine.kt:49`) — the same
extraction rule the evaluator must use. Non-finite stored values are possible: the CSV wizard
accepts `"NaN"` / `"Infinity"` (`domain/csv/CsvFieldInference.kt:32,57`; see the note on
`compareNumeric`, `domain/EntryQueryEngine.kt:146`).

### 3. Required behaviour
- **Table** — `EntryTable.of(entries: List<EntryRow>)` orders rows by `createdAt` then `id`
  (CF-D5) regardless of input order, and exposes `size`, `entryIds: LongArray`,
  `timestamps: LongArray`, `column(fieldId): DoubleArray`. It takes `EntryRow` (which
  `EventEntry` implements) so the Timeline's rows can be evaluated too (CF-11).
  `EntryTable.ofRow(values: Map<Long, FieldValue>)` builds a one-row table (for `evaluateRow`).
- **Missing** is `NaN` inside the engine. `column(fieldId)` yields `NaN` where the entry has no
  value for that id, where the stored value is not `FieldValue.Numeric` (legacy type mismatch),
  and where the stored number is not finite. An id no entry holds is an all-missing column.
- **Operators and functions** — element-wise over rows. If any operand/argument is `NaN` the
  result is `NaN` and the function is not called — except `nullAware` functions, which receive
  the `NaN`s. Every result is normalised: not finite → `NaN`.
- **References** — `FieldRef(id)` reads a computed sibling's already-evaluated column when `id`
  is a computed field in this event, otherwise the stored column. `NameRef` evaluates to an
  all-missing column (it should never reach evaluation; it must not throw).
- **Dependencies** — computed fields may use other computed fields. They are evaluated in
  dependency order. A field on a cycle (including a formula that references itself), and any
  field that depends on such a field, is *unresolvable*: it produces no column and does not
  stop the others.
- **Edge cases**: zero rows → empty columns; one row; entries passed in descending order
  (the repository's order) produce the same result as ascending; two entries with the same
  `createdAt` order by `id`; `1/0`, `0/0`, `ln(0)`, `sqrt(-1)`, `pow(10, 400)` → missing;
  `coalesce({a}, 0)` → `0` where `a` is missing; a 20-deep chain of computed-on-computed.

### 4. Technical design
```kotlin
object ExprEvaluator {
    /** computed: columns of sibling computed fields already evaluated. */
    fun evaluate(expr: Expr, table: EntryTable, computed: Map<Long, DoubleArray>): DoubleArray
}

object ComputedGraph {
    data class Resolution(val order: List<Long>, val unresolvable: Set<Long>)
    fun resolve(exprs: Map<Long, Expr>): Resolution          // Kahn's algorithm over refs to keys of exprs
    fun findCycle(exprs: Map<Long, Expr>, from: Long): List<Long>?   // ids along a cycle, for messages
    fun references(expr: Expr): Set<Long>                    // every FieldRef id in the tree
    fun evaluateAll(exprs: Map<Long, Expr>, table: EntryTable): Map<Long, DoubleArray>
}
```
- One `DoubleArray` per tree node per evaluation is fine at this scale (an event with 10 000
  entries and five formulas is well under a millisecond per node); do not optimise further.
- `evaluateAll` walks `resolve(exprs).order`, filling the `computed` map as it goes.
- Recursion depth is bounded by the parser's nesting cap.

### 5. Data model / persistence changes
None.

### 6. UI/UX changes
None.

### 7. Interactions with existing functionality
None yet. The extraction rule deliberately mirrors charts and filters so a value that a chart
would skip (legacy mismatch) is also skipped by formulas.

### 8. Testing requirements
JVM, `…/domain/compute/`:
- `ExprEvaluatorTest`: the brief's formula over a small table with hand-computed expected
  values (tolerance `1e-12`); a missing-value matrix — every operator and every function with
  the missing value in each argument position; every domain error; non-finite normalisation;
  `coalesce`; legacy-mismatch and non-finite stored inputs treated as missing; row order
  independence; tie-break by id; empty and one-row tables.
- `ComputedGraphTest`: chain `A → B → C` evaluates in order; self-reference; two-field cycle;
  a field downstream of a cycle is unresolvable while an unrelated field still evaluates;
  `findCycle` returns the loop in order.
- Mutation checks: disable the non-finite normalisation → the `1/0` test fails; evaluate in
  input order instead of sorted order → the order-independence test fails.

### 9. Dependencies
CF-01.

### 10. Open questions / product decisions
None beyond P-04 (`coalesce`).

### 11. Acceptance criteria
- [ ] Both test classes pass; the mutation checks fail as described.
- [ ] No evaluation path can throw or return a non-finite number as a value.
- [ ] Still no Android imports in `domain/compute/`.

---

## CF-03 — Computed-field persistence and model

### 1. Objective
Give a field somewhere to keep its formula: one nullable column, carried through the entity,
the domain model, the mappers and the JSON export, with a versioned codec. No evaluation yet.

### 2. Current implementation
- `data/db/entity/EventFieldEntity.kt` — columns `id, eventTypeId, name, type, optionsJson,
  unit, isRequired, sortOrder`.
- `domain/model/EventField.kt` — the same eight properties.
- `data/db/Mappers.kt:64–90` — `EventFieldEntity.toDomain()` (unreadable `type` → `TEXT`,
  unreadable options → empty, per the read policy) and `EventField.toEntity()`.
- `EventRepository.saveEventType` (:97) deletes the event's fields and re-inserts them from the
  domain objects; `addFieldOption` (:125) reads one row, copies the domain object, and
  `@Update`s it. Whatever `EventField` does not carry is lost on either path.
- `export/ExportData.kt:37` `EventFieldRow` and its mapper `export/ExportEngine.kt:341`;
  `EXPORT_SCHEMA_VERSION = 1` (bumped "whenever the structure of the contained rows changes").
- `appJson` (`Mappers.kt:24`) is `ignoreUnknownKeys = true, isLenient = true`, and does **not**
  encode defaults.

### 3. Required behaviour
- A field may carry a formula spec. A field is *computed* iff it has a spec **and** its type
  is `NUMERIC`: `val EventField.isComputed get() = computedSpecJson != null && type == FieldType.NUMERIC`.
  A spec on a non-numeric field (only reachable through a damaged backup) is carried but
  ignored.
- The raw spec string survives every write path untouched: `saveEventType` (even when the
  editor cannot understand it), `addFieldOption`, JSON export.
- Decoding a spec never throws. Result is `SpecState.Readable(spec)` or
  `SpecState.Unreadable(raw)`:
  - not valid JSON, or missing `source` → `Unreadable`;
  - `version` absent, `< 1` or `> ComputedSpec.CURRENT_VERSION` → `Unreadable`;
  - `decimals` outside `0..10` → clamped into range, still `Readable`;
  - unknown extra keys → ignored.
- Encoding always writes `version` explicitly (README R4: an omitted default would be
  reinterpreted if the default ever changed).
- After this spec a field with a spec still behaves as a plain Number field everywhere (no
  evaluation, an ordinary input in the entry form). No UI creates specs yet.

### 4. Technical design
- `EventFieldEntity`: `val computedSpecJson: String? = null` (last property).
- `EventField`: `val computedSpecJson: String? = null` and the `isComputed` extension above.
  The domain model carries the **raw string**, not a parsed object — that is what lets a spec
  from a newer version survive an edit made by this one.
- Mappers: pass the string both ways. Add to the policy comment: a spec is opaque to the data
  layer.
- `domain/compute/ComputedSpec.kt`:
  ```kotlin
  @Serializable
  data class ComputedSpec(val version: Int, val source: String, val decimals: Int? = null) {
      companion object { const val CURRENT_VERSION = 1 }
  }
  sealed interface SpecState {
      data class Readable(val spec: ComputedSpec) : SpecState
      data class Unreadable(val raw: String) : SpecState
  }
  object ComputedSpecCodec {
      fun decode(raw: String): SpecState
      fun encode(spec: ComputedSpec): String     // version always present
  }
  ```
  `version` has no default in the data class, so the encoder cannot omit it and a spec without
  it fails to decode (→ `Unreadable`), which is the intended reading.
- Migration `n → n+1` (take `SCHEMA_VERSION + 1`, per FND-01's rules):
  `ALTER TABLE event_fields ADD COLUMN computedSpecJson TEXT`.
  Bump `@Database(version)` and `SCHEMA_VERSION`; commit the new schema JSON.
- Export: `EventFieldRow.computedSpecJson: String? = null`; map it at `ExportEngine.kt:341`;
  bump `EXPORT_SCHEMA_VERSION`.

### 5. Data model / persistence changes
| Change | Detail |
|---|---|
| Column | `event_fields.computedSpecJson TEXT NULL` (no default clause, no index) |
| Room version | `+1`, one migration, schema JSON committed |
| JSON export | `EventFieldRow` gains the property; `EXPORT_SCHEMA_VERSION` `+1` |
| Stored format | `{"version":1,"source":"ln(1 + {12}/100) - ln(1 + {13}/100)","decimals":4}` — `source` uses `{id}` references only |

Computed **values** are not stored anywhere (CF-D1).

### 6. UI/UX changes
None.

### 7. Interactions with existing functionality
- **SQLite restore / auto-backups**: a backup from before this spec is migrated on open (the
  path FND-01 made real); a backup from after it is rejected by an older build (README R1).
- **CSV wizard** (`csv/CsvImportEngine.kt:133`) builds fields without a spec — unchanged.
- **`EventTypeFieldsQueryTest`** (query-count assertions on `observeAllEventTypes`) — unchanged
  query count.
- **R8**: `ComputedSpec`'s serializer survives on the library's consumer rules (audit L8); it
  need not live under `domain/model`.

### 8. Testing requirements
- Instrumented `MigrationTest`: seed the previous version (`seedV1` or its successor), migrate,
  assert existing fields read back with `computedSpecJson == null` and all other columns
  intact; insert a row with a spec and read it back.
- Instrumented repository test: `saveEventType` round-trips a spec string byte-for-byte for
  (a) a valid spec and (b) `{"version":99,"source":"future()"}`; `addFieldOption` on a sibling
  CHOICE field leaves the computed field's spec intact.
- JVM `ComputedSpecCodecTest`: every decode rule in §3; encode → decode identity; encode output
  contains `"version"`.
- JVM `MappersDecodeTest` additions: entity ↔ domain passthrough, null and non-null.
- Release build, once: build the minified variant, seed a field with a spec, open the event
  editor and save without changes, confirm via `run-as` that the stored string is unchanged.
- Emulator upgrade check over a database with real data (README R1): app opens, data intact.

### 9. Dependencies
FND-01.

### 10. Open questions / product decisions
None.

### 11. Acceptance criteria
- [ ] New schema JSON committed; `SCHEMA_VERSION` equals the `@Database` version; `MigrationTest` green.
- [ ] A spec string is preserved through `saveEventType` and `addFieldOption`, including an unreadable one.
- [ ] `ComputedSpecCodec.decode` never throws; encode always writes `version`.
- [ ] JSON export contains the property; `EXPORT_SCHEMA_VERSION` bumped.
- [ ] No visible behaviour change in the app.

---

## CF-04 — Read-path integration

### 1. Objective
Make computed values appear wherever entries are read — lists, charts, filters, sort, CSV
export, widgets — by computing them once, in the repository. After this spec a formula seeded
directly into the database works across the app with no UI changes.

### 2. Current implementation
The seven read paths are listed in README §1.2. Specifically:
- `observeEntriesForEventType` (`EventRepository.kt:143`) combines the type row and the
  entries (both `distinctUntilChanged`) and maps with `toDomain(entity)`; it does not read
  fields.
- `observeAllEntryRows` (:165) combines `eventEntryDao.observeAll()` with
  `eventTypeDao.observeAll()` and maps every row with `toRow(type)` — no JSON parsing;
  `LazyEntryRow` (`Mappers.kt:165`) decodes on first read and defines equality on the stored
  JSON.
- `getEntry` (:171) — one caller, `EntryViewModel.loadEntry` (`ui/events/EntryViewModel.kt:105`).
- `getAllEntriesForEventType` (:209), `getRecentEntries` (:189),
  `getRecentEntriesByEventType` (:214), `getRecentEntriesByCategory` (:220).
- `saveEntry` (:176) writes `entry.toEntity()` — the whole `fieldValues` map.
- Entry cards show a field when `entry.fieldValues` contains its id
  (`ui/events/EntryCards.kt:338`); the Timeline widget does the same
  (`widget/TimelineWidget.kt:439`). So a value that is in the map is displayed, with no further
  wiring.

### 3. Required behaviour
- For every entry returned by paths 1, 2, 4, 5, 6 and 7, `fieldValues` contains, for each
  computed field of its event whose formula yields a value for that entry, a
  `FieldValue.Numeric`. Where the formula yields nothing, the key is absent.
- A stored value under a computed field's id is never shown: the computed result (or absence)
  replaces it.
- **`getEntry` (path 3) returns stored values only.** It feeds the editor; computed values are
  a display concern and must not enter the edit path.
- **`saveEntry` strips** every key belonging to a computed field before writing — a backstop
  for the invariant "computed values are never stored".
- A formula change, a field rename, or an input-value change is reflected live (the flows
  re-emit).
- **Field states** (never an exception): spec unreadable; formula does not parse; formula
  contains a name instead of an id; unresolvable (cycle). In all four the field simply has no
  values. A reference to a field id that no longer exists is not an error: it evaluates as
  missing.
- **Performance contract**
  - An event with no computed fields costs what it costs today on every path.
  - The Timeline stays lazy: with only pointwise formulas, mapping the table still parses no
    JSON; a row's computed values are produced on the first read of its `fieldValues`.
  - Path 1's evaluation runs where its decoding already runs — off the main thread, upstream of
    `shareIn` in `EventDetailViewModel` (:60–64).
- **Edge cases**: an entry whose event type row is missing; an event whose every formula is
  broken; a computed field deleted while Event Detail is open; an entry that holds a stale
  stored value under a computed id.

### 4. Technical design
**`domain/compute/ComputedFields.kt`**
```kotlin
class ComputedFields private constructor(…) {
    companion object { fun compile(fields: List<EventField>): ComputedFields }

    enum class FieldState { OK, UNREADABLE, INVALID_FORMULA, UNRESOLVABLE }

    val isEmpty: Boolean                       // no field of this event is computed
    val computedFieldIds: Set<Long>            // every computed field, whatever its state
    val states: Map<Long, FieldState>
    val needsHistory: Boolean                  // always false until CF-08
    val fingerprint: String                    // stable digest of (fieldId, spec) pairs, for equality

    /** entryId → (fieldId → value). Entries in any order. */
    fun evaluateTable(entries: List<EntryRow>): Map<Long, Map<Long, Double>>

    /** One entry, no history. Fields that need history are absent from the result. */
    fun evaluateRow(values: Map<Long, FieldValue>): Map<Long, Double>
}
```
`compile` = for each `isComputed` field: `ComputedSpecCodec.decode` → `ExprParser.parse` →
reject trees containing `NameRef` → `ComputedGraph.resolve`. It is cheap (a few short strings)
and is simply re-run per emission; no cache.

**Merging** — one private helper in the repository:
`stored − computedFieldIds + results.mapValues { FieldValue.Numeric(it.value) }`.

**Per path**

| Path | Change |
|---|---|
| 1 `observeEntriesForEventType` | Add `eventFieldDao.observeByEventType(id).distinctUntilChanged()` to the `combine`; `compile`; if not empty, `evaluateTable` over the decoded entries and merge. |
| 2 `observeAllEntryRows` | Add `eventFieldDao.observeAll()` as a third flow. If no field in the table has a spec, map exactly as today. Otherwise compile per event type and pass the event's `ComputedFields` (or null) to `toRow`. |
| `LazyEntryRow` / `toRow` | New optional constructor argument `computed: ComputedFields?`. The lazy block becomes: decode stored values; if `computed != null`, remove `computedFieldIds` and add `evaluateRow(stored)`. `equals`/`hashCode` additionally compare `computed?.fingerprint`, so two rows from the same database row under different formulas are not equal. |
| 3 `getEntry` | Unchanged. Add to its KDoc that it returns stored values for editing. |
| 4 `getAllEntriesForEventType` | `compile(eventFieldDao.getByEventType(id))`; `evaluateTable`; merge. |
| 5–7 `getRecent…` | One `eventFieldDao.getAll()` grouped by type (the helper `groupByEventType` at :260 already exists); `evaluateRow` per entry; merge. |
| `saveEntry` | Read the event's fields, drop keys of computed fields from `entry.fieldValues`, then write as today. |

Update the KDoc on `EntryRow` (`domain/model/EntryRow.kt`) and on `observeAllEntryRows` to say
that a row's lazy read now also evaluates its event's pointwise formulas.

### 5. Data model / persistence changes
None.

### 6. UI/UX changes
None by design. Computed values appear on entry cards (Timeline, Event Detail) and the Timeline
widget, as plain numbers with the field's unit, because those surfaces render whatever is in
`fieldValues`. Until CF-07 they print at full `Double` precision, and until CF-07 the entry
form still shows an (ineffective) input for a computed field.

### 7. Interactions with existing functionality
- **Charts** — `ChartConfigSheet` lists the field (it is `NUMERIC`), `isStaleConfig` accepts
  it, `ChartDataProcessor` plots it. A computed field whose formula is broken has no values, so
  its chart shows the existing "Not enough data yet" state.
- **Filter & sort** (`FilterSortSheet`, `EntryQueryEngine`) — work unchanged on the merged
  entries; entries without a computed value sort last and match no filter, by the engine's
  existing rules. Filtering compares with the tolerance added in audit L5.
- **Search** in Event Detail matches `displayString()` of every value, now including computed
  ones at full precision (refined in CF-07).
- **CSV export** — the computed column is exported like any other, at full precision
  (`CsvWriter.toCsvCell` → `displayString()`). Re-importing such a CSV creates an ordinary
  Number field holding those numbers.
- **JSON / SQLite export** — raw rows; no computed values, by design.
- **Legacy-mismatch warnings** (`legacyMismatchOf`) — a computed value is `Numeric` under a
  `NUMERIC` field; no false warning.
- **Required-field validation** — unaffected here (CF-06 prevents `isRequired` on a computed
  field; CF-07 makes validation ignore it).
- **Archiving** (ARC-01/03) is per event and leaves entries alone, so it does not change any
  table. An archived event's entries are not listed on the Timeline or in widgets (nothing is
  computed for rows that are not returned), and are evaluated as normal on its own screen.
- **`EventTypeFieldsQueryTest`** — pins `observeAllEventTypes`' query count; not affected.
  `EntryRowLazyDecodeTest` — must be extended (below).

### 8. Testing requirements
- JVM `ComputedFieldsTest`: compile states for each failure kind; `evaluateTable` equals
  `evaluateRow` row-by-row for pointwise formulas; stale stored value under a computed id is
  replaced; dangling reference → missing; computed-on-computed.
- JVM `EntryRowLazyDecodeTest` additions: with a pointwise formula, rows are still undecoded
  after mapping; the first `fieldValues` read yields stored + computed; equality differs when
  only the formula differs. Mutation check: evaluate eagerly in `toRow` → the laziness test
  fails.
- Instrumented repository test (in-memory Room): each of paths 1, 2, 4–7 returns the computed
  value; path 3 does not; `saveEntry` of an entry carrying a computed key writes JSON without
  it; a formula edit re-emits path 1 and path 2.
- Emulator, via the seeding recipe: an event with two Number fields and a third whose
  `computedSpecJson` is the brief's formula (ids substituted). Check: Event Detail cards, a
  line chart on the computed field, a `>` filter and a sort on it, CSV export, Timeline cards,
  Timeline widget. Then edit an entry and confirm with `run-as` that its `fieldValuesJson` has
  no key for the computed field.
- Performance: on the 5 000-entry dataset with **no** computed fields, Timeline open and Event
  Detail open show no change in skipped frames against the baseline build (three runs each).

### 9. Dependencies
CF-02, CF-03.

### 10. Open questions / product decisions
None.

### 11. Acceptance criteria
- [ ] The seeded formula's values are visible on all six surfaces listed in §8 without any UI code change.
- [ ] `getEntry` returns stored values only; `saveEntry` never writes a computed key (test + device dump).
- [ ] With no computed fields anywhere, every read path does what it did before (tests + perf check).
- [ ] The Timeline remains lazy with pointwise formulas (test, mutation-checked).
- [ ] No read path throws for any broken spec.

---

## CF-05 — Validation and save-path support

### 1. Objective
Everything the event editor needs *below* the UI to save a formula safely: translate between
the names the user types and the ids that are stored, validate a formula against its sibling
fields, and persist formulas that reference fields created in the same editing session.

### 2. Current implementation
- New fields have `id = 0` until saved (`ui/events/CreateEventScreen.kt:856`:
  `id = existingField?.id ?: 0L`); several new fields share that id.
- `EventRepository.saveEventType` (:97–111): `deleteAllForEventType(id)` then
  `insertAll(fields…toEntity())` — existing ids are re-inserted unchanged, new ones are
  generated; `EventFieldDao.insertAll` returns the row ids in input order
  (`data/db/dao/EventFieldDao.kt:27`).
- `CreateEventViewModel.hasChangedFieldTypes` (:202) treats `id != 0L` as "existed before".
- Field names are validated only for non-blank (`CreateEventScreen.kt:851`); duplicates are
  allowed.
- Validation precedent: `RecurrenceCalculator.validate(rule): String?` (one statement of which
  rules are legal, consumed by `CreateReminderViewModel.save`).

### 3. Required behaviour
**Formula text ↔ stored source** (`FormulaText`)
- `toStored(text, fields)`: every name reference (`[Name]` or bare) is replaced by `{id}` of
  the unique field with that name, compared case-insensitively after trimming. `{id}` tokens
  already present are kept. Everything else — spacing, parentheses, number formatting — is
  preserved exactly. Errors: `UnknownFieldName(name, position)`,
  `AmbiguousFieldName(name, position)` (two or more fields share the name).
- `toDisplay(stored, fields)`: every `{id}` whose field exists and has a *renderable* name
  becomes `[Name]`; otherwise the `{id}` token is left as it is. A name is renderable when it
  is non-blank, contains neither `[` nor `]`, and is unique in the event case-insensitively.
- `toDisplay(toStored(t)) ` is stable: applying the pair again changes nothing.
- `remapIds(stored, map)`: rewrites `{old}` → `{new}` for ids in `map`.

**Validation** (`ComputedFieldValidator.validate(fieldId, spec, allFields): List<Error>`) over
the editor's field list, ids possibly temporary:

| Error | Condition | Reference copy (keep this tone) |
|---|---|---|
| `EmptyFormula` | blank source | "Enter a formula." |
| `Syntax(error)` | parser error | "Unexpected ')' at position 12." |
| `UnknownFunction(name)` | | "There's no function called 'logg'." |
| `WrongArity(name, given)` | | "round() takes 1 or 2 values, but 3 were given." |
| `BadArgument(name, detail)` | | "round()'s second value must be a whole number from 0 to 10." |
| `UnknownField(id)` | `{id}` not in the event | "This formula uses a field that no longer exists." |
| `UnknownFieldName(name)` | from `toStored` | "There's no field called 'PORT3'." |
| `AmbiguousFieldName(name)` | from `toStored` | "Two fields are called 'Weight'. Rename one to use it in a formula." |
| `NotANumberField(name, type)` | referenced field is not `NUMERIC` | "'Mood' is a Single Choice field. Formulas can only use Number fields." |
| `Cycle(names)` | self-reference or loop | "This formula refers back to itself: BMI → Adjusted BMI → BMI." |
| `BadDecimals` | outside `0..10` | "Decimals must be between 0 and 10." |

A formula may reference entered Number fields and other computed fields, never itself.
`validate` returns every error found, most fundamental first; an empty list means the field
may be saved.

**Saving with new fields**
- In the editor, a not-yet-saved field carries a unique **negative** temporary id (assigned by
  the view model in CF-06). Formulas reference it as `{-1}` like any other id.
- `saveEventType` inserts such fields with id 0 (autogenerate), then rewrites every stored
  formula that mentions a temporary id to the real id, inside the same transaction. A stored
  spec never contains a negative id.
- Edge cases: a new computed field referencing a new entered field; a new computed field
  referencing another new computed field; an existing computed field edited to reference a new
  field; no temporary ids at all (must not issue any extra write); an unreadable spec is
  passed through untouched.

### 4. Technical design
- `domain/compute/FormulaText.kt` — built on `ExprLexer.tokenize`: walk the tokens, copy the
  source between reference tokens verbatim, substitute reference tokens by span. If the text
  does not lex, `toStored` returns the lexer's error and `toDisplay` returns the input
  unchanged.
- `domain/compute/ComputedFieldValidator.kt` — `toStored` (if the caller passes display text)
  → `ExprParser.parse` → `ComputedGraph.references` checks → `ComputedGraph.findCycle` over
  all sibling formulas.
- `EventRepository.saveEventType`, replacing the single `insertAll` call:
  1. map each field to its entity with `id = if (field.id < 0) 0 else field.id`;
  2. `val rowIds = eventFieldDao.insertAll(entities)`;
  3. `val remap = fields.indices.filter { fields[it].id < 0 }.associate { fields[it].id to rowIds[it] }`;
  4. if `remap` is not empty: for each field whose readable spec's source mentions a key of
     `remap`, write the row again with `ComputedSpecCodec.encode(spec.copy(source =
     FormulaText.remapIds(spec.source, remap)))` via `eventFieldDao.update`.
- `CreateEventViewModel.hasChangedFieldTypes`: `field.id > 0L` (was `!= 0L`).

### 5. Data model / persistence changes
None (uses CF-03's column). Invariant added: persisted sources contain only positive ids.

### 6. UI/UX changes
None in this spec; the reference copy above is consumed by CF-06.

### 7. Interactions with existing functionality
- `saveEventType` is also reached from nowhere else (the CSV wizard inserts directly), so the
  change is confined to the event editor's save.
- The legacy type-change warning keeps working for existing fields and keeps ignoring new ones.
- Chart configs and filters reference field ids; ids of existing fields are still preserved.
- ADH-01 changes the same function (the `event_types` update); the two edits do not overlap.

### 8. Testing requirements
- JVM `FormulaTextTest`: both directions for bracketed and bare names, names with spaces,
  case-insensitive match, unknown and ambiguous names, unrenderable names staying `{id}`,
  whitespace preserved, idempotence, `remapIds`, a text that does not lex.
- JVM `ComputedFieldValidatorTest`: one test per row of the table; a valid formula returns no
  errors; the brief's example validates against fields named `PORT1` and `PORT2`.
- Instrumented `saveEventType` tests (in-memory Room): the five edge cases in §3; assert the
  stored source has real ids and that CF-04's `evaluateTable` then produces the expected
  value; assert no `update` is issued when there are no temporary ids (query callback, as in
  `EventTypeFieldsQueryTest`).
- Mutation check: skip the remap step → the new-fields test fails.

### 9. Dependencies
CF-02 (for `ComputedGraph`; it brings CF-01 with it) and CF-03. Its instrumented evaluation
assertion uses CF-04 if present; otherwise assert on the stored source only.

### 10. Open questions / product decisions
P-05 (Yes/No fields as inputs) — default: not allowed; `NotANumberField` covers it.

### 11. Acceptance criteria
- [ ] Typing the brief's formula with names produces a stored source with ids, and the reverse.
- [ ] Every error in the table is reachable and tested.
- [ ] A formula referencing fields created in the same save persists with real ids (test, mutation-checked).
- [ ] No persisted spec contains a negative id.

---

## CF-06 — Formula editor UI

### 1. Objective
Let the user create, edit and delete a computed field in the event editor, with live feedback,
and protect formulas from the edits that would break them.

### 2. Current implementation
- `ui/events/CreateEventScreen.kt`: the field list (`FieldConfigCard`, :700 — name, type chip,
  Required chip, unit chip, move up/down, edit, delete) and `AddFieldSheet` (:763) — a
  `ModalBottomSheet` with local `remember` state: name, a `LazyRow` of `FieldType` chips
  (:808), unit (Number only, :818), options (Choice/Multi-select), Required switch, and a
  button that returns an `EventField`.
- `ui/events/CreateEventViewModel.kt`: `addField`, `updateField(index, field)`,
  `removeField(index)`, move up/down, `save` → optional type-change warning → `persist`.
- Sheet-visibility flags are `rememberSaveable` and hoisted to the screen (audit M3).
- Sheets follow `Column(verticalScroll)` + `FlowRow`.

### 3. Required behaviour
**Choosing "Computed"**
- The Field Type row gains a sixth chip, **Computed**. It is an editor notion: the saved field
  is `type = NUMERIC` with a spec (CF-D2).
- A field that already exists in the database (`id > 0`) cannot be switched between entered
  and computed; the chips that would do so are disabled, with the caption "A saved field can't
  be switched between entered and computed. Add a new field instead." (Switching would hide or
  orphan the values already entered.)
- For a computed field the sheet shows: Name; **Formula** with its **suggestion strip**;
  validation message; preview; **Functions**; Unit; **Decimals**. The Required switch is
  hidden and `isRequired` is saved as `false`.

**Formula input (decided, P-01)** — plain text, with type-ahead delivered as a suggestion strip.
- A multi-line text field showing the formula in display form (`FormulaText.toDisplay`), no
  autocorrect, no auto-capitalisation. Everything can be typed by hand, exactly as in the
  brief: `Log(1 + (PORT1 / 100)) - Log(1 + (PORT2 / 100))`.
- Directly under it, a **suggestion strip**: one horizontally scrolling row of chips whose
  content follows the cursor. It has three states:

  | State | When | The strip offers | Tapping a chip |
  |---|---|---|---|
  | *Completing a name* | the cursor is right after one or more letters, or inside an unclosed `[` | the fields and functions matching what has been typed — fields first, prefix matches before contains-matches, case-insensitive | replaces the partial word: a field becomes `[Name]`; a function becomes `name()` with the cursor between the parentheses |
  | *A value is expected* | the formula is empty, or the cursor follows an operator, `(` or `,` | every field the formula may use, then `(`, then the functions | inserts at the cursor, as above |
  | *A value is complete* | the cursor follows a number, a field reference or `)` | the operators `+ − × ÷ ^`; `)` when a parenthesis is open; `,` when inside a function call | inserts the ASCII operator (`+ - * / ^`) at the cursor |

  So `po` narrows the strip to `PORT1`, `PORT2`, `pow`; tapping `PORT1` leaves `[PORT1]` and
  the strip switches to operators. A formula can be built by tapping alone, apart from digits.
- The strip never takes focus: the keyboard stays up and the cursor stays in the field.
- A field whose name cannot be written in brackets (it contains `[` or `]`, or another field
  has the same name) is inserted as its `{id}` token and labelled with its name on the chip.
- With a text selection, a tapped chip replaces the selection.
- *Functions* (button with `Icons.Rounded.Functions`): opens a sheet listing
  `FunctionRegistry.all` grouped by `category` — signature, one-line summary, example. This is
  the place to read what a function does; the strip shows names only. Tapping a function
  inserts `name()` with the cursor between the parentheses.
- On every change the formula is validated (CF-05). The first error is shown under the field
  in the error colour; the save button of the sheet is disabled while there is an error.
- **Preview**: when valid, one line — "Latest entry (Oct 3): 0.0123 %" — evaluated over the
  event's existing entries with the fields as currently edited; "No value for the latest entry
  (PORT2 is empty)" when the result is missing; "Preview appears once this event has entries"
  for a new or empty event.
- *Decimals*: chips `Auto, 0, 1, 2, 3, 4` (Auto = `null`).

*Why a strip and not a floating dropdown.* Both were considered (owner note on P-01). The
dropdown loses on three counts here: operators have no prefix to type ahead on, so in a
dropdown they could only ever be an unfiltered list; a popup anchored to a text field inside a
`ModalBottomSheet`, with the keyboard open, competes for space and — being focusable by
default in Material 3 — for focus, in a codebase that already carries one bottom-sheet gesture
workaround; and a row that is always in the same place lets the formula be built with the
thumb without the list jumping around the caret.

**In the field list**
- A computed field's card shows a **Computed** chip in place of "Number", and its unit.
- If its formula does not currently validate (broken by a later edit, or arrived broken by
  restore), the card shows "Formula needs attention" in the error colour, and saving the event
  is blocked with a message naming the field.
- An **unreadable** spec (CF-D8): the card shows "Formula from a newer version"; opening it
  shows the raw source read-only and one action, "Replace formula", behind a confirmation
  ("Editing it here will replace it."). Saving the event without touching it preserves it.

**Guards**
- Deleting a field that a formula uses is refused with a dialog:
  "'Height' is used by the formula for 'BMI'. Edit or remove 'BMI' first."
- Changing such a field's type away from Number is refused the same way.
- Renaming is free; formulas follow (they store ids).
- New fields get temporary ids so they can be referenced before the event is saved.

**Edge cases**: rotation mid-edit (the view model holds state; sheet flags are
`rememberSaveable`); very long formulas wrap; `font_scale 1.30`; an event with no other Number
fields (the strip still offers `(` and the functions, with the hint "Add a Number field to use
it in a formula"); the cursor in the middle of an existing formula (the strip reflects the
text before the cursor); a partial word that matches nothing (the strip is empty — the text is
left alone and validation reports it); the CSV wizard's draft mode hides the Fields section
and is unaffected.

### 4. Technical design
- New file `ui/events/ComputedFieldEditor.kt` for the computed-specific sheet content and the
  function list sheet. `AddFieldSheet` gains parameters `siblings: List<EventField>` and
  `preview: (EventField) -> PreviewResult`, and delegates when Computed is selected. Do not
  grow `CreateEventScreen.kt` further (README S10).
- **Suggestions are pure logic**, in `domain/compute/FormulaSuggestions.kt`:
  ```kotlin
  sealed interface Suggestion {
      val label: String
      data class Field(val fieldId: Long, override val label: String, val insert: String) : Suggestion  // "[Name]" or "{id}"
      data class Function(val name: String, override val label: String) : Suggestion                    // inserts "name()", cursor inside
      data class Symbol(override val label: String, val insert: String) : Suggestion                    // + - * / ^ ( ) ,
  }
  data class SuggestionSet(val replaceStart: Int, val replaceEnd: Int, val items: List<Suggestion>)

  object FormulaSuggestions {
      fun suggest(text: String, selectionStart: Int, selectionEnd: Int,
                  fields: List<EventField>, functions: List<FunctionDef>): SuggestionSet
      /** The text and cursor position after picking [pick]. */
      fun apply(text: String, set: SuggestionSet, pick: Suggestion): Pair<String, Int>
  }
  ```
  `suggest` works on the text before the cursor: an unclosed `[` → the partial is everything
  after it (spaces allowed); otherwise a trailing run of identifier characters that starts
  with a letter or `_` → the partial; otherwise tokenise the prefix with `ExprLexer` and choose
  *value expected* / *value complete* from the last token, tracking open parentheses and
  whether the innermost one belongs to a function call. A prefix that does not tokenise is
  treated as *value expected*. Cap the list at 12 items.
- The text field holds a `TextFieldValue` (the cursor is needed); the strip is a `LazyRow` of
  chips, each calling `FormulaSuggestions.apply` and writing the result back with the new
  selection.
- `CreateEventViewModel`:
  - `private var nextTempId = -1L`; `addField` replaces an incoming `id == 0L` with
    `nextTempId--`.
  - `removeField` / `updateField` consult a pure helper
    `dependentsOf(fieldId, fields): List<EventField>` (in `domain/compute`, built on
    `ComputedGraph.references`) and, when not empty, set
    `state.blockedEdit = BlockedEdit(fieldName, dependentNames)` instead of applying the edit.
  - `save` validates every computed field first; on failure sets
    `state.formulaErrors: Map<Long, String>` and does not persist.
  - Preview data: on first need, load `repository.getAllEntriesForEventType(eventId)` once
    (skip for a new event) and keep it; preview =
    `ComputedFields.compile(currentFields).evaluateTable(entries)` for the newest entry.
- Error copy is mapped from `ComputedFieldValidator` errors in the UI layer.

### 5. Data model / persistence changes
None beyond what CF-03/CF-05 provide.

### 6. UI/UX changes
As §3. Layout sketch of the sheet in Computed mode:

```
Add Field                                         ✕
[ Field Name *                                   ]
Field Type   Number  Text  Yes/No  Single  Multi  ●Computed
Formula
[ log(1 + [PORT1] / 100) - log(1 + po|            ]
  ( PORT1 ) ( PORT2 ) ( pow )                         ← suggestion strip, completing "po"
  Latest entry (Oct 3): —
[ ƒ  Functions ]
[ Unit (e.g. kg, ml)                              ]
Decimals     ●Auto  0  1  2  3  4
[                 Add Field                       ]
```

### 7. Interactions with existing functionality
- The type-change warning (`showTypeChangeWarning`) still triggers only for real type changes
  of existing fields.
- Move up/down works for computed fields like any other; field order is display order on
  cards.
- Widgets are refreshed by the existing `widgetUpdater.refreshAll()` in `persist`.
- Two-pane layout (audit L14): the editor is a normal destination; nothing changes.

### 8. Testing requirements
- JVM: `dependentsOf`; the save-blocking decision extracted as a pure function over the field
  list.
- JVM `FormulaSuggestionsTest`: each of the three states; ordering (fields before functions,
  prefix before contains); the replaced range for a bare partial and for a bracketed partial
  with spaces; cursor placement after inserting a function; `)` offered only with an open
  parenthesis and `,` only inside a call; a selection is replaced; an unrenderable field name
  inserts `{id}`; a prefix that does not tokenise; and one scripted session — building the
  brief's formula by picks plus typed digits — ending in exactly the expected text.
- Emulator, end to end: create an event with `PORT1`, `PORT2` and a computed field using the
  brief's formula **in one editing session**, save, log two entries, see the value on the
  cards; rename `PORT1` and reopen the formula (shows the new name); try to delete `PORT2`
  (blocked); retype `PORT2` to Text (blocked); produce each validation error; rotate with the
  sheet open and text typed; `font_scale 1.30` screenshots of the sheet and the function list.
  Enter the brief's formula three ways — typed entirely by hand, built entirely from the strip
  (plus digits), and mixed — and confirm the keyboard never closes and the cursor never jumps.
- Unreadable-spec flow: seed `{"version":99,"source":"x"}`, open the editor, save without
  editing, confirm the stored string is unchanged; then use "Replace formula".

### 9. Dependencies
CF-04 (preview and seeing results), CF-05 (validation, text codec, save path).

### 10. Open questions / product decisions
None. P-01 is decided: plain text with `[Field]` references and a suggestion strip.

### 11. Acceptance criteria
- [ ] The brief's computed field can be created from scratch on device, in one session — typed as written, or built from the suggestion strip.
- [ ] The strip follows the cursor through its three states, never steals focus, and its logic is a pure, tested function.
- [ ] Every CF-05 error is reachable and worded as specified.
- [ ] Deleting or retyping a referenced field is blocked; renaming is not.
- [ ] A saved field cannot be switched between entered and computed.
- [ ] An unreadable spec survives an unrelated event edit.
- [ ] `CreateEventScreen.kt` did not grow by more than the delegation wiring.

---

## CF-07 — Display formatting and entry-form presentation

### 1. Objective
Show computed values the way a person expects — rounded, marked as computed, and updating live
in the entry form — through one shared formatter.

### 2. Current implementation
- `FieldValue.displayString()` (`domain/model/FieldValue.kt:28`) prints a `Numeric` as an
  integer when whole, else `Double.toString()` — fine for typed values, but a computed value
  prints as `0.012345678901234567`.
- The same "value + unit" helper exists twice: `fieldDisplayValue`
  (`ui/events/EntryCards.kt:99`) and `widgetFieldValue` (`widget/TimelineWidget.kt:228`).
- Event Detail's search matches `displayString()` (`ui/events/EventDetailViewModel.kt:81`).
- The entry form renders one `FieldInput` per field (`ui/events/EntryFormSheet.kt:144`);
  `FieldInput` switches on `field.type` (`ui/events/components/FieldInputComponents.kt:53`).
- `EntryFormState.missingRequiredFieldIds()` (`ui/events/EntryViewModel.kt:60`) flags any
  required field absent from the form's values.

### 3. Required behaviour
**Formatting** — `FieldValueFormat.display(field, value, decimals)`:
- entered values: exactly as today (`displayString()`);
- computed values with `decimals = n`: fixed-point, `n` digits, half-even, trailing zeros kept;
- computed values with `decimals = null`: six significant digits, half-even, trailing zeros
  trimmed, whole numbers without a decimal point; magnitudes ≥ 1e15 or (non-zero) < 1e-9 fall
  back to `Double.toString()`;
- `.` as the decimal separator, like every number in the app;
- the unit is appended for numeric values when the field has one, as today.
- A field's `decimals` is resolved once per field list, not once per value.

**Surfaces**
- Entry cards (collapsed preview and expanded rows) and the Timeline widget use the shared
  formatter. In the expanded card a computed field's name carries a small "ƒ".
- Event Detail's search matches what is displayed.
- CSV export is unchanged (full precision).

**Entry form**
- A computed field is a read-only row in field order: name, "ƒ", value and unit, updating as
  the inputs change. No value yet → "—". It never takes focus, never shows the Required
  marker, and never blocks saving.
- Required-field validation ignores computed fields.

**Edge cases**: a value that rounds to `-0` prints `0`; very small and very large magnitudes;
a computed field among the first two fields (it appears in the collapsed preview); a field with
a broken formula (no row value → "—" in the form, absent from cards); the quick-add widget's
form (same `EntryFormSheet`).

### 4. Technical design
- New `domain/FieldValueFormat.kt` (pure):
  `fun display(field: EventField, value: FieldValue): String` plus
  `fun decimalsFor(fields: List<EventField>): Map<Long, Int?>` (decodes each computed field's
  spec once). Implement rounding with `BigDecimal.valueOf(v)` and `MathContext(6, HALF_EVEN)` /
  `setScale(n, HALF_EVEN)`.
- Replace `fieldDisplayValue` and `widgetFieldValue` with calls to it; delete both.
- `EventDetailViewModel` search: build the id → field map once per emission and match on the
  formatted string; a value whose field definition is gone falls back to `displayString()`.
- `EntryViewModel`: keep `ComputedFields.compile(eventType.fields)` when the type loads;
  expose `computedValues: Map<Long, Double>` in `EntryFormState`, recomputed by
  `evaluateRow(fieldValues)` on load and in `setFieldValue`. Extract the derivation as a pure
  function so it is unit-testable (the `EntryFormStateTest` pattern).
- `FieldInput`: `if (field.isComputed) ComputedFieldRow(field, value)` before the `when`.
- `missingRequiredFieldIds`: add `&& !it.isComputed`.

### 5. Data model / persistence changes
None.

### 6. UI/UX changes
As §3. The "ƒ" is text in `onSurfaceVariant`, not a new icon asset. Follow the widget design
language already in place (no new visual system).

### 7. Interactions with existing functionality
- `legacyMismatchOf` in `EntryCard` (:342) and `FieldInput` is evaluated before the computed
  branch only for entered fields; a computed field never shows the legacy-value card.
- `addFieldOption` in the entry form replaces a field in `state.eventType`; recompile
  `ComputedFields` when `eventType` changes.
- History-dependent fields (after CF-08, before CF-11) show "—" in the form; CF-11 replaces
  that with an explanatory line.

### 8. Testing requirements
- JVM `FieldValueFormatTest`: fixed decimals; six significant digits; whole numbers; `-0`;
  magnitude fallbacks; unit appending; entered values unchanged.
- JVM `EntryFormStateTest` additions: computed values follow the inputs; computed fields never
  appear in `missingRequiredFieldIds`; a computed field with a stale stored value in the loaded
  entry shows the computed value.
- Emulator: type `PORT1` then `PORT2` and watch the computed row appear; widget shows the
  rounded value; search for the rounded value finds the entry; `font_scale 1.30`.

### 9. Dependencies
CF-04. (CF-06 is not required: a seeded formula is enough to verify.)

### 10. Open questions / product decisions
None.

### 11. Acceptance criteria
- [ ] One formatter; `fieldDisplayValue` and `widgetFieldValue` are gone.
- [ ] The brief's value displays as `0.0123456` (Auto) or to the chosen decimals — never as a 17-digit double.
- [ ] The entry form shows computed fields read-only and live, and they never block saving.
- [ ] Search in Event Detail matches displayed values.
- [ ] CSV export output is byte-identical to before for entered fields.
