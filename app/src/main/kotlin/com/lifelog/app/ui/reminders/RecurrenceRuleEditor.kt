package com.lifelog.app.ui.reminders

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lifelog.app.domain.model.DayOfMonthMode
import com.lifelog.app.domain.model.RecurrenceRule
import com.lifelog.app.domain.model.RecurrenceType
import com.lifelog.app.ui.components.SectionHeader

private val DAYS = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
private val MONTHS = listOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")
private val WEEK_POSITIONS = listOf(1 to "1st", 2 to "2nd", 3 to "3rd", 4 to "4th", -1 to "Last")

/**
 * A self-contained recurrence rule editor.
 * Caller passes the current [rule] and receives an updated copy via [onRuleChange].
 */
@Composable
fun RecurrenceRuleEditor(
    rule: RecurrenceRule,
    onRuleChange: (RecurrenceRule) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        when (rule.type) {
            RecurrenceType.WEEKLY -> {
                DayOfWeekRow(
                    selected = rule.daysOfWeek,
                    onToggle = { day ->
                        val updated = if (day in rule.daysOfWeek) rule.daysOfWeek - day else rule.daysOfWeek + day
                        onRuleChange(rule.copy(daysOfWeek = updated.sorted()))
                    }
                )
                if (rule.daysOfWeek.isEmpty()) {
                    Text(
                        "No days selected — fires every day.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            RecurrenceType.MONTHLY, RecurrenceType.YEARLY -> {
                MonthSelectorRow(
                    selected = rule.months,
                    onToggle = { m ->
                        val updated = if (m in rule.months) rule.months - m else rule.months + m
                        onRuleChange(rule.copy(months = updated.sorted()))
                    },
                    onSelectAll  = { onRuleChange(rule.copy(months = emptyList())) },
                    onSelectEven = { onRuleChange(rule.copy(months = (0..11 step 2).toList())) },
                    onSelectOdd  = { onRuleChange(rule.copy(months = (1..11 step 2).toList())) }
                )
                DayOfMonthModePill(
                    mode = rule.dayOfMonthMode,
                    onModeChange = { onRuleChange(rule.copy(dayOfMonthMode = it)) }
                )
                when (rule.dayOfMonthMode) {
                    DayOfMonthMode.DAY_OF_MONTH -> DomEditor(
                        selected = rule.daysOfMonth,
                        onToggle = { d ->
                            val updated = if (d in rule.daysOfMonth) rule.daysOfMonth - d else rule.daysOfMonth + d
                            onRuleChange(rule.copy(daysOfMonth = updated.sorted()))
                        }
                    )
                    DayOfMonthMode.DAY_OF_WEEK -> DowPositionEditor(
                        selectedDays = rule.daysOfWeek,
                        selectedPositions = rule.weekPositions,
                        onToggleDay = { day ->
                            val updated = if (day in rule.daysOfWeek) rule.daysOfWeek - day else rule.daysOfWeek + day
                            onRuleChange(rule.copy(daysOfWeek = updated.sorted()))
                        },
                        onTogglePosition = { pos ->
                            val updated = if (pos in rule.weekPositions) rule.weekPositions - pos else rule.weekPositions + pos
                            onRuleChange(rule.copy(weekPositions = updated))
                        }
                    )
                }
            }
            RecurrenceType.INTERVAL -> { /* interval field lives in parent screen */ }
            RecurrenceType.TIME_SINCE_LAST -> { /* duration field lives in parent screen */ }
            RecurrenceType.NONE, RecurrenceType.DAILY -> { /* no sub-options needed */ }
        }
    }
}

// ── Month selector ────────────────────────────────────────────────────────────

@Composable
private fun MonthSelectorRow(
    selected: List<Int>,
    onToggle: (Int) -> Unit,
    onSelectAll: () -> Unit,
    onSelectEven: () -> Unit,
    onSelectOdd: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader("Months")

        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            item {
                FilterChip(
                    selected = selected.isEmpty(),
                    onClick = onSelectAll,
                    label = { Text("All") }
                )
            }
            item {
                FilterChip(
                    selected = selected == (0..11 step 2).toList(),
                    onClick = onSelectEven,
                    label = { Text("Even") }
                )
            }
            item {
                FilterChip(
                    selected = selected == (1..11 step 2).toList(),
                    onClick = onSelectOdd,
                    label = { Text("Odd") }
                )
            }
        }

        // 2-row grid of month chips: Jan-Jun / Jul-Dec
        val rows = MONTHS.chunked(6)
        rows.forEachIndexed { rowIdx, monthRow ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                monthRow.forEachIndexed { colIdx, name ->
                    val idx = rowIdx * 6 + colIdx
                    FilterChip(
                        selected = idx in selected,
                        onClick = { onToggle(idx) },
                        label = { Text(name) }
                    )
                }
            }
        }
    }
}

// ── Mode pill ─────────────────────────────────────────────────────────────────

@Composable
private fun DayOfMonthModePill(mode: DayOfMonthMode, onModeChange: (DayOfMonthMode) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = mode == DayOfMonthMode.DAY_OF_MONTH,
            onClick = { onModeChange(DayOfMonthMode.DAY_OF_MONTH) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
        ) { Text("Day of month") }
        SegmentedButton(
            selected = mode == DayOfMonthMode.DAY_OF_WEEK,
            onClick = { onModeChange(DayOfMonthMode.DAY_OF_WEEK) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
        ) { Text("Day of week") }
    }
}

// ── Day-of-month editor ───────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DomEditor(
    selected: List<Int>,
    onToggle: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader("Days of month")

        // 7-column grid for 1-31
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            (1..31).forEach { day ->
                FilterChip(
                    selected = day in selected,
                    onClick = { onToggle(day) },
                    label = { Text(day.toString()) }
                )
            }
        }

        // "Last day of month" option uses sentinel -1
        FilterChip(
            selected = -1 in selected,
            onClick = { onToggle(-1) },
            label = { Text("Last day") }
        )
    }
}

// ── Day-of-week + position editor ─────────────────────────────────────────────

@Composable
private fun DowPositionEditor(
    selectedDays: List<Int>,
    selectedPositions: List<Int>,
    onToggleDay: (Int) -> Unit,
    onTogglePosition: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader("Days of week")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(DAYS.indices.toList()) { idx ->
                FilterChip(
                    selected = idx in selectedDays,
                    onClick = { onToggleDay(idx) },
                    label = { Text(DAYS[idx]) }
                )
            }
        }

        SectionHeader("Week of month")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(WEEK_POSITIONS) { (pos, label) ->
                FilterChip(
                    selected = pos in selectedPositions || selectedPositions.isEmpty(),
                    onClick = { onTogglePosition(pos) },
                    label = { Text(label) }
                )
            }
        }
        if (selectedPositions.isEmpty()) {
            Text(
                "All weeks selected. Tap a week to restrict.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ── Shared day-of-week picker row (for WEEKLY mode) ───────────────────────────

@Composable
private fun DayOfWeekRow(
    selected: List<Int>,
    onToggle: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader("Days of week")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(DAYS.indices.toList()) { idx ->
                FilterChip(
                    selected = idx in selected,
                    onClick = { onToggle(idx) },
                    label = { Text(DAYS[idx]) }
                )
            }
        }
    }
}

// ── Compact SMTWTFS weekday display (for reminder cards) ─────────────────────

/**
 * Renders "SMTWTFS" in a single row where the letters for [activeDays] are bold
 * and full-opacity, and inactive days are dimmed.
 */
@Composable
fun CompactWeekdayBadge(activeDays: List<Int>, modifier: Modifier = Modifier) {
    val letters = listOf("S", "M", "T", "W", "T", "F", "S")
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(1.dp)) {
        letters.forEachIndexed { idx, letter ->
            val active = idx in activeDays
            Text(
                text = letter,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                color = if (active)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
            )
        }
    }
}
