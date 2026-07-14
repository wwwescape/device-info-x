package com.wwwescape.deviceinfox.console.ui.periodtracker

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.EditCalendar
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.wwwescape.deviceinfox.R
import com.wwwescape.deviceinfox.console.data.calendar.CalendarItem
import com.wwwescape.deviceinfox.console.data.cycle.PeriodDayLog
import com.wwwescape.deviceinfox.console.data.cycle.CyclePrediction
import com.wwwescape.deviceinfox.console.ui.calendar.JumpToDateDialog
import com.wwwescape.deviceinfox.console.ui.calendar.icon
import com.wwwescape.deviceinfox.console.ui.components.drawHatchedCircle
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.first

/** Same generous fixed window as `CalendarScreen`'s own `MONTH_RANGE_PAST`/`MONTH_RANGE_FUTURE` —
 * this grid has no repository-side sync window of its own to mirror the way Calendar's does, so
 * there's no "correct" bound to derive; this just needs to be generous enough that Jump to date's
 * range validation is never the thing standing between a couple and an old or upcoming entry. */
private const val MONTH_RANGE_PAST = 120L
private const val MONTH_RANGE_FUTURE = 120L

private const val DAY_DETAILS_KEY = "day_details"

/** Fixed pastel colors, deliberately not derived from [MaterialTheme] like the rest of this app's
 * palette — this grid's color coding (period/predicted/fertile/intimacy) needs to read the same
 * regardless of light/dark mode or dynamic color, since it's a fixed legend the user memorizes,
 * not a themed accent. Each filled swatch also gets its own fixed "on" text color, since a pastel
 * fill can't rely on a theme's `onPrimary`/`onTertiary` (tuned for that theme's own saturated
 * color) to stay legible on top of it. */
// internal, not private — PeriodInsightsView/PeriodHistoryView (same package) reuse these so a
// "period day" reads as the same color everywhere in the tab, not a second invented swatch.
internal val PeriodDayColor = Color(0xFFEF9A9A)
internal val OnPeriodDayColor = Color(0xFF7A1F1F)
private val FertileWindowColor = Color(0xFFFFCC80)

/** Marks days with an Intimacy Log entry (see [CalendarRepository.intimacyDates][com.wwwescape.deviceinfox.console.data.calendar.CalendarRepository.intimacyDates]) —
 * purely a read-only cross-feature signal, nothing here is editable from the Period Tracker. */
private val IntimacyDayColor = Color(0xFF89CFF0)
private val OnIntimacyDayColor = Color(0xFF0D3B4F)

/** A single-month calendar (prev/next chevrons, like the just rebuilt Calendar tab) with solid
 * circles for logged period days, a full-size outline ring for a day log with no real flow
 * (spotting/symptom/mood/notes only — see [PeriodDayLog]'s own doc comment), and dashed circles
 * for the predicted next period window, plus a summary header. [dayLogs] is most-recent-first
 * (see `CycleRepository.observeDayLogs`).
 *
 * The cycle's owner ([canEdit]) taps a day to open the editor. A read-only viewer (the partner)
 * taps a day with a logged event (a day log, i.e. Period/Logged, or an Intimacy Log) to select it
 * instead, which shows [DayEventsSection]'s summary cards below the Fertility Window card; tapping
 * a card opens its view-only details page. Predicted/fertile/plain Today days have nothing logged
 * to show, so tapping one (or the selected day again) clears it. */
@Composable
fun CycleCalendarView(
    dayLogs: List<PeriodDayLog>,
    prediction: CyclePrediction?,
    canEdit: Boolean,
    intimacyItemsByDate: Map<LocalDate, List<CalendarItem>>,
    onViewDayLog: (PeriodDayLog) -> Unit,
    onViewIntimacyItem: (CalendarItem) -> Unit,
    onEditDayLog: (PeriodDayLog) -> Unit,
    onNewDayLog: (dateEpochMillis: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val zoneId = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now(zoneId) }
    val currentYearMonth = remember(today) { YearMonth.from(today) }
    val minYearMonth = remember(currentYearMonth) { currentYearMonth.minusMonths(MONTH_RANGE_PAST) }
    val maxYearMonth = remember(currentYearMonth) { currentYearMonth.plusMonths(MONTH_RANGE_FUTURE) }
    var displayedYearMonth by remember { mutableStateOf(currentYearMonth) }
    var showJumpToDateDialog by remember { mutableStateOf(false) }
    // Keyed on canEdit so switching to the self tab (TwoCycles) drops a selection made on the
    // partner's tab: the owner never sees the card.
    var selectedDate by rememberSaveable(canEdit) { mutableStateOf<LocalDate?>(null) }
    val listState = rememberLazyListState()

    fun showMonth(yearMonth: YearMonth) {
        if (yearMonth != displayedYearMonth) {
            displayedYearMonth = yearMonth
            selectedDate = null
        }
    }

    val dayLogsByDate = remember(dayLogs, zoneId) { dayLogs.associateBy { it.dateEpochMillis.toLocalDate(zoneId) } }
    val intimacyDates = intimacyItemsByDate.keys
    // Resolved against live data rather than captured at tap time, so an entry deleted (or
    // edited) on the owner's device while selected updates or hides its card on its own.
    val selectedDayLog = selectedDate?.let { dayLogsByDate[it] }
    val selectedIntimacyItems = selectedDate?.let { intimacyItemsByDate[it] }.orEmpty()
    val showDayDetails = selectedDate != null && (selectedDayLog != null || selectedIntimacyItems.isNotEmpty())
    val periodDates = remember(dayLogsByDate) {
        dayLogsByDate.filterValues { it.flowIntensity != null }.keys
    }
    val otherLoggedDates = remember(dayLogsByDate, periodDates) {
        dayLogsByDate.keys - periodDates
    }
    val predictedRange = remember(prediction, zoneId) {
        prediction?.let {
            val start = it.predictedNextStartEpochMillis.toLocalDate(zoneId)
            start..start.plusDays((it.predictedDurationDays - 1).toLong().coerceAtLeast(0))
        }
    }
    val fertileRange = remember(prediction, zoneId) {
        prediction?.let { it.fertileWindowStartEpochMillis.toLocalDate(zoneId)..it.fertileWindowEndEpochMillis.toLocalDate(zoneId) }
    }

    if (showDayDetails) {
        // Header, month grid, then the Fertility Window card only when there's a prediction.
        val dayDetailsIndex = if (prediction != null) 3 else 2
        LaunchedEffect(selectedDate) { listState.revealItem(DAY_DETAILS_KEY, dayDetailsIndex) }
    }

    LazyColumn(
        modifier = modifier,
        state = listState,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            CycleDayHeaderCard(prediction = prediction, today = today, zoneId = zoneId, locale = locale)
        }
        item {
            MonthGridCard(
                yearMonth = displayedYearMonth,
                today = today,
                locale = locale,
                periodDates = periodDates,
                otherLoggedDates = otherLoggedDates,
                predictedRange = predictedRange,
                fertileRange = fertileRange,
                intimacyDates = intimacyDates,
                selectedDate = selectedDate.takeIf { showDayDetails },
                canGoToPreviousMonth = displayedYearMonth > minYearMonth,
                canGoToNextMonth = displayedYearMonth < maxYearMonth,
                onPreviousMonth = { showMonth(displayedYearMonth.minusMonths(1)) },
                onNextMonth = { showMonth(displayedYearMonth.plusMonths(1)) },
                onTodayClick = { showMonth(currentYearMonth) },
                onJumpToDateClick = { showJumpToDateDialog = true },
                onDayClick = { date ->
                    if (canEdit) {
                        val existing = dayLogsByDate[date]
                        if (existing != null) onEditDayLog(existing) else onNewDayLog(date.toEpochMillis(zoneId))
                    } else {
                        val hasLoggedEvent = date in dayLogsByDate || date in intimacyItemsByDate
                        selectedDate = if (!hasLoggedEvent || date == selectedDate) null else date
                    }
                },
            )
        }
        if (prediction != null) {
            item {
                FertilityCard(fertileRange = fertileRange!!)
            }
        }
        if (showDayDetails) {
            item(key = DAY_DETAILS_KEY) {
                DayEventsSection(
                    date = selectedDate!!,
                    dayLog = selectedDayLog,
                    intimacyItems = selectedIntimacyItems,
                    locale = locale,
                    onDayLogClick = onViewDayLog,
                    onIntimacyItemClick = onViewIntimacyItem,
                )
            }
        }
    }

    if (showJumpToDateDialog) {
        JumpToDateDialog(
            minYearMonth = minYearMonth,
            maxYearMonth = maxYearMonth,
            onConfirm = { date -> showMonth(YearMonth.from(date)) },
            onDismiss = { showJumpToDateDialog = false },
        )
    }
}

/** Estimated fertile window + a mandatory disclaimer — never presented as a guarantee, see the
 * class doc on [CyclePrediction]. Deliberately doesn't also mark "safer" days on the grid: that
 * would read as false reassurance on specific dates, whereas this card states the caveat once,
 * clearly, next to the one thing that actually carries elevated (not zero) risk. */
@Composable
private fun FertilityCard(fertileRange: ClosedRange<LocalDate>) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.console_period_fertile_window_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.tertiary,
            )
            val context = LocalContext.current
            val zoneId = remember { ZoneId.systemDefault() }
            val formatter = remember(context) { DateFormat.getMediumDateFormat(context) }
            Text(
                text = "${formatter.format(Date(fertileRange.start.toEpochMillis(zoneId)))} – " +
                    formatter.format(Date(fertileRange.endInclusive.toEpochMillis(zoneId))),
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.console_period_fertile_window_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Scrolls just far enough that the item with [key] is fully on screen, keeping as much of the
 * content above it (the month grid) in view as possible. `animateScrollToItem` would instead pin
 * it to the top. A single scroll can't always do it: an item still below the viewport isn't laid
 * out yet, so its size is unknown until a first scroll brings it to the bottom edge. */
private suspend fun LazyListState.revealItem(key: Any, index: Int) {
    // The effect can start before the layout pass that adds the item, so wait for it to join the
    // list, then one more frame so a swapped-in day's new content height has been measured too.
    snapshotFlow { layoutInfo.totalItemsCount }.first { it > index }
    withFrameNanos {}
    repeat(2) {
        val info = layoutInfo
        val viewportEnd = info.viewportEndOffset - info.afterContentPadding
        val item = info.visibleItemsInfo.firstOrNull { it.key == key }
        val delta = if (item != null) {
            // Never scroll its top past the viewport's top, even when it's taller than the screen.
            minOf(item.offset + item.size - viewportEnd, item.offset - info.viewportStartOffset)
        } else {
            val last = info.visibleItemsInfo.lastOrNull() ?: return
            if (last.index >= info.totalItemsCount - 1) return
            last.offset + last.size - viewportEnd + info.mainAxisItemSpacing + 1
        }
        if (delta <= 0) return
        animateScrollBy(delta.toFloat())
    }
}

/** The day's logged events for a read-only viewer (the partner), mirroring the Calendar tab's
 * day agenda: a "Thursday, 9 · 2 events" header, then one summary card per event. Tapping a card
 * opens that event's view-only details page (via [onDayLogClick]/[onIntimacyItemClick]); the
 * owner never sees this, since their tap opens the editor instead. */
@Composable
private fun DayEventsSection(
    date: LocalDate,
    dayLog: PeriodDayLog?,
    intimacyItems: List<CalendarItem>,
    locale: Locale,
    onDayLogClick: (PeriodDayLog) -> Unit,
    onIntimacyItemClick: (CalendarItem) -> Unit,
) {
    val dayLabel = remember(date, locale) { DateTimeFormatter.ofPattern("EEEE, d", locale).format(date) }
    val eventCount = intimacyItems.size + if (dayLog != null) 1 else 0
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
        ) {
            Text(text = dayLabel, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(
                text = if (eventCount == 1) {
                    stringResource(R.string.console_calendar_month_one_event)
                } else {
                    stringResource(R.string.console_calendar_month_event_count, eventCount)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        dayLog?.let { DayLogSummaryCard(dayLog = it, onClick = { onDayLogClick(it) }) }
        intimacyItems.forEach { item -> IntimacySummaryCard(item = item, onClick = { onIntimacyItemClick(item) }) }
    }
}

/** Same shape as the Calendar's agenda card (icon, title, one-line subtitle), minus its 3-dot
 * Edit/Delete menu — this viewer can't change a day log. */
@Composable
private fun DayLogSummaryCard(dayLog: PeriodDayLog, onClick: () -> Unit) {
    val details = listOfNotNull(dayLogFlowLabel(dayLog)) +
        dayLog.symptoms.asSymptomTags().map { symptomLabel(it) } +
        dayLog.symptoms.asMoodTags().map { moodLabel(it) }
    SummaryCard(
        onClick = onClick,
        leading = { Icon(Icons.Rounded.WaterDrop, contentDescription = null, tint = PeriodDayColor) },
        title = stringResource(dayLogKindLabelRes(dayLog)),
        subtitle = details.joinToString(", ").ifEmpty { dayLog.notes?.takeIf { it.isNotBlank() } },
    )
}

@Composable
private fun IntimacySummaryCard(item: CalendarItem, onClick: () -> Unit) {
    SummaryCard(
        onClick = onClick,
        leading = { Icon(item.type.icon, contentDescription = null, tint = IntimacyDayColor) },
        title = item.title,
        subtitle = stringResource(R.string.console_calendar_intimacy_section_header),
        coverPhotoFilePath = item.coverPhotoFilePath,
    )
}

@Composable
private fun SummaryCard(
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
    title: String,
    subtitle: String?,
    coverPhotoFilePath: String? = null,
) {
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box {
            // Same faint cover-photo backdrop as the Calendar's agenda card.
            if (coverPhotoFilePath != null) {
                AsyncImage(
                    model = coverPhotoFilePath,
                    contentDescription = null,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop,
                    alpha = 0.12f,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                leading()
                Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(text = title, style = MaterialTheme.typography.bodyLarge)
                    subtitle?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Always has content regardless of data state — the legend explains [MonthGridCard]'s color
 * coding (nowhere else in the UI does), and previously this card could render as a totally blank
 * `Row` (e.g. viewing a partner's tab with no logged entries and no prediction yet). Cycle-day/
 * prediction text still only shows when there's actually data for it, below a divider. */
@Composable
private fun CycleDayHeaderCard(
    prediction: CyclePrediction?,
    today: LocalDate,
    zoneId: ZoneId,
    locale: Locale,
) {
    // The most recent *derived* cycle's start (see CycleRepository.deriveCycles) — never a raw
    // day log's own date, since under day-level logging the most recently logged day isn't
    // necessarily a cycle start.
    val cycleDay = prediction?.currentCycleStartEpochMillis?.toLocalDate(zoneId)?.let { daysBetween(it, today) + 1 }
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            CalendarLegend()
            if (cycleDay != null || prediction != null) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    if (cycleDay != null) {
                        Column {
                            Text(
                                text = stringResource(R.string.console_period_cycle_day_label, cycleDay),
                                style = MaterialTheme.typography.headlineSmall,
                            )
                        }
                    }
                    if (prediction != null) {
                        val predictedStart = prediction.predictedNextStartEpochMillis.toLocalDate(zoneId)
                        Column(horizontalAlignment = Alignment.End) {
                            if (prediction.predictedNextStartRangeHalfWidthDays > 0) {
                                val formatter = remember(locale) { DateTimeFormatter.ofPattern("MMM d", locale) }
                                val halfWidth = prediction.predictedNextStartRangeHalfWidthDays.toLong()
                                Text(
                                    text = stringResource(
                                        R.string.console_period_next_period_range,
                                        predictedStart.minusDays(halfWidth).format(formatter),
                                        predictedStart.plusDays(halfWidth).format(formatter),
                                    ),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            } else {
                                val daysUntil = daysBetween(today, predictedStart)
                                Text(
                                    text = if (daysUntil > 0) {
                                        stringResource(R.string.console_period_next_period_in_days, daysUntil)
                                    } else {
                                        stringResource(R.string.console_period_next_period_due)
                                    },
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            if (prediction.isIrregular) {
                                Text(
                                    text = stringResource(R.string.console_period_irregular_cycle_notice),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** What each marking on [MonthGridCard] means — solid/dashed/outline circle swatches mirroring
 * exactly how [PeriodDayCell] draws each state, so the legend can never visually drift from the
 * grid it's explaining. */
@Composable
private fun CalendarLegend(modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        LegendItem(LegendSwatchStyle.FILLED, PeriodDayColor, stringResource(R.string.console_period_legend_period))
        LegendItem(LegendSwatchStyle.OUTLINE, PeriodDayColor, stringResource(R.string.console_period_legend_logged))
        LegendItem(LegendSwatchStyle.DASHED, PeriodDayColor, stringResource(R.string.console_period_legend_predicted))
        LegendItem(LegendSwatchStyle.DASHED, FertileWindowColor, stringResource(R.string.console_period_legend_fertile))
        LegendItem(LegendSwatchStyle.FILLED, IntimacyDayColor, stringResource(R.string.console_period_legend_intimacy))
        LegendItem(LegendSwatchStyle.HATCH, MaterialTheme.colorScheme.primary, stringResource(R.string.console_period_legend_today))
    }
}

private enum class LegendSwatchStyle { FILLED, DASHED, OUTLINE, HATCH }

@Composable
private fun LegendItem(style: LegendSwatchStyle, color: Color, label: String, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .drawBehind {
                    when (style) {
                        LegendSwatchStyle.FILLED -> drawCircle(color = color)
                        LegendSwatchStyle.DASHED -> drawCircle(
                            color = color,
                            style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 2f))),
                        )
                        LegendSwatchStyle.OUTLINE -> drawCircle(color = color, style = Stroke(width = 1.5.dp.toPx()))
                        LegendSwatchStyle.HATCH -> drawHatchedCircle(color = color, lineSpacing = 1.5.dp, strokeWidth = 0.75.dp)
                    }
                },
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MonthGridCard(
    yearMonth: YearMonth,
    today: LocalDate,
    locale: Locale,
    periodDates: Set<LocalDate>,
    otherLoggedDates: Set<LocalDate>,
    predictedRange: ClosedRange<LocalDate>?,
    fertileRange: ClosedRange<LocalDate>?,
    intimacyDates: Set<LocalDate>,
    selectedDate: LocalDate?,
    canGoToPreviousMonth: Boolean,
    canGoToNextMonth: Boolean,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onTodayClick: () -> Unit,
    onJumpToDateClick: () -> Unit,
    onDayClick: (LocalDate) -> Unit,
) {
    val monthLabel = remember(yearMonth, locale) {
        yearMonth.month.getDisplayName(TextStyle.FULL, locale).replaceFirstChar { it.titlecase(locale) } + " " + yearMonth.year
    }
    val firstDayOfWeek = remember(locale) { WeekFields.of(locale).firstDayOfWeek }
    val weekdayLabels = remember(firstDayOfWeek, locale) {
        (0..6).map { offset -> firstDayOfWeek.plus(offset.toLong()).getDisplayName(TextStyle.NARROW, locale) }
    }
    val firstOfMonth = remember(yearMonth) { yearMonth.atDay(1) }
    val daysInMonth = yearMonth.lengthOfMonth()
    val leadingBlanks = remember(firstOfMonth, firstDayOfWeek) {
        (firstOfMonth.dayOfWeek.value - firstDayOfWeek.value + 7) % 7
    }
    val weekCount = (leadingBlanks + daysInMonth + 6) / 7

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(text = monthLabel, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = onTodayClick) {
                    Icon(Icons.Rounded.Today, contentDescription = stringResource(R.string.console_calendar_today_action))
                }
                IconButton(onClick = onJumpToDateClick) {
                    Icon(Icons.Rounded.EditCalendar, contentDescription = stringResource(R.string.console_calendar_jump_to_date_action))
                }
                IconButton(onClick = onPreviousMonth, enabled = canGoToPreviousMonth) {
                    Icon(Icons.Rounded.ChevronLeft, contentDescription = stringResource(R.string.console_calendar_previous_month))
                }
                IconButton(onClick = onNextMonth, enabled = canGoToNextMonth) {
                    Icon(Icons.Rounded.ChevronRight, contentDescription = stringResource(R.string.console_calendar_next_month))
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                weekdayLabels.forEach { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            for (week in 0 until weekCount) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (column in 0 until 7) {
                        val dayNumber = week * 7 + column - leadingBlanks + 1
                        if (dayNumber in 1..daysInMonth) {
                            val date = yearMonth.atDay(dayNumber)
                            PeriodDayCell(
                                date = date,
                                isToday = date == today,
                                isLogged = date in periodDates,
                                isOtherLogged = date in otherLoggedDates,
                                isPredicted = predictedRange?.let { date in it } == true,
                                isFertile = fertileRange?.let { date in it } == true,
                                isIntimacy = date in intimacyDates,
                                isSelected = date == selectedDate,
                                onClick = { onDayClick(date) },
                                modifier = Modifier.weight(1f),
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f).height(44.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PeriodDayCell(
    date: LocalDate,
    isToday: Boolean,
    isLogged: Boolean,
    isOtherLogged: Boolean,
    isPredicted: Boolean,
    isFertile: Boolean,
    isIntimacy: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val loggedColor = PeriodDayColor
    val predictedColor = PeriodDayColor
    val fertileColor = FertileWindowColor
    // The neutral hatch color for a plain Today with no period/intimate fill underneath —
    // matches Calendar's own DayCell so "today" reads as the same color app-wide.
    val todayNeutralColor = MaterialTheme.colorScheme.primary
    val selectedRingColor = MaterialTheme.colorScheme.onSurface
    Box(
        modifier = modifier
            .height(44.dp)
            .padding(2.dp)
            .drawBehind {
                // Base fill layer: whatever solid circle this day would draw (intimate's blue,
                // a period day's red, or nothing) renders as a hatch of that same color instead
                // whenever it's also Today — falling back to a neutral hatch when there's no fill
                // at all (e.g. a predicted- or fertile-only day) — so Today always reads as
                // recognizably "today" regardless of what else is true about the day. Every
                // stroke-only marker below (predicted/fertile's dashed rings, the logged-no-flow
                // dot, and intimate's own period/fertile outline) draws exactly as it always has,
                // layered on top of this fill either way.
                val fillColor = if (isIntimacy) IntimacyDayColor else if (isLogged) loggedColor else null
                when {
                    fillColor != null && isToday -> drawHatchedCircle(color = fillColor)
                    fillColor != null -> drawCircle(color = fillColor)
                    isToday -> drawHatchedCircle(color = todayNeutralColor)
                }
                if (isIntimacy) {
                    // An outline in whichever of the period/ovulation colors already applies —
                    // the same stroke/dash this cell already uses for isLogged/isFertile on their
                    // own, just layered on top of the fill instead of being the only thing drawn.
                    // isPredicted never coincides with isIntimacy: a predicted day is always in
                    // the future, and intimacy can only ever be logged for today-or-past.
                    when {
                        isLogged -> drawCircle(color = loggedColor, style = Stroke(width = 1.5.dp.toPx()))
                        isFertile -> drawCircle(
                            color = fertileColor,
                            style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f))),
                        )
                    }
                } else {
                    when {
                        isPredicted -> drawCircle(
                            color = predictedColor,
                            style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f))),
                        )
                        isFertile -> drawCircle(
                            color = fertileColor,
                            style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f))),
                        )
                    }
                    // A day log with no real flow (spotting/symptom/mood/notes only) — a full-size
                    // outline ring, same size as every other circle in this cell, distinct from a
                    // real period day only by being unfilled.
                    if (isOtherLogged && !isLogged) {
                        drawCircle(color = loggedColor, style = Stroke(width = 1.5.dp.toPx()))
                    }
                }
                // The read-only viewer's selected day (its details card is showing): a ring just
                // outside every other marker, drawn into the cell's 2dp padding, so it never
                // hides what the day already shows.
                if (isSelected) {
                    drawCircle(
                        color = selectedRingColor,
                        radius = size.minDimension / 2 + 1.dp.toPx(),
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isToday) FontWeight.Bold else null,
            color = when {
                isIntimacy -> OnIntimacyDayColor
                isLogged -> OnPeriodDayColor
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

private fun daysBetween(from: LocalDate, to: LocalDate): Int = java.time.temporal.ChronoUnit.DAYS.between(from, to).toInt()

private fun Long.toLocalDate(zoneId: ZoneId): LocalDate = Instant.ofEpochMilli(this).atZone(zoneId).toLocalDate()

private fun LocalDate.toEpochMillis(zoneId: ZoneId): Long = atStartOfDay(zoneId).toInstant().toEpochMilli()
