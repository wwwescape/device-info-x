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

private const val MIN_CYCLE_LENGTH_DAYS = 20
private const val MAX_CYCLE_LENGTH_DAYS = 45
private const val DEFAULT_CYCLE_LENGTH_DAYS = 28

/** Reachable any time from Settings (independent of whether Period Tracker onboarding has ever
 * run) — the "reconfigurable via settings" half of the cycle-length estimate, using the same
 * [DaysSlider] as `ui/periodtracker/CycleSetupScreen.kt`'s onboarding slider. */
@Composable
fun CycleLengthDialog(currentValue: Int?, onSave: (Int) -> Unit, onDismiss: () -> Unit) {
    var cycleLengthDays by remember { mutableFloatStateOf((currentValue ?: DEFAULT_CYCLE_LENGTH_DAYS).toFloat()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.console_settings_cycle_length_dialog_title)) },
        text = {
            Column {
                DaysSlider(
                    label = "",
                    value = cycleLengthDays,
                    onValueChange = { cycleLengthDays = it },
                    minDays = MIN_CYCLE_LENGTH_DAYS,
                    maxDays = MAX_CYCLE_LENGTH_DAYS,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(cycleLengthDays.toInt()); onDismiss() }) {
                Text(stringResource(R.string.console_calendar_save_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.console_pin_cancel)) }
        },
    )
}
