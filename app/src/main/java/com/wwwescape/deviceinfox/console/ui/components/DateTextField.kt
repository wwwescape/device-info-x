package com.wwwescape.deviceinfox.console.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle
import java.util.Calendar

private val DateFieldFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT)

/** Digits-only input that auto-inserts the `/` separators as the user types (`18` → `18/`,
 * `1806` → `18/06/`, …) — same masking [com.wwwescape.deviceinfox.console.ui.calendar.JumpToDateDialog]
 * uses for its own DD/MM/YYYY field. */
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
private fun parseTypedDate(text: String): LocalDate? {
    if (text.length != 10) return null
    return try {
        LocalDate.parse(text, DateFieldFormatter)
    } catch (e: DateTimeParseException) {
        null
    }
}

/** [toIsoDateString][com.wwwescape.deviceinfox.console.data.network.dto.toIsoDateString] (what
 * ultimately turns a birthday's epoch millis into the `YYYY-MM-DD` the server stores) reads Y/M/D
 * off the *device's default timezone* [Calendar], not UTC — this keeps a typed date's epoch-millis
 * value consistent with that existing convention (rather than landing one day off in a
 * negative-UTC-offset timezone, the way handing [DatePickerDialog]'s raw UTC-midnight
 * `selectedDateMillis` straight through would). */
private fun LocalDate.toLocalMidnightEpochMillis(): Long =
    Calendar.getInstance().apply {
        clear()
        set(year, monthValue - 1, dayOfMonth)
    }.timeInMillis

private fun Long.toLocalDateAtDefaultZone(): LocalDate {
    val calendar = Calendar.getInstance().apply { timeInMillis = this@toLocalDateAtDefaultZone }
    return LocalDate.of(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1, calendar.get(Calendar.DAY_OF_MONTH))
}

/**
 * A DD/MM/YYYY text field — type a date directly, or tap the trailing calendar icon to pick one
 * from [DatePickerDialog] instead; either way lands on the same [dateEpochMillis]. Shared by
 * [com.wwwescape.deviceinfox.console.ui.auth.AccountSetupDialog]'s registration flow and
 * Settings' Profile section so Birthday looks and behaves identically in both places, as just
 * another field alongside the plain [OutlinedTextField]s next to it.
 *
 * [resetKey] mirrors the same keying every sibling field on those two screens already uses
 * (`remember(uiState.X)` in Settings, plain `rememberSaveable` with no key at all during
 * registration) — the field only reseeds its own typed text when [resetKey] changes, which
 * happens on a genuine external reload, never as a side effect of its own [onDateChange] calls.
 * Without that distinction, [onDateChange] pushing an intermediate `null` mid-typing (an
 * incomplete date) would immediately stomp whatever the user was still typing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTextField(
    label: String,
    dateEpochMillis: Long?,
    onDateChange: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    resetKey: Any? = Unit,
) {
    var fieldValue by remember(resetKey) {
        mutableStateOf(TextFieldValue(dateEpochMillis?.toLocalDateAtDefaultZone()?.format(DateFieldFormatter).orEmpty()))
    }
    var showDatePicker by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = fieldValue,
        onValueChange = { new ->
            val masked = maskDateInput(new.text)
            fieldValue = TextFieldValue(masked, selection = TextRange(masked.length))
            onDateChange(parseTypedDate(masked)?.toLocalMidnightEpochMillis())
        },
        label = { Text(label) },
        placeholder = { Text(stringResource(R.string.console_date_field_placeholder)) },
        singleLine = true,
        enabled = enabled,
        trailingIcon = {
            IconButton(onClick = { showDatePicker = true }, enabled = enabled) {
                Icon(Icons.Rounded.CalendarMonth, contentDescription = stringResource(R.string.console_date_field_pick_action))
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier.fillMaxWidth(),
    )

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = dateEpochMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    enabled = datePickerState.selectedDateMillis != null,
                    onClick = {
                        val picked = datePickerState.selectedDateMillis?.let { utcMillis ->
                            Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate()
                        }
                        if (picked != null) {
                            fieldValue = TextFieldValue(picked.format(DateFieldFormatter))
                            onDateChange(picked.toLocalMidnightEpochMillis())
                        }
                        showDatePicker = false
                    },
                ) { Text(stringResource(R.string.console_pin_continue)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.console_pin_cancel)) }
            },
        ) { DatePicker(state = datePickerState) }
    }
}
