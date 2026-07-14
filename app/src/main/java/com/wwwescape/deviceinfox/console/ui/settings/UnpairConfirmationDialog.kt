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

/** Deliberate, confirmed action — this is the confirmation itself, never reachable by a single
 * accidental tap on the underlying "Unpair" row. [isPending] blocks both buttons while the
 * unpair is in flight — on success the caller locks/exits (there's no account left paired to
 * show a settings screen for), which dismisses this along with the rest of the screen; on
 * failure the caller shows a Toast and leaves this dialog open to retry. */
@Composable
fun UnpairConfirmationDialog(
    isPending: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.console_pairing_unpair_confirm_title)) },
        text = {
            Column {
                Text(stringResource(R.string.console_pairing_unpair_confirm_body))
                if (isPending) {
                    Text(
                        text = stringResource(R.string.console_pairing_unpair_pending),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !isPending, onClick = onConfirm) {
                Text(stringResource(R.string.console_pairing_unpair_confirm_action))
            }
        },
        dismissButton = {
            TextButton(enabled = !isPending, onClick = onDismiss) { Text(stringResource(R.string.console_pin_cancel)) }
        },
        modifier = modifier,
    )
}
