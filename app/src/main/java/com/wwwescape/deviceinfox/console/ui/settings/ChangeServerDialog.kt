package com.wwwescape.deviceinfox.console.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.unit.dp
import com.wwwescape.deviceinfox.R
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** "Change server" from the new Server section in Settings — wipes all local data (same
 * mechanics as [com.wwwescape.deviceinfox.console.ui.auth.DestroyAccountDialog], minus deleting
 * the account itself) and points the app at a new server address. Prefilled with the current
 * server URL so the user edits rather than retypes it from scratch.
 *
 * [isPending] blocks both buttons while the change is in flight; [failed] renders inline error
 * text rather than a Toast, since on success the whole console exits (see
 * [com.wwwescape.deviceinfox.console.ui.settings.ConsoleSettingsScreen]'s `onLock` on
 * `serverChanged`) before a Toast would ever be seen, and on failure the dialog stays open for
 * the same reason [com.wwwescape.deviceinfox.console.ui.auth.DestroyAccountDialog] does. */
@Composable
fun ChangeServerDialog(
    currentServerUrl: String?,
    isPending: Boolean,
    failed: Boolean,
    onConfirm: (newServerUrl: String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var serverUrl by rememberSaveable { mutableStateOf(currentServerUrl.orEmpty()) }
    val serverUrlValid = serverUrl.isNotBlank() && serverUrl.toHttpUrlOrNull() != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.console_settings_change_server_confirm_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.console_settings_change_server_confirm_body))
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    singleLine = true,
                    enabled = !isPending,
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
                } else if (failed) {
                    Text(
                        text = stringResource(R.string.console_settings_change_server_failed),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else if (isPending) {
                    Text(
                        text = stringResource(R.string.console_settings_change_server_pending),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = serverUrlValid && !isPending,
                onClick = { onConfirm(serverUrl) },
            ) {
                Text(
                    stringResource(R.string.console_settings_change_server_confirm_action),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(enabled = !isPending, onClick = onDismiss) { Text(stringResource(R.string.console_pin_cancel)) }
        },
        modifier = modifier,
    )
}
