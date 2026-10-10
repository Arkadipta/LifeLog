# CF — Computed fields (cross-entry: CF-08 … CF-11)

Formulas that look beyond the current entry: the previous value, running (expanding) totals,
rolling windows over the last *n* values, and rolling windows over a span of time.

Read the *Shared design* section of
[CF-computed-fields-core.md](CF-computed-fields-core.md) first — decisions CF-D1 … CF-D12, the
grammar and the function table apply here unchanged. Paths are relative to
`app/src/main/kotlin/com/lifelog/app/`.

---

## Shared semantics for cross-entry functions

These rules are stated once and hold for every function in CF-08, CF-09 and CF-10
(decision **P-03**, confirmed by the owner on 2026-10-10).

1. **The table** is all of the event's entries ordered by `createdAt`, then `id` (CF-D5).
2. **Observations.** For a series argument `x` (any expression), the *observations* are the
   rows where `x` is not missing, in table order. Rows where `x` is missing are skipped — they
   neither count towards a window nor break one.
3. **A window** is a contiguous run of observations ending at the latest observation at or
   before the current row. The current row's own value is included when it is not missing.
4. **Result.** A window function aggregates the observations in its window. The result is
   missing **only when the window holds no observation** (for `std`, fewer than two). `count`
   is the exception: an empty window counts `0`. A row whose own `x` is missing still gets the
   window's aggregate — a running total is known at every entry, not only at entries that add
   to it.
5. **Partial windows produce values.** `rolling_mean(x, 7)` at the third observation is the
   mean of three.
6. **Non-finite results are missing** (overflow in a running product, for example), as
   everywhere else (CF-D4).
7. **Arguments nest.** The series argument is an arbitrary expression and may itself contain
   cross-entry functions: `[Value] / expanding_max([Value]) - 1` is a drawdown.
8. **Window sizes are literals**, never expressions: a whole number (count) or a duration.

**Kernels** — the same eight aggregates serve expanding, rolling-count and rolling-time
windows:

| Kernel | Result | Missing when |
|---|---|---|
| `sum` | sum of the window | window empty |
| `mean` | arithmetic mean | window empty |
| `min`, `max` | smallest / largest | window empty |
| `count` | number of observations | never (0) |
| `std` | sample standard deviation (divisor `n − 1`) | fewer than 2 observations |
| `median` | middle value; mean of the two middle values for an even count | window empty |
| `prod` | product of the window | window empty |

**Worked example** — entries in table order, `x` = a Number field:

| row | `x` | `prev(x)` | `delta(x)` | `expanding_sum(x)` | `rolling_mean(x, 2)` |
|---|---|---|---|---|---|
| 1 | 10 | — | — | 10 | 10 |
| 2 | — | 10 | — | 10 | 10 |
| 3 | 14 | 10 | 4 | 24 | 12 |
| 4 | 20 | 14 | 6 | 44 | 17 |

---

## CF-08 — Cross-entry evaluation and expanding functions

### 1. Objective
Give the engine the ability to evaluate a function over the whole ordered table, and deliver
the functions that need no window size: `prev`, `delta`, and the `expanding_*` family. Make
the single-entry evaluation path honest about what it cannot compute.

### 2. Current implementation
After CF-02 the evaluator already works column-by-column over an `EntryTable` ordered per
CF-D5, but `FunctionKind` has one value (`POINTWISE`), every function is applied row by row,
`ComputedFields.needsHistory` is always `false`, and `evaluateRow` evaluates every formula
over a one-row table.

Nothing in the app computes across entries today except chart bucketing
(`ChartDataProcessor.aggregate`, `domain/ChartDataProcessor.kt:502`), whose `AggregationStrategy`
(MEAN, SUM, MIN, MAX, MEDIAN, LATEST) is the nearest precedent for the kernel list.

### 3. Required behaviour
**Functions**

| Function | Meaning |
|---|---|
| `prev(x)` | the most recent observation of `x` **strictly before** the current row |
| `prev(x, n)` | the `n`-th most recent observation strictly before the current row; `n` a whole-number literal `1..1000`; missing if fewer than `n` exist |
| `delta(x)` | `x − prev(x)`; missing if `x` is missing at this row or there is no earlier observation |
| `expanding_sum(x)`, `expanding_mean(x)`, `expanding_min(x)`, `expanding_max(x)`, `expanding_count(x)`, `expanding_std(x)`, `expanding_median(x)`, `expanding_prod(x)` | the kernel over every observation at or before the current row |

**Engine**
- `FunctionKind.HISTORY` exists; a history function receives whole columns and the table.
- A formula is *history-dependent* if its tree contains a history function, or references a
  computed field that is history-dependent (transitively).
- `ComputedFields.needsHistory` is true when any field in state `OK` is history-dependent;
  `historyFieldIds` lists them.
- `evaluateTable` computes everything. **`evaluateRow` omits history-dependent fields** (and
  therefore anything that depends on them) — it never returns a value computed from a
  one-row "history".
- Literal parameters (`n`) are validated by the parser: `BAD_ARGUMENT` if not a whole-number
  literal in range.

**Edge cases** (each a test): the worked example above; an empty table; a single row
(`prev` missing, `expanding_sum` = the value); two rows with the same `createdAt` (ordered by
`id`, both rows distinct); `x` missing on every row (all results missing; `expanding_count` is
`0` everywhere); `x` missing on the first rows (results missing until the first observation);
a nested call (`expanding_max(expanding_sum(x))`); a pointwise wrapper
(`exp(expanding_sum(ln(1 + [R]/100))) - 1` — the compounded return of the brief's portfolio);
overflow in `expanding_prod` → missing from that row on; entries supplied newest-first give
the same result as oldest-first.

### 4. Technical design
- `FunctionRegistry`:
  ```kotlin
  enum class FunctionKind { POINTWISE, HISTORY }
  enum class ParamKind { SERIES, COUNT_LITERAL, WINDOW_LITERAL }   // WINDOW_LITERAL used from CF-09

  class FunctionDef(
      …,                                         // as in CF-01
      val params: List<ParamKind>? = null,       // null = all SERIES (every pointwise function)
      val history: ((series: List<DoubleArray>, literals: List<Expr>, table: EntryTable) -> DoubleArray)? = null
  )
  ```
  The parser checks literal parameters against `params`; the evaluator evaluates only the
  `SERIES` arguments to columns and passes literal arguments through as trees.
- `domain/compute/WindowKernels.kt`:
  ```kotlin
  enum class WindowKernel { SUM, MEAN, MIN, MAX, COUNT, STD, MEDIAN, PROD }

  /** lo[i]..hi[i] are inclusive indices into the observation sequence for row i; hi[i] < lo[i] = empty. */
  fun aggregate(kernel: WindowKernel, observations: DoubleArray, lo: IntArray, hi: IntArray): DoubleArray
  fun observationsOf(x: DoubleArray): Observations     // values + row→"last observation index at or before"
  ```
  Every window function in CF-08/09/10 reduces to: build the observation sequence, compute
  `lo`/`hi` per row, call `aggregate`. `lo` and `hi` are both non-decreasing in every case,
  which allows: prefix sums for `sum`/`mean`/`count`; a monotonic deque for `min`/`max`; an
  incremental (Welford) pass for expanding `std`; two heaps or binary insertion for expanding
  `median`; a running product for expanding `prod`. No quadratic rescans for the expanding
  family.
- The `expanding_*` functions are generated from the kernel enum (one registry entry per
  kernel, shared signature/summary template), so CF-09 and CF-10 add families, not code.
- `Expr` analysis: `fun usesHistory(expr): Boolean`; `ComputedFields.compile` propagates it
  along `ComputedGraph`'s order.

### 5. Data model / persistence changes
None. Formulas using these functions are stored like any other (`version` stays 1: an older
build without the function reads the spec as an invalid formula and shows no values — the
salvage behaviour of CF-D8).

### 6. UI/UX changes
None directly. The functions appear automatically in CF-06's function list (new categories
"Previous entry" and "Running (all entries so far)"), with summaries written in plain language
— e.g. `expanding_sum(x)`: "Running total of x over every entry up to this one. Entries where x
is empty are skipped."

### 7. Interactions with existing functionality
- **Event Detail, charts, filter/sort, CSV export, the editor preview** use `evaluateTable`
  (CF-04, CF-06) and show these fields immediately.
- **Timeline, Timeline widget, entry form** use `evaluateRow` and **omit** these fields until
  CF-11. That is a known, intended interim state; do CF-11 next.
- **Charts**: a running total charted with the default *Average* bucket aggregation shows the
  average of the running total within each bucket. *Latest* is the faithful choice; AGG-01's
  caption makes the current choice visible, AGG-02's all-points mode avoids the question.
- **Filter is a view** (CF-D11): filtering the list does not change a running total.
- **Entry edits** that move an entry in time (changing `createdAt`) or change an input
  re-evaluate every later entry automatically — nothing is stored (CF-D1).
- **Deleting an entry** removes it from the table, so later entries' running and rolling
  values change — automatically, since nothing is stored.
- **Archiving** (ARC) is per event and does not change any table.

### 8. Testing requirements
JVM, `…/domain/compute/`:
- `WindowKernelsTest`: each kernel on hand-computed windows, including empty and single-value
  windows, even/odd median, `std` with one value.
- `HistoryFunctionsTest`: the worked example table; every edge case in §3; equivalence
  property — for random small tables, each `expanding_k(x)` equals the kernel applied naively
  to "all observations up to row i".
- `ComputedFieldsTest` additions: `needsHistory` and `historyFieldIds` including the transitive
  case; `evaluateRow` omits history-dependent fields and their dependents while still
  returning pointwise siblings.
- Parser tests: `prev(x, 0)`, `prev(x, 1.5)`, `prev(x, [n])` → `BAD_ARGUMENT`.
- Mutation checks: count rows positionally instead of observations → the worked-example test
  fails; let `evaluateRow` evaluate history functions → its test fails.

### 9. Dependencies
CF-02. (CF-04 is needed only for the `ComputedFields` additions; if CF-04 has not landed,
deliver the engine and add the `ComputedFields` part with CF-04.)

### 10. Open questions / product decisions
P-03 is decided (observation-based, partial windows). P-04 (`prev` / `delta` are additions to
the stated list) is on its default: include them.

### 11. Acceptance criteria
- [ ] All functions in §3 evaluate per the shared semantics; tests green and mutation-checked.
- [ ] `evaluateRow` never returns a history-dependent value.
- [ ] On device, a seeded `expanding_sum` field shows on Event Detail cards and plots in a chart.
- [ ] Still no Android imports in `domain/compute/`.

---

## CF-09 — Rolling count-window functions

### 1. Objective
Rolling aggregates over the last *n* values: `rolling_mean([Weight], 7)`.

### 2. Current implementation
After CF-08: `WindowKernels.aggregate` and the observation sequence exist; `ParamKind` has
`WINDOW_LITERAL` declared but unused; no `rolling_*` function is registered.

### 3. Required behaviour
- `rolling_sum`, `rolling_mean`, `rolling_min`, `rolling_max`, `rolling_count`, `rolling_std`,
  `rolling_median`, `rolling_prod` — each `(x, n)`.
- `n` is a whole-number literal in `1..10000` (`BAD_ARGUMENT` otherwise).
- The window is the `n` most recent observations of `x` at or before the current row; fewer if
  fewer exist (shared semantics 3 and 5).
- `rolling_<k>(x, 1)` is "the latest known value of x" (a forward fill) — state this in the
  function's summary; it is useful, not an accident.
- `rolling_count(x, n)` with a count window is always `min(n, observations so far)`; it is
  registered for symmetry with CF-10, where it is meaningful, and its summary says so.
- **Edge cases**: `n` larger than the table; `n = 1`; `x` missing at the current row (the
  window still ends at the latest earlier observation); all `x` missing; the worked example's
  last column.

### 4. Technical design
- Generate the family from `WindowKernel`, as CF-08 did for `expanding_*`:
  `params = listOf(SERIES, WINDOW_LITERAL)`.
- `lo[i] = max(0, hi[i] − n + 1)`. `sum`/`mean`/`count` via prefix sums, `min`/`max` via the
  monotonic deque, `std`/`median`/`prod` computed directly over the window slice (window sizes
  are small; `n ≤ 10000` bounds the worst case).
- In CF-09 the parser accepts only a `NUMBER` in the `WINDOW_LITERAL` position.

### 5. Data model / persistence changes
None.

### 6. UI/UX changes
None directly; a "Rolling (last n values)" category appears in the function list.

### 7. Interactions with existing functionality
As CF-08 §7. Additionally: a rolling mean drawn through chart bucket aggregation is smoothed
twice — see AGG-01/AGG-02.

### 8. Testing requirements
- `HistoryFunctionsTest` additions: each kernel against a naive reference over random small
  tables with missing values, for `n ∈ {1, 2, 3, 7, table size, table size + 5}`; the edge
  cases in §3.
- Parser: `rolling_mean(x)` → `WRONG_ARITY`; `rolling_mean(x, 0)`, `(x, 2.5)`, `(x, [n])`,
  `(x, 10001)` → `BAD_ARGUMENT`.
- Mutation check: require a full window (no partial) → the partial-window tests fail.

### 9. Dependencies
CF-08.

### 10. Open questions / product decisions
None. P-03 is decided.

### 11. Acceptance criteria
- [ ] Eight `rolling_*` functions registered from the kernel enum, not hand-duplicated.
- [ ] All tests green; reference-equivalence holds for every kernel.
- [ ] On device, a seeded `rolling_mean(…, 7)` field plots as a visibly smoother line than its input.

---

## CF-10 — Time-based window functions

### 1. Objective
Rolling aggregates over a span of time rather than a number of values:
`rolling_mean([Weight], 7d)`, `rolling_count([Cups], 24h)`.

### 2. Current implementation
After CF-09: `rolling_<k>(x, n)` with a count window. The lexer has no duration token (`7d`
is a syntax error). `EntryTable.timestamps` (CF-02) holds each row's `createdAt` in
milliseconds.

The app's other time arithmetic is deliberately **calendar-local**: chart buckets step in
local calendar days so DST days are 23 or 25 hours (`ChartDataProcessor`'s `DayMath`, audit
L4). That is the opposite of what a formula needs.

### 3. Required behaviour
- **Duration literal**: one or more digits immediately followed by a unit letter —
  `m` (minutes), `h` (hours), `d` (days = 24 h), `w` (weeks = 7 d). Examples: `30m`, `24h`,
  `7d`, `2w`. No space between number and unit; whole numbers only; value ≥ 1; at most ten
  years. `1.5h`, `7days`, `0d` are errors with a message that shows a valid form
  ("Write durations like 90m, 24h, 7d or 2w.").
- A duration is valid **only** as the window argument of a `rolling_*` function; anywhere else
  it is an error ("A duration like 7d can only be used as a window size.").
- `rolling_<k>(x, D)`: the window holds the observations of `x` at rows `j ≤ i` (table order)
  with `timestamp[i] − D < timestamp[j]`. The current row is always inside its own window.
- **Durations are elapsed time**, not calendar time: `1d` is exactly 86 400 000 ms. The result
  does not depend on the device's time zone or on daylight saving (CF-D6, README R8).
- Shared semantics 2–6 apply unchanged (missing values skipped; empty window → missing, `count`
  → 0).
- **Edge cases**: several entries with the same timestamp (those earlier in table order are in
  the window, later ones are not yet); an entry exactly `D` older than the current one is
  **outside** the window (strict `<`); a window containing only the current row; `x` missing
  at the current row with older observations inside the span; long gaps (window empties →
  missing, then refills); entries dated in the future (they are rows like any other).

### 4. Technical design
- Lexer: after reading digits, if the next character is one of `m h d w` and the character
  after that is not an identifier character, emit `DURATION`; if it is an identifier character
  (`7days`), emit the `BAD_ARGUMENT` error above. Otherwise emit `NUMBER` as before.
- Parser: `primary` accepts `DURATION` only when parsing an argument in a `WINDOW_LITERAL`
  position; `Expr.Duration(millis)` carries the value.
- Evaluation: the same `rolling_*` registry entries dispatch on the literal's type —
  `Num` → CF-09's count window, `Duration` → `lo[i]` advanced by a forward-only pointer over
  the observation timestamps (`while (obsTime[lo] <= t_i − D) lo++`). Then
  `WindowKernels.aggregate`.
- `FormulaText` (CF-05) needs no change: it substitutes reference tokens only.

### 5. Data model / persistence changes
None. A formula with a duration is stored as written (`rolling_mean({12}, 7d)`).

### 6. UI/UX changes
The function list shows both forms for the rolling family — "rolling_mean(x, n)" and
"rolling_mean(x, 7d)" — with a one-line explanation of the difference: *"7 = the last 7
values; 7d = everything in the last 7 days."*

### 7. Interactions with existing functionality
- **Chart buckets vs formula windows**: a chart's "week" is seven local calendar days; a
  formula's `7d` is 168 hours back from each entry. They are different by design and can
  differ by up to an hour across a DST change.
- **Backdated or re-timed entries** move within the table; windows follow automatically.
- **Heatmaps** of a `rolling_count(x, 24h)` field aggregate per calendar day as for any
  numeric field.

### 8. Testing requirements
- `ExprLexerTest` / `ExprParserTest`: every valid unit; the three malformed forms; a duration
  outside a window position; a field named `d` still referenced as `[d]` or bare `d`
  alongside `7d`.
- `HistoryFunctionsTest`: each kernel against a naive reference over random tables with
  irregular timestamps and missing values; every edge case in §3; a test that runs the same
  table under two different default time zones (`TimeZone.setDefault` in a `try/finally`, as
  `RecurrenceCalculatorTest`'s `inZone` helper does) and asserts identical results.
- Mutation checks: make the boundary inclusive (`<=`) → the exact-boundary test fails; use
  local-midnight alignment → the two-zone test fails.

### 9. Dependencies
CF-09.

### 10. Open questions / product decisions
None new. (Calendar-aligned windows — "this calendar week" — are deliberately out of scope;
they would make results depend on time zone.)

### 11. Acceptance criteria
- [ ] `rolling_<k>(x, <duration>)` works for all eight kernels with the boundary rule in §3.
- [ ] Results are identical under different device time zones (test).
- [ ] Malformed durations produce the specified messages.
- [ ] Count-window behaviour from CF-09 is unchanged (its tests still pass untouched).

---

## CF-11 — History-aware read paths

### 1. Objective
Show history-dependent computed fields everywhere entries appear — the Timeline, the Timeline
widget, the entry form — not only where the whole event is already loaded.

### 2. Current implementation
After CF-04 and CF-08:
- `observeEntriesForEventType` and `getAllEntriesForEventType` call `evaluateTable` — complete.
- `observeAllEntryRows` (Timeline) attaches a `ComputedFields` to each lazy row and calls
  `evaluateRow` on first read; `getRecentEntries*` (widget) call `evaluateRow` per entry. Both
  therefore **omit** history-dependent fields.
- The entry form (CF-07) shows "—" for them.
- The Timeline's design rule (audit P2): mapping the table parses no JSON; `EntryRow`'s KDoc
  forbids reading `fieldValues` over a whole list (`domain/model/EntryRow.kt:16`).

### 3. Required behaviour
- **Timeline**: an entry of an event with history-dependent fields shows them, with the same
  values Event Detail shows.
- **Timeline widget**: likewise, for all three filter modes.
- **Entry form**: a history-dependent field's row reads
  "Calculated from this event's history after you save" instead of a value. Pointwise fields
  keep their live preview.
- **Cost is opt-in and bounded**: only events that define a history-dependent field are
  decoded eagerly; every other event's rows stay lazy; an install with no such field pays
  nothing.
- **Edge cases**: an event with both pointwise and history-dependent fields (both shown); a
  history-dependent field with a broken formula (absent, no crash); the widget's
  `LIMIT 50` window containing entries from several events, some needing history; an event
  with thousands of entries.

### 4. Technical design
**Timeline — `EventRepository.observeAllEntryRows`**
1. As in CF-04, compile per event type from `eventFieldDao.observeAll()`.
2. Partition event types: `needsHistory` vs not.
3. For types that do not need history: unchanged (lazy row + `evaluateRow`).
4. For each type that does: take that type's rows out of the already-loaded entry list, build
   their lazy rows, call `compiled.evaluateTable(rowsOfType)` (this is what decodes them),
   and wrap each in a row whose `fieldValues` is `stored − computedFieldIds + precomputed`.
   Equality of the wrapper = the underlying row's equality **and** the precomputed map's.
5. Emit rows in the original (newest-first) order.

This runs inside the Timeline pipeline's `flowOn(Dispatchers.Default)`
(`ui/timeline/TimelineViewModel.kt:82`), never on the main thread.

**Widget — `getRecentEntries`, `getRecentEntriesByEventType`, `getRecentEntriesByCategory`**
- After the `LIMIT` query, group the returned entries by event type. For a type that needs
  history, load its full table once (`eventEntryDao.getAllForExport(typeId)` — the same query
  the CSV export uses), `evaluateTable`, and pick the values for the returned ids. Other types
  keep `evaluateRow`.

**Entry form**
- `EntryFormState` gains `historyFieldIds: Set<Long>` (from the compiled fields);
  `ComputedFieldRow` shows the explanatory line for those ids.

**Documentation**
- Update the KDoc on `EntryRow` and `observeAllEntryRows`: rows of events with
  history-dependent fields arrive decoded; the rule against reading `fieldValues` in a loop
  still stands for consumers.

**Only if the measurement in §8 calls for it**: memoise per event type on
`(that type's entity rows, ComputedFields.fingerprint)` so an entry logged under event A does
not recompute event B. Do not add this speculatively.

### 5. Data model / persistence changes
None. Nothing is cached on disk (CF-D1).

### 6. UI/UX changes
Values appear on Timeline cards and the widget; the entry form line described above.

### 7. Interactions with existing functionality
- **Timeline search and tag filter** read only plain columns (`EventFilterUseCase.filterEntries`)
  and are unaffected.
- **`entryListModel` / date navigation** read `createdAt` only — unaffected.
- **Compose skipping**: rows are unstable and compared by instance, as before.
- **Widget refresh** (`WidgetUpdater.refreshTimeline`) already fires on every entry save and
  delete; with archiving it also fires on archive/unarchive (ARC-03).
- **Archive** (per event): an archived event's rows are not returned to the Timeline or the
  widget at all, so nothing is decoded or computed for them there; on its own screen the
  event is evaluated over all its entries as usual.
- **`EntryRowLazyDecodeTest`**: its laziness assertions must still hold for events without
  history-dependent fields.

### 8. Testing requirements
- JVM: wrapper-row equality; a mixed list (one history event, one plain event) — plain rows
  remain undecoded after mapping (extend `EntryRowLazyDecodeTest`).
- Instrumented repository tests (in-memory Room): Timeline rows of a history event carry the
  same values as `observeEntriesForEventType`; each widget path returns the full-history value
  for an entry inside the `LIMIT` window (seed more than 50 entries so the window is a strict
  subset); an event without history fields issues no extra query (query callback).
- **Performance** (the existing harness: 5 000-entry dataset, `Choreographer: Skipped N frames`,
  three runs per build), comparing the build before and after this spec:
  - no history fields anywhere → no measurable difference;
  - one event of ~2 000 entries with an `expanding_sum` field → Timeline open, scroll, and
    "log an entry" each within run-to-run noise of the baseline for skipped frames. Record the
    numbers in the commit message. If they are not within noise, add the memo described in §4
    and re-measure.
- Emulator: Timeline, Event Detail and the widget show identical values for the same entry.

### 9. Dependencies
CF-04, CF-08. (CF-07 for the entry-form line; if CF-07 has not landed, skip that part and note
it in CF-07.)

### 10. Open questions / product decisions
None.

### 11. Acceptance criteria
- [ ] A history-dependent field shows the same value on Event Detail, the Timeline and the widget.
- [ ] Events without such fields remain lazy on the Timeline (test).
- [ ] The widget computes from the full history, not from its 50-row window (test).
- [ ] Performance numbers recorded; within noise, or memoised and re-measured.
- [ ] `EntryRow` / `observeAllEntryRows` documentation reflects the new behaviour.
