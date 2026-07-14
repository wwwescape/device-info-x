package com.wwwescape.deviceinfox.console.ui.home

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.wwwescape.deviceinfox.R

/** Shown before starting Edit/Reply would silently throw away real, user-authored content that
 * exists nowhere else — a fresh unsent draft, or unsaved changes to an in-progress edit. See
 * `HomeScreen.wouldDiscardRealContent` for exactly when that's the case (not simply "the composer
 * has non-blank text" — switching away from an *unmodified* edit loses nothing, since the
 * original body isn't going anywhere). Same title/body/action shape as
 * `DeleteMessageConfirmationDialog`/`DeleteEventConfirmationDialog`. */
@Composable
fun DiscardDraftConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.console_home_discard_draft_confirm_title)) },
        text = { Text(stringResource(R.string.console_home_discard_draft_confirm_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.console_home_discard_draft_confirm_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.console_pin_cancel)) }
        },
        modifier = modifier,
    )
}
