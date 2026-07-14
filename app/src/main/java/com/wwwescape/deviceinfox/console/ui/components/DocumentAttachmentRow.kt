package com.wwwescape.deviceinfox.console.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wwwescape.deviceinfox.R
import com.wwwescape.deviceinfox.console.data.messaging.MessageAttachment
import com.wwwescape.deviceinfox.util.formatBytes

/** File icon + filename + human-readable size + an Export button. Shared between Messages'
 * `MessageBubble` and Calendar's `EventDetailScreen`. [onSaveToVault] mirrors [ImagePreviewDialog]'s
 * nullable pattern — pass `null` to omit that button entirely. Export shows [ExportWarningDialog]
 * first, then launches the system "Save As" picker itself; the caller's [onExport] is responsible
 * for lazily downloading the document's bytes first if they aren't local yet (mirrors the existing
 * `HomeViewModel.exportDocument` shape), since — unlike video — there's no dialog here to hold a
 * loading state in. [onClick] opens `PdfPreviewDialog` — nullable and applied only to the
 * icon+filename+size area (not the Export/Save-to-vault buttons), since Messages routes document
 * taps through its own bubble-level tap dispatch instead (it alone has a selection-mode gesture to
 * not conflict with) and leaves this `null`; Calendar, with no such concern, passes a real
 * callback. */
@Composable
fun DocumentAttachmentRow(
    attachment: MessageAttachment,
    onExport: (MessageAttachment, Uri) -> Unit,
    onPickerLaunch: () -> Unit,
    onPickerResult: () -> Unit,
    modifier: Modifier = Modifier,
    onSaveToVault: ((MessageAttachment) -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    isSaveToVaultPending: Boolean = false,
) {
    var showExportWarning by remember { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(attachment.mimeType),
    ) { uri ->
        onPickerResult()
        uri?.let { onExport(attachment, it) }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(8.dp),
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.InsertDriveFile,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f, fill = true)
                .let { if (onClick != null) it.clickable(onClick = onClick) else it }
                .padding(horizontal = 8.dp),
        ) {
            Text(
                text = attachment.originalFilename ?: stringResource(R.string.console_home_document_generic_name),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            attachment.sizeBytes?.let { size ->
                Text(
                    text = formatBytes(size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        onSaveToVault?.let { saveToVault ->
            IconButton(onClick = { saveToVault(attachment) }, enabled = !isSaveToVaultPending) {
                Icon(
                    imageVector = Icons.Rounded.PhotoLibrary,
                    contentDescription = stringResource(R.string.console_home_save_to_vault_action),
                )
            }
        }
        IconButton(onClick = { showExportWarning = true }) {
            Icon(
                imageVector = Icons.Rounded.Download,
                contentDescription = stringResource(R.string.console_home_export_document_action),
            )
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
