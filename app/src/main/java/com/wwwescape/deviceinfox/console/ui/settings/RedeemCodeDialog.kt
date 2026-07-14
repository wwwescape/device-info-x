package com.wwwescape.deviceinfox.console.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wwwescape.deviceinfox.R

private const val MIN_CODE_LENGTH = 4
private const val MAX_CODE_LENGTH = 16

@Composable
fun RedeemCodeDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RedeemCodeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var codeInput by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(uiState) {
        if (uiState == RedeemCodeUiState.Success) onDismiss()
    }

    val isPending = uiState == RedeemCodeUiState.Pending
    val errorState = uiState as? RedeemCodeUiState.Error
    val errorText = when {
        uiState == RedeemCodeUiState.InvalidCode -> stringResource(R.string.console_pairing_error_invalid_code)
        uiState == RedeemCodeUiState.AlreadyPaired -> stringResource(R.string.console_pairing_error_already_paired)
        errorState != null -> errorState.message ?: stringResource(R.string.console_pairing_error_generic)
        else -> null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.console_pairing_redeem_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.console_pairing_redeem_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = codeInput,
                    onValueChange = { new ->
                        val upper = new.trim().uppercase()
                        if (upper.length <= MAX_CODE_LENGTH) codeInput = upper
                    },
                    singleLine = true,
                    enabled = !isPending,
                    label = { Text(stringResource(R.string.console_pairing_code_label)) },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
                if (errorText != null) {
                    Text(
                        text = errorText,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                } else if (isPending) {
                    Text(
                        text = stringResource(R.string.console_pairing_redeem_pending),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = codeInput.length in MIN_CODE_LENGTH..MAX_CODE_LENGTH && !isPending,
                onClick = { viewModel.submit(codeInput) },
            ) { Text(stringResource(R.string.console_pairing_redeem_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.console_pin_cancel)) }
        },
        modifier = modifier,
    )
}
