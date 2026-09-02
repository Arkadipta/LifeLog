package com.lifelog.app.ui.events

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.abs

/**
 * Shared "jump to date" engine behind both the Timeline and Event Detail
 * screens. It owns the date-picker visibility, knows which days actually hold
 * entries (so empty days can't be chosen), tracks the day at the top of the
 * viewport for preselection, and scrolls the list to a chosen day or back to
 * the newest entries. A screen only supplies its prepared list, the list's
 * [LazyListState], and how many items precede the cards — all selection,
 * validation, and scrolling lives here so the two screens cannot drift apart.
 *
 * Build one with [rememberDateNavigator] and render [JumpToDateDialog] once;
 * trigger it from a top-bar icon (Timeline) or an overflow item (Event Detail).
 */
@Stable
class DateNavigator internal constructor(
    private val listState: LazyListState,
    private val scope: CoroutineScope
) {
    /** Day positions for the current list; refreshed by [rememberDateNavigator]
     *  every recomposition before any screen reads the navigator. */
    internal var anchors: List<EntryDateAnchor> = emptyList()

    var pickerVisible by mutableStateOf(false)
        private set

    /** Date jumping only makes sense for a chronological list with entries. */
    val canPickDate: Boolean get() = anchors.isNotEmpty()

    /** UTC start-of-day millis for every day with entries — the picker's
     *  selectable set, so non-entry days are greyed out and can't be picked. */
    val selectableDates: Set<Long> get() = anchors.mapTo(HashSet()) { it.utcDateMillis }

    /** The day currently at the top of the viewport, for picker preselection;
     *  null only when there is nothing to jump to. */
    fun currentVisibleDate(): Long? {
        if (anchors.isEmpty()) return null
        val first = listState.firstVisibleItemIndex
        return (anchors.lastOrNull { it.index <= first } ?: anchors.first()).utcDateMillis
    }

    fun openPicker() {
        if (canPickDate) pickerVisible = true
    }

    fun dismissPicker() {
        pickerVisible = false
    }

    /** Smoothly scroll to the entries for [utcDateMillis], falling back to the
     *  nearest day with data if that exact day is somehow unavailable. */
    fun jumpToDate(utcDateMillis: Long) {
        pickerVisible = false
        val target = anchors.minByOrNull { abs(it.utcDateMillis - utcDateMillis) } ?: return
        scope.launch { listState.animateScrollToItem(target.index) }
    }

    /** Instantly return to the newest entries at the top of the list. */
    fun jumpToTop() {
        scope.launch { listState.scrollToItem(0) }
    }
}

/**
 * Creates the [DateNavigator] for a list and keeps its day positions in sync
 * with [model], whose anchors were computed with the rest of the list. Pass
 * [leadingItemCount] for any items the screen renders above the entry cards
 * (e.g. a chart carousel). A model with no day groups is not chronological, so
 * date jumping disables itself while jump-to-top stays available.
 */
@Composable
fun rememberDateNavigator(
    model: EntryListModel,
    listState: LazyListState,
    leadingItemCount: Int = 0
): DateNavigator {
    val scope = rememberCoroutineScope()
    val navigator = remember(listState) { DateNavigator(listState, scope) }
    navigator.anchors = remember(model.anchors, leadingItemCount) {
        model.anchors.offsetBy(leadingItemCount)
    }
    return navigator
}

/**
 * The Material 3 date-picker flow for a [DateNavigator]. Renders only while the
 * navigator's picker is open, preselects the currently visible day, and greys
 * out every day without entries so a jump always lands on real data.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JumpToDateDialog(navigator: DateNavigator) {
    if (!navigator.pickerVisible) return

    val selectable = navigator.selectableDates
    val years = remember(selectable) { selectable.mapTo(HashSet(), ::utcYearOf) }
    val initial = remember { navigator.currentVisibleDate() }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial,
        initialDisplayedMonthMillis = initial,
        yearRange = remember(years) {
            if (years.isEmpty()) IntRange(1900, 2100) else years.min()..years.max()
        },
        selectableDates = remember(selectable, years) {
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis in selectable
                override fun isSelectableYear(year: Int) = year in years
            }
        }
    )

    DatePickerDialog(
        onDismissRequest = { navigator.dismissPicker() },
        confirmButton = {
            TextButton(
                onClick = { state.selectedDateMillis?.let(navigator::jumpToDate) },
                enabled = state.selectedDateMillis != null
            ) { Text("Jump") }
        },
        dismissButton = {
            TextButton(onClick = { navigator.dismissPicker() }) { Text("Cancel") }
        }
    ) {
        DatePicker(
            state = state,
            title = {
                Text(
                    "Jump to date",
                    modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp)
                )
            }
        )
    }
}

private fun utcYearOf(utcMillis: Long): Int =
    Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        .apply { timeInMillis = utcMillis }
        .get(Calendar.YEAR)
