package com.wwwescape.deviceinfox.console.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.wwwescape.deviceinfox.R
import com.wwwescape.deviceinfox.console.data.messaging.MessageAttachment
import java.util.UUID

/** The one gate every Export action goes through, everywhere in the app (Messages, Calendar,
 * Safe Locker) — Export is the sole way to get a file out of the app, and every tap of it shows
 * this first, since it's the one action that can make a file accessible outside the app's own
 * privacy. Same shape as `DeleteMessageConfirmationDialog`/`DeleteVaultItemConfirmationDialog`. */
@Composable
fun ExportWarningDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.console_export_warning_title)) },
        text = { Text(stringResource(R.string.console_export_warning_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.console_export_confirm_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.console_pin_cancel)) }
        },
        modifier = modifier,
    )
}

/** Suggested filename for the system "Save As" picker — mirrors `VaultItemViewerDialog`'s own
 * private `defaultExportFileName`, generalized to any [MessageAttachment] (Messages/Calendar
 * attachments, not just Vault items). */
fun defaultExportFileName(attachment: MessageAttachment): String {
    attachment.originalFilename?.let { return it }
    val extension = attachment.mimeType.substringAfter('/', "jpg")
    return "${attachment.mediaId ?: UUID.randomUUID()}.$extension"
}
