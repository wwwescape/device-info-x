package com.wwwescape.deviceinfox.console.ui.home

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.wwwescape.deviceinfox.R
import com.wwwescape.deviceinfox.console.data.messaging.ConsoleMessage

/** Which of the 2 nearly-identical delete confirmations this is — same "one dialog, mode enum"
 * shape as `StarredPinnedMode`, since the two only differ in copy, not structure. */
enum class DeleteMessageMode { HIDE_FOR_ME, DELETE_FOR_EVERYONE }

/** What's pending confirmation — held by `HomeScreen` between the menu tap and the user's
 * choice, so neither `MessageRepository.hideForMe`/`deleteMessage` fires until confirmed. */
data class PendingMessageDelete(val message: ConsoleMessage, val mode: DeleteMessageMode)

/** Previously neither "Delete for me" nor "Delete for everyone" confirmed at all — both fired
 * straight from the menu tap. Matches WhatsApp/Signal's confirm-before-destructive-action
 * default for this app. Reuses the same title/body/action shape as `DeleteVaultItemConfirmationDialog`. */
@Composable
fun DeleteMessageConfirmationDialog(
    mode: DeleteMessageMode,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val titleRes = when (mode) {
        DeleteMessageMode.HIDE_FOR_ME -> R.string.console_home_delete_for_me_confirm_title
        DeleteMessageMode.DELETE_FOR_EVERYONE -> R.string.console_home_delete_for_everyone_confirm_title
    }
    val bodyRes = when (mode) {
        DeleteMessageMode.HIDE_FOR_ME -> R.string.console_home_delete_for_me_confirm_body
        DeleteMessageMode.DELETE_FOR_EVERYONE -> R.string.console_home_delete_for_everyone_confirm_body
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleRes)) },
        text = { Text(stringResource(bodyRes)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.console_vault_delete_item_confirm_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.console_pin_cancel)) }
        },
        modifier = modifier,
    )
}

/** The multi-select top bar's own "Delete for me" (`HomeScreen.kt`'s selection-mode `TopAppBar`,
 * `viewModel::bulkDeleteForMe`) — same shape as [DeleteMessageConfirmationDialog]'s
 * [DeleteMessageMode.HIDE_FOR_ME] case, just for [count] messages at once rather than one, and
 * with no "delete for everyone" bulk equivalent to branch on (none exists in this app). */
@Composable
fun BulkDeleteForMeConfirmationDialog(
    count: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.console_home_bulk_delete_for_me_confirm_title, count)) },
        text = { Text(stringResource(R.string.console_home_bulk_delete_for_me_confirm_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.console_vault_delete_item_confirm_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.console_pin_cancel)) }
        },
        modifier = modifier,
    )
}
