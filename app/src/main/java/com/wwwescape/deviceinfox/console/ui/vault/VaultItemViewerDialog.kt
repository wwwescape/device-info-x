package com.wwwescape.deviceinfox.console.ui.vault

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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wwwescape.deviceinfox.R
import com.wwwescape.deviceinfox.console.data.messaging.MessageAttachmentKind
import com.wwwescape.deviceinfox.console.data.vault.VaultItem
import com.wwwescape.deviceinfox.console.ui.components.ExportWarningDialog
import com.wwwescape.deviceinfox.console.ui.components.PdfPageViewer
import com.wwwescape.deviceinfox.console.ui.components.VideoPlayerPage
import com.wwwescape.deviceinfox.console.ui.components.ZoomableImage
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Keeps the same chrome (top bar, caption overlay, favorite/move-album/delete/export actions —
 * all kind-agnostic Vault operations Messages/Calendar don't have) for every [VaultItem.kind],
 * with only the content area dispatching per kind. Deliberately does NOT embed the standalone
 * `ImagePreviewDialog`/`VideoPreviewDialog`/`DocumentAttachmentRow` from `console/ui/components/`
 * directly — nesting those would either duplicate or hide this dialog's own per-item actions,
 * which a video/document locker item still needs just as much as a photo does.
 *
 * Swipeable across [items] (whatever order `VaultScreen`'s own grid currently has them in) via a
 * `HorizontalPager`, same as `MediaPagerDialog` (Messages/Shared Media) — [ZoomableImage] and
 * [VideoPlayerPage] are shared with that dialog rather than duplicated here. [onCurrentItemChanged]
 * fires on every page change so `VaultScreen` can keep its own "which item is open" state in sync
 * with whatever page swiping actually landed on, not just the id this dialog was opened with. */
@Composable
fun VaultItemViewerDialog(
    items: List<VaultItem>,
    initialItemId: String,
    onDismiss: () -> Unit,
    onToggleFavorite: (VaultItem) -> Unit,
    onRequestEditCaption: (VaultItem) -> Unit,
    onRequestMoveToAlbum: (VaultItem) -> Unit,
    onRequestDelete: (VaultItem) -> Unit,
    onExport: (VaultItem, Uri) -> Unit,
    onPickerLaunch: () -> Unit,
    onPickerResult: () -> Unit,
    ensureVideoDownloaded: suspend (VaultItem) -> String?,
    ensureDocumentDownloaded: suspend (VaultItem) -> String?,
    isPending: Boolean = false,
    onCurrentItemChanged: (VaultItem) -> Unit = {},
) {
    if (items.isEmpty()) {
        onDismiss()
        return
    }
    val initialPage = remember(items, initialItemId) {
        items.indexOfFirst { it.id == initialItemId }.coerceAtLeast(0)
    }
    val pagerState = rememberPagerState(initialPage = initialPage) { items.size }
    val zoomedPages = remember { mutableStateMapOf<Int, Boolean>() }
    val currentItem = items[pagerState.currentPage.coerceIn(items.indices)]
    var showMenu by remember { mutableStateOf(false) }
    var showExportWarning by remember { mutableStateOf(false) }

    LaunchedEffect(currentItem.id) { onCurrentItemChanged(currentItem) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(currentItem.mimeType),
    ) { uri ->
        onPickerResult()
        uri?.let { onExport(currentItem, it) }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = zoomedPages[pagerState.currentPage] != true,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val item = items[page]
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    when (item.kind) {
                        MessageAttachmentKind.IMAGE -> {
                            ZoomableImage(
                                model = item.filePath,
                                contentDescription = item.caption,
                                onZoomedChanged = { zoomed -> zoomedPages[page] = zoomed },
                            )
                        }
                        MessageAttachmentKind.VIDEO -> {
                            VideoPlayerPage(
                                parentId = item.id,
                                initialLocalPath = item.localVideoFilePath,
                                ensureDownloaded = { ensureVideoDownloaded(item) },
                            )
                        }
                        MessageAttachmentKind.DOCUMENT -> {
                            PdfPageViewer(
                                parentId = item.id,
                                initialLocalPath = item.localDocumentFilePath,
                                ensureDownloaded = { ensureDocumentDownloaded(item) },
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.console_pin_cancel), tint = Color.White)
                }
                Row {
                    if (currentItem.isMine) {
                        IconButton(onClick = { onToggleFavorite(currentItem) }, enabled = !isPending) {
                            Icon(
                                imageVector = if (currentItem.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                contentDescription = stringResource(R.string.console_vault_favorite_action),
                                tint = Color.White,
                            )
                        }
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.console_calendar_options), tint = Color.White)
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            if (currentItem.isMine) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.console_vault_caption_title)) },
                                    leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                                    onClick = { showMenu = false; onRequestEditCaption(currentItem) },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.console_vault_move_to_album_title)) },
                                    leadingIcon = { Icon(Icons.Rounded.DriveFileMove, contentDescription = null) },
                                    onClick = { showMenu = false; onRequestMoveToAlbum(currentItem) },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.console_vault_export_action)) },
                                leadingIcon = { Icon(Icons.Rounded.Download, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    showExportWarning = true
                                },
                            )
                            if (currentItem.isMine) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.console_calendar_delete_action)) },
                                    leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                                    onClick = { showMenu = false; onRequestDelete(currentItem) },
                                )
                            }
                        }
                    }
                }
            }
            currentItem.caption?.let { caption ->
                Text(
                    text = caption,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(12.dp),
                )
            }
        }
    }

    if (showExportWarning) {
        ExportWarningDialog(
            onConfirm = {
                showExportWarning = false
                onPickerLaunch()
                exportLauncher.launch(defaultExportFileName(currentItem))
            },
            onDismiss = { showExportWarning = false },
        )
    }
}

private fun defaultExportFileName(item: VaultItem): String {
    item.originalFilename?.let { return it }
    val extension = item.mimeType.substringAfter('/', "jpg")
    return "${item.id}.$extension"
}
