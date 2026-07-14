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

private const val MIN_PERIOD_LENGTH_DAYS = 3
private const val MAX_PERIOD_LENGTH_DAYS = 7
private const val DEFAULT_PERIOD_LENGTH_DAYS = 5

/** The period-length counterpart to [CycleLengthDialog] — same reasoning, same [DaysSlider]. */
@Composable
fun PeriodLengthDialog(currentValue: Int?, onSave: (Int) -> Unit, onDismiss: () -> Unit) {
    var periodLengthDays by remember { mutableFloatStateOf((currentValue ?: DEFAULT_PERIOD_LENGTH_DAYS).toFloat()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.console_settings_period_length_dialog_title)) },
        text = {
            Column {
                DaysSlider(
                    label = "",
                    value = periodLengthDays,
                    onValueChange = { periodLengthDays = it },
                    minDays = MIN_PERIOD_LENGTH_DAYS,
                    maxDays = MAX_PERIOD_LENGTH_DAYS,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(periodLengthDays.toInt()); onDismiss() }) {
                Text(stringResource(R.string.console_calendar_save_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.console_pin_cancel)) }
        },
    )
}
