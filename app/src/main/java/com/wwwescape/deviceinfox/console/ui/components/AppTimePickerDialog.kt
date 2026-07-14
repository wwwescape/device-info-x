package com.wwwescape.deviceinfox.console.ui.components

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.wwwescape.deviceinfox.R
import java.util.Calendar

/** Material3 has no ready-made `TimePickerDialog` (unlike `DatePickerDialog`) — this wraps
 * [TimePicker] the same way the platform's own component does internally. Shared between
 * Calendar's `EventEditorDialog` (event start/end times) and the Messages composer's Schedule
 * Send dialog — both need the identical wrapper, so it lives here once rather than being
 * duplicated. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTimePickerDialog(
    initialEpochMillis: Long?,
    onDismissRequest: () -> Unit,
    onConfirm: (hour: Int, minute: Int) -> Unit,
) {
    val calendar = remember(initialEpochMillis) {
        Calendar.getInstance().apply { initialEpochMillis?.let { timeInMillis = it } }
    }
    val state = rememberTimePickerState(
        initialHour = calendar.get(Calendar.HOUR_OF_DAY),
        initialMinute = calendar.get(Calendar.MINUTE),
        is24Hour = DateFormat.is24HourFormat(LocalContext.current),
    )
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                TimePicker(state = state)
                Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismissRequest) { Text(stringResource(R.string.console_pin_cancel)) }
                    TextButton(onClick = { onConfirm(state.hour, state.minute) }) {
                        Text(stringResource(R.string.console_pin_continue))
                    }
                }
            }
        }
    }
}

/** Replaces the year/month/day of this epoch-millis value with [newDateEpochMillis]'s, keeping
 * its own time-of-day — used to combine a separately-picked date and time into one value. */
fun Long.withDate(newDateEpochMillis: Long): Long {
    val newDate = Calendar.getInstance().apply { timeInMillis = newDateEpochMillis }
    return Calendar.getInstance().apply {
        timeInMillis = this@withDate
        set(Calendar.YEAR, newDate.get(Calendar.YEAR))
        set(Calendar.MONTH, newDate.get(Calendar.MONTH))
        set(Calendar.DAY_OF_MONTH, newDate.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

/** Replaces the hour/minute of this epoch-millis value with [hour]/[minute], zeroing seconds. */
fun Long.withTime(hour: Int, minute: Int): Long =
    Calendar.getInstance().apply {
        timeInMillis = this@withTime
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
