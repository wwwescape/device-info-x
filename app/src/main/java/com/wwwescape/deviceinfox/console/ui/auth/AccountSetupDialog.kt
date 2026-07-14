package com.wwwescape.deviceinfox.console.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wwwescape.deviceinfox.R
import com.wwwescape.deviceinfox.console.data.db.PartnerGender
import com.wwwescape.deviceinfox.console.ui.components.DateTextField
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Establishes the server account session — shown before [PinGateDialog] whenever no session
 * exists yet (genuine first-run, or the refresh token was revoked/expired and needs a fresh
 * login). Deliberately generic chrome ("Set up sync" / "Sign in"), same voice as
 * [PinGateDialog]'s "Enter code" — never anything console-branded.
 *
 * [isPinAlreadySet] only picks which mode this opens in by default (Login for a returning
 * device whose session lapsed, Register for a genuine first run) — the user can switch either
 * way regardless.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSetupDialog(
    isPinAlreadySet: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onCompleted: () -> Unit = {},
    viewModel: AccountSetupViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val completed by viewModel.completed.collectAsStateWithLifecycle()

    LaunchedEffect(completed) {
        if (completed) onCompleted()
    }

    var isRegisterMode by rememberSaveable { mutableStateOf(!isPinAlreadySet) }
    var serverUrl by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var displayName by rememberSaveable { mutableStateOf("") }
    var firstName by rememberSaveable { mutableStateOf("") }
    var lastName by rememberSaveable { mutableStateOf("") }
    var birthdayEpochMillis by rememberSaveable { mutableStateOf<Long?>(null) }
    var gender by rememberSaveable { mutableStateOf(PartnerGender.UNSPECIFIED) }
    var setupToken by rememberSaveable { mutableStateOf("") }

    val loading = uiState is AccountSetupUiState.Loading
    val errorState = uiState as? AccountSetupUiState.Error
    val errorText = when {
        errorState == null -> null
        errorState.message != null -> errorState.message
        else -> stringResource(R.string.console_account_error_generic)
    }
    // toHttpUrlOrNull() requires a scheme (e.g. "https://") — the same requirement
    // BaseUrlInterceptor's toHttpUrl() enforces on every request, checked here up front instead
    // of only discovering it's malformed after a network round trip (or worse, crashing —
    // OkHttp interceptors that throw anything but IOException take the whole process down).
    val serverUrlValid = serverUrl.isNotBlank() && serverUrl.toHttpUrlOrNull() != null
    val canSubmit = !loading && serverUrlValid && username.isNotBlank() && password.isNotBlank() &&
        (
            !isRegisterMode || (
                displayName.isNotBlank() && firstName.isNotBlank() && lastName.isNotBlank() &&
                    birthdayEpochMillis != null && setupToken.isNotBlank()
                )
            )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (isRegisterMode) R.string.console_account_register_title else R.string.console_account_login_title,
                ),
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    singleLine = true,
                    enabled = !loading,
                    isError = serverUrl.isNotBlank() && !serverUrlValid,
                    label = { Text(stringResource(R.string.console_account_server_url_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (serverUrl.isNotBlank() && !serverUrlValid) {
                    Text(
                        text = stringResource(R.string.console_account_server_url_invalid),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    singleLine = true,
                    enabled = !loading,
                    label = { Text(stringResource(R.string.console_account_username_label)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    singleLine = true,
                    enabled = !loading,
                    label = { Text(stringResource(R.string.console_account_password_label)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (isRegisterMode) {
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        singleLine = true,
                        enabled = !loading,
                        label = { Text(stringResource(R.string.console_account_display_name_label)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = firstName,
                        onValueChange = { firstName = it },
                        singleLine = true,
                        enabled = !loading,
                        label = { Text(stringResource(R.string.console_account_first_name_label)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = lastName,
                        onValueChange = { lastName = it },
                        singleLine = true,
                        enabled = !loading,
                        label = { Text(stringResource(R.string.console_account_last_name_label)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    DateTextField(
                        label = stringResource(R.string.console_settings_birthday_label),
                        dateEpochMillis = birthdayEpochMillis,
                        onDateChange = { birthdayEpochMillis = it },
                        enabled = !loading,
                    )
                    Text(stringResource(R.string.console_settings_gender_label), style = MaterialTheme.typography.bodyMedium)
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        val options = listOf(
                            PartnerGender.MALE to stringResource(R.string.console_settings_gender_male),
                            PartnerGender.FEMALE to stringResource(R.string.console_settings_gender_female),
                            PartnerGender.UNSPECIFIED to stringResource(R.string.console_settings_gender_unspecified),
                        )
                        options.forEachIndexed { index, (value, label) ->
                            SegmentedButton(
                                selected = gender == value,
                                onClick = { gender = value },
                                enabled = !loading,
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = setupToken,
                        onValueChange = { setupToken = it },
                        singleLine = true,
                        enabled = !loading,
                        label = { Text(stringResource(R.string.console_account_setup_token_label)) },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (errorText != null) {
                    Text(
                        text = errorText,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                TextButton(onClick = { isRegisterMode = !isRegisterMode }, enabled = !loading) {
                    Text(
                        stringResource(
                            if (isRegisterMode) {
                                R.string.console_account_switch_to_login
                            } else {
                                R.string.console_account_switch_to_register
                            },
                        ),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSubmit,
                onClick = {
                    if (isRegisterMode) {
                        viewModel.register(
                            serverUrl, username, password, displayName,
                            firstName, lastName, birthdayEpochMillis!!, setupToken, gender,
                        )
                    } else {
                        viewModel.login(serverUrl, username, password)
                    }
                },
            ) { Text(stringResource(R.string.console_pin_continue)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !loading) { Text(stringResource(R.string.console_pin_cancel)) }
        },
        modifier = modifier,
    )
}
