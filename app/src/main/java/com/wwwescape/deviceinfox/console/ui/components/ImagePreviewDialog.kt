package com.wwwescape.deviceinfox.console.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.wwwescape.deviceinfox.R
import com.wwwescape.deviceinfox.console.data.messaging.MessageAttachment

/** Full-screen black [Dialog] with pinch-zoom (via [ZoomableImage] — plain single-finger drag at
 * 1x deliberately does nothing, see its own doc comment) and close/save-to-vault/export actions
 * in the top row. Shared between Messages (`HomeScreen.kt`'s `MessageBubble`) and Calendar
 * (`EventDetailScreen.kt`) — both just hand it whichever [MessageAttachment] the user tapped, the
 * dialog itself has no message/event concept. [onSaveToVault] is Messages-only (Calendar has no
 * "save to vault" affordance for its own attachments) — pass `null` to omit that button entirely.
 * Export always shows [ExportWarningDialog] first, then launches the system "Save As" picker
 * itself — same mechanism as Safe Locker's own Export (`VaultItemViewerDialog`), just hosted here
 * since this dialog already has the attachment/mime type in scope. */
@Composable
fun ImagePreviewDialog(
    attachment: MessageAttachment,
    onDismiss: () -> Unit,
    onExport: (MessageAttachment, Uri) -> Unit,
    onPickerLaunch: () -> Unit,
    onPickerResult: () -> Unit,
    onSaveToVault: (() -> Unit)? = null,
    isSaveToVaultPending: Boolean = false,
) {
    var showExportWarning by remember { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(attachment.mimeType),
    ) { uri ->
        onPickerResult()
        uri?.let { onExport(attachment, it) }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            ZoomableImage(model = attachment.filePath, contentDescription = null)
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.console_pin_cancel),
                        tint = Color.White,
                    )
                }
                Row {
                    onSaveToVault?.let { onSave ->
                        IconButton(onClick = onSave, enabled = !isSaveToVaultPending) {
                            Icon(
                                Icons.Rounded.PhotoLibrary,
                                contentDescription = stringResource(R.string.console_home_save_to_vault_action),
                                tint = Color.White,
                            )
                        }
                    }
                    IconButton(onClick = { showExportWarning = true }) {
                        Icon(
                            Icons.Rounded.Download,
                            contentDescription = stringResource(R.string.console_home_export_image_action),
                            tint = Color.White,
                        )
                    }
                }
            }
        }
    }

    if (showExportWarning) {
        ExportWarningDialog(
            onConfirm = {
                showExportWarning = false
                onPickerLaunch()
                exportLauncher.launch(defaultExportFileName(attachment))
            },
            onDismiss = { showExportWarning = false },
        )
    }
}
