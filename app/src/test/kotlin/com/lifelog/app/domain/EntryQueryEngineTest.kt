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
