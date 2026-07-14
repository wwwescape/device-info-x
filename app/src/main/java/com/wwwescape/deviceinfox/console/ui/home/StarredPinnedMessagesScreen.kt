package com.wwwescape.deviceinfox.console.ui.home

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wwwescape.deviceinfox.R
import com.wwwescape.deviceinfox.console.data.messaging.ConsoleMessage
import com.wwwescape.deviceinfox.console.data.messaging.MessageAttachmentKind
import java.util.Date

/** Read-only browse lists for Star/Pin — see the TODOS.md scope writeup this was built from for
 * why one parameterized screen/ViewModel over two near-duplicate ones, why chronological order
 * (not "most recent first"), and why unstarring/unpinning isn't done from here (v1 decisions,
 * all reasoned through and confirmed before this was written). Tapping a row pops back to the
 * Messages screen and jumps it to that message — see [StarredPinnedMessagesViewModel.jumpTo] /
 * `MessageJumpRequester` for how that reaches `HomeScreen`, which has no direct nav-result path
 * back from this screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StarredPinnedMessagesScreen(
    mode: StarredPinnedMode,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StarredPinnedMessagesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val messages = uiState.messagesFor(mode)
    val titleRes = when (mode) {
        StarredPinnedMode.STARRED -> R.string.console_home_starred_messages_title
        StarredPinnedMode.PINNED -> R.string.console_home_pinned_messages_title
    }
    val emptyRes = when (mode) {
        StarredPinnedMode.STARRED -> R.string.console_home_starred_messages_empty
        StarredPinnedMode.PINNED -> R.string.console_home_pinned_messages_empty
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(titleRes)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { innerPadding ->
        if (messages.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(emptyRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                items(messages, key = { it.id }) { message ->
                    StarredPinnedMessageRow(
                        message = message,
                        senderLabel = if (message.isFromSelf) {
                            stringResource(R.string.console_home_you_label)
                        } else {
                            uiState.partnerDisplayName ?: stringResource(R.string.console_home_title_fallback)
                        },
                        onClick = {
                            viewModel.jumpTo(message.id)
                            onBack()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun StarredPinnedMessageRow(message: ConsoleMessage, senderLabel: String, onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = senderLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = DateFormat.getMediumDateFormat(LocalContext.current).format(Date(message.sentAtEpochMillis)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = previewText(message),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        HorizontalDivider()
    }
}

/** [ConsoleMessage.body] is null for a media-only message — falls back to a type label the same
 * way a chat app's own conversation-list preview line would ("Photo", "Voice message", ...)
 * rather than rendering blank. */
@Composable
private fun previewText(message: ConsoleMessage): String {
    message.body?.let { return it }
    message.attachment?.let { attachment ->
        return stringResource(
            when (attachment.kind) {
                MessageAttachmentKind.IMAGE -> R.string.console_home_preview_photo
                MessageAttachmentKind.VIDEO -> R.string.console_home_preview_video
                MessageAttachmentKind.DOCUMENT -> R.string.console_home_preview_document
            },
        )
    }
    if (message.voiceNote != null) return stringResource(R.string.console_home_preview_voice_note)
    return ""
}
