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

/** "Destroy all data" from the Danger Zone section of Settings — PIN re-entry ([DestroyAccountStep.PIN])
 * then one more plain confirmation ([DestroyAccountStep.CONFIRM]) before the account is actually
 * gone. [onDestroyed] fires once [DestroyAccountViewModel.isComplete] flips true — the caller uses
 * it to lock the console the same way the existing manual "Lock" action does, since there's no
 * account left to show a settings screen for. */
@Composable
fun DestroyAccountDialog(
    onDismiss: () -> Unit,
    onDestroyed: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DestroyAccountViewModel = hiltViewModel(),
) {
    val step by viewModel.step.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isDestroying by viewModel.isDestroying.collectAsStateWithLifecycle()
    val destroyFailed by viewModel.destroyFailed.collectAsStateWithLifecycle()
    val isComplete by viewModel.isComplete.collectAsStateWithLifecycle()

    LaunchedEffect(isComplete) {
        if (isComplete) {
            onDestroyed()
            onDismiss()
        }
    }

    LaunchedEffect(uiState) {
        val locked = uiState as? PinGateUiState.Locked ?: return@LaunchedEffect
        delay(locked.retryAfterSeconds * 1_000)
        viewModel.resetUiState()
    }

    when (step) {
        DestroyAccountStep.PIN -> {
            var pinInput by rememberSaveable { mutableStateOf("") }
            val locked = uiState is PinGateUiState.Locked
            val errorText = when {
                uiState == PinGateUiState.Error -> stringResource(R.string.console_pin_error_incorrect)
                uiState is PinGateUiState.Locked -> stringResource(
                    R.string.console_pin_locked,
                    (uiState as PinGateUiState.Locked).retryAfterSeconds,
                )
                else -> null
            }

            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(stringResource(R.string.console_settings_destroy_pin_title)) },
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
                        onClick = { viewModel.submitPin(pinInput) },
                    ) { Text(stringResource(R.string.console_pin_continue)) }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.console_pin_cancel)) }
                },
                modifier = modifier,
            )
        }
        DestroyAccountStep.CONFIRM -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(stringResource(R.string.console_settings_destroy_confirm_title)) },
                text = {
                    Column {
                        Text(stringResource(R.string.console_settings_destroy_confirm_body))
                        if (destroyFailed) {
                            Text(
                                text = stringResource(R.string.console_settings_destroy_failed),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = !isDestroying,
                        onClick = { viewModel.confirmDestroy() },
                    ) {
                        Text(
                            stringResource(R.string.console_settings_destroy_confirm_action),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                },
                dismissButton = {
                    TextButton(enabled = !isDestroying, onClick = onDismiss) { Text(stringResource(R.string.console_pin_cancel)) }
                },
                modifier = modifier,
            )
        }
    }
}
