package com.wwwescape.deviceinfox.console.ui.home

import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wwwescape.deviceinfox.R
import com.wwwescape.deviceinfox.console.data.messaging.ConsoleMessage
import com.wwwescape.deviceinfox.console.data.messaging.MessageAttachmentKind
import java.util.Date

/** The 3-dot menu's Info option (own messages only) — a dedicated read-only screen showing when
 * a sent message was delivered and read, WhatsApp-style. [MessageInfoViewModel.message] is looked
 * up live from the same [com.wwwescape.deviceinfox.console.data.messaging.MessageRepository.conversation]
 * flow every other screen already observes, so this updates in real time if the partner reads the
 * message while this screen happens to still be open, rather than needing to be reopened.
 *
 * Only reachable for a message whose [MessageMenuButton]'s Info item is actually shown, which
 * already guarantees [ConsoleMessage.isFromSelf] and a delivery state of `SENT`/`DELIVERED` (never
 * `SENDING`/`FAILED`, which don't show the menu at all — see `MessageBubble`'s own gate) — so this
 * screen never needs to render a "sending"/"failed" state of its own. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageInfoScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MessageInfoViewModel = hiltViewModel(),
) {
    val message by viewModel.message.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.console_message_info_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { innerPadding ->
        // Null only for the brief window between navigating here and the conversation Flow's
        // first emission, or if the message was deleted out from under this screen while it was
        // open (Delete for everyone from a second device, say) — either way, nothing to show.
        val currentMessage = message ?: return@Scaffold

        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            MessageInfoPreview(currentMessage)
            if (currentMessage.readAtEpochMillis != null) {
                MessageInfoRow(
                    icon = Icons.Rounded.DoneAll,
                    tint = readTickTint(),
                    label = stringResource(R.string.console_message_info_read_label),
                    timestamp = messageInfoTimestampLabel(currentMessage.readAtEpochMillis),
                )
            }
            if (currentMessage.deliveredAtEpochMillis != null) {
                MessageInfoRow(
                    icon = Icons.Rounded.DoneAll,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    label = stringResource(R.string.console_message_info_delivered_label),
                    timestamp = messageInfoTimestampLabel(currentMessage.deliveredAtEpochMillis),
                )
            }
            if (currentMessage.readAtEpochMillis == null && currentMessage.deliveredAtEpochMillis == null) {
                Text(
                    text = stringResource(R.string.console_message_info_not_delivered_yet),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
    }
}

/** A static, non-interactive echo of the bubble this message already renders as in the main
 * conversation list — deliberately not the real `MessageBubble` composable, which carries over a
 * dozen callbacks (swipe-to-reply, reactions, the menu button itself, etc.) that make no sense on
 * a read-only info screen; this only needs to look like the same bubble, not behave like one. */
@Composable
private fun MessageInfoPreview(message: ConsoleMessage) {
    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.End) {
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.primaryContainer, messageBubbleShape(isFromSelf = true))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(text = previewText(message), style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                Text(
                    text = DateFormat.getTimeFormat(LocalContext.current).format(Date(message.sentAtEpochMillis)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = if (message.isRead || message.deliveredAtEpochMillis != null) Icons.Rounded.DoneAll else Icons.Rounded.Check,
                    contentDescription = null,
                    tint = if (message.isRead) readTickTint() else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(14.dp),
                )
            }
        }
    }
}

@Composable
private fun MessageInfoRow(icon: ImageVector, tint: Color, label: String, timestamp: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.width(18.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
        }
        Text(
            text = timestamp,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 30.dp),
        )
    }
}

/** Same "same lightness, hue shift for contrast" resolution [HomeScreen.kt]'s own read-tick color
 * already uses — picked at render time off the resolved `onSurfaceVariant`'s luminance rather than
 * `isSystemInDarkTheme()`, so it stays correct under dynamic color/generated themes too, not just
 * plain light/dark. */
@Composable
private fun readTickTint(): Color {
    val base = MaterialTheme.colorScheme.onSurfaceVariant
    return if (base.luminance() > 0.5f) DarkMessageReadTick else MessageReadTick
}

/** Same fallback shape as `StarredPinnedMessagesScreen`'s own `previewText` — [ConsoleMessage.body]
 * is null for a media-only message, not an error, so this falls back to a type label the same way
 * a chat app's own conversation-list preview line would. Duplicated rather than shared: it's ten
 * lines, and the two screens have no other reason to depend on each other. */
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

/** "Today, HH:MM" / "Yesterday, HH:MM" / "Tuesday, HH:MM" / a locale-formatted date beyond that —
 * same day-bucket boundaries as `dateDividerLabel`/`lastSeenLabel` in `HomeScreen.kt`, just with
 * its own wording (a plain "Today, ..." rather than "Last seen today at ..."), so not shared
 * directly with either. */
@Composable
private fun messageInfoTimestampLabel(epochMillis: Long): String {
    val context = LocalContext.current
    val now = System.currentTimeMillis()
    val time = DateFormat.getTimeFormat(context).format(Date(epochMillis))
    return when {
        isSameDay(epochMillis, now) -> stringResource(R.string.console_message_info_today, time)
        isSameDay(epochMillis, now - DateUtils.DAY_IN_MILLIS) -> stringResource(R.string.console_message_info_yesterday, time)
        now - epochMillis < 6 * DateUtils.DAY_IN_MILLIS -> stringResource(
            R.string.console_message_info_weekday,
            DateFormat.format("EEEE", Date(epochMillis)).toString(),
            time,
        )
        else -> stringResource(R.string.console_message_info_date, DateFormat.getDateFormat(context).format(Date(epochMillis)), time)
    }
}
