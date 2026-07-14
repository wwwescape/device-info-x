package com.wwwescape.deviceinfox.console.ui.periodtracker

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wwwescape.deviceinfox.R
import com.wwwescape.deviceinfox.console.data.cycle.CycleGrouping
import com.wwwescape.deviceinfox.console.data.cycle.DerivedCycle
import com.wwwescape.deviceinfox.console.data.cycle.PeriodDayLog
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val DAY_MILLIS = 24 * 60 * 60 * 1000L

/** Only the most recent this-many cycles are charted — a couple with years of history would
 * otherwise cram dozens of illegible slivers into a fixed-width card; the most recent handful is
 * also the most useful trend to actually look at. */
private const val MAX_TREND_ENTRIES = 12

/** A day within this many days of the *next* cycle's start counts as "pre-period" rather than
 * "post-period/follicular" for [CorrelationCard] — a coarse stand-in for the luteal phase (which
 * this app has no way to observe directly), roughly the same ballpark as the luteal-phase seed
 * default elsewhere in Period Tracker. */
private const val PRE_PERIOD_WINDOW_DAYS = 5

private data class ChartEntry(val label: String, val value: Int)

private fun Long.toLocalDate(zoneId: ZoneId): LocalDate = Instant.ofEpochMilli(this).atZone(zoneId).toLocalDate()

/** One bar per gap between consecutive cycle starts (so N cycles → N-1 bars), labeled with the
 * *newer* cycle's start date — "this cycle started N days after the last one". */
private fun cycleLengthTrendEntries(cyclesAscending: List<DerivedCycle>, formatter: DateTimeFormatter, zoneId: ZoneId): List<ChartEntry> =
    cyclesAscending.zipWithNext { older, newer ->
        val gapDays = ((newer.startDateEpochMillis - older.startDateEpochMillis) / DAY_MILLIS).toInt()
        ChartEntry(newer.startDateEpochMillis.toLocalDate(zoneId).format(formatter), gapDays)
    }

/** One bar per cycle, labeled with that cycle's own start date. */
private fun periodLengthTrendEntries(cyclesAscending: List<DerivedCycle>, formatter: DateTimeFormatter, zoneId: ZoneId): List<ChartEntry> =
    cyclesAscending.map { cycle ->
        val durationDays = ((cycle.endDateEpochMillis - cycle.startDateEpochMillis) / DAY_MILLIS).toInt() + 1
        ChartEntry(cycle.startDateEpochMillis.toLocalDate(zoneId).format(formatter), durationDays)
    }

private enum class CyclePhase { PRE_PERIOD, PERIOD, POST_PERIOD }

/** Null when [dateEpochMillis] can't be classified — it's after the last known cycle with no
 * next one to be "pre-period" relative to (see [PRE_PERIOD_WINDOW_DAYS]'s doc comment); those day
 * logs are simply excluded from the correlation below rather than guessed at. */
private fun phaseFor(dateEpochMillis: Long, cyclesAscending: List<DerivedCycle>): CyclePhase? {
    val duringCycle = cyclesAscending.any { dateEpochMillis in it.startDateEpochMillis..it.endDateEpochMillis }
    if (duringCycle) return CyclePhase.PERIOD
    val nextCycleStart = cyclesAscending
        .map { it.startDateEpochMillis }
        .filter { it > dateEpochMillis }
        .minOrNull() ?: return null
    val daysUntilNext = (nextCycleStart - dateEpochMillis) / DAY_MILLIS
    return if (daysUntilNext <= PRE_PERIOD_WINDOW_DAYS) CyclePhase.PRE_PERIOD else CyclePhase.POST_PERIOD
}

private data class TagCorrelation(val tag: String, val prePeriod: Int, val period: Int, val postPeriod: Int) {
    val total: Int get() = prePeriod + period + postPeriod
}

/** Every symptom/mood/spotting tag across [dayLogs] (see `CycleTags.kt`), bucketed by which
 * [CyclePhase] its date falls in relative to [cyclesAscending] — sorted by total occurrences
 * descending so the most common correlations surface first. */
private fun symptomCorrelation(dayLogs: List<PeriodDayLog>, cyclesAscending: List<DerivedCycle>): List<TagCorrelation> {
    val counts = mutableMapOf<String, IntArray>()
    dayLogs.forEach { log ->
        val phase = phaseFor(log.dateEpochMillis, cyclesAscending) ?: return@forEach
        val tags = log.symptoms.asSymptomTags() + log.symptoms.asMoodTags() +
            if (log.symptoms.hasSpotting()) listOf(SPOTTING_KEY) else emptyList()
        tags.forEach { tag ->
            val bucket = counts.getOrPut(tag) { IntArray(3) }
            val index = when (phase) {
                CyclePhase.PRE_PERIOD -> 0
                CyclePhase.PERIOD -> 1
                CyclePhase.POST_PERIOD -> 2
            }
            bucket[index]++
        }
    }
    return counts.map { (tag, bucket) -> TagCorrelation(tag, bucket[0], bucket[1], bucket[2]) }
        .filter { it.total > 0 }
        .sortedByDescending { it.total }
}

@Composable
private fun tagLabel(tag: String): String =
    if (tag == SPOTTING_KEY) stringResource(R.string.console_period_flow_spotting) else if (tag.startsWith(MOOD_PREFIX)) moodLabel(tag) else symptomLabel(tag)

private val PrePeriodColor: Color @Composable get() = MaterialTheme.colorScheme.tertiary
private val PostPeriodColor: Color @Composable get() = MaterialTheme.colorScheme.secondary

/** The Insights tab of `PeriodTrackerScreen` — cycle-length trend, period-length trend, and
 * symptom/mood-vs-cycle-phase correlation, all purely derived from [dayLogs] (the same list
 * `CycleCalendarView`/`PeriodHistoryView` already receive) via [CycleGrouping.deriveCycles] — no
 * separate ViewModel/repository state needed, matching `CalendarStatsView`'s own precedent. */
@Composable
fun PeriodInsightsView(dayLogs: List<PeriodDayLog>, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    val zoneId = remember { ZoneId.systemDefault() }
    val dateFormatter = remember(locale) { DateTimeFormatter.ofPattern("MMM d", locale) }

    val cyclesAscending = remember(dayLogs) {
        CycleGrouping.deriveCycles(dayLogs).sortedBy { it.startDateEpochMillis }.takeLast(MAX_TREND_ENTRIES)
    }
    val cycleLengthEntries = remember(cyclesAscending, dateFormatter, zoneId) {
        cycleLengthTrendEntries(cyclesAscending, dateFormatter, zoneId)
    }
    val periodLengthEntries = remember(cyclesAscending, dateFormatter, zoneId) {
        periodLengthTrendEntries(cyclesAscending, dateFormatter, zoneId)
    }
    val correlations = remember(dayLogs, cyclesAscending) { symptomCorrelation(dayLogs, cyclesAscending) }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        InsightsCard(
            titleRes = R.string.console_period_insights_cycle_length_title,
            icon = Icons.Rounded.Timeline,
            color = MaterialTheme.colorScheme.primary,
            hasData = cycleLengthEntries.isNotEmpty(),
        ) {
            TrendBarChart(entries = cycleLengthEntries, color = MaterialTheme.colorScheme.primary, unitSuffix = "d")
        }

        InsightsCard(
            titleRes = R.string.console_period_insights_period_length_title,
            icon = Icons.Rounded.WaterDrop,
            color = PeriodDayColor,
            hasData = periodLengthEntries.isNotEmpty(),
        ) {
            TrendBarChart(entries = periodLengthEntries, color = OnPeriodDayColor, unitSuffix = "d")
        }

        InsightsCard(
            titleRes = R.string.console_period_insights_correlation_title,
            icon = Icons.Rounded.Insights,
            color = MaterialTheme.colorScheme.tertiary,
            hasData = correlations.isNotEmpty(),
        ) {
            CorrelationLegend()
            Spacer(modifier = Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                correlations.forEach { correlation -> CorrelationRow(correlation) }
            }
        }
    }
}

@Composable
private fun InsightsCard(
    titleRes: Int,
    icon: ImageVector,
    color: Color,
    hasData: Boolean,
    content: @Composable () -> Unit,
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(icon, contentDescription = null, tint = color)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(titleRes), style = MaterialTheme.typography.titleSmall, color = color)
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (hasData) {
                content()
            } else {
                Text(
                    text = stringResource(R.string.console_period_insights_no_data),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** A simple, hand-rolled bar chart built from plain layout primitives — same shape as
 * `CalendarStatsView.MiniBarChart` (no `Canvas`, no charting dependency), generalized to a plain
 * day-count value with an optional unit suffix on each bar's number label. */
@Composable
private fun TrendBarChart(entries: List<ChartEntry>, color: Color, unitSuffix: String, modifier: Modifier = Modifier) {
    val maxValue = entries.maxOf { it.value }.coerceAtLeast(1)
    Row(modifier = modifier.fillMaxWidth().height(120.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        entries.forEach { entry ->
            Column(modifier = Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${entry.value}$unitSuffix",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 2.dp),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        modifier = Modifier
                            .width(14.dp)
                            .fillMaxHeight(entry.value.toFloat() / maxValue)
                            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                            .background(color),
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

@Composable
private fun CorrelationLegend() {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        LegendDot(PrePeriodColor, stringResource(R.string.console_period_phase_pre_period))
        LegendDot(PeriodDayColor, stringResource(R.string.console_period_phase_period))
        LegendDot(PostPeriodColor, stringResource(R.string.console_period_phase_post_period))
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CorrelationRow(correlation: TagCorrelation, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(text = tagLabel(correlation.tag), style = MaterialTheme.typography.bodyMedium)
            Text(
                text = correlation.total.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
        ) {
            if (correlation.prePeriod > 0) {
                Box(modifier = Modifier.weight(correlation.prePeriod.toFloat()).fillMaxHeight().background(PrePeriodColor))
            }
            if (correlation.period > 0) {
                Box(modifier = Modifier.weight(correlation.period.toFloat()).fillMaxHeight().background(PeriodDayColor))
            }
            if (correlation.postPeriod > 0) {
                Box(modifier = Modifier.weight(correlation.postPeriod.toFloat()).fillMaxHeight().background(PostPeriodColor))
            }
        }
    }
}
