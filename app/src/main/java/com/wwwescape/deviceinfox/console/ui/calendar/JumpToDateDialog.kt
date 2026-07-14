package com.wwwescape.deviceinfox.console.ui.calendar

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import com.wwwescape.deviceinfox.R
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle

private val JumpDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT)

/** Digits-only input that auto-inserts the `/` separators as the user types (`18` → `18/`,
 * `1806` → `18/06/`, …) — reads as a guided DD/MM/YYYY field without depending on the keyboard's
 * own `/` key, which isn't always one tap away. */
private fun maskDateInput(raw: String): String {
    val digits = raw.filter { it.isDigit() }.take(8)
    return buildString {
        digits.forEachIndexed { index, c ->
            if (index == 2 || index == 4) append('/')
            append(c)
        }
    }
}

/** Only attempts a parse once all 10 characters (`DD/MM/YYYY`) are in — [ResolverStyle.STRICT]
 * rejects a calendar-invalid date like `31/02/2024` instead of silently rolling it over to March. */
private fun parseJumpDate(text: String): LocalDate? {
    if (text.length != 10) return null
    return try {
        LocalDate.parse(text, JumpDateFormatter)
    } catch (e: DateTimeParseException) {
        null
    }
}

/** The DD/MM/YYYY popup [MonthCalendarCard]'s jump-to-date button opens — a 20-year sync window
 * (see `CalendarRepository.refresh()` and this package's `MONTH_RANGE_PAST`/`_FUTURE`) is still a
 * lot of chevron taps to cross by hand, so this jumps [CalendarMonthView] straight to the typed
 * date's month, with that exact day pre-selected in the agenda panel below it. */
@Composable
fun JumpToDateDialog(
    minYearMonth: YearMonth,
    maxYearMonth: YearMonth,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    var fieldValue by remember { mutableStateOf(TextFieldValue("")) }
    val parsedDate = parseJumpDate(fieldValue.text)
    val inRange = parsedDate != null && YearMonth.from(parsedDate) in minYearMonth..maxYearMonth
    val showInvalid = parsedDate == null && fieldValue.text.length == 10

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.console_calendar_jump_to_date_action)) },
        text = {
            Column {
                TextField(
                    value = fieldValue,
                    onValueChange = { new ->
                        val masked = maskDateInput(new.text)
                        fieldValue = TextFieldValue(masked, selection = TextRange(masked.length))
                    },
                    placeholder = { Text(stringResource(R.string.console_calendar_jump_to_date_placeholder)) },
                    singleLine = true,
                    isError = showInvalid || (parsedDate != null && !inRange),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                when {
                    showInvalid -> Text(
                        text = stringResource(R.string.console_calendar_jump_to_date_invalid),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    parsedDate != null && !inRange -> Text(
                        text = stringResource(
                            R.string.console_calendar_jump_to_date_out_of_range,
                            minYearMonth.year,
                            maxYearMonth.year,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { parsedDate?.let(onConfirm); onDismiss() },
                enabled = parsedDate != null && inRange,
            ) {
                Text(stringResource(R.string.console_calendar_jump_to_date_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.console_pin_cancel)) }
        },
    )
}
