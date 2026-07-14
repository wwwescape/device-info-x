package com.wwwescape.deviceinfox.console.ui.vault

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

/** Deleting an album only detaches its items (falls back to "no album", per
 * [com.wwwescape.deviceinfox.console.data.db.VaultAlbumDao.delete]'s `SET_NULL` foreign key) —
 * the items themselves aren't touched, which the confirmation body makes explicit. [isPending]
 * blocks both buttons while the delete is in flight — the caller is responsible for dismissing
 * once the result is known. */
@Composable
fun DeleteAlbumConfirmationDialog(
    isPending: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.console_vault_delete_album_confirm_title)) },
        text = {
            Column {
                Text(stringResource(R.string.console_vault_delete_album_confirm_body))
                if (isPending) {
                    Text(
                        text = stringResource(R.string.console_vault_delete_pending),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !isPending, onClick = onConfirm) {
                Text(stringResource(R.string.console_vault_delete_album_confirm_action))
            }
        },
        dismissButton = {
            TextButton(enabled = !isPending, onClick = onDismiss) { Text(stringResource(R.string.console_pin_cancel)) }
        },
        modifier = modifier,
    )
}
