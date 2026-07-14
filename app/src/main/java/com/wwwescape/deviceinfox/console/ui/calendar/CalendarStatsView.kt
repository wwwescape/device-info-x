package com.wwwescape.deviceinfox.console.ui.calendar

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Luggage
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.wwwescape.deviceinfox.R
import com.wwwescape.deviceinfox.console.data.calendar.CalendarItem
import com.wwwescape.deviceinfox.console.data.db.EventType
import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.launch

/** Roughly matches [CalendarMonthView]'s own `MONTH_RANGE_PAST`/`MONTH_RANGE_FUTURE`, which in
 * turn mirrors [com.wwwescape.deviceinfox.console.data.calendar.CalendarRepository]'s sync
 * window — the overall span the Yearly range scroller (and the Monthly view's year navigation)
 * can page within; neither can usefully go further than the data actually covers. */
private const val YEAR_RANGE_PAST = 10
private const val YEAR_RANGE_FUTURE = 10

/** How many bars the Yearly chart shows at once. 21 candidate years (`YEAR_RANGE_PAST` +
 * current + `YEAR_RANGE_FUTURE`) all crammed into one fixed-width chart left each bar's "YYYY"
 * label too narrow to render, which is what was actually causing every bar to visibly show just
 * "20" — not a formatting bug on its own, a *crowding* one that a 4-digit label just made obvious.
 * Fixing the crowding (a windowed, pageable range) is the real fix; shortening bar labels to 2
 * digits (see [yearShortLabel]) is the second, complementary half — even 5 full "YYYY" labels
 * would start to feel tight next to each other at this card width. */
private const val YEARLY_RANGE_SIZE = 5

private enum class StatsViewMode { YEARLY, MONTHLY }

/** The four categories the user tracks counts for. [labelRes] and [icon] deliberately reuse
 * [EventTypeUi.kt]'s existing Date/Drive/Trip labels and icons, and the existing Intimacy Log
 * section header, so this view never introduces a second name for the same concept. */
private enum class StatsCategory(val labelRes: Int, val icon: ImageVector) {
    DATE(R.string.console_calendar_type_date, Icons.Rounded.Event),
    DRIVE(R.string.console_calendar_type_drive, Icons.Rounded.DirectionsCar),
    TRIP(R.string.console_calendar_type_trip, Icons.Rounded.Luggage),
    INTIMACY(R.string.console_calendar_intimacy_section_header, Icons.Rounded.Favorite),
}

/** Date, Drive, and Trip are each anchored by a cancellable planned type (`PLANNED_DATE`/
 * `PLANNED_DRIVE`/`PLANNED_TRIP`, see `EventType.supportsCancellation`) — only those three cards
 * split their header count into Kept/Cancelled chips. Intimacy has no cancellation concept at
 * all (it's keyed off [CalendarItem.hasIntimacyLogged], not a cancellable type), so it's excluded. */
private val StatsCategory.tracksCancellation: Boolean
    get() = this != StatsCategory.INTIMACY

/** Same baby-blue [CycleCalendarView][com.wwwescape.deviceinfox.console.ui.periodtracker.CycleCalendarView]
 * uses to mark intimacy days on the Period Tracker — reused here (as a small fixed literal, same
 * as that file does) so intimacy has one consistent color across the app rather than an
 * arbitrary theme role. */
private val IntimacyChartColor = Color(0xFF89CFF0)

private val StatsCategory.chartColor: Color
    @Composable get() = when (this) {
        StatsCategory.DATE -> MaterialTheme.colorScheme.primary
        StatsCategory.DRIVE -> MaterialTheme.colorScheme.secondary
        StatsCategory.TRIP -> MaterialTheme.colorScheme.tertiary
        StatsCategory.INTIMACY -> IntimacyChartColor
    }

private data class ChartEntry(val label: String, val count: Int)

private fun Long.toLocalDate(zoneId: ZoneId): LocalDate = Instant.ofEpochMilli(this).atZone(zoneId).toLocalDate()

private fun CalendarItem.matches(category: StatsCategory): Boolean = when (category) {
    StatsCategory.DATE -> type == EventType.PLANNED_DATE || type == EventType.UNPLANNED_DATE
    StatsCategory.DRIVE -> type == EventType.PLANNED_DRIVE || type == EventType.UNPLANNED_DRIVE
    StatsCategory.TRIP -> type == EventType.PLANNED_TRIP
    StatsCategory.INTIMACY -> hasIntimacyLogged
}

/** What the bars themselves count — a cancelled `PLANNED_DATE`/`PLANNED_DRIVE`/`PLANNED_TRIP`
 * doesn't count as a "kept" occurrence; every other matching item (including the
 * never-cancellable Unplanned variants and Intimacy, where [StatsCategory.tracksCancellation] is
 * false) counts exactly as before. */
private fun CalendarItem.isKept(category: StatsCategory): Boolean =
    matches(category) && (!category.tracksCancellation || !cancelled)

/** The header chip's "Cancelled" count — the complement of [isKept] within [matches], but only
 * for categories where cancellation is even a concept. */
private fun CalendarItem.isCancelledFor(category: StatsCategory): Boolean =
    category.tracksCancellation && matches(category) && cancelled

/** Bucketed by [CalendarItem.startAtEpochMillis], not `displayAtEpochMillis` — none of these four
 * categories' types ever recur (see `EventType.supportsRecurrence`), so `displayAtEpochMillis`
 * would just equal the start date anyway, but start date is the one that's actually correct in
 * spirit for a historical count. One bar per year in `[minYear, maxYear]`, zero-filled. */
private fun List<CalendarItem>.yearlyCounts(category: StatsCategory, minYear: Int, maxYear: Int, zoneId: ZoneId): List<ChartEntry> {
    val counts = asSequence()
        .filter { it.isKept(category) }
        .map { it.startAtEpochMillis.toLocalDate(zoneId).year }
        .groupingBy { it }
        .eachCount()
    return (minYear..maxYear).map { year -> ChartEntry(yearShortLabel(year), counts[year] ?: 0) }
}

/** "YY" instead of "YYYY" — see [YEARLY_RANGE_SIZE]'s doc comment for why bar labels need to be
 * this short even after windowing the chart down to 5 bars. */
private fun yearShortLabel(year: Int): String = "%02d".format(year % 100)

/** One bar per calendar month of [year], zero-filled, labeled with the locale's short month name. */
private fun List<CalendarItem>.monthlyCounts(category: StatsCategory, year: Int, zoneId: ZoneId): List<ChartEntry> {
    val counts = asSequence()
        .filter { it.isKept(category) }
        .map { it.startAtEpochMillis.toLocalDate(zoneId) }
        .filter { it.year == year }
        .groupingBy { it.monthValue }
        .eachCount()
    return (1..12).map { month ->
        val label = Month.of(month).getDisplayName(TextStyle.SHORT, Locale.getDefault())
        ChartEntry(label, counts[month] ?: 0)
    }
}

/** The header chip's Cancelled count — windowed the same way as [yearlyCounts]/[monthlyCounts]
 * (every year in range for Yearly mode, just [selectedYear] for Monthly), so it always describes
 * the same span the bars below it are showing. */
private fun List<CalendarItem>.cancelledCount(
    category: StatsCategory,
    statsMode: StatsViewMode,
    selectedYear: Int,
    minYear: Int,
    maxYear: Int,
    zoneId: ZoneId,
): Int = asSequence()
    .filter { it.isCancelledFor(category) }
    .map { it.startAtEpochMillis.toLocalDate(zoneId).year }
    .count { year -> if (statsMode == StatsViewMode.YEARLY) year in minYear..maxYear else year == selectedYear }

/** The Stats tab of [CalendarScreen] — four small, independently-scaled bar charts (Date, Drive,
 * Trip, Intimacy), switchable between a fixed-range Yearly view and a Monthly view for one
 * navigable year at a time. Purely derived from [items] (the same agenda list
 * [CalendarMonthView]/[CalendarTimelineView] already receive).
 *
 * Long-pressing a card that has data captures it as an image and calls [onChartLongPress] with the
 * image and a suggested caption ("<Category> · <period>") — the caller shows the preview/caption
 * dialog and does the sending. Cards showing "no data" don't react to a long-press at all. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarStatsView(
    items: List<CalendarItem>,
    onChartLongPress: (image: Bitmap, caption: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val zoneId = remember { ZoneId.systemDefault() }
    val today = remember(zoneId) { LocalDate.now(zoneId) }
    val minYear = today.year - YEAR_RANGE_PAST
    val maxYear = today.year + YEAR_RANGE_FUTURE

    var statsMode by remember { mutableStateOf(StatsViewMode.YEARLY) }
    var selectedYear by remember { mutableStateOf(today.year) }
    // Centered on the current year by default (current year plus 2 before, 2 after), same
    // "start of a fixed-size window" shape as selectedYear, just 5-wide instead of 1.
    var yearlyWindowStart by remember {
        mutableStateOf((today.year - 2).coerceIn(minYear, maxYear - YEARLY_RANGE_SIZE + 1))
    }
    val yearlyWindowEnd = yearlyWindowStart + YEARLY_RANGE_SIZE - 1

    val categoryEntries = remember(items, statsMode, selectedYear, yearlyWindowStart, zoneId) {
        StatsCategory.entries.associateWith { category ->
            when (statsMode) {
                StatsViewMode.YEARLY -> items.yearlyCounts(category, yearlyWindowStart, yearlyWindowEnd, zoneId)
                StatsViewMode.MONTHLY -> items.monthlyCounts(category, selectedYear, zoneId)
            }
        }
    }
    val categoryCancelledCounts = remember(items, statsMode, selectedYear, yearlyWindowStart, zoneId) {
        StatsCategory.entries.filter { it.tracksCancellation }.associateWith { category ->
            items.cancelledCount(category, statsMode, selectedYear, yearlyWindowStart, yearlyWindowEnd, zoneId)
        }
    }

    val periodLabel = if (statsMode == StatsViewMode.YEARLY) "$yearlyWindowStart–$yearlyWindowEnd" else selectedYear.toString()

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = statsMode == StatsViewMode.YEARLY,
                onClick = { statsMode = StatsViewMode.YEARLY },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) {
                Text(stringResource(R.string.console_calendar_stats_yearly))
            }
            SegmentedButton(
                selected = statsMode == StatsViewMode.MONTHLY,
                onClick = { statsMode = StatsViewMode.MONTHLY },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) {
                Text(stringResource(R.string.console_calendar_stats_monthly))
            }
        }

        if (statsMode == StatsViewMode.YEARLY) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = { yearlyWindowStart = (yearlyWindowStart - YEARLY_RANGE_SIZE).coerceAtLeast(minYear) },
                    enabled = yearlyWindowStart > minYear,
                ) {
                    Icon(Icons.Rounded.ChevronLeft, contentDescription = null)
                }
                Text(
                    text = "$yearlyWindowStart–$yearlyWindowEnd",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                IconButton(
                    onClick = {
                        yearlyWindowStart = (yearlyWindowStart + YEARLY_RANGE_SIZE).coerceAtMost(maxYear - YEARLY_RANGE_SIZE + 1)
                    },
                    enabled = yearlyWindowEnd < maxYear,
                ) {
                    Icon(Icons.Rounded.ChevronRight, contentDescription = null)
                }
            }
        }

        if (statsMode == StatsViewMode.MONTHLY) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { selectedYear-- }, enabled = selectedYear > minYear) {
                    Icon(Icons.Rounded.ChevronLeft, contentDescription = null)
                }
                Text(
                    text = selectedYear.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                IconButton(onClick = { selectedYear++ }, enabled = selectedYear < maxYear) {
                    Icon(Icons.Rounded.ChevronRight, contentDescription = null)
                }
            }
        }

        StatsCategory.entries.forEach { category ->
            CategoryStatsCard(
                category = category,
                entries = categoryEntries.getValue(category),
                cancelledCount = categoryCancelledCounts[category] ?: 0,
                periodLabel = periodLabel,
                onChartLongPress = onChartLongPress,
            )
        }
    }
}

@Composable
private fun CategoryStatsCard(
    category: StatsCategory,
    entries: List<ChartEntry>,
    cancelledCount: Int,
    periodLabel: String,
    onChartLongPress: (image: Bitmap, caption: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = category.chartColor
    val keptCount = entries.sumOf { it.count }
    val hasData = keptCount > 0 || cancelledCount > 0

    // Long-press → record this card into a graphics layer, snapshot it, and hand it up with a
    // suggested caption. The card is recorded on every draw (cheap — it's just a display list)
    // so there's always a layer ready to snapshot. Only cards with data get the gesture; an empty
    // "no data" card does nothing on long-press.
    val graphicsLayer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val screenBackground = MaterialTheme.colorScheme.background
    val paddingPx = with(LocalDensity.current) { EXPORT_IMAGE_PADDING.roundToPx() }
    val caption = stringResource(R.string.console_calendar_stats_share_caption, stringResource(category.labelRes), periodLabel)
    val shareActionLabel = stringResource(R.string.console_calendar_stats_share_dialog_title)
    val startShare by rememberUpdatedState {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch {
            val captured = graphicsLayer.toImageBitmap()
            onChartLongPress(captured.onOpaqueBackground(screenBackground, paddingPx), caption)
        }
        Unit
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .drawWithContent {
                graphicsLayer.record { this@drawWithContent.drawContent() }
                drawLayer(graphicsLayer)
            }
            .then(
                if (hasData) {
                    Modifier
                        .pointerInput(Unit) { detectTapGestures(onLongPress = { startShare() }) }
                        // The same action for TalkBack, since a plain long-press gesture isn't
                        // reachable from the accessibility menu on its own.
                        .semantics { onLongClick(label = shareActionLabel) { startShare(); true } }
                } else {
                    Modifier
                },
            ),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(category.icon, contentDescription = null, tint = color)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(category.labelRes),
                    style = MaterialTheme.typography.titleSmall,
                    color = color,
                    modifier = Modifier.weight(1f),
                )
                // Hidden entirely when there's nothing to report — a "0 Kept" chip next to a
                // fresh Date/Drive/Trip card would just be noise.
                if (category.tracksCancellation) {
                    if (keptCount > 0) {
                        StatCountChip(
                            count = keptCount,
                            labelRes = R.string.console_calendar_stats_kept_chip,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (cancelledCount > 0) {
                        if (keptCount > 0) Spacer(modifier = Modifier.width(6.dp))
                        StatCountChip(
                            count = cancelledCount,
                            labelRes = R.string.console_calendar_stats_cancelled_chip,
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (keptCount == 0 && cancelledCount == 0) {
                Text(
                    text = stringResource(R.string.console_calendar_stats_no_data),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                MiniBarChart(entries = entries, color = color, modifier = Modifier.fillMaxWidth().height(120.dp))
            }
        }
    }
}

/** A small read-only count chip — reused for both the neutral "Kept" chip and the red
 * "Cancelled" one (same [AssistChip]-as-a-badge pattern as [CancelledChip], just with a count
 * baked into the label instead of a fixed word). */
@Composable
private fun StatCountChip(count: Int, labelRes: Int, containerColor: Color, contentColor: Color, modifier: Modifier = Modifier) {
    AssistChip(
        onClick = {},
        enabled = false,
        label = { Text(stringResource(labelRes, count)) },
        colors = AssistChipDefaults.assistChipColors(
            disabledContainerColor = containerColor,
            disabledLabelColor = contentColor,
        ),
        border = null,
        modifier = modifier,
    )
}

/** Margin around the captured card in the exported image. */
private val EXPORT_IMAGE_PADDING = 16.dp

/** The captured card on an opaque [background] with [paddingPx] of margin on every side.
 *
 * Opaque, because a raw capture has transparent rounded corners that would show the chat bubble's
 * colour through once sent. No longer letterboxed onto a square: Messages now frames a clearly wide
 * image by its own proportions (see `rememberThumbnailSize`), so the chart shows in full. The
 * capture can be a hardware bitmap (which can't be drawn onto a software canvas), hence the
 * ARGB_8888 copy. */
private fun ImageBitmap.onOpaqueBackground(background: Color, paddingPx: Int): Bitmap {
    val source = asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)
    val output = Bitmap.createBitmap(source.width + 2 * paddingPx, source.height + 2 * paddingPx, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(output)
    canvas.drawColor(background.toArgb())
    canvas.drawBitmap(source, paddingPx.toFloat(), paddingPx.toFloat(), null)
    return output
}

/** A simple, hand-rolled bar chart built from plain layout primitives (no [androidx.compose.foundation.Canvas],
 * no charting dependency) — each bar is a [Box] whose height is a fraction of the tallest entry
 * in [entries], bottom-aligned within a fixed-height row so every [CategoryStatsCard] renders at
 * the same compact size regardless of category. */
@Composable
private fun MiniBarChart(entries: List<ChartEntry>, color: Color, modifier: Modifier = Modifier) {
    val maxCount = entries.maxOf { it.count }.coerceAtLeast(1)
    Row(modifier = modifier, horizontalArrangement = Arrangement.SpaceEvenly) {
        entries.forEach { entry ->
            Column(modifier = Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (entry.count > 0) entry.count.toString() else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 2.dp),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        modifier = Modifier
                            .width(18.dp)
                            .fillMaxHeight(entry.count.toFloat() / maxCount)
                            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                            .background(color.copy(alpha = if (entry.count > 0) 1f else 0.15f)),
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = entry.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}
