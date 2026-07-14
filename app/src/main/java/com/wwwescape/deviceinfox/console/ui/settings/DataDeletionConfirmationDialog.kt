package com.wwwescape.deviceinfox.console.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wwwescape.deviceinfox.R

/** Shared two-button confirmation for every "Delete data" action in Settings — same simple
 * [AlertDialog] shape as [UnpairConfirmationDialog], reused across all 4 per-section deletes plus
 * "Delete all data" (5 call sites), each supplying its own title/body copy since the scope of
 * what's actually deleted differs per section. [isPending] blocks both buttons while the delete
 * is in flight — same reasoning as [com.wwwescape.deviceinfox.console.ui.auth.DestroyAccountDialog]
 * disabling its own Confirm/Cancel, including Cancel since backing out doesn't stop the already
 * in-flight delete. The caller is responsible for dismissing once the result is known. */
@Composable
fun DataDeletionConfirmationDialog(
    title: String,
    body: String,
    isPending: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(body)
                if (isPending) {
                    Text(
                        text = stringResource(R.string.console_settings_delete_pending),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !isPending, onClick = onConfirm) {
                Text(stringResource(R.string.console_settings_delete_data_confirm_action), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(enabled = !isPending, onClick = onDismiss) { Text(stringResource(R.string.console_pin_cancel)) }
        },
        modifier = modifier,
    )
}
