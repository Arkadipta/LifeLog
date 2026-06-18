package com.lifelog.app.ui.events

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.lifelog.app.domain.model.EventEntry
import com.lifelog.app.domain.model.EventField
import com.lifelog.app.domain.model.FieldValue
import com.lifelog.app.ui.components.LifeLogCard
import com.lifelog.app.ui.components.SwipeActionBackground
import com.lifelog.app.ui.theme.accentTileColors
import com.lifelog.app.ui.theme.rememberAccentOnSurface
import com.lifelog.app.ui.theme.Motion
import com.lifelog.app.ui.theme.Spacing
import com.lifelog.app.util.iconForName
import com.lifelog.app.util.relativeTimeLabel
import com.lifelog.app.util.toClockParts
import com.lifelog.app.util.toDisplayDate
import com.lifelog.app.util.toUtcDateMillis

/**
 * The one entry-card system shared by the Timeline and Event Detail screens.
 * Both render entries through [entryCardItems], so layout, spacing, swipe
 * actions, and expansion behavior cannot drift apart between the two.
 *
 * Card anatomy: a tinted time tile leads (the sticky [EntryDateHeader] above
 * the group carries the date, so the card never repeats it), field previews
 * sit beside it truncated to one line each, and the relative age reads
 * quietly from the top-right corner. Tapping expands the card in place to
 * the full labeled field list and note. Edit and Delete live exclusively on
 * swipe gestures (start-to-end edits, end-to-start deletes).
 */

/** Collapsed cards preview at most this many field values. */
private const val PREVIEW_FIELD_COUNT = 2

/** Rough single-line capacity of a preview field line ("Name value"). */
private const val FIELD_PREVIEW_FIT = 26

/** Rough single-line capacity of the collapsed note line. */
private const val NOTE_PREVIEW_FIT = 32

private fun String.likelyTruncatedAt(fit: Int): Boolean = length > fit || contains('\n')

/** Field value with its unit appended for numeric fields ("120 mmHg"). */
private fun fieldDisplayValue(field: EventField, value: FieldValue): String {
    val display = value.displayString()
    return if (value is FieldValue.Numeric && field.unit.isNotBlank()) "$display ${field.unit}" else display
}

/**
 * Emits the standard entry list: day groups under sticky [EntryDateHeader]s,
 * each entry a [SwipeableEntryCard] with shared paddings. Set [groupByDate]
 * false when the list is not in chronological order (e.g. sorted by a field
 * value) — headers disappear and each card carries its own date caption.
 */
@OptIn(ExperimentalFoundationApi::class)
fun LazyListScope.entryCardItems(
    entries: List<EventEntry>,
    fieldsFor: (EventEntry) -> List<EventField>,
    onEdit: (EventEntry) -> Unit,
    onDeleteRequest: (EventEntry) -> Unit,
    showEventName: Boolean = false,
    groupByDate: Boolean = true
) {
    if (groupByDate) {
        entries.groupBy { it.createdAt.toDisplayDate() }.forEach { (date, dayEntries) ->
            stickyHeader(key = "header_$date") { EntryDateHeader(date) }
            items(dayEntries, key = { it.id }) { entry ->
                EntryListItem(entry, fieldsFor(entry), showEventName, false, onEdit, onDeleteRequest)
            }
        }
    } else {
        items(entries, key = { it.id }) { entry ->
            EntryListItem(entry, fieldsFor(entry), showEventName, true, onEdit, onDeleteRequest)
        }
    }
}

/**
 * Where a single day group sits inside an [entryCardItems] list, so a
 * [DateNavigator] can scroll straight to it. [index] is the flat
 * [LazyListScope] position of the group's sticky header (or its first card when
 * ungrouped), offset by [leadingItemCount] for items a screen emits before the
 * cards. [utcDateMillis] keys the day in UTC to line up with the M3 picker.
 */
data class EntryDateAnchor(val utcDateMillis: Long, val index: Int)

/**
 * The list position of every day group, in render order. Mirrors the grouping
 * in [entryCardItems] exactly — they live together so a computed jump target
 * can never drift from what is actually laid out. When [groupByDate] is false
 * the list is not chronological, so the first card of each contiguous date run
 * anchors instead of a header.
 */
fun entryDateAnchors(
    entries: List<EventEntry>,
    groupByDate: Boolean = true,
    leadingItemCount: Int = 0
): List<EntryDateAnchor> {
    if (entries.isEmpty()) return emptyList()
    val anchors = mutableListOf<EntryDateAnchor>()
    var index = leadingItemCount
    if (groupByDate) {
        entries.groupBy { it.createdAt.toDisplayDate() }.forEach { (_, dayEntries) ->
            anchors += EntryDateAnchor(dayEntries.first().createdAt.toUtcDateMillis(), index)
            index += 1 + dayEntries.size // sticky header + one item per entry
        }
    } else {
        var lastDate: String? = null
        entries.forEach { entry ->
            val date = entry.createdAt.toDisplayDate()
            if (date != lastDate) {
                anchors += EntryDateAnchor(entry.createdAt.toUtcDateMillis(), index)
                lastDate = date
            }
            index++
        }
    }
    return anchors
}

@Composable
private fun LazyItemScope.EntryListItem(
    entry: EventEntry,
    fields: List<EventField>,
    showEventName: Boolean,
    showFullDate: Boolean,
    onEdit: (EventEntry) -> Unit,
    onDeleteRequest: (EventEntry) -> Unit
) {
    SwipeableEntryCard(
        entry = entry,
        fields = fields,
        onEdit = { onEdit(entry) },
        onDeleteRequest = { onDeleteRequest(entry) },
        showEventName = showEventName,
        showFullDate = showFullDate,
        modifier = Modifier
            .animateItem()
            .padding(horizontal = Spacing.screenEdge, vertical = Spacing.cardGap / 2)
    )
}

/** Pinned day separator: a quiet primary label over the screen background. */
@Composable
fun EntryDateHeader(date: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            date,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(
                start = Spacing.screenEdge + Spacing.xs,
                end = Spacing.screenEdge,
                top = Spacing.sm,
                bottom = Spacing.xs
            )
        )
    }
}

/**
 * [EntryCard] wrapped in the shared swipe gestures: start-to-end reveals
 * Edit, end-to-start reveals Delete. Both snap back after triggering so the
 * screen can confirm (delete) or open the editor without losing the row.
 * The same actions are exposed to accessibility services as custom actions.
 */
@Composable
fun SwipeableEntryCard(
    entry: EventEntry,
    fields: List<EventField>,
    onEdit: () -> Unit,
    onDeleteRequest: () -> Unit,
    modifier: Modifier = Modifier,
    showEventName: Boolean = false,
    showFullDate: Boolean = false
) {
    val currentOnEdit by rememberUpdatedState(onEdit)
    val currentOnDeleteRequest by rememberUpdatedState(onDeleteRequest)
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> currentOnEdit()
                SwipeToDismissBoxValue.EndToStart -> currentOnDeleteRequest()
                SwipeToDismissBoxValue.Settled -> Unit
            }
            false
        }
    )
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = { SwipeActionBackground(dismissState) },
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true,
        modifier = modifier
    ) {
        EntryCard(
            entry = entry,
            fields = fields,
            showEventName = showEventName,
            showFullDate = showFullDate,
            modifier = Modifier.semantics {
                customActions = listOf(
                    CustomAccessibilityAction("Edit entry") { currentOnEdit(); true },
                    CustomAccessibilityAction("Delete entry") { currentOnDeleteRequest(); true }
                )
            }
        )
    }
}

/**
 * One logged entry. The time tile anchors the card (its day comes from the
 * sticky header, or the date caption when [showFullDate]); previews truncate
 * to single lines, and tapping expands the full field list inline with a
 * spring. [showEventName] adds the accent-colored event identity line on
 * screens that mix event types (the timeline).
 */
@Composable
fun EntryCard(
    entry: EventEntry,
    fields: List<EventField>,
    modifier: Modifier = Modifier,
    showEventName: Boolean = false,
    showFullDate: Boolean = false
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    val accent = Color(entry.eventTypeColor)
    // The bright pastel accents need the luminance correction to stay legible as
    // text/icon directly on the card surface (raw, they wash out in light mode).
    val accentOnSurface = rememberAccentOnSurface(accent)
    val orderedFields = fields.filter { entry.fieldValues.containsKey(it.id) }
    val preview = orderedFields.take(PREVIEW_FIELD_COUNT)
    val hasMore = orderedFields.size > PREVIEW_FIELD_COUNT ||
        entry.note.likelyTruncatedAt(NOTE_PREVIEW_FIT) ||
        preview.any { field ->
            entry.fieldValues[field.id]?.let { fv ->
                "${field.name} ${fieldDisplayValue(field, fv)}".likelyTruncatedAt(FIELD_PREVIEW_FIT)
            } == true
        }

    LifeLogCard(
        onClick = if (hasMore) ({ expanded = !expanded }) else null,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md)) {
            // Collapsed, the header row is the whole card, so center it; the
            // leading time tile, field previews, and trailing meta stay
            // mutually centered no matter how many lines each holds.
            Row(verticalAlignment = Alignment.CenterVertically) {
                TimeTile(timestamp = entry.createdAt, accent = accent)
                Spacer(Modifier.width(Spacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    if (showEventName) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                iconForName(entry.eventTypeIcon),
                                null,
                                tint = accentOnSurface,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                entry.eventTypeName,
                                style = MaterialTheme.typography.labelMedium,
                                color = accentOnSurface,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    AnimatedVisibility(
                        visible = !expanded,
                        enter = expandVertically(animationSpec = Motion.spatial()) +
                            fadeIn(tween(Motion.SHORT)),
                        exit = shrinkVertically(animationSpec = Motion.snappy()) +
                            fadeOut(tween(Motion.SHORT))
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier.padding(top = if (showEventName) Spacing.xs else 0.dp)
                        ) {
                            preview.forEach { field ->
                                entry.fieldValues[field.id]?.let { fv ->
                                    FieldPreviewLine(field.name, fieldDisplayValue(field, fv))
                                }
                            }
                            if (entry.note.isNotBlank()) {
                                Text(
                                    entry.note,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.width(Spacing.sm))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        entry.createdAt.relativeTimeLabel(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (showFullDate) {
                        Text(
                            entry.createdAt.toDisplayDate(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                    if (hasMore) {
                        val rotation by animateFloatAsState(
                            targetValue = if (expanded) 180f else 0f,
                            animationSpec = Motion.spatial(),
                            label = "chevron_rotation"
                        )
                        Icon(
                            Icons.Rounded.KeyboardArrowDown,
                            contentDescription = if (expanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier
                                .padding(top = Spacing.xs)
                                .size(18.dp)
                                .graphicsLayer { rotationZ = rotation }
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(animationSpec = Motion.spatial()) +
                    fadeIn(tween(Motion.SHORT)),
                exit = shrinkVertically(animationSpec = Motion.snappy()) +
                    fadeOut(tween(Motion.SHORT))
            ) {
                Column(modifier = Modifier.padding(top = Spacing.sm)) {
                    orderedFields.forEach { field ->
                        entry.fieldValues[field.id]?.let { fv ->
                            FieldValueRow(field.name, fieldDisplayValue(field, fv))
                        }
                    }
                    if (entry.note.isNotBlank()) {
                        Spacer(Modifier.height(Spacing.xs))
                        Text(
                            entry.note,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * Compact clock tile leading every entry card, tinted with the event accent
 * like [com.lifelog.app.ui.components.IconTile]. Honors the device 12/24-hour
 * setting; in 12-hour mode the meridiem reads small under the clock.
 */
@Composable
private fun TimeTile(timestamp: Long, accent: Color) {
    val context = LocalContext.current
    val is24Hour = remember(context) { DateFormat.is24HourFormat(context) }
    val (clock, meridiem) = remember(timestamp, is24Hour) { timestamp.toClockParts(is24Hour) }
    val tile = accentTileColors(accent)
    Surface(shape = MaterialTheme.shapes.medium, color = tile.container) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .widthIn(min = 56.dp)
                .padding(horizontal = Spacing.sm, vertical = 6.dp)
        ) {
            Text(
                clock,
                style = MaterialTheme.typography.titleMedium,
                color = tile.content,
                maxLines = 1
            )
            meridiem?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = tile.content.copy(alpha = 0.85f),
                    maxLines = 1
                )
            }
        }
    }
}

/** Collapsed preview: muted field name and emphasized value on one line. */
@Composable
private fun FieldPreviewLine(fieldName: String, value: String) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                append(fieldName)
                append("  ")
            }
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                append(value)
            }
        },
        style = MaterialTheme.typography.bodySmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

/** Expanded view: labeled row per field, values free to wrap. */
@Composable
private fun FieldValueRow(fieldName: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Text(
            fieldName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(min = 80.dp)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}
