package com.wwwescape.deviceinfox.console.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.wwwescape.deviceinfox.R
import com.wwwescape.deviceinfox.console.ui.periodtracker.DaysSlider

private const val MIN_LUTEAL_PHASE_DAYS = 10
private const val MAX_LUTEAL_PHASE_DAYS = 16
private const val DEFAULT_LUTEAL_PHASE_DAYS = 14

/** The luteal-phase counterpart to [CycleLengthDialog]/[PeriodLengthDialog] — same reasoning,
 * same [DaysSlider], reachable any time from Settings regardless of whether onboarding ever set
 * one (see `ConsoleSettingsRepository.averageLutealPhaseDaysSeed`'s own doc comment for why a
 * null value here still means "use the 14-day textbook default," not "use 0"). */
@Composable
fun LutealPhaseDialog(currentValue: Int?, onSave: (Int) -> Unit, onDismiss: () -> Unit) {
    var lutealPhaseDays by remember { mutableFloatStateOf((currentValue ?: DEFAULT_LUTEAL_PHASE_DAYS).toFloat()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.console_settings_luteal_phase_dialog_title)) },
        text = {
            Column {
                DaysSlider(
                    label = "",
                    value = lutealPhaseDays,
                    onValueChange = { lutealPhaseDays = it },
                    minDays = MIN_LUTEAL_PHASE_DAYS,
                    maxDays = MAX_LUTEAL_PHASE_DAYS,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(lutealPhaseDays.toInt()); onDismiss() }) {
                Text(stringResource(R.string.console_calendar_save_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.console_pin_cancel)) }
        },
    )
}
