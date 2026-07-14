package com.wwwescape.deviceinfox.console.ui.periodtracker

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.wwwescape.deviceinfox.R
import com.wwwescape.deviceinfox.console.data.cycle.PeriodDayLog
import com.wwwescape.deviceinfox.console.ui.calendar.DetailChipRow
import com.wwwescape.deviceinfox.console.ui.calendar.DetailRow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** View-only page for one day log, opened from a read-only viewer's (the partner's) summary card
 * in [CycleCalendarView] — the Period Tracker's counterpart to the Calendar's `EventDetailScreen`,
 * with the same full-screen dialog, back button and hero header layout but no Edit action, since
 * only the cycle's owner can change a day log. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeriodDayLogDetailScreen(dayLog: PeriodDayLog, onBack: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val dateLabel = remember(dayLog.dateEpochMillis, locale) {
        Instant.ofEpochMilli(dayLog.dateEpochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
            .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale))
    }
    Dialog(onDismissRequest = onBack, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Scaffold(
                topBar = {
                    CenterAlignedTopAppBar(
                        title = { Text(stringResource(R.string.console_period_day_log_detail_title)) },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = stringResource(R.string.console_pin_cancel),
                                )
                            }
                        },
                    )
                },
            ) { innerPadding ->
                Column(
                    modifier = Modifier.fillMaxSize().padding(innerPadding).verticalScroll(rememberScrollState()),
                ) {
                    DayLogHeroHeader(dayLog = dayLog)
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        Text(text = dateLabel, style = MaterialTheme.typography.headlineSmall)
                        dayLogFlowLabel(dayLog)?.let {
                            DetailRow(label = stringResource(R.string.console_period_flow_label), value = it)
                        }
                        val symptoms = dayLog.symptoms.asSymptomTags()
                        if (symptoms.isNotEmpty()) {
                            DetailChipRow(
                                label = stringResource(R.string.console_period_symptoms_label),
                                values = symptoms.map { symptomLabel(it) },
                            )
                        }
                        val moods = dayLog.symptoms.asMoodTags()
                        if (moods.isNotEmpty()) {
                            DetailChipRow(
                                label = stringResource(R.string.console_period_mood_label),
                                values = moods.map { moodLabel(it) },
                            )
                        }
                        if (!dayLog.notes.isNullOrBlank()) {
                            DetailRow(label = stringResource(R.string.console_period_notes_label), value = dayLog.notes)
                        }
                    }
                }
            }
        }
    }
}

/** Same shape as `EventDetailScreen`'s hero header, tinted with the grid's own period swatch. */
@Composable
private fun DayLogHeroHeader(dayLog: PeriodDayLog, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
            .background(Brush.verticalGradient(listOf(PeriodDayColor, MaterialTheme.colorScheme.surfaceContainerHigh))),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.WaterDrop, contentDescription = null, tint = OnPeriodDayColor, modifier = Modifier.size(40.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(dayLogKindLabelRes(dayLog)),
                style = MaterialTheme.typography.labelLarge,
                color = OnPeriodDayColor,
            )
        }
    }
}

/** "Period" for a day log with real flow, "Logged" otherwise — the grid legend's own two labels. */
internal fun dayLogKindLabelRes(dayLog: PeriodDayLog): Int =
    if (dayLog.flowIntensity != null) R.string.console_period_legend_period else R.string.console_period_legend_logged

/** The real flow level, or Spotting (a synthetic tag, see [SPOTTING_KEY]), or null for neither. */
@Composable
internal fun dayLogFlowLabel(dayLog: PeriodDayLog): String? =
    dayLog.flowIntensity?.let { flowLabel(it) }
        ?: if (dayLog.symptoms.hasSpotting()) stringResource(R.string.console_period_flow_spotting) else null
