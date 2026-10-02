package com.lifelog.app.domain

import com.lifelog.app.domain.model.EventEntry
import com.lifelog.app.domain.model.FieldValue
import com.lifelog.app.domain.query.*
import org.junit.Assert.*
import org.junit.Test

class EntryQueryEngineTest {

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun entry(
        id: Long,
        createdAt: Long = id * 1000L,
        vararg values: Pair<Long, FieldValue>
    ) = EventEntry(
        id = id,
        eventTypeId = 1L,
        fieldValues = values.toMap(),
        note = "",
        createdAt = createdAt,
        updatedAt = createdAt
    )

    private val SYSTOLIC = 1L
    private val DIASTOLIC = 2L
    private val SMOKE = 3L
    private val CATEGORY = 4L
    private val TAGS = 5L

    private val entries = listOf(
        entry(1, values = arrayOf(
            SYSTOLIC to FieldValue.Numeric(120.0),
            DIASTOLIC to FieldValue.Numeric(80.0),
            SMOKE to FieldValue.Bool(false),
            CATEGORY to FieldValue.Choice("Breakfast"),
            TAGS to FieldValue.MultiSelect(listOf("Vegetarian", "High Protein"))
        )),
        entry(2, values = arrayOf(
            SYSTOLIC to FieldValue.Numeric(140.0),
            DIASTOLIC to FieldValue.Numeric(95.0),
            SMOKE to FieldValue.Bool(true),
            CATEGORY to FieldValue.Choice("Dinner"),
            TAGS to FieldValue.MultiSelect(listOf("Cheat Meal"))
        )),
        entry(3, values = arrayOf(
            SYSTOLIC to FieldValue.Numeric(130.0),
            DIASTOLIC to FieldValue.Numeric(85.0),
            SMOKE to FieldValue.Bool(false),
            CATEGORY to FieldValue.Choice("Lunch"),
            TAGS to FieldValue.MultiSelect(listOf("Vegetarian"))
        )),
        entry(4) // entry with no field values
    )

    // ── Numeric filter tests ───────────────────────────────────────────────────

    @Test fun `numeric GREATER_THAN filters correctly`() {
        val condition = FilterCondition.NumericFilter(SYSTOLIC, "Systolic", NumericOperator.GREATER_THAN, 130.0)
        val result = EntryQueryEngine.apply(entries, EntryQuery(listOf(condition)))
        assertEquals(listOf(2L), result.map { it.id })
    }

    @Test fun `numeric GREATER_THAN_OR_EQUAL filters correctly`() {
        val condition = FilterCondition.NumericFilter(SYSTOLIC, "Systolic", NumericOperator.GREATER_THAN_OR_EQUAL, 130.0)
        val result = EntryQueryEngine.apply(entries, EntryQuery(listOf(condition)))
        assertEquals(setOf(2L, 3L), result.map { it.id }.toSet())
    }

    @Test fun `numeric LESS_THAN filters correctly`() {
        val condition = FilterCondition.NumericFilter(SYSTOLIC, "Systolic", NumericOperator.LESS_THAN, 130.0)
        val result = EntryQueryEngine.apply(entries, EntryQuery(listOf(condition)))
        assertEquals(listOf(1L), result.map { it.id })
    }

    @Test fun `numeric LESS_THAN_OR_EQUAL filters correctly`() {
        val condition = FilterCondition.NumericFilter(SYSTOLIC, "Systolic", NumericOperator.LESS_THAN_OR_EQUAL, 130.0)
        val result = EntryQueryEngine.apply(entries, EntryQuery(listOf(condition)))
        assertEquals(setOf(1L, 3L), result.map { it.id }.toSet())
    }

    @Test fun `numeric EQUALS filters correctly`() {
        val condition = FilterCondition.NumericFilter(SYSTOLIC, "Systolic", NumericOperator.EQUALS, 140.0)
        val result = EntryQueryEngine.apply(entries, EntryQuery(listOf(condition)))
        assertEquals(listOf(2L), result.map { it.id })
    }

    @Test fun `numeric NOT_EQUALS filters correctly`() {
        val condition = FilterCondition.NumericFilter(SYSTOLIC, "Systolic", NumericOperator.NOT_EQUALS, 120.0)
        val result = EntryQueryEngine.apply(entries, EntryQuery(listOf(condition)))
        // entries 2, 3 match; entry 4 has no value so is excluded
        assertEquals(setOf(2L, 3L), result.map { it.id }.toSet())
    }

    @Test fun `numeric filter excludes entries with missing field`() {
        val condition = FilterCondition.NumericFilter(SYSTOLIC, "Systolic", NumericOperator.GREATER_THAN, 0.0)
        val result = EntryQueryEngine.apply(entries, EntryQuery(listOf(condition)))
        assertFalse(result.any { it.id == 4L })
    }

    // ── Numeric tolerance (L5) ────────────────────────────────────────────────
    //
    // Filters compare through compareNumeric, which answers "is this the number the
    // user typed?" rather than "are these bit-identical Doubles?".

    /** The value a user would type as 0.3, arrived at by arithmetic: 0.30000000000000004. */
    private val accumulated = 0.1 + 0.2

    @Test fun `numeric EQUALS matches a value carrying floating-point error`() {
        val drifted = listOf(entry(10, values = arrayOf(SYSTOLIC to FieldValue.Numeric(accumulated))))
        val condition = FilterCondition.NumericFilter(SYSTOLIC, "Systolic", NumericOperator.EQUALS, 0.3)
        assertTrue("premise: exact equality would miss it", 0.3 != accumulated)
        val result = EntryQueryEngine.apply(drifted, EntryQuery(listOf(condition)))
        assertEquals(listOf(10L), result.map { it.id })
    }

    @Test fun `numeric NOT_EQUALS excludes a value carrying floating-point error`() {
        val drifted = listOf(entry(10, values = arrayOf(SYSTOLIC to FieldValue.Numeric(accumulated))))
        val condition = FilterCondition.NumericFilter(SYSTOLIC, "Systolic", NumericOperator.NOT_EQUALS, 0.3)
        val result = EntryQueryEngine.apply(drifted, EntryQuery(listOf(condition)))
        assertTrue(result.isEmpty())
    }

    @Test fun `tolerance scales with magnitude instead of being a fixed window`() {
        // 1e-9 relative: 1_000_000 tolerates ~1e-3, while 1.0 tolerates only ~1e-9.
        val large = listOf(entry(10, values = arrayOf(SYSTOLIC to FieldValue.Numeric(1_000_000.0000001))))
        val largeEq = FilterCondition.NumericFilter(SYSTOLIC, "Systolic", NumericOperator.EQUALS, 1_000_000.0)
        assertEquals(listOf(10L), EntryQueryEngine.apply(large, EntryQuery(listOf(largeEq))).map { it.id })

        val small = listOf(entry(11, values = arrayOf(SYSTOLIC to FieldValue.Numeric(1.0000001))))
        val smallEq = FilterCondition.NumericFilter(SYSTOLIC, "Systolic", NumericOperator.EQUALS, 1.0)
        assertTrue(EntryQueryEngine.apply(small, EntryQuery(listOf(smallEq))).isEmpty())
    }

    @Test fun `genuinely different values stay different`() {
        // The tolerance must never fuse two numbers a user could tell apart.
        val condition = FilterCondition.NumericFilter(SYSTOLIC, "Systolic", NumericOperator.EQUALS, 120.001)
        val result = EntryQueryEngine.apply(entries, EntryQuery(listOf(condition)))
        assertTrue(result.isEmpty())
    }

    @Test fun `the six numeric operators cannot disagree`() {
        // Exactly one of <, ≈, > holds, so >= is the union of > and ≈, and NOT_EQUALS is
        // the exact complement of EQUALS — for a near-tie as much as for a clear one.
        val near = listOf(entry(10, values = arrayOf(SYSTOLIC to FieldValue.Numeric(accumulated))))
        fun matches(op: NumericOperator) = EntryQueryEngine
            .apply(near, EntryQuery(listOf(FilterCondition.NumericFilter(SYSTOLIC, "Systolic", op, 0.3))))
            .isNotEmpty()

        assertTrue(matches(NumericOperator.EQUALS))
        assertFalse(matches(NumericOperator.NOT_EQUALS))
        assertFalse(matches(NumericOperator.GREATER_THAN))
        assertTrue(matches(NumericOperator.GREATER_THAN_OR_EQUAL))
        assertFalse(matches(NumericOperator.LESS_THAN))
        assertTrue(matches(NumericOperator.LESS_THAN_OR_EQUAL))
    }

    @Test fun `a stored NaN is excluded by every operator, negative ones included`() {
        // Reachable today: CSV inference calls toDoubleOrNull, which accepts "NaN".
        assertTrue("NaN".toDoubleOrNull()?.isNaN() == true)
        val corrupt = listOf(entry(10, values = arrayOf(SYSTOLIC to FieldValue.Numeric(Double.NaN))))
        NumericOperator.entries.forEach { op ->
            val condition = FilterCondition.NumericFilter(SYSTOLIC, "Systolic", op, 120.0)
            val result = EntryQueryEngine.apply(corrupt, EntryQuery(listOf(condition)))
            assertTrue("NaN leaked into a $op result", result.isEmpty())
        }
    }

    @Test fun `a stored infinity is excluded by every operator`() {
        assertTrue("Infinity".toDoubleOrNull()?.isInfinite() == true)
        val corrupt = listOf(entry(10, values = arrayOf(SYSTOLIC to FieldValue.Numeric(Double.POSITIVE_INFINITY))))
        NumericOperator.entries.forEach { op ->
            val condition = FilterCondition.NumericFilter(SYSTOLIC, "Systolic", op, 120.0)
            val result = EntryQueryEngine.apply(corrupt, EntryQuery(listOf(condition)))
            assertTrue("Infinity leaked into a $op result", result.isEmpty())
        }
    }

    @Test fun `a non-finite filter value matches nothing rather than everything`() {
        val condition = FilterCondition.NumericFilter(SYSTOLIC, "Systolic", NumericOperator.NOT_EQUALS, Double.NaN)
        assertTrue(EntryQueryEngine.apply(entries, EntryQuery(listOf(condition))).isEmpty())
    }

    @Test fun `sorting keeps a total order when a value is NaN`() {
        // Sorting is deliberately exact, and Double.compareTo is a total order — a NaN
        // must not corrupt the comparator, it just lands last.
        val withNaN = entries + entry(10, values = arrayOf(SYSTOLIC to FieldValue.Numeric(Double.NaN)))
        val spec = SortSpecification(SortField.NumericField(SYSTOLIC, "Systolic"), SortDirection.ASCENDING)
        val result = EntryQueryEngine.apply(withNaN, EntryQuery(sort = spec))
        // 120, 130, 140, NaN, then the entry with no value at all
        assertEquals(listOf(1L, 3L, 2L, 10L, 4L), result.map { it.id })
    }

    // ── Boolean filter tests ──────────────────────────────────────────────────

    @Test fun `boolean EQUALS true filters correctly`() {
        val condition = FilterCondition.BooleanFilter(SMOKE, "Smoke", BooleanOperator.EQUALS, true)
        val result = EntryQueryEngine.apply(entries, EntryQuery(listOf(condition)))
        assertEquals(listOf(2L), result.map { it.id })
    }

    @Test fun `boolean EQUALS false filters correctly`() {
        val condition = FilterCondition.BooleanFilter(SMOKE, "Smoke", BooleanOperator.EQUALS, false)
        val result = EntryQueryEngine.apply(entries, EntryQuery(listOf(condition)))
        assertEquals(setOf(1L, 3L), result.map { it.id }.toSet())
    }

    @Test fun `boolean NOT_EQUALS filters correctly`() {
        val condition = FilterCondition.BooleanFilter(SMOKE, "Smoke", BooleanOperator.NOT_EQUALS, true)
        val result = EntryQueryEngine.apply(entries, EntryQuery(listOf(condition)))
        assertEquals(setOf(1L, 3L), result.map { it.id }.toSet())
    }

    // ── Choice filter tests ───────────────────────────────────────────────────

    @Test fun `choice EQUALS filters correctly`() {
        val condition = FilterCondition.ChoiceFilter(CATEGORY, "Category", ChoiceOperator.EQUALS, "Dinner")
        val result = EntryQueryEngine.apply(entries, EntryQuery(listOf(condition)))
        assertEquals(listOf(2L), result.map { it.id })
    }

    @Test fun `choice NOT_EQUALS filters correctly`() {
        val condition = FilterCondition.ChoiceFilter(CATEGORY, "Category", ChoiceOperator.NOT_EQUALS, "Breakfast")
        val result = EntryQueryEngine.apply(entries, EntryQuery(listOf(condition)))
        assertEquals(setOf(2L, 3L), result.map { it.id }.toSet())
    }

    // ── MultiSelect filter tests ──────────────────────────────────────────────

    @Test fun `multi-select CONTAINS filters correctly`() {
        val condition = FilterCondition.MultiSelectFilter(TAGS, "Tags", MultiSelectOperator.CONTAINS, "Vegetarian")
        val result = EntryQueryEngine.apply(entries, EntryQuery(listOf(condition)))
        assertEquals(setOf(1L, 3L), result.map { it.id }.toSet())
    }

    @Test fun `multi-select DOES_NOT_CONTAIN filters correctly`() {
        val condition = FilterCondition.MultiSelectFilter(TAGS, "Tags", MultiSelectOperator.DOES_NOT_CONTAIN, "Cheat Meal")
        val result = EntryQueryEngine.apply(entries, EntryQuery(listOf(condition)))
        assertEquals(setOf(1L, 3L), result.map { it.id }.toSet())
    }

    // ── Composite / logical operator tests ───────────────────────────────────

    @Test fun `AND composition - both conditions must match`() {
        val query = EntryQuery(
            filters = listOf(
                FilterCondition.NumericFilter(SYSTOLIC, "Systolic", NumericOperator.GREATER_THAN, 130.0),
                FilterCondition.BooleanFilter(SMOKE, "Smoke", BooleanOperator.EQUALS, true)
            ),
            logicalOperator = LogicalOperator.AND
        )
        val result = EntryQueryEngine.apply(entries, query)
        assertEquals(listOf(2L), result.map { it.id })
    }

    @Test fun `OR composition - either condition must match`() {
        val query = EntryQuery(
            filters = listOf(
                FilterCondition.NumericFilter(SYSTOLIC, "Systolic", NumericOperator.GREATER_THAN, 135.0),
                FilterCondition.NumericFilter(DIASTOLIC, "Diastolic", NumericOperator.GREATER_THAN, 90.0)
            ),
            logicalOperator = LogicalOperator.OR
        )
        val result = EntryQueryEngine.apply(entries, query)
        assertEquals(listOf(2L), result.map { it.id })
    }

    @Test fun `empty query returns all entries unchanged`() {
        val result = EntryQueryEngine.apply(entries, EntryQuery.Empty)
        assertEquals(entries, result)
    }

    // ── Sorting tests ─────────────────────────────────────────────────────────

    @Test fun `sort by timestamp ascending`() {
        val spec = SortSpecification(SortField.Timestamp, SortDirection.ASCENDING)
        val result = EntryQueryEngine.apply(entries, EntryQuery(sort = spec))
        assertEquals(listOf(1L, 2L, 3L, 4L), result.map { it.id })
    }

    @Test fun `sort by timestamp descending`() {
        val spec = SortSpecification(SortField.Timestamp, SortDirection.DESCENDING)
        val result = EntryQueryEngine.apply(entries, EntryQuery(sort = spec))
        assertEquals(listOf(4L, 3L, 2L, 1L), result.map { it.id })
    }

    @Test fun `sort by numeric field ascending`() {
        val spec = SortSpecification(SortField.NumericField(SYSTOLIC, "Systolic"), SortDirection.ASCENDING)
        val result = EntryQueryEngine.apply(entries, EntryQuery(sort = spec))
        // 120, 130, 140, then missing (entry 4) last
        assertEquals(listOf(1L, 3L, 2L, 4L), result.map { it.id })
    }

    @Test fun `sort by numeric field descending`() {
        val spec = SortSpecification(SortField.NumericField(SYSTOLIC, "Systolic"), SortDirection.DESCENDING)
        val result = EntryQueryEngine.apply(entries, EntryQuery(sort = spec))
        // 140, 130, 120, then missing (entry 4) last
        assertEquals(listOf(2L, 3L, 1L, 4L), result.map { it.id })
    }

    @Test fun `missing values appear last on ascending sort`() {
        val spec = SortSpecification(SortField.NumericField(SYSTOLIC, "Systolic"), SortDirection.ASCENDING)
        val result = EntryQueryEngine.apply(entries, EntryQuery(sort = spec))
        assertEquals(4L, result.last().id)
    }

    @Test fun `missing values appear last on descending sort`() {
        val spec = SortSpecification(SortField.NumericField(SYSTOLIC, "Systolic"), SortDirection.DESCENDING)
        val result = EntryQueryEngine.apply(entries, EntryQuery(sort = spec))
        assertEquals(4L, result.last().id)
    }

    // ── Combined filter + sort ────────────────────────────────────────────────

    @Test fun `filter and sort combined`() {
        val query = EntryQuery(
            filters = listOf(
                FilterCondition.BooleanFilter(SMOKE, "Smoke", BooleanOperator.EQUALS, false)
            ),
            sort = SortSpecification(SortField.NumericField(SYSTOLIC, "Systolic"), SortDirection.DESCENDING)
        )
        val result = EntryQueryEngine.apply(entries, query)
        // Smoke == false: entries 1, 3 (entry 4 excluded because no SMOKE field)
        // Sort Systolic descending: 3 (130), 1 (120)
        assertEquals(listOf(3L, 1L), result.map { it.id })
    }
}
