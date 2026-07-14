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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.wwwescape.deviceinfox.R

/**
 * Reached via a "Forgot code?" link on [PinGateDialog]'s normal PIN-entry screen — asks for the
 * *account* password (not the PIN itself, since that's what's forgotten) and, once verified
 * server-side, wipes the local PIN so [PinGateDialog] falls through to its existing "set a new
 * code" flow. Deliberately a "dumb" composable, not its own `hiltViewModel()` — it's driven by
 * [PinGateViewModel] (passed down from [PinGateDialog]) so a successful reset can flip that same
 * ViewModel's PIN-set state directly, without a second ViewModel instance to keep in sync.
 */
@Composable
fun ForgotCodeDialog(
    state: AccountSetupUiState,
    onSubmit: (accountPassword: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var password by rememberSaveable { mutableStateOf("") }
    val loading = state is AccountSetupUiState.Loading
    val errorState = state as? AccountSetupUiState.Error
    val errorText = errorState?.let { it.message ?: stringResource(R.string.console_error_network) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.console_forgot_code_title)) },
        text = {
            Column {
                Text(stringResource(R.string.console_forgot_code_body), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    singleLine = true,
                    enabled = !loading,
                    label = { Text(stringResource(R.string.console_account_password_label)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
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
                enabled = !loading && password.isNotBlank(),
                onClick = { onSubmit(password) },
            ) { Text(stringResource(R.string.console_pin_continue)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !loading) { Text(stringResource(R.string.console_pin_cancel)) }
        },
    )
}
