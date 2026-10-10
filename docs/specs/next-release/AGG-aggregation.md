# AGG — Transparent and configurable chart aggregation

Today every line, bar and heatmap chart aggregates its data, the bucket size is picked silently
from the time range, and nothing on the chart says so. This group makes aggregation visible
(AGG-01), adjustable (AGG-04), optional (AGG-02) and automatic above a threshold (AGG-03).

**Revised 2026-10-10 (owner decisions).** AGG-04 is confirmed. AGG-02 and AGG-03 are accepted
*on condition that they do not cost noticeable performance*; AGG-02 therefore opens with a
measured go/no-go, and if it fails both are shelved. AGG-01 and AGG-04 do not depend on them.

Read [README.md](README.md) §1.3, §5 and §8 first. Paths are relative to
`app/src/main/kotlin/com/lifelog/app/` unless they start with `app/`.

---

## Shared design

### How it works today (answers to the investigation questions)

**Where aggregation happens.** Only in `domain/ChartDataProcessor.kt` — a pure object called by
`EventDetailViewModel.chartDataMap` (`ui/events/EventDetailViewModel.kt:112`) for every readable
chart, on `Dispatchers.Default`, whenever entries, fields or chart configs change. Nothing in
the UI layer or the database aggregates.

**The pipeline** (`process`, :28):

```
all entries of the event (not the list's filter/search/sort — charts ignore those)
  → keep entries holding a value this chart can plot          hasChartableValue :63
  → pick the window's end: now, or the latest entry if the     resolveAnchor :87
    now-anchored window would be empty ("as of <date>")
  → drop entries before the window start                       applyTimeRange :99 / windowStartMs :124
  → lay out buckets for the range                              bucketEdges :186
  → put entries into buckets (one pass)                        distribute :254
  → one value per bucket per field                             aggregate :502
```

**How the bucket size is chosen** — by the time range alone (`bucketEdges`):

| Range | Buckets | Count |
|---|---|---|
| Day | hours tiling the anchor's local day | 23 / 24 / 25 |
| Week | local calendar days | 7 |
| Month | 7-day blocks (the last one is 8 days: it runs to the end of the anchor's day) | 4 |
| Year | calendar months | 12 |
| All | by span of the data: 1 h bins if ≤ 26 h; 24 h bins if ≤ 20 d; 7-day bins if ≤ 140 d; 30-day bins if ≤ 600 d; otherwise 20 equal bins | ≤ 27 |

So no chart ever draws more than about 27 points per series, however much data there is. The
"Month → four weekly points" case is the one called out in the brief.

**Aggregation functions.** `AggregationStrategy` (`domain/model/AggregationStrategy.kt`):
Average (default), Sum, Min, Max, Median, Latest — one per chart, applied to every series,
chosen in `ChartConfigSheet`'s "Aggregation" chips (`ui/events/components/ChartConfigSheet.kt:186`).

**By chart and field type**

| Chart | Fields | Bucketing | Aggregation |
|---|---|---|---|
| Line, Bar | one or more Number fields | by range (table above) | the chart's strategy |
| Heatmap | one Number or Yes/No field (Yes = 1, No = 0) | always one cell per local calendar day (`buildHeatmapData`, :350) | the chart's strategy, per day |
| Pie | one Number field + one Choice/Multi-select field | none over time | always the **sum** of positive values per category; the stored strategy is forced to Sum on save (`ChartConfigSheet.kt:340`) and ignored by the processor |

**What the user can see of all this.** The strategy chips in the editor. On the chart itself:
nothing for line/bar; the heatmap's tap strip shows "date · value · N entries"; the pie shows a
total. The marker on a line/bar chart labels a bucket with its **midpoint** date
(`ui/events/components/ChartMarker.kt:97`, pattern from `ChartTickGenerator.markerPattern`),
which for a weekly bucket reads like a single day.

**How charts receive data.** `ChartData.Cartesian(type, series, bucketTimestamps, anchoredEndMs)`
with `Series(fieldName, colorArgb, points, unit)` and `Point(bucketIndex, value)`
(`domain/model/ChartData.kt`). The x-axis is the bucket index; `bucketTimestamps` (bucket
midpoints) label it. `CartesianChartContent` pins the x-range to all buckets
(`fullSpanRangeProvider`, `ui/events/components/CartesianChartContent.kt:190`) and places ticks
at explicit bucket indices.

**How many points the chart library can draw.** Unknown by measurement — today's maximum is
~27. By construction: a chart card is 280 dp wide (`ChartCarousel.kt:47`); after padding and
the y-axis the plot is roughly 220 dp. Each point is currently drawn with a 4 dp marker, so
markers start to touch at about 55 points across; a line stops gaining information at roughly
one point per pixel.

**What a point costs** (read from the Vico `2.0.0-beta.2` sources,
`LineCartesianLayer.drawInternal`). On every draw, for every series, the line layer: rebuilds
the path (a cubic segment per point by default); erases an off-screen ARGB bitmap the size of
the chart and rasterises the line into it **in software**; blits the bitmap; then walks every
point again, reading one pixel back from the bitmap per point for the marker targets; then
draws a shape per point. The cost is linear in points and is paid on every redraw — opening
the screen, moving the inspect marker, and possibly each frame while the carousel scrolls.

**Performance position** (the owner's condition on P-11/P-12). Three things in the design keep
this from being a significant hit:
1. *Auto is bounded by its own threshold* — it never draws more than the threshold (300 by
   default) un-grouped; above that it groups, as today.
2. *All points has a hard ceiling* — above `RAW_POINT_HARD_LIMIT` the chart is drawn grouped
   and says why. The ceiling is set by measurement, not guessed.
3. *The raw path is made cheap* — straight segments instead of cubic curves, no per-point
   shapes once a series is dense, a fixed x-step.

One measurement decides whether they are enough: **AGG-02 begins with a gate** (its §8) —
measure first, build the editor only on a pass; a fail shelves AGG-02 and AGG-03 with the
numbers recorded. Expectation, to be confirmed on device: a few hundred points per chart is
comfortably cheap; the risk is in the thousands, which the ceiling removes.

**A library constraint that shapes the design.** Vico's default x-step is the greatest common
divisor of the x-differences, rounded to four decimals, and it **throws**
`IllegalArgumentException("The x values are too precise…")` when that rounds to zero
(`CartesianLayerModel.getXDeltaGcd`, Vico `2.0.0-beta.2`). Points at arbitrary fractional
positions therefore crash the chart unless the step is supplied explicitly
(`rememberCartesianChart(getXStep = …)`, present in this version).

### Decisions

| # | Decision | Why |
|---|---|---|
| AGG-D1 | **The processor decides and reports; the UI renders.** `ChartData` carries a description of what was done to produce it. | Keeps the renderer dumb (the existing split) and makes "what does the caption say" unit-testable. |
| AGG-D2 | **The bucket grid stays the axis frame in every mode.** Window, anchor and tick logic are untouched; un-aggregated points are placed at fractional positions inside the grid. | The window/display alignment (audit M1) and DST handling (audit L4) were hard-won; raw points reuse them instead of introducing a second, time-based axis. |
| AGG-D3 | **Stored charts keep their meaning.** A new `ChartConfig` property defaults to the legacy behaviour, permanently (README S7/R4). New charts get a different value because the editor writes it explicitly. | `ChartConfig` is JSON encoded without defaults. |
| AGG-D4 | **Mode is per chart; the threshold is global.** | A chart is where "show me everything" is decided; a threshold is a property of the screen and the renderer, not of a chart. |
| AGG-D5 | **Only line charts can show un-aggregated points.** Bars, heatmap cells and pie slices are aggregates by nature, and say so. | P-13. |
| AGG-D6 | **Charts keep ignoring the list's filter, search and sort.** | Existing behaviour; out of scope. |
| AGG-D7 | **Computed fields arrive already computed** over the full history (CF-D11). Aggregation never recomputes a formula per bucket. | A weekly average of a rolling mean is smoothed twice; that is the user's choice to make, and AGG-01 is what shows it. |

---

## AGG-01 — Aggregation disclosure

### 1. Objective
Every chart states, on the card, how its data was grouped and combined; the inspect marker
states what a point covers; the editor explains what its settings will do.

### 2. Current implementation
See *How it works today*. Specifically:
- `ChartData.Cartesian` carries no bucket size and no strategy; `ChartData.Heatmap` carries the
  strategy (`ChartData.kt:54`) but nothing renders it; `ChartData.Pie` carries neither the
  strategy nor the grouping field's name.
- `ChartCardHeader` (`ui/events/components/ChartCard.kt:161`) shows the title and, when the
  window is anchored to old data, an "as of <date>" caption (`anchoredCaption`, :241).
- `chartContentDescription` (:254) names the chart type and fields.
- `ChartMarkerValueFormatter` (`ChartMarker.kt:76`) prints the bucket's midpoint date and the
  values.
- `ChartConfigSheet` shows "Aggregation" chips with no explanation (:186–205).

### 3. Required behaviour
**On the card** — a caption under the title, always present for a chart that has data:

| Chart | Caption | Examples |
|---|---|---|
| Line / Bar | `<bucket> <strategy>` | "Weekly average", "Hourly sum", "Monthly median", "30-day latest", "≈18-day average" |
| Heatmap | `Daily <strategy>` | "Daily average"; for a Yes/No field "Daily average (Yes = 1)" |
| Pie | `Total by <group field>` | "Total by Meal" |

When the window is anchored, the two captions share the line: "Weekly average · as of May 4".
One line, ellipsised; it must not push the chart (the card height is fixed at 224 dp).

**In the marker** (line/bar) — the first line names what the point covers, by bucket size:
hour → "May 4, 14:00"; day → "Mon, May 4"; multi-day → the range "May 4 – May 10";
calendar month → "May 2026". When a bucket combined more than one entry, append " · 5 entries".

**In the editor** — one sentence under the Aggregation chips that follows the selected type,
range and strategy: "Entries are grouped into weekly buckets for this range; each point is the
average of its bucket." For *All*: "…grouped by a bucket size that depends on how much history
there is…". Heatmap: "Each day shows the average of that day's entries." Pie (under *Group by*):
"Each slice is the total of the number field for that category."

**Accessibility** — the chart's content description includes the caption.

**Edge cases**: every bucket holds at most one entry (the caption is still stated — the
position is still a bucket); a single-instant history (*All* with one timestamp → one bucket:
caption "All entries · <strategy>"); stale/empty/insufficient states show no caption; long
field names in the pie caption; `font_scale 1.30`.

### 4. Technical design
- `domain/model/ChartData.kt`:
  ```kotlin
  enum class BucketUnit { HOUR, DAY, WEEK, DAYS_30, CALENDAR_MONTH, EVEN }

  data class AggregationInfo(
      val grouped: Boolean,                 // always true in AGG-01
      val unit: BucketUnit?,                // null for pie and for the single-instant case
      val bucketMillis: Long?,              // nominal bucket length; used for EVEN's "≈N-day"
      val strategy: AggregationStrategy?,
      val entryCount: Int,                  // entries with a plotted value inside the window
      val pointCount: Int                   // points drawn in the largest series
  )
  ```
  `Cartesian` gains `aggregation: AggregationInfo` and `bucketEdges: List<Long>` (n + 1 edges
  beside the existing midpoints). `Cartesian.Point` gains `entryCount: Int`.
  `Heatmap` gains `aggregation: AggregationInfo` (unit `DAY`) and `isBoolean: Boolean`.
  `Pie` gains `aggregation` (strategy `SUM`, unit `null`) and `groupFieldName: String`.
- `ChartDataProcessor`: `bucketEdges` returns a small `BucketGrid(edges, unit, bucketMillis)`;
  `buildCartesianData` fills the new fields. Expose
  `fun bucketUnitFor(range: TimeRange): BucketUnit?` (null for `ALL`) for the editor sentence.
- New pure `domain/ChartCaptions.kt`: `fun caption(data: ChartData): String?`,
  `fun bucketLabel(unit, startMs, endMs, locale): String`,
  `fun editorExplanation(type, range, strategy): String`. Pure so they are unit-tested; the UI
  only places the strings.
- `ChartCard`: render `ChartCaptions.caption(data)` joined with `anchoredCaption`.
  `ChartMarker`: use `bucketLabel` with `bucketEdges` and append the entry count. That removes
  the only production caller of `ChartTickGenerator.markerPattern` (`ChartMarker.kt:82`);
  delete it together with its cases in `ChartTickGeneratorTest` (:136–140).
- `ChartConfigSheet`: the explanation `Text` under the chips.

### 5. Data model / persistence changes
None. `ChartData` is computed, never stored.

### 6. UI/UX changes
As §3. No new controls.

### 7. Interactions with existing functionality
- `ChartDataProcessorTest` / `ChartDstBucketsTest` construct and compare `ChartData`; update
  them for the new properties.
- The x-axis tick labels (`ChartTickGenerator.generate`) are unchanged.
- Heatmap's tap strip already shows the per-day entry count; unchanged.
- Computed fields: a caption on a chart of a rolling/running field is what tells the user it
  is being averaged again (AGG-D7).

### 8. Testing requirements
- JVM `ChartDataProcessorTest`: for each range, the reported `unit`; each of the five *All*
  span tiers; `entryCount` and per-point `entryCount`; pie and heatmap info.
- JVM `ChartCaptionsTest`: every caption form; every `bucketLabel` form including a range that
  crosses a month and a year; the editor sentences.
- Emulator: one chart of each type, anchored and not; marker on hourly, daily, weekly and
  monthly buckets; `font_scale 1.30`; TalkBack reads the caption.

### 9. Dependencies
None.

### 10. Open questions / product decisions
None.

### 11. Acceptance criteria
- [ ] Every chart with data shows a caption that is true of what is drawn.
- [ ] A weekly bucket's marker shows a date range, not a single day, and how many entries it combined.
- [ ] The editor explains the selected combination in one sentence.
- [ ] Caption and label text come from pure functions with tests.
- [ ] Charts look otherwise identical to before (same points, ticks, colours).

---

## AGG-02 — All-points mode for line charts

### 1. Objective
Let a line chart plot every entry at its true position in time, with no aggregation.

### 2. Current implementation
- `ChartConfig` (`domain/model/ChartConfig.kt`) has `aggregation: AggregationStrategy = MEAN`
  and no notion of a mode.
- `buildCartesianData` (`ChartDataProcessor.kt:272`) always buckets; a point's x is its bucket
  index (an `Int`).
- `CartesianChartContent`: `series(points.map { bucketIndex }, values)` (:55, :60); x-range
  `0 … bucketCount − 1` (`fullSpanRangeProvider`, :190); no `getXStep`; every line gets a 4 dp
  point marker (:118); the marker finds its bucket with `targets.first().x.roundToInt()`
  (`ChartMarker.kt:95`).
- After AGG-01: `Cartesian` carries `bucketEdges` and `AggregationInfo`; `Point` carries
  `entryCount`.

### 3. Required behaviour
- A line chart has a mode: **Grouped** (today's behaviour) or **All points**. Stored charts
  without the setting are Grouped (AGG-D3).
- In All-points mode:
  - the time window, the anchor ("as of"), the axis ticks and labels are exactly those of the
    Grouped chart for the same range;
  - every entry inside the window with a value for a plotted field is one point for that
    series, at its own time; nothing is combined, and the strategy is not applied;
  - points are joined by **straight** segments in time order (no curve smoothing: between
    irregularly spaced points a curve overshoots and invents values, and it costs more);
  - a series with more than 60 points is drawn as a line without point markers; a series with
    60 or fewer keeps them (a single-point series must stay visible);
  - the marker shows that entry's own date and time and its value(s);
  - the caption reads "Every entry · 142 points".
- **Hard ceiling.** If the window holds more than `RAW_POINT_HARD_LIMIT` entries, an
  All-points chart is drawn Grouped and its caption says so:
  "Too many points to show individually (12,400) · weekly average". The limit is the largest
  count that passed the gate in §8.
- Bar, heatmap and pie charts have no mode (AGG-D5). A stored bar chart that carries the
  setting is treated as Grouped.
- The editor shows the mode for line charts only; the strategy chips are hidden in All-points
  mode.
- **Edge cases**: two entries with the same timestamp in one series (both plotted); an entry
  dated after the last bucket edge (dropped, as it is today — pinned by an existing test); one
  entry only; a series with values on some entries and not others; the *All* range over a
  single instant; a 23- or 25-hour DST day bucket (position computed against that bucket's
  real length); several thousand points (must not crash; see §8).

### 4. Technical design
**Order of work** (so a failed gate costs little):
1. The rendering path only — processor raw path, `Point.x`, renderer changes — reachable by
   seeding `"aggregationMode":"RAW"` into a chart's `configJson`. No editor UI yet.
2. The gate in §8. On a fail: stop, revert, mark AGG-02 and AGG-03 *shelved* in README §3 with
   the measurements.
3. On a pass: set `RAW_POINT_HARD_LIMIT`, then the editor control, captions and the full tests.

- `domain/model/ChartConfig.kt`:
  ```kotlin
  @Serializable enum class AggregationMode { BUCKETED, RAW }      // AGG-03 adds AUTO
  val aggregationMode: AggregationMode = AggregationMode.BUCKETED  // legacy default — never change it
  ```
- `ChartData.Cartesian.Point` becomes `Point(x: Double, value: Double, entryCount: Int,
  timestampMs: Long? = null)`. Grouped points use `x = bucketIndex.toDouble()`; raw points set
  `timestampMs`.
- `ChartDataProcessor`: for `type == LINE && mode == RAW`, a `buildRawCartesianData` that
  reuses the same grid (`bucketEdges`) and, for an entry at time `t` in bucket `i` with edges
  `e[i] ≤ t < e[i+1]`, sets
  `x = i − 0.5 + (t − e[i]) / (e[i+1] − e[i])` — so an integer x is a bucket's midpoint, which
  is where the tick for that bucket already sits. `AggregationInfo(grouped = false, unit = null,
  strategy = null, …)`.
- `CartesianChartContent`:
  - `rememberCartesianChart(…, getXStep = { 1.0 })` — **always**, both modes (see the library
    constraint in *Shared design*; with the range provider fixed this does not change the
    Grouped layout — verify by screenshot comparison);
  - x-range for raw data: `−0.5 … bucketCount − 0.5`;
  - `pointProvider = null` for a series with more than `RAW_POINT_MARKER_LIMIT = 60` points;
  - for raw series, a straight-line `LineCartesianLayer.PointConnector`
    (`{ _, path, _, _, x, y -> path.lineTo(x, y) }`) instead of the default cubic one. Grouped
    charts keep the cubic connector — their look must not change.
- `ChartDataProcessor`: `RAW_POINT_HARD_LIMIT` (a constant beside the processor); when the
  windowed entry count exceeds it, fall through to the grouped path and set
  `AggregationInfo.overLimit = true` for the caption.
- `ChartMarker`: look the touched x up in each series
  (`abs(point.x − target.x) < 1e-6`); for raw points format `timestampMs`.
- `ChartConfigSheet`: a two-option segmented control for line charts; on save,
  `aggregationMode = if (type == LINE) selected else BUCKETED`.
- `ChartCaptions.caption`: the "Every entry" form.

### 5. Data model / persistence changes
No schema change. One new key inside `chart_configs.configJson`, absent on existing rows and
on any chart saved in Grouped mode (defaults are not encoded).

### 6. UI/UX changes
A "Grouped / All points" control in the chart editor for line charts; the caption; denser
lines.

### 7. Interactions with existing functionality
- **Window ↔ display alignment** (audit M1) holds by construction — same window, same grid.
  Keep the "Window ↔ display alignment" tests passing and add raw-mode cases.
- **Tick labels and the y-axis fit** (`LineChartAxisScale`) work on values and bucket
  timestamps; unchanged.
- **Carousel scrolling**: the scroll/zoom setup that lets drags reach the carousel
  (`CartesianChartContent.kt:159–169`) is unchanged.
- **Computed fields**: All-points mode is the faithful way to chart a rolling or running
  field.
- **Unreadable charts**: an unknown mode name would make a config unreadable; only possible if
  a newer database were read by an older build (README R5).

### 8. Testing requirements
- JVM `ChartDataProcessorTest`, new "All points" section: positions for entries at a bucket's
  start, midpoint and just before its end; alignment with the grouped chart's window; dropped
  future entry; multi-series with gaps; duplicate timestamps; single entry; `ChartDstBucketsTest`
  case for a 23-hour day.
- JVM: `ChartConfig` decodes a stored JSON without the key as `BUCKETED`; encoding a
  `BUCKETED` config writes no key; `RAW` round-trips. Bar + `RAW` processes as grouped.
- Emulator:
  - regression pin for the Vico constraint: a raw chart with irregular timestamps renders
    without `IllegalArgumentException` (remove `getXStep` in a scratch build to confirm the
    crash it prevents);
  - screenshot comparison of a Grouped chart before/after the `getXStep` change;
- **Performance gate — do this before any editor work** (the existing harness: two APKs over
  one seeded dataset, `Choreographer: Skipped N frames` and p95 frame time, three runs each).
  - *Dataset*: one event with a line chart over range *All*, seeded so the window holds 300,
    1 000, 2 000, 5 000 and 20 000 entries in turn. *Baseline*: the same chart Grouped.
  - *Scenarios*: (a) open Event Detail with the chart in view; (b) scroll the chart carousel
    across it and back; (c) press and drag the inspect marker along it; (d) log an entry and
    return (chart rebuild).
  - *Go*: at 300 and at 1 000 points, all four scenarios are within run-to-run noise of the
    baseline. Then set `RAW_POINT_HARD_LIMIT` to the largest tested count that is still within
    noise.
  - *Go, reduced*: 300 passes and 1 000 does not → proceed with the ceiling at the passing
    count, and AGG-03 offers no threshold above it.
  - *No-go*: 300 points is measurably worse than the baseline → shelve AGG-02 and AGG-03.
  - Record the table of numbers in the commit message (pass) or in README §3 (shelved).

### 9. Dependencies
AGG-01.

### 10. Open questions / product decisions
P-11 is decided (existing charts stay Grouped), subject to the gate. P-13 (no all-points mode
for bars) is on its default.

### 11. Acceptance criteria
- [ ] A line chart in All-points mode draws one point per entry at its own time, within the same window and axis as the Grouped chart.
- [ ] No existing chart changes appearance.
- [ ] Irregular x positions cannot crash the chart (pinned).
- [ ] The marker shows the individual entry's timestamp and value.
- [ ] The performance gate was run before the editor was built; its numbers are recorded; `RAW_POINT_HARD_LIMIT` is the measured value — or the spec is marked shelved.
- [ ] Above the ceiling the chart groups and its caption says why.

---

## AGG-03 — Automatic aggregation with a global threshold

### 1. Objective
A third mode — **Auto** — that shows every point while there are few enough to read and groups
them once there are too many, with the limit set once, in Settings.

### 2. Current implementation
- After AGG-02: `AggregationMode { BUCKETED, RAW }`; `process(config, entries, fields, nowMs)`.
- `data/repository/UserPreferencesRepository.kt`: `UserPreferences` (:30) and its `Keys`
  (:42) — booleans, strings and a long; decoding never throws (audit L6).
- `ui/settings/SettingsScreen.kt` has sections Appearance, Data & Backup, Import;
  `SingleChoiceDialog` (`ui/components/Dialogs.kt:58`) is the house picker.
- `EventDetailViewModel` does not inject `UserPreferencesRepository`.

### 3. Required behaviour
- `AggregationMode.AUTO`: for a line chart, if the number of entries inside the window holding
  a plotted value is **greater than** the threshold the chart is Grouped, otherwise All points.
  For a bar chart Auto means Grouped.
- The threshold is one app-wide setting: **Settings → Charts → "Group points automatically"**,
  choices 100 / 300 / 1 000 / 3 000, default 300 (P-12, decided). Offer only the choices that
  do not exceed AGG-02's measured `RAW_POINT_HARD_LIMIT`; if 300 itself did not pass the gate
  this spec is shelved with AGG-02.
- Changing the threshold re-renders open charts.
- The chart editor offers **Auto / Grouped / All points** for line charts. A chart created
  after this spec starts on Auto; existing charts are unchanged (P-11).
- The caption says what Auto did and why:
  "Auto · every entry (142)" or "Auto · weekly average (1,240 entries)".
- The editor sentence for Auto: "Shows every entry while there are 300 or fewer in range;
  above that, groups them into weekly averages." (numbers and words follow the settings).
- **Edge cases**: exactly at the threshold (All points); one over (Grouped); the count
  crossing the threshold as entries are logged (the chart switches on the next emission); a
  stored preference that is missing, zero or negative (default); Auto on a bar chart.

### 4. Technical design
- `AggregationMode.AUTO` (append; do not reorder).
- `ChartDataProcessor.process(config, entries, fields, nowMs = …, autoThreshold: Int =
  DEFAULT_AUTO_THRESHOLD)`. Resolve the effective mode after windowing
  (`windowed.size > autoThreshold`). `AggregationInfo` gains `auto: Boolean` and
  `threshold: Int?`.
- `UserPreferences.chartAutoGroupThreshold: Int = 300`;
  `Keys.CHART_AUTO_GROUP_THRESHOLD = intPreferencesKey("chart_auto_group_threshold")`;
  `toUserPreferences` treats a missing or non-positive value as the default;
  `suspend fun setChartAutoGroupThreshold(value: Int)`.
- `SettingsScreen` / `SettingsViewModel`: a "Charts" section with one item opening a
  `SingleChoiceDialog`.
- `EventDetailViewModel`: inject `UserPreferencesRepository`; add
  `prefs.userPreferences.map { it.chartAutoGroupThreshold }.distinctUntilChanged()` as a
  fourth input to `chartDataMap`'s `combine`.
- `ChartConfigSheet`: three-option control; for a new chart the initial selection is `AUTO`
  (written explicitly because it differs from the data-class default).
- `ChartCaptions`: the two Auto forms and the editor sentence (needs the threshold as a
  parameter).

### 5. Data model / persistence changes
No schema change. One DataStore key; one more possible value of the `aggregationMode` key in
chart JSON.

### 6. UI/UX changes
Settings → Charts (new section, one item, one dialog); the editor's mode control gains Auto;
captions.

### 7. Interactions with existing functionality
- `UserPreferencesDecodeTest` — extend for the new key.
- `chartDataMap` gains an input that almost never changes; `distinctUntilChanged` keeps it
  from causing rebuilds.
- Widgets, export, backup: the preference lives in DataStore, which is not part of the SQLite
  backup — same as theme and sort preferences today.
- Computed fields: Auto counts entries holding a value for the plotted field, whether entered
  or computed.

### 8. Testing requirements
- JVM `ChartDataProcessorTest`: threshold boundary (equal → raw, one more → grouped); Auto +
  bar; the reported `auto`/`threshold`.
- JVM `UserPreferencesDecodeTest`: missing, valid, zero, negative, wrong-typed key.
- JVM `ChartCaptionsTest`: Auto forms.
- Emulator: change the threshold in Settings and watch an open chart switch; create a new
  chart (starts on Auto); an old chart is still Grouped; cross the threshold by logging
  entries.

### 9. Dependencies
AGG-02.

### 10. Open questions / product decisions
None. P-11 and P-12 are decided, conditional on AGG-02's performance gate.

### 11. Acceptance criteria
- [ ] Auto resolves by the rule in §3 and the caption states the outcome and the count.
- [ ] The threshold is editable in Settings and takes effect without restarting.
- [ ] New charts default to Auto; no existing chart changes.
- [ ] The default threshold and the offered choices are within AGG-02's measured ceiling.
- [ ] On the gate's dataset, an Auto chart at the threshold stays within noise of the Grouped baseline.

---

## AGG-04 — User-selectable bucket size

### 1. Objective
Let the user override the bucket size a range implies — for example daily points on a Month
chart instead of four weekly ones.

### 2. Current implementation
`bucketEdges` (`ChartDataProcessor.kt:186`) derives the grid from the range alone (table in
*Shared design*). After AGG-01 the chosen unit is reported as `BucketUnit`.

### 3. Required behaviour
- Line and bar charts gain **Group by**: *Automatic* (today's choice) or an explicit unit.
  Offered units depend on the range:

  | Range | Offered |
  |---|---|
  | Day | Automatic (hour) |
  | Week | Automatic (day), Hour (line charts only) |
  | Month | Automatic (week), Day |
  | Year | Automatic (month), Week, Day (line charts only) |
  | All | Automatic, Day, Week, Month |

- An explicit unit lays out calendar-aligned buckets over the same window: hours tile local
  days; days are local calendar days; weeks are 7-calendar-day blocks ending at the end of the
  anchor's day; months are calendar months.
- **Bucket ceilings** (performance and legibility — README R13): at most 400 buckets on a line
  chart and 120 on a bar chart. If the chosen unit would exceed that (possible on *All*), the
  processor uses the next coarser unit, and the caption reports the unit actually used.
- A series with more than 60 points is drawn without point markers (the rule AGG-02 uses; if
  AGG-02 has not landed or was shelved, introduce `RAW_POINT_MARKER_LIMIT` here).
- *Automatic* reproduces today's grids exactly, including Month's 8-day last bucket.
- Applies only where the chart is Grouped (or Auto and currently grouped). Heatmap and pie
  unaffected.
- **Edge cases**: switching range to one that does not offer the stored unit (falls back to
  Automatic in the editor and in the processor); DST days; a window with no entries in most
  buckets (ticks and x-range still span all buckets, as today).

### 4. Technical design
- `ChartConfig.bucketUnit: BucketUnit? = null` (null = Automatic; legacy default).
  `BucketUnit` must then be `@Serializable`; only `HOUR`, `DAY`, `WEEK`, `CALENDAR_MONTH` are
  ever written.
- `bucketEdges(…, override: BucketUnit?)`: when non-null and allowed for the range, build edges
  with `DayMath` (`midnightOf`, `addDays`, `firstOfMonth`) from `windowStartMs` to the end of
  the anchor's day; otherwise the existing code path, untouched.
- `windowStartMs` is unchanged — the override changes the grid inside the window, not the
  window.
- `ChartConfigSheet`: a chip row under Aggregation, filtered by the selected range.
- `ChartTickGenerator` is already span- and count-adaptive; `columnThickness`
  (`CartesianChartContent.kt:176`) already thins bars as buckets multiply.

### 5. Data model / persistence changes
No schema change; one optional key in chart JSON.

### 6. UI/UX changes
One chip row in the chart editor; captions follow automatically (AGG-01).

### 7. Interactions with existing functionality
- AGG-03's Auto decides *whether* to group; this decides *how*, when grouping.
- Existing tests for the automatic grids must pass unmodified.

### 8. Testing requirements
- JVM `ChartDataProcessorTest`: each offered range × unit combination — bucket count, first
  and last edges, contiguity; DST week; both bucket ceilings and their fallback; disallowed
  combinations fall back to Automatic.
- Emulator: Month + Day on a line and on a bar chart; Year + Week; Year + Day on a line chart;
  `font_scale 1.30`.
- Performance check (same harness as AGG-02's gate, scenarios a–c): a Year + Day line chart
  (365 points) and a 120-bucket bar chart are within run-to-run noise of the same charts on
  Automatic. If not, lower the ceilings until they are and record the numbers.

### 9. Dependencies
AGG-01. Independent of AGG-02/03.

### 10. Open questions / product decisions
None. P-14 is decided: build it.

### 11. Acceptance criteria
- [ ] Each offered combination produces calendar-aligned, contiguous buckets over the unchanged window.
- [ ] Automatic is byte-for-byte today's behaviour (existing tests untouched and green).
- [ ] The caption always names the unit actually used.
- [ ] Bucket ceilings are enforced and were checked against the performance harness.
