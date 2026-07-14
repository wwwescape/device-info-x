package com.wwwescape.deviceinfox.console.ui.periodtracker

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.EditCalendar
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wwwescape.deviceinfox.R
import com.wwwescape.deviceinfox.console.data.cycle.PeriodDayLog
import com.wwwescape.deviceinfox.console.data.cycle.CyclePrediction
import com.wwwescape.deviceinfox.console.ui.calendar.JumpToDateDialog
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

/** Same generous fixed window as `CalendarScreen`'s own `MONTH_RANGE_PAST`/`MONTH_RANGE_FUTURE` —
 * this grid has no repository-side sync window of its own to mirror the way Calendar's does, so
 * there's no "correct" bound to derive; this just needs to be generous enough that Jump to date's
 * range validation is never the thing standing between a couple and an old or upcoming entry. */
private const val MONTH_RANGE_PAST = 120L
private const val MONTH_RANGE_FUTURE = 120L

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
 * circles for logged period days, an outline dot for a day log with no real flow (spotting/
 * symptom/mood/notes only — see [PeriodDayLog]'s own doc comment), and dashed circles for the
 * predicted next period window, plus a summary header. [dayLogs] is most-recent-first (see
 * `CycleRepository.observeDayLogs`). */
@Composable
fun CycleCalendarView(
    dayLogs: List<PeriodDayLog>,
    prediction: CyclePrediction?,
    canEdit: Boolean,
    intimacyDates: Set<LocalDate>,
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

    val dayLogsByDate = remember(dayLogs, zoneId) { dayLogs.associateBy { it.dateEpochMillis.toLocalDate(zoneId) } }
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

    LazyColumn(
        modifier = modifier,
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
                canGoToPreviousMonth = displayedYearMonth > minYearMonth,
                canGoToNextMonth = displayedYearMonth < maxYearMonth,
                onPreviousMonth = { displayedYearMonth = displayedYearMonth.minusMonths(1) },
                onNextMonth = { displayedYearMonth = displayedYearMonth.plusMonths(1) },
                onTodayClick = { displayedYearMonth = currentYearMonth },
                onJumpToDateClick = { showJumpToDateDialog = true },
                onDayClick = { date ->
                    if (canEdit) {
                        val existing = dayLogsByDate[date]
                        if (existing != null) onEditDayLog(existing) else onNewDayLog(date.toEpochMillis(zoneId))
                    }
                },
            )
        }
        if (prediction != null) {
            item {
                FertilityCard(fertileRange = fertileRange!!)
            }
        }
    }

    if (showJumpToDateDialog) {
        JumpToDateDialog(
            minYearMonth = minYearMonth,
            maxYearMonth = maxYearMonth,
            onConfirm = { date -> displayedYearMonth = YearMonth.from(date) },
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
        LegendItem(LegendSwatchStyle.SMALL_OUTLINE, PeriodDayColor, stringResource(R.string.console_period_legend_logged))
        LegendItem(LegendSwatchStyle.DASHED, PeriodDayColor, stringResource(R.string.console_period_legend_predicted))
        LegendItem(LegendSwatchStyle.DASHED, FertileWindowColor, stringResource(R.string.console_period_legend_fertile))
        LegendItem(LegendSwatchStyle.FILLED, IntimacyDayColor, stringResource(R.string.console_period_legend_intimacy))
        LegendItem(LegendSwatchStyle.HATCH, MaterialTheme.colorScheme.primary, stringResource(R.string.console_period_legend_today))
    }
}

private enum class LegendSwatchStyle { FILLED, DASHED, OUTLINE, SMALL_OUTLINE, HATCH }

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
                        LegendSwatchStyle.SMALL_OUTLINE -> drawCircle(
                            color = color,
                            radius = size.minDimension / 2 * DAY_LOG_DOT_RADIUS_FRACTION,
                            style = Stroke(width = 1.5.dp.toPx()),
                        )
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

/** Fraction of the cell's swatch radius used for the small "logged, no real flow" dot (spotting/
 * symptom/mood/notes-only day) — deliberately smaller than the full-size solid/dashed circles the
 * rest of this cell draws, so it reads as "something noted" rather than "a period day". */
private const val DAY_LOG_DOT_RADIUS_FRACTION = 0.35f

@Composable
private fun PeriodDayCell(
    date: LocalDate,
    isToday: Boolean,
    isLogged: Boolean,
    isOtherLogged: Boolean,
    isPredicted: Boolean,
    isFertile: Boolean,
    isIntimacy: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val loggedColor = PeriodDayColor
    val predictedColor = PeriodDayColor
    val fertileColor = FertileWindowColor
    // The neutral hatch color for a plain Today with no period/intimate fill underneath —
    // matches Calendar's own DayCell so "today" reads as the same color app-wide.
    val todayNeutralColor = MaterialTheme.colorScheme.primary
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
                    // A day log with no real flow (spotting/symptom/mood/notes only) — drawn as a
                    // small dot rather than a full-size circle, distinct from a real period day.
                    if (isOtherLogged && !isLogged) {
                        drawCircle(
                            color = loggedColor,
                            radius = size.minDimension / 2 * DAY_LOG_DOT_RADIUS_FRACTION,
                            style = Stroke(width = 1.5.dp.toPx()),
                        )
                    }
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
