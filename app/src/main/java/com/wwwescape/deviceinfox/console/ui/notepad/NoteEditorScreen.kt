package com.wwwescape.deviceinfox.console.ui.notepad

import android.text.format.DateFormat
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wwwescape.deviceinfox.R
import java.util.Date

/** No separate view/edit mode — the note is always editable, saved automatically ~1.5s after
 * typing pauses, *and* via the top bar's explicit Save action for immediate, visible feedback (see
 * [NoteEditorViewModel]'s own doc comment for both mechanisms, and the TODOS.md writeup this was
 * built from). [androidx.compose.runtime.DisposableEffect] flushes any pending debounced save the
 * instant this screen leaves composition, so a fast back-tap right after the last keystroke
 * doesn't strand it inside an unfired debounce window. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NoteEditorViewModel = hiltViewModel(),
) {
    val entry by viewModel.entry.collectAsStateWithLifecycle()
    val titleText by viewModel.titleText.collectAsStateWithLifecycle()
    val bodyText by viewModel.bodyText.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val conflictingRemote by viewModel.conflictingRemote.collectAsStateWithLifecycle()
    var showConflictDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current

    DisposableEffect(Unit) {
        onDispose { viewModel.flushPendingSave() }
    }

    LaunchedEffect(Unit) {
        viewModel.conflictEvent.collect { showConflictDialog = true }
    }

    val saveSuccessMessage = stringResource(R.string.console_notepad_save_success)
    val genericErrorMessage = stringResource(R.string.console_error_network)
    LaunchedEffect(Unit) {
        viewModel.saveSucceeded.collect { Toast.makeText(context, saveSuccessMessage, Toast.LENGTH_SHORT).show() }
    }
    LaunchedEffect(Unit) {
        viewModel.saveFailed.collect { detail -> Toast.makeText(context, detail ?: genericErrorMessage, Toast.LENGTH_LONG).show() }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp).padding(end = 16.dp), strokeWidth = 2.dp)
                    } else {
                        TextButton(onClick = viewModel::saveNowExplicit, enabled = entry != null) {
                            Text(stringResource(R.string.console_notepad_save_action))
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        if (entry == null) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
            ) {
                // Created/updated, stacked and right-aligned — a quiet metadata corner, not
                // competing for attention with the title/body fields below it. Generous top/bottom
                // padding gives it real breathing room from both the top bar above and the title
                // field below, rather than sitting flush against either; spacedBy keeps the two
                // lines close to each other specifically, reading as one small block.
                val dateFormatter = remember(context) { DateFormat.getMediumDateFormat(context) }
                entry?.let { current ->
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.console_notepad_created_label, dateFormatter.format(Date(current.createdAtEpochMillis))),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.End,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = stringResource(R.string.console_notepad_updated_label, dateFormatter.format(Date(current.updatedAtEpochMillis))),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.End,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                TextField(
                    value = titleText,
                    onValueChange = viewModel::onTitleChanged,
                    placeholder = { Text(stringResource(R.string.console_notepad_title_placeholder)) },
                    textStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                TextField(
                    value = bodyText,
                    onValueChange = viewModel::onBodyChanged,
                    placeholder = { Text(stringResource(R.string.console_notepad_editor_placeholder)) },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
            }
        }
    }

    if (showConflictDialog) {
        // A manual-merge invitation, not a forced exclusive pick — freely dismissable (tap
        // outside/back press, same as "Continue editing" below) since neither of those actions is
        // destructive: this device's own typed title/body are never touched by dismissing, only
        // "Use their version instead" replaces them, and that's only ever reached by its own
        // explicit tap.
        AlertDialog(
            onDismissRequest = {
                viewModel.resolveConflictContinueEditing()
                showConflictDialog = false
            },
            title = { Text(stringResource(R.string.console_notepad_conflict_title)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(stringResource(R.string.console_notepad_conflict_message))
                    Text(
                        text = stringResource(R.string.console_notepad_conflict_merge_hint),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    conflictingRemote?.let { remote ->
                        Column(
                            modifier = Modifier
                                .padding(top = 12.dp)
                                .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.small)
                                .padding(8.dp),
                        ) {
                            if (remote.title.isNotBlank()) {
                                Text(
                                    text = remote.title,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                            Text(
                                text = remote.body,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.resolveConflictContinueEditing()
                        showConflictDialog = false
                    },
                ) {
                    Text(stringResource(R.string.console_notepad_conflict_continue_editing))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.resolveConflictUseTheirVersion()
                        showConflictDialog = false
                    },
                ) {
                    Text(stringResource(R.string.console_notepad_conflict_load_theirs))
                }
            },
        )
    }
}
