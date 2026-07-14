package com.wwwescape.deviceinfox.console.ui.auth

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wwwescape.deviceinfox.R
import com.wwwescape.deviceinfox.console.data.auth.isPalindromeCode
import kotlinx.coroutines.delay

private const val MIN_PIN_LENGTH = 4
private const val MAX_PIN_LENGTH = 8

/**
 * Deliberately generic chrome — "Enter code", never "Private Mode" or anything console-flavored
 * — so it doesn't out itself if triggered by accident. Handles both first-run setup (enter twice
 * to confirm) and normal verification against the stored hash.
 */
@Composable
fun PinGateDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onAuthenticated: () -> Unit = {},
    viewModel: PinGateViewModel = hiltViewModel(),
) {
    val isPinSet by viewModel.isPinSet.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isAuthenticated by viewModel.isAuthenticated.collectAsStateWithLifecycle()
    val hasServerSession by viewModel.hasServerSession.collectAsStateWithLifecycle()
    val mustChangePasswordRequired by viewModel.mustChangePasswordRequired.collectAsStateWithLifecycle()

    LaunchedEffect(isAuthenticated) {
        if (isAuthenticated) {
            onAuthenticated()
            onDismiss()
        }
    }

    LaunchedEffect(uiState) {
        val locked = uiState as? PinGateUiState.Locked ?: return@LaunchedEffect
        delay(locked.retryAfterSeconds * 1_000)
        viewModel.resetUiState()
    }

    val pinSetLoaded = isPinSet ?: return // still loading EncryptedSharedPreferences

    // Establishes the server account session, once — genuine first run, or a returning device
    // whose refresh token lapsed. Once this is true, PIN alone gates every later entry.
    if (!hasServerSession) {
        AccountSetupDialog(isPinAlreadySet = pinSetLoaded, onDismiss = onDismiss, modifier = modifier)
        return
    }

    // An admin-generated one-time password (`python -m app.cli reset-password`) forces this
    // before the PIN gate even runs — there's no point verifying a PIN that guards an account
    // still sitting on a password someone else was just handed.
    if (mustChangePasswordRequired) {
        ChangePasswordRequiredDialog(onDismiss = onDismiss, modifier = modifier)
        return
    }

    // One-shot ask, right as account setup completes — same "ask once during first-run" pattern
    // as the rest of this flow. No re-prompt if denied; the OS's own Settings screen is how a
    // user reconsiders later, same as any other permission in this app.
    val context = LocalContext.current
    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Withheld by PinGateViewModel.setPin until the popup is dismissed — see
    // [PinGateViewModel.showDuressInfo]'s doc comment.
    val showDuressInfo by viewModel.showDuressInfo.collectAsStateWithLifecycle()
    if (showDuressInfo) {
        DuressCodeInfoDialog(onDismiss = viewModel::acknowledgeDuressInfo)
        return
    }

    val settingUp = !pinSetLoaded

    var pinInput by rememberSaveable { mutableStateOf("") }
    var firstEnteredPin by rememberSaveable { mutableStateOf<String?>(null) }
    var mismatch by rememberSaveable { mutableStateOf(false) }
    var palindromeError by rememberSaveable { mutableStateOf(false) }
    var showForgotCode by rememberSaveable { mutableStateOf(false) }
    val forgotCodeState by viewModel.forgotCodeState.collectAsStateWithLifecycle()

    val locked = uiState is PinGateUiState.Locked
    val titleRes = when {
        settingUp && firstEnteredPin == null -> R.string.console_pin_set_title
        settingUp -> R.string.console_pin_confirm_title
        else -> R.string.console_pin_enter_title
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
                            mismatch = false
                            palindromeError = false
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
                if (!settingUp) {
                    TextButton(onClick = { showForgotCode = true }, enabled = !locked) {
                        Text(stringResource(R.string.console_forgot_code_action))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = pinInput.length in MIN_PIN_LENGTH..MAX_PIN_LENGTH && !locked,
                onClick = {
                    if (settingUp) {
                        val first = firstEnteredPin
                        when {
                            first == null -> {
                                firstEnteredPin = pinInput
                                pinInput = ""
                            }
                            first == pinInput && isPalindromeCode(first) -> {
                                // Its reverse is itself — could never work as a duress trigger.
                                palindromeError = true
                                firstEnteredPin = null
                                pinInput = ""
                            }
                            first == pinInput -> viewModel.setPin(first)
                            else -> {
                                mismatch = true
                                firstEnteredPin = null
                                pinInput = ""
                            }
                        }
                    } else {
                        viewModel.submitPin(pinInput)
                        pinInput = ""
                    }
                },
            ) { Text(stringResource(R.string.console_pin_continue)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.console_pin_cancel)) }
        },
        modifier = modifier,
    )

    if (showForgotCode) {
        ForgotCodeDialog(
            state = forgotCodeState,
            onSubmit = { accountPassword -> viewModel.resetCodeWithPassword(accountPassword) },
            onDismiss = {
                showForgotCode = false
                viewModel.resetForgotCodeState()
            },
        )
    }

    // The reset succeeded (isPinSet flipped back to false) — close the now-stale password prompt
    // and let the recomposition above naturally show the "set a new code" flow instead.
    LaunchedEffect(pinSetLoaded) {
        if (!pinSetLoaded) showForgotCode = false
    }
}
