package com.wwwescape.deviceinfox.console.ui.components

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.wwwescape.deviceinfox.R
import com.wwwescape.deviceinfox.console.data.messaging.MessageAttachment

/** Full-screen black [Dialog] with pinch-zoom (via [ZoomableImage] — plain single-finger drag at
 * 1x deliberately does nothing, see its own doc comment) and close/save-to-vault/download actions
 * in the top row. Shared between Messages (`HomeScreen.kt`'s `MessageBubble`) and Calendar
 * (`EventDetailScreen.kt`) — both just hand it whichever [MessageAttachment] the user tapped, the
 * dialog itself has no message/event concept. [onSaveToVault] is Messages-only (Calendar has no
 * "save to vault" affordance for its own attachments) — pass `null` to omit that button entirely. */
@Composable
fun ImagePreviewDialog(
    attachment: MessageAttachment,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onSaveToVault: (() -> Unit)? = null,
) {
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
                        IconButton(onClick = onSave) {
                            Icon(
                                Icons.Rounded.PhotoLibrary,
                                contentDescription = stringResource(R.string.console_home_save_to_vault_action),
                                tint = Color.White,
                            )
                        }
                    }
                    IconButton(onClick = onDownload) {
                        Icon(
                            Icons.Rounded.Download,
                            contentDescription = stringResource(R.string.console_home_download_image_action),
                            tint = Color.White,
                        )
                    }
                }
            }
        }
    }
}
