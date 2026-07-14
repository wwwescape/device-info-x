package com.wwwescape.deviceinfox.console.ui.periodtracker

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wwwescape.deviceinfox.R
import java.util.Date

private const val MIN_CYCLE_LENGTH_DAYS = 20
private const val MAX_CYCLE_LENGTH_DAYS = 45
private const val DEFAULT_CYCLE_LENGTH_DAYS = 28

private const val MIN_PERIOD_LENGTH_DAYS = 3
private const val MAX_PERIOD_LENGTH_DAYS = 7
private const val DEFAULT_PERIOD_LENGTH_DAYS = 5

private const val MIN_LUTEAL_PHASE_DAYS = 10
private const val MAX_LUTEAL_PHASE_DAYS = 16
private const val DEFAULT_LUTEAL_PHASE_DAYS = 14

/** "When did your last period start" is a historical fact, not a guess — no future dates. */
@OptIn(ExperimentalMaterial3Api::class)
private val pastOrTodaySelectableDates = object : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= System.currentTimeMillis()
}

/** First-time cycle onboarding — shown by `PeriodTrackerScreen` only while the self tab has zero
 * logged entries (a fully-derived condition, see that file), so it naturally never reappears once
 * [onGetStarted] logs the first real entry. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CycleSetupScreen(
    onGetStarted: (
        startDateEpochMillis: Long,
        cycleLengthDays: Int,
        periodLengthDays: Int,
        lutealPhaseDays: Int,
    ) -> Unit,
    modifier: Modifier = Modifier,
) {
    var startDateEpochMillis by remember { mutableStateOf<Long?>(null) }
    var cycleLengthDays by remember { mutableFloatStateOf(DEFAULT_CYCLE_LENGTH_DAYS.toFloat()) }
    var periodLengthDays by remember { mutableFloatStateOf(DEFAULT_PERIOD_LENGTH_DAYS.toFloat()) }
    var lutealPhaseDays by remember { mutableFloatStateOf(DEFAULT_LUTEAL_PHASE_DAYS.toFloat()) }
    var showDatePicker by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column {
            Text(stringResource(R.string.console_period_onboarding_title), style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.console_period_onboarding_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(stringResource(R.string.console_period_onboarding_date_label), style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDatePicker = true }
                        .padding(vertical = 4.dp),
                ) {
                    val context = LocalContext.current
                    val text = startDateEpochMillis?.let { DateFormat.getMediumDateFormat(context).format(Date(it)) }
                        ?: stringResource(R.string.console_calendar_date_not_set)
                    Text(text = text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Icon(Icons.Rounded.Event, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                DaysSlider(
                    label = stringResource(R.string.console_period_onboarding_cycle_length_label),
                    value = cycleLengthDays,
                    onValueChange = { cycleLengthDays = it },
                    minDays = MIN_CYCLE_LENGTH_DAYS,
                    maxDays = MAX_CYCLE_LENGTH_DAYS,
                )
            }
        }

        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                DaysSlider(
                    label = stringResource(R.string.console_period_period_length_label),
                    value = periodLengthDays,
                    onValueChange = { periodLengthDays = it },
                    minDays = MIN_PERIOD_LENGTH_DAYS,
                    maxDays = MAX_PERIOD_LENGTH_DAYS,
                )
            }
        }

        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                DaysSlider(
                    label = stringResource(R.string.console_period_onboarding_luteal_phase_label),
                    value = lutealPhaseDays,
                    onValueChange = { lutealPhaseDays = it },
                    minDays = MIN_LUTEAL_PHASE_DAYS,
                    maxDays = MAX_LUTEAL_PHASE_DAYS,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.console_period_onboarding_luteal_phase_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Button(
            onClick = {
                startDateEpochMillis?.let {
                    onGetStarted(it, cycleLengthDays.toInt(), periodLengthDays.toInt(), lutealPhaseDays.toInt())
                }
            },
            enabled = startDateEpochMillis != null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.console_period_onboarding_get_started_action))
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = startDateEpochMillis,
            selectableDates = pastOrTodaySelectableDates,
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { startDateEpochMillis = it }
                    showDatePicker = false
                }) { Text(stringResource(R.string.console_pin_continue)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.console_pin_cancel)) }
            },
        ) { DatePicker(state = datePickerState) }
    }
}

/** A labeled "N days" slider card body — shared by the cycle-length and period-length pickers
 * here, and reused as-is (this is `internal`, so visible module-wide) by
 * `ui/settings/CycleLengthDialog.kt`/`PeriodLengthDialog.kt` for the same sliders' settings-side
 * equivalents. */
@Composable
internal fun DaysSlider(label: String, value: Float, onValueChange: (Float) -> Unit, minDays: Int, maxDays: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            text = stringResource(R.string.console_period_days_value, value.toInt()),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = minDays.toFloat()..maxDays.toFloat(),
        steps = (maxDays - minDays - 1).coerceAtLeast(0),
    )
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            text = stringResource(R.string.console_period_days_value, minDays),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.console_period_days_value, maxDays),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
