package com.wwwescape.deviceinfox.console.ui.periodtracker

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

/** Shown instead of saving immediately when a *new* day log's flow value would start a brand-new
 * cycle well outside the predicted window (see `CycleRepository.isUnexpectedCycleStart`) — e.g. a
 * period logged well early or well late becoming the new basis for every future prediction with
 * no check first. [isPending] blocks both buttons while the save is in flight, same shape as
 * [DeleteCycleEntryConfirmationDialog]; the caller is responsible for dismissing once the result
 * is known. */
@Composable
fun UnexpectedCycleStartConfirmationDialog(
    isPending: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.console_period_unexpected_start_confirm_title)) },
        text = {
            Column {
                Text(stringResource(R.string.console_period_unexpected_start_confirm_body))
                if (isPending) {
                    Text(
                        text = stringResource(R.string.console_period_save_pending),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !isPending, onClick = onConfirm) {
                Text(stringResource(R.string.console_period_unexpected_start_confirm_action))
            }
        },
        dismissButton = {
            TextButton(enabled = !isPending, onClick = onDismiss) { Text(stringResource(R.string.console_pin_cancel)) }
        },
        modifier = modifier,
    )
}
