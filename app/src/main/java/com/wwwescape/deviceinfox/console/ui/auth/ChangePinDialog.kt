package com.wwwescape.deviceinfox.console.ui.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wwwescape.deviceinfox.R
import kotlinx.coroutines.delay

private const val MIN_PIN_LENGTH = 4
private const val MAX_PIN_LENGTH = 8

@Composable
fun ChangePinDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onComplete: () -> Unit = {},
    viewModel: ChangePinViewModel = hiltViewModel(),
) {
    val step by viewModel.step.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val mismatch by viewModel.mismatch.collectAsStateWithLifecycle()
    val palindromeError by viewModel.palindromeError.collectAsStateWithLifecycle()
    val isComplete by viewModel.isComplete.collectAsStateWithLifecycle()

    LaunchedEffect(isComplete) {
        if (isComplete) {
            onComplete()
            onDismiss()
        }
    }

    LaunchedEffect(uiState) {
        val locked = uiState as? PinGateUiState.Locked ?: return@LaunchedEffect
        delay(locked.retryAfterSeconds * 1_000)
        viewModel.resetUiState()
    }

    var pinInput by rememberSaveable(step) { mutableStateOf("") }

    val locked = uiState is PinGateUiState.Locked
    val titleRes = when (step) {
        ChangePinStep.CURRENT -> R.string.console_change_pin_current_title
        ChangePinStep.NEW -> R.string.console_change_pin_new_title
        ChangePinStep.CONFIRM -> R.string.console_change_pin_confirm_title
    }
    val errorText = when {
        palindromeError -> stringResource(R.string.console_pin_error_palindrome)
        mismatch -> stringResource(R.string.console_pin_error_mismatch)
        uiState == PinGateUiState.Error -> stringResource(R.string.console_pin_error_incorrect)
        uiState is PinGateUiState.Locked -> stringResource(
            R.string.console_pin_locked,
            (uiState as PinGateUiState.Locked).retryAfterSeconds,
        )
        else -> null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleRes)) },
        text = {
            Column {
                OutlinedTextField(
                    value = pinInput,
                    onValueChange = { new ->
                        if (new.length <= MAX_PIN_LENGTH && new.all(Char::isDigit)) {
                            pinInput = new
                        }
                    },
                    singleLine = true,
                    enabled = !locked,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (errorText != null) {
                    Text(
                        text = errorText,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = pinInput.length in MIN_PIN_LENGTH..MAX_PIN_LENGTH && !locked,
                onClick = {
                    when (step) {
                        ChangePinStep.CURRENT -> viewModel.submitCurrentPin(pinInput)
                        ChangePinStep.NEW -> viewModel.submitNewPin(pinInput)
                        ChangePinStep.CONFIRM -> viewModel.submitConfirmPin(pinInput)
                    }
                },
            ) { Text(stringResource(R.string.console_pin_continue)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.console_pin_cancel)) }
        },
        modifier = modifier,
    )
}
