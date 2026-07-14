package com.wwwescape.deviceinfox.console.ui.messaging

import android.text.format.DateFormat
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wwwescape.deviceinfox.R
import com.wwwescape.deviceinfox.console.data.messaging.ScheduledMessage
import java.util.Date

/** Settings → Scheduled Messages — a plain list, soonest-to-send first (already the order
 * [ScheduledMessagesViewModel.uiState] hands over), each row showing a short preview of the
 * message (2 lines, ellipsized — [ScheduledMessageRow]) and its send time; tapping a row opens
 * [ScheduledMessageDetailDialog] with the full text. Both the row's own 3-dot menu and the detail
 * popup offer Send Now / Delete — no in-place edit either way, to change wording or timing,
 * cancel and reschedule via the composer. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduledMessagesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ScheduledMessagesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var deletingEntry by remember { mutableStateOf<ScheduledMessage?>(null) }
    var detailEntry by remember { mutableStateOf<ScheduledMessage?>(null) }
    var showBulkDeleteConfirm by remember { mutableStateOf(false) }

    val networkErrorMessage = stringResource(R.string.console_error_network)
    LaunchedEffect(Unit) {
        viewModel.errorEvent.collect { detail ->
            Toast.makeText(context, detail ?: networkErrorMessage, Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            if (uiState.isSelectionMode) {
                TopAppBar(
                    title = { Text(stringResource(R.string.console_home_selection_count, uiState.selectedIds.size)) },
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
                    title = { Text(stringResource(R.string.console_scheduled_messages_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        if (uiState.entries.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.console_scheduled_messages_empty_state),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                items(uiState.entries, key = { it.id }) { entry ->
                    ScheduledMessageRow(
                        entry = entry,
                        isSelectionMode = uiState.isSelectionMode,
                        isSelected = entry.id in uiState.selectedIds,
                        onToggleSelect = { viewModel.toggleSelection(entry.id) },
                        onEnterSelection = { viewModel.enterSelection(entry.id) },
                        onShowDetail = { detailEntry = entry },
                        onSendNow = { viewModel.sendNow(entry) },
                        onRequestDelete = { deletingEntry = entry },
                    )
                }
            }
        }
    }

    detailEntry?.let { entry ->
        ScheduledMessageDetailDialog(
            entry = entry,
            onDismiss = { detailEntry = null },
            onSendNow = {
                detailEntry = null
                viewModel.sendNow(entry)
            },
            onRequestDelete = {
                detailEntry = null
                deletingEntry = entry
            },
        )
    }

    deletingEntry?.let { entry ->
        AlertDialog(
            onDismissRequest = { deletingEntry = null },
            title = { Text(stringResource(R.string.console_scheduled_messages_delete_confirm_title)) },
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
            title = {
                Text(
                    stringResource(
                        R.string.console_scheduled_messages_bulk_delete_confirm_title,
                        uiState.selectedIds.size,
                    ),
                )
            },
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

/** [entry.body] renders as a 2-line, ellipsized preview here — same "short preview, tap for the
 * full thing" shape most message-list rows use, rather than the full un-truncated text a first
 * pass at this screen used. [onShowDetail] (a plain tap, only outside selection mode) opens
 * [ScheduledMessageDetailDialog] for the complete text; the row's own 3-dot menu still offers
 * Send Now/Delete directly, for a quick action without opening that popup at all. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ScheduledMessageRow(
    entry: ScheduledMessage,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onEnterSelection: () -> Unit,
    onShowDetail: () -> Unit,
    onSendNow: () -> Unit,
    onRequestDelete: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    val scheduledLabel = rememberScheduledLabel(entry.scheduledAtEpochMillis)

    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (isSelectionMode) onToggleSelect() else onShowDetail() },
                // Free gesture here — same reasoning as NoteRow's own comment: nothing in this
                // list uses long-press today.
                onLongClick = { if (!isSelectionMode) onEnterSelection() },
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        if (isSelectionMode) {
            Icon(
                imageVector = if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 16.dp, top = 2.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.body,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.console_scheduled_messages_sends_at_label, scheduledLabel),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (!isSelectionMode) {
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        Icons.Rounded.MoreVert,
                        contentDescription = stringResource(R.string.console_scheduled_messages_options_action),
                    )
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.console_scheduled_messages_send_now_action)) },
                        leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null) },
                        onClick = { showMenu = false; onSendNow() },
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

@Composable
private fun rememberScheduledLabel(scheduledAtEpochMillis: Long): String {
    val context = LocalContext.current
    val dateFormatter = remember(context) { DateFormat.getMediumDateFormat(context) }
    val timeFormatter = remember(context) { DateFormat.getTimeFormat(context) }
    return remember(scheduledAtEpochMillis, context) {
        val date = Date(scheduledAtEpochMillis)
        "${dateFormatter.format(date)}, ${timeFormatter.format(date)}"
    }
}

/** The full-text detail popup reached by tapping a row — non-editable (there's still no in-place
 * edit for a pending scheduled message, per the design this screen was built from), scrollable if
 * the message is long enough to need it, with the send timestamp shown neatly at the top and
 * Send Now/Delete as the two bottom actions. A plain [Dialog] (not [AlertDialog]) rather than
 * reusing the confirm/dismiss two-button shape every other dialog on this screen uses — neither
 * bottom action here is a "cancel," so a dedicated close [IconButton] plus the platform's own
 * tap-outside/back-press dismissal (both still fully intact via [onDismissRequest]) is the
 * unambiguous way out, rather than overloading Send Now or Delete to also mean "never mind." */
@Composable
private fun ScheduledMessageDetailDialog(
    entry: ScheduledMessage,
    onDismiss: () -> Unit,
    onSendNow: () -> Unit,
    onRequestDelete: () -> Unit,
) {
    val scheduledLabel = rememberScheduledLabel(entry.scheduledAtEpochMillis)

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.console_scheduled_messages_detail_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.action_dismiss))
                    }
                }
                Text(
                    text = stringResource(R.string.console_scheduled_messages_sends_at_label, scheduledLabel),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                )
                Text(
                    text = entry.body,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onRequestDelete) {
                        Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.console_calendar_delete_action))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = onSendNow) {
                        Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.console_scheduled_messages_send_now_action))
                    }
                }
            }
        }
    }
}
