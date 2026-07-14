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
import androidx.compose.runtime.LaunchedEffect
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

/** Same full-screen black [Dialog] shell as [ImagePreviewDialog]/[VideoPreviewDialog] (close +
 * save-to-vault + export in the same top-row position), with [PdfPageViewer] as the content area.
 * Shared between Messages and Calendar only — Safe Locker embeds [PdfPageViewer] directly inline
 * in `VaultItemViewerDialog` instead of this shell, same as it already does for [ZoomableImage]/
 * [VideoPlayerPage]. [parentId] is opaque to this composable, same as [VideoPreviewDialog]'s own
 * — just whatever id [ensureDownloaded]'s caller needs to resolve the download. Export shows
 * [ExportWarningDialog] first, reusing the exact same `CreateDocument` launcher pattern the other
 * two preview dialogs already use, unchanged. */
@Composable
fun PdfPreviewDialog(
    parentId: String,
    attachment: MessageAttachment,
    onDismiss: () -> Unit,
    onExport: (MessageAttachment, Uri) -> Unit,
    onPickerLaunch: () -> Unit,
    onPickerResult: () -> Unit,
    ensureDownloaded: suspend () -> String?,
    onSaveToVault: (() -> Unit)? = null,
    isSaveToVaultPending: Boolean = false,
) {
    var localPath by remember(parentId) { mutableStateOf(attachment.localDocumentFilePath) }
    LaunchedEffect(parentId) {
        if (localPath == null) {
            localPath = ensureDownloaded()
        }
    }
    var showExportWarning by remember { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(attachment.mimeType),
    ) { uri ->
        onPickerResult()
        uri?.let { onExport(attachment, it) }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            PdfPageViewer(parentId = parentId, initialLocalPath = attachment.localDocumentFilePath, ensureDownloaded = ensureDownloaded)
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
                    onSaveToVault?.let { saveToVault ->
                        IconButton(onClick = saveToVault, enabled = localPath != null && !isSaveToVaultPending) {
                            Icon(
                                Icons.Rounded.PhotoLibrary,
                                contentDescription = stringResource(R.string.console_home_save_to_vault_action),
                                tint = Color.White,
                            )
                        }
                    }
                    IconButton(onClick = { showExportWarning = true }, enabled = localPath != null) {
                        Icon(
                            Icons.Rounded.Download,
                            contentDescription = stringResource(R.string.console_home_export_document_action),
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
