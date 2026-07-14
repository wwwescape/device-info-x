package com.wwwescape.deviceinfox.console.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.wwwescape.deviceinfox.R

private const val MIN_POLL_OPTIONS = 2
private const val MAX_POLL_OPTIONS = 12

/** WhatsApp-shape poll creator — question, a dynamic option list (2-12, matching the server's own
 * `MIN_POLL_OPTIONS`/`MAX_POLL_OPTIONS` bounds in `app/schemas/message.py`), and an "allow
 * multiple answers" toggle. Full-screen `Dialog` rather than a compact `AlertDialog` — the same
 * `usePlatformDefaultWidth = false` shell `ImagePreviewDialog`/`MediaPagerDialog` already use —
 * since a scrollable list of up to 12 option fields doesn't fit comfortably in a small popup. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePollDialog(
    onSend: (question: String, options: List<String>, allowsMultiple: Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var question by rememberSaveable { mutableStateOf("") }
    var options by rememberSaveable { mutableStateOf(listOf("", "")) }
    var allowsMultiple by rememberSaveable { mutableStateOf(false) }

    val nonBlankOptions = options.map { it.trim() }.filter { it.isNotBlank() }
    val canSend = question.isNotBlank() && nonBlankOptions.size >= MIN_POLL_OPTIONS

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            modifier = modifier,
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.console_home_create_poll_title)) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.console_pin_cancel))
                        }
                    },
                    actions = {
                        TextButton(
                            enabled = canSend,
                            onClick = { onSend(question.trim(), nonBlankOptions, allowsMultiple) },
                        ) {
                            Text(stringResource(R.string.console_home_send_action))
                        }
                    },
                )
            },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it },
                    label = { Text(stringResource(R.string.console_home_poll_question_label)) },
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(
                    text = stringResource(R.string.console_home_poll_options_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                options.forEachIndexed { index, optionText ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = optionText,
                            onValueChange = { newValue -> options = options.toMutableList().also { it[index] = newValue } },
                            label = { Text(stringResource(R.string.console_home_poll_option_number_label, index + 1)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        // Only removable once above the minimum — the last 2 option fields always
                        // stay, matching the server's own MIN_POLL_OPTIONS floor.
                        if (options.size > MIN_POLL_OPTIONS) {
                            IconButton(onClick = { options = options.toMutableList().also { it.removeAt(index) } }) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.console_home_poll_remove_option_action),
                                )
                            }
                        }
                    }
                }

                if (options.size < MAX_POLL_OPTIONS) {
                    TextButton(onClick = { options = options + "" }) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.console_home_poll_add_option_action))
                    }
                }

                HorizontalDivider()

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.console_home_poll_allow_multiple_label),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = allowsMultiple, onCheckedChange = { allowsMultiple = it })
                }
            }
        }
    }
}
