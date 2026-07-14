package com.wwwescape.deviceinfox.console.ui.calendar

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

/** Deliberate, confirmed action — a permanent hard delete (no tombstone, unlike message
 * delete), so it's never reachable by a single accidental tap on the underlying row. [isPending]
 * blocks both buttons while the delete is in flight, same reasoning as Settings'
 * `DataDeletionConfirmationDialog` — the caller is responsible for dismissing once the result
 * (success or failure) is known, this no longer dismisses itself from the confirm button. */
@Composable
fun DeleteEventConfirmationDialog(
    isPending: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.console_calendar_delete_confirm_title)) },
        text = {
            Column {
                Text(stringResource(R.string.console_calendar_delete_confirm_body))
                if (isPending) {
                    Text(
                        text = stringResource(R.string.console_calendar_delete_pending),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !isPending, onClick = onConfirm) {
                Text(stringResource(R.string.console_calendar_delete_confirm_action))
            }
        },
        dismissButton = {
            TextButton(enabled = !isPending, onClick = onDismiss) { Text(stringResource(R.string.console_pin_cancel)) }
        },
        modifier = modifier,
    )
}
