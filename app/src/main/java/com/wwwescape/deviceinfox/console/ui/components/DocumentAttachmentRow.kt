package com.wwwescape.deviceinfox.console.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wwwescape.deviceinfox.R
import com.wwwescape.deviceinfox.console.data.messaging.MessageAttachment
import com.wwwescape.deviceinfox.util.formatBytes

/** File icon + filename + human-readable size + a Save button — no preview, matching the
 * "download-only, no viewer" spec documents were built to. Shared between Messages'
 * `MessageBubble` and Calendar's `EventDetailScreen`. [onSaveToVault] mirrors [ImagePreviewDialog]'s
 * nullable pattern — pass `null` to omit that button entirely. */
@Composable
fun DocumentAttachmentRow(
    attachment: MessageAttachment,
    onSave: (MessageAttachment) -> Unit,
    modifier: Modifier = Modifier,
    onSaveToVault: ((MessageAttachment) -> Unit)? = null,
) {
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
        Column(modifier = Modifier.weight(1f, fill = true).padding(horizontal = 8.dp)) {
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
            IconButton(onClick = { saveToVault(attachment) }) {
                Icon(
                    imageVector = Icons.Rounded.PhotoLibrary,
                    contentDescription = stringResource(R.string.console_home_save_to_vault_action),
                )
            }
        }
        IconButton(onClick = { onSave(attachment) }) {
            Icon(
                imageVector = Icons.Rounded.Download,
                contentDescription = stringResource(R.string.console_home_save_document_action),
            )
        }
    }
}
