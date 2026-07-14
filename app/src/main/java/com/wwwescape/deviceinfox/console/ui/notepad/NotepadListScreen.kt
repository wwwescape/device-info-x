package com.wwwescape.deviceinfox.console.ui.notepad

import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wwwescape.deviceinfox.R
import com.wwwescape.deviceinfox.console.data.notepad.NotepadEntry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotepadListScreen(
    onBack: () -> Unit,
    onOpenEntry: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NotepadViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isCreating by viewModel.isCreating.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var deletingEntry by remember { mutableStateOf<NotepadEntry?>(null) }
    var showBulkDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.createdEntryId.collect { entryId -> onOpenEntry(entryId) }
    }

    val networkErrorMessage = stringResource(R.string.console_error_network)
    LaunchedEffect(Unit) {
        viewModel.errorEvent.collect { detail ->
            Toast.makeText(context, detail ?: networkErrorMessage, Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            Column {
                if (uiState.isSelectionMode) {
                    TopAppBar(
                        title = { Text(stringResource(R.string.console_home_selection_count, uiState.selectedEntryIds.size)) },
                        navigationIcon = {
                            IconButton(onClick = viewModel::clearSelection) {
                                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.console_pin_cancel))
                            }
                        },
                        actions = {
                            IconButton(onClick = viewModel::selectAll) {
                                Icon(Icons.Rounded.SelectAll, contentDescription = stringResource(R.string.console_notepad_select_all_action))
                            }
                            IconButton(onClick = { showBulkDeleteConfirm = true }) {
                                Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.console_calendar_delete_action))
                            }
                        },
                    )
                } else {
                    TopAppBar(
                        title = { Text(stringResource(R.string.console_notepad_title)) },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                            }
                        },
                    )
                    TabRow(selectedTabIndex = if (uiState.selectedTab == NotepadTab.PRIVATE) 0 else 1) {
                        Tab(
                            selected = uiState.selectedTab == NotepadTab.PRIVATE,
                            onClick = { viewModel.selectTab(NotepadTab.PRIVATE) },
                            text = { Text(stringResource(R.string.console_notepad_tab_private)) },
                        )
                        Tab(
                            selected = uiState.selectedTab == NotepadTab.SHARED,
                            onClick = { viewModel.selectTab(NotepadTab.SHARED) },
                            text = { Text(stringResource(R.string.console_notepad_tab_shared)) },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (!uiState.isSelectionMode) {
                // FloatingActionButton has no `enabled` param in Material3 — dim it and gate the
                // click ourselves instead (createNote() itself also no-ops while isCreating is
                // true, so this is belt-and-suspenders, not the only guard).
                FloatingActionButton(
                    onClick = { if (!isCreating) viewModel.createNote() },
                    modifier = if (isCreating) Modifier.alpha(0.5f) else Modifier,
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.console_notepad_add_action))
                }
            }
        },
    ) { innerPadding ->
        if (uiState.visibleEntries.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.console_notepad_empty_state),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                items(uiState.visibleEntries, key = { it.id }) { entry ->
                    NoteRow(
                        entry = entry,
                        isSelectionMode = uiState.isSelectionMode,
                        isSelected = entry.id in uiState.selectedEntryIds,
                        onClick = { onOpenEntry(entry.id) },
                        onToggleSelect = { viewModel.toggleSelection(entry.id) },
                        onEnterSelection = { viewModel.enterSelection(entry.id) },
                        onDuplicate = { viewModel.duplicate(entry) },
                        onRequestDelete = { deletingEntry = entry },
                    )
                }
            }
        }
    }

    deletingEntry?.let { entry ->
        AlertDialog(
            onDismissRequest = { deletingEntry = null },
            title = { Text(stringResource(R.string.console_notepad_delete_confirm_title)) },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(entry); deletingEntry = null }) {
                    Text(stringResource(R.string.console_calendar_delete_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingEntry = null }) {
                    Text(stringResource(R.string.console_pin_cancel))
                }
            },
        )
    }

    if (showBulkDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showBulkDeleteConfirm = false },
            title = { Text(stringResource(R.string.console_notepad_bulk_delete_confirm_title, uiState.selectedEntryIds.size)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.bulkDelete()
                        showBulkDeleteConfirm = false
                    },
                ) {
                    Text(stringResource(R.string.console_calendar_delete_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showBulkDeleteConfirm = false }) {
                    Text(stringResource(R.string.console_pin_cancel))
                }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteRow(
    entry: NotepadEntry,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onToggleSelect: () -> Unit,
    onEnterSelection: () -> Unit,
    onDuplicate: () -> Unit,
    onRequestDelete: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (isSelectionMode) onToggleSelect() else onClick() },
                // Free gesture here — same reasoning as VaultGridCell's own comment: nothing in
                // this list uses long-press today.
                onLongClick = { if (!isSelectionMode) onEnterSelection() },
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        if (isSelectionMode) {
            Icon(
                imageVector = if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 16.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.title.ifBlank { entry.preview }.ifBlank { stringResource(R.string.console_notepad_empty_state) },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = DateUtils.getRelativeTimeSpanString(
                    entry.updatedAtEpochMillis,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS,
                ).toString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!isSelectionMode) {
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.console_notepad_note_options_action))
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.console_notepad_menu_edit)) },
                        leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                        onClick = { showMenu = false; onClick() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.console_notepad_menu_duplicate)) },
                        leadingIcon = { Icon(Icons.Rounded.ContentCopy, contentDescription = null) },
                        onClick = { showMenu = false; onDuplicate() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.console_calendar_delete_action)) },
                        leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                        onClick = { showMenu = false; onRequestDelete() },
                    )
                }
            }
        }
    }
}
