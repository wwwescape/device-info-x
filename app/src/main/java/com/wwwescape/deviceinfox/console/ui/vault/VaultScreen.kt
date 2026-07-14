package com.wwwescape.deviceinfox.console.ui.vault

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.wwwescape.deviceinfox.R
import com.wwwescape.deviceinfox.console.data.messaging.MessageAttachmentKind
import com.wwwescape.deviceinfox.console.data.vault.VaultAlbum
import com.wwwescape.deviceinfox.console.data.vault.VaultItem
import com.wwwescape.deviceinfox.console.ui.components.SettingsGearIcon
import com.wwwescape.deviceinfox.console.ui.components.rememberCameraCapture
import com.wwwescape.deviceinfox.console.ui.tabs.ConsoleTabContentWindowInsets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(
    onSettingsClick: () -> Unit,
    onLock: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VaultViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isPending by viewModel.isPending.collectAsStateWithLifecycle()
    val isUpdateAvailable by viewModel.isUpdateAvailable.collectAsStateWithLifecycle()
    val isLiveLocationActive by viewModel.isLiveLocationActive.collectAsStateWithLifecycle()

    var viewingItemId by remember { mutableStateOf<String?>(null) }
    var captionDialogItemId by remember { mutableStateOf<String?>(null) }
    var moveDialogItemId by remember { mutableStateOf<String?>(null) }
    var deletingItemId by remember { mutableStateOf<String?>(null) }

    var showAlbumManagement by remember { mutableStateOf(false) }
    var showNewAlbumDialog by remember { mutableStateOf(false) }
    var renamingAlbumId by remember { mutableStateOf<String?>(null) }
    var deletingAlbumId by remember { mutableStateOf<String?>(null) }

    var showBulkDeleteConfirm by remember { mutableStateOf(false) }
    var showBulkMoveDialog by remember { mutableStateOf(false) }

    // Looked up from visibleItems, not the unfiltered items — that's what the grid the user
    // actually tapped from shows, and what VaultItemViewerDialog's swipe pages through below.
    val viewingItem = uiState.visibleItems.firstOrNull { it.id == viewingItemId }
    val captionDialogItem = uiState.items.firstOrNull { it.id == captionDialogItemId }
    val moveDialogItem = uiState.items.firstOrNull { it.id == moveDialogItemId }
    val deletingItem = uiState.items.firstOrNull { it.id == deletingItemId }
    val renamingAlbum = uiState.albums.firstOrNull { it.id == renamingAlbumId }
    val deletingAlbum = uiState.albums.firstOrNull { it.id == deletingAlbumId }

    val context = LocalContext.current
    var showImportMenu by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(),
    ) { uris ->
        viewModel.endPickerLaunch()
        if (uris.isNotEmpty()) {
            val (videoUris, imageUris) = uris.partition { context.contentResolver.getType(it)?.startsWith("video/") == true }
            if (imageUris.isNotEmpty()) viewModel.importImages(imageUris)
            if (videoUris.isNotEmpty()) viewModel.importVideos(videoUris)
        }
    }
    val documentImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        viewModel.endPickerLaunch()
        if (uris.isNotEmpty()) viewModel.importDocuments(uris)
    }

    // Captured items always go to no album, regardless of the current filter — see
    // VaultViewModel.importCapturedImage/importCapturedVideo's own doc comment.
    val cameraCapture = rememberCameraCapture(
        onImageCaptured = { uri -> viewModel.importCapturedImage(uri) },
        onVideoCaptured = { uri -> viewModel.importCapturedVideo(uri) },
        onPickerLaunch = viewModel::beginPickerLaunch,
        onPickerResult = viewModel::endPickerLaunch,
    )

    val networkErrorMessage = stringResource(R.string.console_error_network)
    LaunchedEffect(Unit) {
        viewModel.errorEvent.collect { detail ->
            Toast.makeText(context, detail ?: networkErrorMessage, Toast.LENGTH_LONG).show()
        }
    }

    val partnerWipedMessage = stringResource(R.string.console_vault_partner_wiped)
    LaunchedEffect(Unit) {
        viewModel.lockerWipedEvent.collect {
            Toast.makeText(context, partnerWipedMessage, Toast.LENGTH_LONG).show()
        }
    }

    // Only toasts when one of the dialogs below was actually open — toggleFavorite/importImages
    // also route through the same launchCatching/actionSucceeded plumbing, and a toast on every
    // favorite tap would be noise (the filled-in heart icon is already the confirmation there).
    val actionSuccessMessage = stringResource(R.string.console_vault_action_success)
    LaunchedEffect(Unit) {
        viewModel.actionSucceeded.collect {
            if (captionDialogItemId != null || moveDialogItemId != null || deletingItemId != null ||
                showNewAlbumDialog || renamingAlbumId != null || deletingAlbumId != null
            ) {
                Toast.makeText(context, actionSuccessMessage, Toast.LENGTH_SHORT).show()
            }
            // The full-screen viewer only closes once the delete actually succeeds, not the
            // instant Delete was tapped — same "wait for the result" fix as everything else here.
            if (viewingItemId != null && viewingItemId == deletingItemId) viewingItemId = null
            captionDialogItemId = null
            moveDialogItemId = null
            deletingItemId = null
            showNewAlbumDialog = false
            renamingAlbumId = null
            deletingAlbumId = null
        }
    }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = ConsoleTabContentWindowInsets,
        topBar = {
            if (uiState.isSelectionMode) {
                TopAppBar(
                    title = { Text(stringResource(R.string.console_home_selection_count, uiState.selectedItemIds.size)) },
                    navigationIcon = {
                        IconButton(onClick = viewModel::clearSelection) {
                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.console_pin_cancel))
                        }
                    },
                    actions = {
                        IconButton(onClick = { showBulkDeleteConfirm = true }) {
                            Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.console_calendar_delete_action))
                        }
                        IconButton(onClick = { showBulkMoveDialog = true }) {
                            Icon(Icons.AutoMirrored.Rounded.DriveFileMove, contentDescription = stringResource(R.string.console_vault_move_to_album_title))
                        }
                        IconButton(onClick = { viewModel.bulkSetFavorite(!uiState.allSelectedAreFavorite) }) {
                            Icon(Icons.Rounded.Favorite, contentDescription = stringResource(R.string.console_vault_favorite_action))
                        }
                    },
                )
            } else if (uiState.isSearching) {
                TopAppBar(
                    title = {
                        TextField(
                            value = uiState.searchQuery,
                            onValueChange = viewModel::setSearchQuery,
                            placeholder = { Text(stringResource(R.string.console_vault_search_hint)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = viewModel::toggleSearch) {
                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.console_pin_cancel))
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.console_vault_title)) },
                    actions = {
                        IconButton(onClick = viewModel::toggleSearch) {
                            Icon(Icons.Rounded.Search, contentDescription = stringResource(R.string.console_vault_search_action))
                        }
                        IconButton(onClick = { showAlbumManagement = true }) {
                            Icon(Icons.Rounded.PhotoLibrary, contentDescription = stringResource(R.string.console_vault_manage_albums_title))
                        }
                        IconButton(onClick = onLock) {
                            Icon(Icons.Rounded.Lock, contentDescription = stringResource(R.string.console_home_lock_action))
                        }
                        IconButton(onClick = onSettingsClick) {
                            SettingsGearIcon(isLiveLocationActive = isLiveLocationActive, isUpdateAvailable = isUpdateAvailable)
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            Box {
                FloatingActionButton(onClick = { showImportMenu = true }) {
                    Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.console_vault_import_action))
                }
                DropdownMenu(expanded = showImportMenu, onDismissRequest = { showImportMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.console_home_attach_gallery_option)) },
                        leadingIcon = { Icon(Icons.Rounded.PhotoLibrary, contentDescription = null) },
                        onClick = {
                            showImportMenu = false
                            viewModel.beginPickerLaunch()
                            importLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.console_home_attach_camera_option)) },
                        leadingIcon = { Icon(Icons.Rounded.PhotoCamera, contentDescription = null) },
                        onClick = {
                            showImportMenu = false
                            cameraCapture.launchCamera()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.console_home_attach_video_option)) },
                        leadingIcon = { Icon(Icons.Rounded.Videocam, contentDescription = null) },
                        onClick = {
                            showImportMenu = false
                            cameraCapture.launchVideoCamera()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.console_home_attach_document_option)) },
                        leadingIcon = { Icon(Icons.AutoMirrored.Rounded.InsertDriveFile, contentDescription = null) },
                        onClick = {
                            showImportMenu = false
                            viewModel.beginPickerLaunch()
                            documentImportLauncher.launch(arrayOf("*/*"))
                        },
                    )
                }
            }
        },
    ) { innerPadding ->
        androidx.compose.foundation.layout.Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            FilterRow(
                albums = uiState.albums,
                selectedFilter = uiState.selectedFilter,
                onSelectFilter = viewModel::selectFilter,
            )

            if (uiState.visibleItems.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.console_vault_empty_state),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 96.dp),
                    contentPadding = PaddingValues(4.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(uiState.visibleItems, key = { it.id }) { item ->
                        VaultGridCell(
                            item = item,
                            isSelectionMode = uiState.isSelectionMode,
                            isSelected = item.id in uiState.selectedItemIds,
                            onClick = { viewingItemId = item.id },
                            onToggleSelect = { viewModel.toggleSelection(item.id) },
                            onEnterSelection = { viewModel.enterSelection(item.id) },
                        )
                    }
                }
            }
        }
    }

    viewingItem?.let {
        VaultItemViewerDialog(
            items = uiState.visibleItems,
            initialItemId = viewingItemId ?: it.id,
            onDismiss = { viewingItemId = null },
            onToggleFavorite = viewModel::toggleFavorite,
            onRequestEditCaption = { current -> captionDialogItemId = current.id },
            onRequestMoveToAlbum = { current -> moveDialogItemId = current.id },
            onRequestDelete = { current -> deletingItemId = current.id },
            onExport = { current, uri -> viewModel.exportItem(current, uri) },
            onPickerLaunch = viewModel::beginPickerLaunch,
            onPickerResult = viewModel::endPickerLaunch,
            ensureVideoDownloaded = { viewModel.ensureVideoDownloaded(it) },
            // Keeps viewingItemId tracking whatever page swiping actually landed on, not just the
            // id the dialog was originally opened with — see the delete-closes-viewer check below.
            onCurrentItemChanged = { current -> viewingItemId = current.id },
        )
    }

    captionDialogItem?.let { item ->
        CaptionDialog(
            existingCaption = item.caption,
            isPending = isPending,
            onSave = { caption -> viewModel.setCaption(item, caption) },
            onDismiss = { captionDialogItemId = null },
        )
    }

    moveDialogItem?.let { item ->
        MoveToAlbumDialog(
            albums = uiState.albums,
            currentAlbumId = item.albumId,
            isPending = isPending,
            onSelect = { albumId -> viewModel.moveToAlbum(item, albumId) },
            onDismiss = { moveDialogItemId = null },
        )
    }

    deletingItem?.let { item ->
        DeleteVaultItemConfirmationDialog(
            isPending = isPending,
            onConfirm = { viewModel.deleteItem(item) },
            onDismiss = { deletingItemId = null },
        )
    }

    if (showBulkDeleteConfirm) {
        BulkDeleteVaultItemsConfirmationDialog(
            count = uiState.selectedItemIds.size,
            onConfirm = {
                viewModel.bulkDelete()
                showBulkDeleteConfirm = false
            },
            onDismiss = { showBulkDeleteConfirm = false },
        )
    }

    if (showBulkMoveDialog) {
        MoveToAlbumDialog(
            albums = uiState.albums,
            // No single "current" album to highlight across a multi-item selection — matches
            // this dialog's own null-means-"No album" radio option, just never pre-selected here.
            currentAlbumId = null,
            isPending = false,
            onSelect = { albumId ->
                viewModel.bulkMoveToAlbum(albumId)
                showBulkMoveDialog = false
            },
            onDismiss = { showBulkMoveDialog = false },
        )
    }

    if (showAlbumManagement) {
        AlbumManagementDialog(
            albums = uiState.albums,
            onNewAlbum = { showAlbumManagement = false; showNewAlbumDialog = true },
            onRenameAlbum = { album -> showAlbumManagement = false; renamingAlbumId = album.id },
            onDeleteAlbum = { album -> showAlbumManagement = false; deletingAlbumId = album.id },
            onDismiss = { showAlbumManagement = false },
        )
    }

    if (showNewAlbumDialog) {
        AlbumDialog(
            existingName = null,
            isPending = isPending,
            onSave = { name -> viewModel.createAlbum(name) },
            onDismiss = { showNewAlbumDialog = false },
        )
    }
    renamingAlbum?.let { album ->
        AlbumDialog(
            existingName = album.name,
            isPending = isPending,
            onSave = { name -> viewModel.renameAlbum(album, name) },
            onDismiss = { renamingAlbumId = null },
        )
    }
    deletingAlbum?.let { album ->
        DeleteAlbumConfirmationDialog(
            isPending = isPending,
            onConfirm = { viewModel.deleteAlbum(album) },
            onDismiss = { deletingAlbumId = null },
        )
    }
}

@Composable
private fun FilterRow(
    albums: List<VaultAlbum>,
    selectedFilter: VaultFilter,
    onSelectFilter: (VaultFilter) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selectedFilter == VaultFilter.All,
            onClick = { onSelectFilter(VaultFilter.All) },
            label = { Text(stringResource(R.string.console_vault_filter_all)) },
        )
        FilterChip(
            selected = selectedFilter == VaultFilter.Favorites,
            onClick = { onSelectFilter(VaultFilter.Favorites) },
            label = { Text(stringResource(R.string.console_vault_filter_favorites)) },
            leadingIcon = { Icon(Icons.Rounded.Favorite, contentDescription = null, modifier = Modifier.size(16.dp)) },
        )
        albums.forEach { album ->
            FilterChip(
                selected = selectedFilter == VaultFilter.Album(album.id),
                onClick = { onSelectFilter(VaultFilter.Album(album.id)) },
                label = { Text(album.name) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VaultGridCell(
    item: VaultItem,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onToggleSelect: () -> Unit,
    onEnterSelection: () -> Unit,
) {
    Box(
        modifier = Modifier
            .padding(2.dp)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(4.dp))
            .combinedClickable(
                onClick = { if (isSelectionMode) onToggleSelect() else onClick() },
                // Free gesture here — unlike Messages, where long-press already opens the
                // reaction picker, nothing in the grid uses long-press today.
                onLongClick = { if (!isSelectionMode) onEnterSelection() },
            ),
    ) {
        when (item.kind) {
            MessageAttachmentKind.DOCUMENT -> {
                Box(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.foundation.layout.Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.InsertDriveFile,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        item.originalFilename?.let { name ->
                            Text(
                                text = name,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            else -> {
                AsyncImage(
                    model = item.filePath,
                    contentDescription = item.caption,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                if (item.kind == MessageAttachmentKind.VIDEO) {
                    Icon(
                        imageVector = Icons.Rounded.PlayCircle,
                        contentDescription = null,
                        tint = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.align(Alignment.Center).size(28.dp),
                    )
                }
            }
        }
        if (item.isFavorite) {
            Icon(
                imageVector = Icons.Rounded.Favorite,
                contentDescription = stringResource(R.string.console_vault_favorite_action),
                tint = androidx.compose.ui.graphics.Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(16.dp)
                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.35f), RoundedCornerShape(50)),
            )
        }
        if (isSelectionMode) {
            if (isSelected) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)))
            }
            Icon(
                imageVector = if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .size(20.dp)
                    .background(Color.Black.copy(alpha = 0.35f), CircleShape),
            )
        }
    }
}
