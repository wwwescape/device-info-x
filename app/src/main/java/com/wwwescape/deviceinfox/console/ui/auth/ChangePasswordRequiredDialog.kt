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

private const val MIN_PASSWORD_LENGTH = 8

/** The mandatory screen [PinGateDialog] shows instead of the normal PIN flow whenever the server
 * reports `must_change_password` — the account owner just logged in with an admin-generated
 * one-time password (`python -m app.cli reset-password`) and has to set a real one before the
 * console unlocks. Unlike [ChangePinDialog]'s multi-step wizard, this is a single screen — three
 * distinct fields (current/new/confirm), not one field reused three times. */
@Composable
fun ChangePasswordRequiredDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onComplete: () -> Unit = {},
    viewModel: ChangePasswordRequiredViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isComplete by viewModel.isComplete.collectAsStateWithLifecycle()

    LaunchedEffect(isComplete) {
        if (isComplete) {
            onComplete()
            onDismiss()
        }
    }

    var currentPassword by rememberSaveable { mutableStateOf("") }
    var newPassword by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }

    val loading = uiState is AccountSetupUiState.Loading
    val errorState = uiState as? AccountSetupUiState.Error
    val serverError = errorState?.let { it.message ?: stringResource(R.string.console_error_network) }
    val mismatch = confirmPassword.isNotEmpty() && newPassword != confirmPassword
    val canSubmit = !loading && currentPassword.isNotBlank() &&
        newPassword.length >= MIN_PASSWORD_LENGTH && newPassword == confirmPassword

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.console_change_password_required_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.console_change_password_required_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.console_change_password_current_label),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 12.dp),
                )
                OutlinedTextField(
                    value = currentPassword,
                    onValueChange = { currentPassword = it },
                    singleLine = true,
                    enabled = !loading,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.console_change_password_new_label),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 12.dp),
                )
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    singleLine = true,
                    enabled = !loading,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.console_change_password_confirm_label),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 12.dp),
                )
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    singleLine = true,
                    enabled = !loading,
                    isError = mismatch,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                val errorText = when {
                    mismatch -> stringResource(R.string.console_change_password_mismatch)
                    serverError != null -> serverError
                    else -> null
                }
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
                enabled = canSubmit,
                onClick = { viewModel.submit(currentPassword, newPassword) },
            ) { Text(stringResource(R.string.console_pin_continue)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !loading) { Text(stringResource(R.string.console_pin_cancel)) }
        },
        modifier = modifier,
    )
}
