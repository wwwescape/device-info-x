package com.wwwescape.deviceinfox.console.ui.periodtracker

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wwwescape.deviceinfox.R
import com.wwwescape.deviceinfox.console.data.cycle.PeriodDayLog
import com.wwwescape.deviceinfox.console.data.db.CycleFlowIntensity
import java.util.Date

private sealed interface HistoryRow {
    data class MonthHeader(val label: String) : HistoryRow
    data class Item(val dayLog: PeriodDayLog) : HistoryRow
}

/** [dayLogs] is already most-recent-first (see `CycleRepository.observeDayLogs`), so grouping
 * preserves that order — unlike Calendar's Timeline (forward-looking, chronological), History is
 * backward-looking, so newest-first is the natural read order and there's no "scroll to today"
 * concern. */
private fun buildHistoryRows(dayLogs: List<PeriodDayLog>): List<HistoryRow> {
    val rows = mutableListOf<HistoryRow>()
    var lastMonthKey: String? = null
    dayLogs.forEach { dayLog ->
        val monthKey = DateFormat.format("MMMM yyyy", Date(dayLog.dateEpochMillis)).toString()
        if (monthKey != lastMonthKey) {
            rows += HistoryRow.MonthHeader(monthKey)
            lastMonthKey = monthKey
        }
        rows += HistoryRow.Item(dayLog)
    }
    return rows
}

/** The History tab of `PeriodTrackerScreen` — a flat, most-recent-first list of every logged day,
 * grouped by month header (mirrors Calendar's own Timeline, `CalendarScreen.CalendarTimelineView`
 * / `buildAgendaRows`). No 3-dot edit/delete menu like Calendar's rows have — `PeriodDayLogEditor`
 * already has Delete built into its own top bar, and Month's calendar cells already work by
 * tap-to-open-editor alone, so a row here does the same rather than duplicating that affordance. */
@Composable
fun PeriodHistoryView(
    dayLogs: List<PeriodDayLog>,
    canEdit: Boolean,
    onItemClick: (PeriodDayLog) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = remember(dayLogs) { buildHistoryRows(dayLogs) }

    if (dayLogs.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.console_period_history_empty_state),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(
            rows,
            key = { row ->
                when (row) {
                    is HistoryRow.MonthHeader -> row.label
                    is HistoryRow.Item -> row.dayLog.id
                }
            },
        ) { row ->
            when (row) {
                is HistoryRow.MonthHeader -> Text(
                    text = row.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                )
                is HistoryRow.Item -> HistoryItemRow(
                    dayLog = row.dayLog,
                    clickable = canEdit,
                    onClick = { onItemClick(row.dayLog) },
                )
            }
        }
    }
}

@Composable
private fun flowLabel(flowIntensity: CycleFlowIntensity): String = when (flowIntensity) {
    CycleFlowIntensity.LIGHT -> stringResource(R.string.console_period_flow_light)
    CycleFlowIntensity.MEDIUM -> stringResource(R.string.console_period_flow_medium)
    CycleFlowIntensity.HEAVY -> stringResource(R.string.console_period_flow_heavy)
}

@Composable
private fun HistoryItemRow(dayLog: PeriodDayLog, clickable: Boolean, onClick: () -> Unit) {
    val content: @Composable () -> Unit = {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                val context = LocalContext.current
                Text(
                    text = DateFormat.getMediumDateFormat(context).format(Date(dayLog.dateEpochMillis)),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                dayLog.flowIntensity?.let { flow ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(PeriodDayColor))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = flowLabel(flow),
                            style = MaterialTheme.typography.labelMedium,
                            color = OnPeriodDayColor,
                        )
                    }
                }
            }
            val tags = dayLog.symptoms.asSymptomTags() + dayLog.symptoms.asMoodTags() +
                if (dayLog.symptoms.hasSpotting()) listOf(SPOTTING_KEY) else emptyList()
            if (tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    tags.forEach { tag -> TagChip(label = tagLabelFor(tag)) }
                }
            }
            if (!dayLog.notes.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = dayLog.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }

    if (clickable) {
        Card(
            onClick = onClick,
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier.fillMaxWidth(),
        ) { content() }
    } else {
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier.fillMaxWidth(),
        ) { content() }
    }
}

@Composable
private fun tagLabelFor(tag: String): String =
    if (tag == SPOTTING_KEY) stringResource(R.string.console_period_flow_spotting) else if (tag.startsWith(MOOD_PREFIX)) moodLabel(tag) else symptomLabel(tag)

@Composable
private fun TagChip(label: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
