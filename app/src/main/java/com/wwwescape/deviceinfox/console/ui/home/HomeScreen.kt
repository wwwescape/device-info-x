package com.wwwescape.deviceinfox.console.ui.home

import android.Manifest
import android.content.ClipData
import android.content.pm.PackageManager
import android.text.format.DateFormat
import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReplyAll
import androidx.compose.material.icons.automirrored.rounded.ScheduleSend
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.wwwescape.deviceinfox.R
import com.wwwescape.deviceinfox.console.data.db.MessageDeliveryState
import com.wwwescape.deviceinfox.console.data.messaging.ConsoleMessage
import com.wwwescape.deviceinfox.console.data.messaging.MessageAttachment
import com.wwwescape.deviceinfox.console.data.messaging.MessageAttachmentKind
import com.wwwescape.deviceinfox.console.data.messaging.MessageLinkPreview
import com.wwwescape.deviceinfox.console.data.messaging.MessagePoll
import com.wwwescape.deviceinfox.console.data.messaging.MessageReaction
import com.wwwescape.deviceinfox.console.data.presence.PartnerPresence
import com.wwwescape.deviceinfox.console.data.presence.PresenceRepository
import com.wwwescape.deviceinfox.console.ui.components.AppTimePickerDialog
import com.wwwescape.deviceinfox.console.ui.components.DocumentAttachmentRow
import com.wwwescape.deviceinfox.console.ui.components.SettingsGearIcon
import com.wwwescape.deviceinfox.console.ui.components.MediaPagerDialog
import com.wwwescape.deviceinfox.console.ui.components.rememberCameraCapture
import com.wwwescape.deviceinfox.console.ui.components.withDate
import com.wwwescape.deviceinfox.console.ui.components.withTime
import com.wwwescape.deviceinfox.console.ui.tour.FeatureTourTarget
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val REACTION_EMOJIS = listOf("👍", "❤️", "😂", "😮", "😢", "🙏")

/** How close to the top of the list (in item count) triggers `loadOlderMessages()`. */
private const val NEAR_TOP_LOAD_THRESHOLD = 10

/** How long a message jumped to from the Starred/Pinned screens stays visually highlighted. */
private const val JUMP_HIGHLIGHT_DURATION_MILLIS = 1_500L

/** Coarse enough that re-enabling the online-ping icon after its 15-minute cooldown doesn't need
 * a snappy tick, cheap enough that recomposing the whole top bar on it isn't worth avoiding. */
private const val COOLDOWN_POLL_INTERVAL_MILLIS = 30_000L

/** Shared with [com.wwwescape.deviceinfox.console.ui.tabs.ConsoleTabsScreen]'s bottom nav bar
 * collapse — both must animate with the same duration so the composer panel expanding in and the
 * nav bar collapsing away read as one synchronized motion instead of two independently-timed
 * transitions. */
internal const val COMPOSER_PANEL_ANIMATION_MILLIS = 200

@Composable
private fun rememberNowMillis(): State<Long> = produceState(initialValue = System.currentTimeMillis()) {
    while (true) {
        value = System.currentTimeMillis()
        delay(COOLDOWN_POLL_INTERVAL_MILLIS)
    }
}

/** Fixed "read" receipt blue, deliberately not derived from `MaterialTheme.colorScheme.primary`
 * — this app's static palette (and Material You dynamic color) can land primary/onSurfaceVariant
 * at nearly identical lightness, which was the original delivered-vs-read bug. Two variants
 * because a single fixed hue can't stay legible against both a light-theme-style dark gray tick
 * and a dark-theme-style pale gray tick; [MessageBubble] below picks between them off the
 * *resolved* onSurfaceVariant's own luminance (not `isSystemInDarkTheme()`), so this stays correct
 * under dynamic color, generated color themes, and pure/absolute dark alike. */
internal val MessageReadTick = Color(0xFF34B7F1)
internal val DarkMessageReadTick = Color(0xFF1E3A8A)

/** Signal-style bubble: fully rounded except the outer-bottom corner, which pulls in to a much
 * tighter radius to read as a subtle tail pointing toward the sender's side of the screen. */
internal fun messageBubbleShape(isFromSelf: Boolean): RoundedCornerShape {
    val corner = 20.dp
    val tail = 4.dp
    return if (isFromSelf) {
        RoundedCornerShape(topStart = corner, topEnd = corner, bottomStart = corner, bottomEnd = tail)
    } else {
        RoundedCornerShape(topStart = corner, topEnd = corner, bottomStart = tail, bottomEnd = corner)
    }
}

private enum class MessageContextOverlayKind { MENU, REACTION_PICKER }

/** Identifies the overlay by [messageId] rather than holding a [ConsoleMessage] snapshot directly
 * — [HomeScreen] re-resolves the live message from `uiState.visibleMessages` every recomposition
 * (see its own render site), so the overlay's content (star/pin/edit availability, reaction list,
 * etc.) stays live for however long it's open instead of freezing at the moment it was opened. */
private data class MessageContextOverlayTarget(val messageId: String, val kind: MessageContextOverlayKind)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onSettingsClick: () -> Unit,
    onStarredMessagesClick: () -> Unit,
    onPinnedMessagesClick: () -> Unit,
    onMediaClick: () -> Unit,
    onInfoClick: (String) -> Unit,
    onLock: () -> Unit,
    modifier: Modifier = Modifier,
    // Reports whether the keyboard, emoji picker, or attach panel currently occupies the
    // "below the composer" slot, so the host (ConsoleTabsScreen) can hide its bottom nav bar
    // out of that space while any of them is open — see ComposerBar's own isExpanded logic.
    onComposerExpandedChanged: (Boolean) -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    // Gated before any other state is collected below — those LaunchedEffect/DisposableEffect
    // hooks (scroll-triggered pagination, "Here" presence, toast collectors) have nothing
    // meaningful to do yet while the very first sync is still in flight, and an early return here
    // is what keeps the composer/message list from flashing empty before real data lands. See
    // MessageRepository.isInitialSyncing's doc comment for why this never re-fires after the
    // first cold start (reconnect resyncs don't touch it).
    val isInitialSyncing by viewModel.isInitialSyncing.collectAsStateWithLifecycle()
    val isInitialPresenceLoading by viewModel.isInitialPresenceLoading.collectAsStateWithLifecycle()
    if (isInitialSyncing) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.console_home_loading_label),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isRecording by viewModel.isRecording.collectAsStateWithLifecycle()
    val playingVoiceNotePath by viewModel.playingVoiceNotePath.collectAsStateWithLifecycle()
    val recentEmojis by viewModel.recentEmojis.collectAsStateWithLifecycle()
    val isLoadingOlderMessages by viewModel.isLoadingOlderMessages.collectAsStateWithLifecycle()
    val onlinePingAvailableAt by viewModel.onlinePingAvailableAtEpochMillis.collectAsStateWithLifecycle()
    val isUpdateAvailable by viewModel.isUpdateAvailable.collectAsStateWithLifecycle()
    val isLiveLocationActive by viewModel.isLiveLocationActive.collectAsStateWithLifecycle()
    val nowMillis by rememberNowMillis()
    val isOnlinePingOnCooldown = onlinePingAvailableAt.let { it != null && nowMillis < it }
    // No point showing the "I'm online" icon at all if the partner's device is already Here/Online
    // in the app — isOnline covers both tiers, since PartnerPresence.isInMessagesScreen ("Here") is
    // only ever set true while isOnline is too (see that class's own doc comment). Hidden, not
    // merely disabled: unlike the cooldown below (where disabling communicates "you already did
    // this, wait"), there's nothing useful conveyed by a greyed-out icon here — the icon's whole
    // premise (the partner isn't already in the app) just doesn't hold, so it shouldn't be shown.
    val isPartnerAlreadyPresent = uiState.partnerPresence?.isOnline == true
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val context = LocalContext.current
    var previewMessage by remember { mutableStateOf<ConsoleMessage?>(null) }
    var pendingDelete by remember { mutableStateOf<PendingMessageDelete?>(null) }
    var showBulkDeleteConfirm by remember { mutableStateOf(false) }
    var showConversationMenu by remember { mutableStateOf(false) }
    var showCreatePollDialog by remember { mutableStateOf(false) }

    // Reported to onComposerExpandedChanged OR'd together with the composer's own IME/panel
    // state below, rather than forwarding ComposerBar's signal straight through — opening a
    // message's context overlay (see MessageContextOverlayHost) is still an Android Dialog, which
    // steals window focus from the composer and closes the keyboard the same way the old
    // DropdownMenu-based menu/reaction-picker did; the overlay's own full-screen scrim now hides
    // the reflow this used to guard against, but keeping this in place still avoids a visible
    // glitch in the instant right after the overlay is dismissed and the scrim is gone.
    var isComposerImeExpanded by remember { mutableStateOf(false) }
    var messageContextOverlay by remember { mutableStateOf<MessageContextOverlayTarget?>(null) }
    val isAnyMessageOverlayOpen = messageContextOverlay != null
    LaunchedEffect(isComposerImeExpanded, isAnyMessageOverlayOpen) {
        onComposerExpandedChanged(isComposerImeExpanded || isAnyMessageOverlayOpen)
    }
    val isPartnerTyping = uiState.partnerPresence?.isTyping == true

    // +1 for the typing-indicator bubble appended after the last message when isPartnerTyping —
    // keyed on both so the list also scrolls down when the bubble itself appears/disappears, not
    // just when a real message arrives.
    val itemCount = uiState.visibleMessages.size + if (isPartnerTyping) 1 else 0
    // Keyed on the *newest* message's id, not the list size — loadOlderMessages() prepending
    // older history at the top also changes uiState.messages.size, but shouldn't yank the view
    // back down to the bottom the way a real new send/receive should.
    val newestMessageId = uiState.visibleMessages.lastOrNull()?.id
    LaunchedEffect(newestMessageId, isPartnerTyping) {
        if (itemCount > 0) listState.animateScrollToItem(itemCount - 1)
    }

    // Loads another page of history once the user scrolls near the top — skipped while searching,
    // since the search-filtered view isn't the right context to page in more of the conversation.
    LaunchedEffect(listState, uiState.isSearching) {
        if (uiState.isSearching) return@LaunchedEffect
        snapshotFlow { listState.firstVisibleItemIndex }.collect { index ->
            if (index < NEAR_TOP_LOAD_THRESHOLD) viewModel.loadOlderMessages()
        }
    }

    // A tap in the Starred/Pinned messages screens, relayed via MessageJumpRequester ->
    // HomeViewModel.jumpToMessage — see that function's doc comment for why the target might not
    // be in visibleMessages the instant this fires (it can still be paginating in). Re-runs
    // whenever scrollToMessageId changes, which covers both "already loaded, jump immediately"
    // and "ViewModel finished paginating it in while we were still on the other screen."
    val scrollToMessageId by viewModel.scrollToMessageId.collectAsStateWithLifecycle()
    var highlightedMessageId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(scrollToMessageId) {
        val targetId = scrollToMessageId ?: return@LaunchedEffect
        val index = uiState.visibleMessages.indexOfFirst { it.id == targetId }
        if (index >= 0) {
            listState.animateScrollToItem(index)
            highlightedMessageId = targetId
            delay(JUMP_HIGHLIGHT_DURATION_MILLIS)
            highlightedMessageId = null
        }
        viewModel.consumeScrollRequest()
    }

    val jumpTargetNotFoundMessage = stringResource(R.string.console_home_jump_target_not_found)
    LaunchedEffect(Unit) {
        viewModel.jumpTargetNotFoundEvent.collect {
            Toast.makeText(context, jumpTargetNotFoundMessage, Toast.LENGTH_SHORT).show()
        }
    }

    // The "Here" presence tier — lets the partner see this specific device is looking at the
    // chat right now, not just that the console is open somewhere. DisposableEffect rather than
    // ViewModel init/onCleared: HomeViewModel survives tab switches (ConsoleTabsScreen's
    // saveState/restoreState), so it has no moment of its own that corresponds to "the user just
    // switched onto/off of this tab" — re-entering/leaving composition is that signal instead.
    DisposableEffect(Unit) {
        viewModel.enterMessagesScreen()
        onDispose { viewModel.leaveMessagesScreen() }
    }

    val imageSavedMessage = stringResource(R.string.console_home_image_saved)
    LaunchedEffect(Unit) {
        viewModel.imageSavedEvent.collect {
            Toast.makeText(context, imageSavedMessage, Toast.LENGTH_SHORT).show()
        }
    }

    val vaultSavedMessage = stringResource(R.string.console_home_saved_to_vault_message)
    val vaultAlreadySavedMessage = stringResource(R.string.console_home_already_saved_to_vault_message)
    LaunchedEffect(Unit) {
        viewModel.vaultSaveEvent.collect { saved ->
            Toast.makeText(context, if (saved) vaultSavedMessage else vaultAlreadySavedMessage, Toast.LENGTH_SHORT).show()
        }
    }

    val networkErrorMessage = stringResource(R.string.console_error_network)
    LaunchedEffect(Unit) {
        viewModel.errorEvent.collect { detail ->
            Toast.makeText(context, detail ?: networkErrorMessage, Toast.LENGTH_LONG).show()
        }
    }

    val partnerWipedMessage = stringResource(R.string.console_home_partner_wiped_conversation)
    LaunchedEffect(Unit) {
        viewModel.conversationWipedEvent.collect {
            Toast.makeText(context, partnerWipedMessage, Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            if (uiState.isSelectionMode) {
                TopAppBar(
                    title = { Text(stringResource(R.string.console_home_selection_count, uiState.selectedMessageIds.size)) },
                    navigationIcon = {
                        IconButton(onClick = viewModel::clearSelection) {
                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.console_pin_cancel))
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            val text = uiState.selectedMessages
                                .filter { !it.isDeleted }
                                .mapNotNull { it.body }
                                .joinToString("\n")
                            if (text.isNotBlank()) {
                                coroutineScope.launch {
                                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("messages", text)))
                                }
                            }
                            viewModel.clearSelection()
                        }) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = stringResource(R.string.console_home_action_copy))
                        }
                        IconButton(onClick = { showBulkDeleteConfirm = true }) {
                            Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.console_home_action_delete_for_me))
                        }
                        IconButton(onClick = { viewModel.bulkSetStarred(!uiState.allSelectedAreStarred) }) {
                            Icon(Icons.Rounded.Star, contentDescription = stringResource(R.string.console_home_action_star))
                        }
                        IconButton(onClick = { viewModel.bulkSetPinned(!uiState.allSelectedArePinned) }) {
                            Icon(Icons.Rounded.PushPin, contentDescription = stringResource(R.string.console_home_action_pin))
                        }
                    },
                )
            } else if (uiState.isSearching) {
                TopAppBar(
                    title = {
                        TextField(
                            value = uiState.searchQuery,
                            onValueChange = viewModel::setSearchQuery,
                            placeholder = { Text(stringResource(R.string.console_home_search_hint)) },
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
                    title = {
                        Column(modifier = Modifier.clickable(onClick = onMediaClick)) {
                            Text(uiState.partnerDisplayName ?: stringResource(R.string.console_home_title_fallback))
                            presenceSubtitle(uiState.partnerPresence, nowMillis, isInitialPresenceLoading)?.let { subtitle ->
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    actions = {
                        if (!isPartnerAlreadyPresent) {
                            FeatureTourTarget(
                                tourKey = "online_ping",
                                description = stringResource(R.string.console_home_online_ping_tour_description),
                            ) {
                                IconButton(onClick = viewModel::sendOnlinePing, enabled = !isOnlinePingOnCooldown) {
                                    Icon(
                                        Icons.Rounded.NotificationsActive,
                                        contentDescription = stringResource(R.string.console_home_notify_online_action),
                                    )
                                }
                            }
                        }
                        IconButton(onClick = viewModel::toggleSearch) {
                            Icon(Icons.Rounded.Search, contentDescription = stringResource(R.string.console_home_search_action))
                        }
                        IconButton(onClick = onLock) {
                            Icon(Icons.Rounded.Lock, contentDescription = stringResource(R.string.console_home_lock_action))
                        }
                        Box {
                            IconButton(onClick = { showConversationMenu = true }) {
                                Icon(
                                    Icons.Rounded.MoreVert,
                                    contentDescription = stringResource(R.string.console_home_conversation_menu_action),
                                )
                            }
                            DropdownMenu(expanded = showConversationMenu, onDismissRequest = { showConversationMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.console_home_starred_messages_title)) },
                                    onClick = { showConversationMenu = false; onStarredMessagesClick() },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.console_home_pinned_messages_title)) },
                                    onClick = { showConversationMenu = false; onPinnedMessagesClick() },
                                )
                            }
                        }
                        IconButton(onClick = onSettingsClick) {
                            SettingsGearIcon(isLiveLocationActive = isLiveLocationActive, isUpdateAvailable = isUpdateAvailable)
                        }
                    },
                )
            }
        },
        bottomBar = {
            ComposerBar(
                replyTarget = uiState.replyTarget,
                editingMessage = uiState.editingMessage,
                onCancelReply = viewModel::cancelReply,
                onCancelEdit = viewModel::cancelEdit,
                onSubmit = viewModel::submitComposerText,
                onSchedule = viewModel::scheduleMessage,
                onTextChanged = viewModel::onComposerTextChanged,
                recentEmojis = recentEmojis,
                onEmojiUsed = viewModel::onEmojiUsed,
                onAttachImage = viewModel::attachImage,
                onAttachVideo = viewModel::attachVideo,
                onAttachDocument = viewModel::attachDocument,
                onCreatePoll = { showCreatePollDialog = true },
                onPickerLaunch = viewModel::beginPickerLaunch,
                onPickerResult = viewModel::endPickerLaunch,
                isRecording = isRecording,
                onStartRecording = viewModel::startRecording,
                onStopRecording = viewModel::stopRecordingAndSend,
                onCancelRecording = viewModel::cancelRecording,
                onExpandedChanged = { isComposerImeExpanded = it },
                forceNavigationBarsPadding = isAnyMessageOverlayOpen,
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            val pinned = uiState.pinnedMessage
            if (pinned != null) {
                PinnedBanner(pinned)
            }

            if (uiState.visibleMessages.isEmpty() && !isPartnerTyping) {
                Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.console_home_empty_state),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (isLoadingOlderMessages) {
                        item(key = "loading_older_messages") {
                            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                    itemsIndexed(uiState.visibleMessages, key = { _, message -> message.id }) { index, message ->
                        val previousMessage = uiState.visibleMessages.getOrNull(index - 1)
                        if (previousMessage == null || !isSameDay(message.sentAtEpochMillis, previousMessage.sentAtEpochMillis)) {
                            DateDivider(message.sentAtEpochMillis)
                        }
                        MessageBubble(
                            message = message,
                            isSelected = message.id in uiState.selectedMessageIds,
                            isHighlighted = message.id == highlightedMessageId,
                            isSelectionMode = uiState.isSelectionMode,
                            onToggleSelect = { viewModel.toggleSelection(message.id) },
                            onReply = { viewModel.startReply(message) },
                            onReplyPreviewClick = { messageId -> viewModel.jumpToMessage(messageId) },
                            onImageClick = { previewMessage = message },
                            onSaveDocument = { attachment -> viewModel.saveDocument(message.id, attachment) },
                            onSaveDocumentToVault = { attachment -> viewModel.saveDocumentAttachmentToVault(message.id, attachment) },
                            isPlayingVoiceNote = playingVoiceNotePath != null && playingVoiceNotePath == message.voiceNote?.filePath,
                            onPlayVoiceNote = { filePath -> viewModel.togglePlayback(filePath) },
                            onVotePoll = { optionIds -> viewModel.votePoll(message, optionIds) },
                            onClosePoll = { viewModel.closePoll(message) },
                            onOpenMenu = {
                                messageContextOverlay = MessageContextOverlayTarget(message.id, MessageContextOverlayKind.MENU)
                            },
                            onOpenReactionPicker = {
                                messageContextOverlay = MessageContextOverlayTarget(message.id, MessageContextOverlayKind.REACTION_PICKER)
                            },
                            onRemoveMyReaction = {
                                message.myReaction?.let { emoji -> viewModel.onReactionSelected(message, emoji) }
                            },
                        )
                    }
                    if (isPartnerTyping) {
                        item(key = "typing_indicator") {
                            TypingIndicatorBubble()
                        }
                    }
                }
            }
        }

        // Re-resolved from uiState.visibleMessages every recomposition (see
        // MessageContextOverlayTarget's own doc comment) rather than carried as a snapshot — if
        // the target message vanishes from the list entirely while this is open (e.g. hidden via
        // a bulk action elsewhere), fall back to dismissing instead of rendering stale/broken data.
        messageContextOverlay?.let { target ->
            val overlayMessage = uiState.visibleMessages.firstOrNull { it.id == target.messageId }
            if (overlayMessage == null) {
                LaunchedEffect(target) { messageContextOverlay = null }
            } else {
                MessageContextOverlayHost(
                    message = overlayMessage,
                    kind = target.kind,
                    contentPadding = innerPadding,
                    onDismiss = { messageContextOverlay = null },
                    onReply = { messageContextOverlay = null; viewModel.startReply(overlayMessage) },
                    onEdit = { messageContextOverlay = null; viewModel.startEdit(overlayMessage) },
                    onDelete = {
                        messageContextOverlay = null
                        pendingDelete = PendingMessageDelete(overlayMessage, DeleteMessageMode.DELETE_FOR_EVERYONE)
                    },
                    onInfo = { messageContextOverlay = null; onInfoClick(overlayMessage.id) },
                    onToggleStar = { messageContextOverlay = null; viewModel.toggleStar(overlayMessage) },
                    onTogglePin = { messageContextOverlay = null; viewModel.togglePin(overlayMessage) },
                    onCopy = {
                        messageContextOverlay = null
                        overlayMessage.body?.let { body ->
                            coroutineScope.launch {
                                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("message", body)))
                            }
                        }
                    },
                    onHideForMe = {
                        messageContextOverlay = null
                        pendingDelete = PendingMessageDelete(overlayMessage, DeleteMessageMode.HIDE_FOR_ME)
                    },
                    onSelect = { messageContextOverlay = null; viewModel.enterSelection(overlayMessage.id) },
                    onReact = { emoji -> messageContextOverlay = null; viewModel.onReactionSelected(overlayMessage, emoji) },
                )
            }
        }
    }

    previewMessage?.let { message ->
        MediaPagerDialog(
            messages = uiState.visibleMessages,
            initialMessageId = message.id,
            onDismiss = { previewMessage = null },
            onDownloadImage = { attachment -> viewModel.downloadImage(attachment) },
            onDownloadVideo = { attachment -> viewModel.downloadVideo(attachment) },
            ensureVideoDownloaded = { messageId, attachment -> viewModel.ensureVideoDownloaded(messageId, attachment) },
            onSaveToVault = { messageId, attachment ->
                if (attachment.kind == MessageAttachmentKind.VIDEO) {
                    viewModel.saveVideoAttachmentToVault(messageId, attachment)
                } else {
                    viewModel.saveAttachmentToVault(messageId, attachment)
                }
            },
        )
    }

    pendingDelete?.let { pending ->
        DeleteMessageConfirmationDialog(
            mode = pending.mode,
            onConfirm = {
                when (pending.mode) {
                    DeleteMessageMode.HIDE_FOR_ME -> viewModel.hideForMe(pending.message)
                    DeleteMessageMode.DELETE_FOR_EVERYONE -> viewModel.deleteMessage(pending.message)
                }
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }

    if (showBulkDeleteConfirm) {
        BulkDeleteForMeConfirmationDialog(
            count = uiState.selectedMessageIds.size,
            onConfirm = {
                viewModel.bulkDeleteForMe()
                showBulkDeleteConfirm = false
            },
            onDismiss = { showBulkDeleteConfirm = false },
        )
    }

    if (showCreatePollDialog) {
        CreatePollDialog(
            onSend = { question, options, allowsMultiple ->
                viewModel.sendPoll(question, options, allowsMultiple)
                showCreatePollDialog = false
            },
            onDismiss = { showCreatePollDialog = false },
        )
    }

    val videoSavedMessage = stringResource(R.string.console_home_video_saved)
    LaunchedEffect(Unit) {
        viewModel.videoSavedEvent.collect {
            Toast.makeText(context, videoSavedMessage, Toast.LENGTH_SHORT).show()
        }
    }

    val documentSavedMessage = stringResource(R.string.console_home_document_saved)
    LaunchedEffect(Unit) {
        viewModel.documentSavedEvent.collect {
            Toast.makeText(context, documentSavedMessage, Toast.LENGTH_SHORT).show()
        }
    }

    val messageScheduledMessage = stringResource(R.string.console_home_message_scheduled_toast)
    LaunchedEffect(Unit) {
        viewModel.messageScheduledEvent.collect {
            Toast.makeText(context, messageScheduledMessage, Toast.LENGTH_SHORT).show()
        }
    }
}

/** Here > Online > Last seen > Loading… — deliberately not including [PartnerPresence.isTyping]:
 * that only drives [TypingIndicatorBubble] in the message list now, which already says the same
 * thing (a message is actively landing) more concretely than a text subtitle would, so showing
 * "Typing…" up here too would just be the same fact twice. Null when there's genuinely nothing to
 * show (unpaired, or paired-but-confirmed-offline-with-no-last-seen-ever); "Loading…" instead
 * whenever that exact same shape is still just the unresolved connection-window default — see
 * [PresenceRepository.isInitialPresenceLoading]'s doc comment for why those two cases need
 * [isInitialLoading] to tell apart at all, and `HomeViewModel.isInitialSyncing`'s own doc comment
 * for the precedent this mirrors. */
@Composable
private fun presenceSubtitle(presence: PartnerPresence?, nowMillis: Long, isInitialLoading: Boolean): String? {
    if (presence == null) return null
    return when {
        presence.isInMessagesScreen -> stringResource(R.string.console_home_here_label)
        presence.isOnline -> stringResource(R.string.console_home_online_label)
        presence.lastSeenEpochMillis != null -> lastSeenLabel(presence.lastSeenEpochMillis, nowMillis)
        isInitialLoading -> stringResource(R.string.console_home_loading_label)
        else -> null
    }
}

/** "Last seen today at HH:MM" / "yesterday at HH:MM" / "Tuesday at HH:MM" / a locale-formatted
 * date beyond that — same day-bucket boundaries as [dateDividerLabel], and the same WhatsApp
 * convention this app already follows there, rather than always showing a bare time-of-day
 * (misleading once it's more than a few hours stale — "Last seen 3:45 PM" reads as "earlier
 * today" even when it was actually last week). [now] is threaded in from [rememberNowMillis]
 * rather than read directly via `System.currentTimeMillis()` — this is a plain (non-reactive)
 * `@Composable`, so reading the clock internally would freeze the bucket (today/yesterday/
 * weekday/date) at whatever it was on the last *unrelated* recomposition, e.g. never actually
 * flipping "today" to "yesterday" right after midnight without some other state change forcing a
 * redraw first. */
@Composable
private fun lastSeenLabel(epochMillis: Long, now: Long): String {
    val context = LocalContext.current
    val time = DateFormat.getTimeFormat(context).format(Date(epochMillis))
    return when {
        isSameDay(epochMillis, now) -> stringResource(R.string.console_home_last_seen_today, time)
        isSameDay(epochMillis, now - DateUtils.DAY_IN_MILLIS) -> stringResource(R.string.console_home_last_seen_yesterday, time)
        now - epochMillis < 6 * DateUtils.DAY_IN_MILLIS -> stringResource(
            R.string.console_home_last_seen_weekday,
            DateFormat.format("EEEE", Date(epochMillis)).toString(),
            time,
        )
        else -> stringResource(R.string.console_home_last_seen_date, DateFormat.getDateFormat(context).format(Date(epochMillis)))
    }
}

/** Appended as the last item in the message list while the partner is typing — same bubble
 * shape/color as an incoming [MessageBubble] (left-aligned, [messageBubbleShape] with
 * `isFromSelf = false`), so it reads as "a message is about to land here" the way WhatsApp/
 * Signal/iMessage's typing bubbles do, rather than just the plain text subtitle already shown in
 * the top bar (see [presenceSubtitle]) — this is the "in the message window" version of that same
 * state. Three dots pulse in a staggered loop via [rememberInfiniteTransition]. */
@Composable
private fun TypingIndicatorBubble() {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .clip(messageBubbleShape(isFromSelf = false))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            val transition = rememberInfiniteTransition(label = "typingIndicator")
            repeat(3) { index ->
                val scale by transition.animateFloat(
                    initialValue = 0.4f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 600, delayMillis = index * 160, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "typingDot$index",
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            alpha = 0.4f + 0.6f * scale
                        }
                        .background(MaterialTheme.colorScheme.onSurfaceVariant, CircleShape),
                )
            }
        }
    }
}

@Composable
private fun PinnedBanner(message: ConsoleMessage) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.PushPin,
            contentDescription = stringResource(R.string.console_home_pinned_label),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.height(16.dp),
        )
        Text(
            text = message.body ?: stringResource(R.string.console_home_pinned_label),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: ConsoleMessage,
    isSelected: Boolean,
    isHighlighted: Boolean,
    isSelectionMode: Boolean,
    onToggleSelect: () -> Unit,
    onReply: () -> Unit,
    onReplyPreviewClick: (String) -> Unit,
    onImageClick: (MessageAttachment) -> Unit,
    onSaveDocument: (MessageAttachment) -> Unit,
    onSaveDocumentToVault: (MessageAttachment) -> Unit,
    isPlayingVoiceNote: Boolean,
    onPlayVoiceNote: (String) -> Unit,
    onVotePoll: (List<String>) -> Unit,
    onClosePoll: () -> Unit,
    onOpenMenu: () -> Unit,
    onOpenReactionPicker: () -> Unit,
    onRemoveMyReaction: () -> Unit,
) {
    val targetBubbleColor = when {
        isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
        // Same tint/alpha as isSelected — both mean "this bubble is what you should be looking
        // at right now," just from a different trigger (multi-select vs. a Starred/Pinned jump).
        isHighlighted -> MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
        message.isFromSelf -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    // Animated rather than a bare val: isHighlighted flips back to false ~1.5s after a jump lands
    // (see JUMP_HIGHLIGHT_DURATION_MILLIS), and an instant snap back to the normal bubble color
    // read as a glitch rather than a highlight fading out.
    val bubbleColor by animateColorAsState(targetValue = targetBubbleColor, animationSpec = tween(400), label = "bubbleColor")

    // WhatsApp-style swipe-to-reply: dragging right reveals a reply icon behind the bubble and
    // slides the bubble away from it; releasing past [replyTriggerPx] fires onReply, any other
    // release springs back to 0. Disabled during selection mode, where a tap toggles selection
    // instead.
    val swipeCoroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val maxSwipePx = remember(density) { with(density) { 56.dp.toPx() } }
    val replyTriggerPx = remember(density) { with(density) { 36.dp.toPx() } }
    val swipeOffset = remember { Animatable(0f) }

    // Extra bottom padding only when a reaction pill is actually hooked onto this bubble's bottom
    // corner (see MessageReactionPills) — it hangs partway below the Card's own bottom edge, and
    // the LazyColumn's own inter-item spacing (6.dp) alone isn't enough room for it not to
    // overlap the next message.
    val bottomPaddingForReactions = if (message.reactions.isNotEmpty()) 12.dp else 0.dp
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = bottomPaddingForReactions)) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isFromSelf) Arrangement.End else Arrangement.Start,
    ) {
        // SENDING/FAILED are excluded because the row is still keyed by a local, client-only
        // pendingId at that point (see MessageRepository.sendText) — edit/delete would target an
        // id the server has never heard of. SENT and DELIVERED both mean the real server-assigned
        // message exists, so both (not just the fleeting SENT window) need the menu available.
        if (!isSelectionMode && message.isFromSelf &&
            (message.deliveryState == MessageDeliveryState.SENT || message.deliveryState == MessageDeliveryState.DELIVERED)
        ) {
            MessageMenuButton(onOpenMenu)
        }
        Box(
            modifier = Modifier.pointerInput(isSelectionMode, message.isDeleted) {
                // Swipe-to-reply bypasses the 3-dot menu's own reply gate entirely — needs the
                // same tombstone exclusion (see the menu's Reply item comment) or it'd fire the
                // same 404-on-a-deleted-message call the menu was fixed to avoid.
                if (isSelectionMode || message.isDeleted) return@pointerInput
                detectHorizontalDragGestures(
                    onDragEnd = {
                        val shouldReply = swipeOffset.value >= replyTriggerPx
                        swipeCoroutineScope.launch { swipeOffset.animateTo(0f, spring()) }
                        if (shouldReply) onReply()
                    },
                    onDragCancel = {
                        swipeCoroutineScope.launch { swipeOffset.animateTo(0f, spring()) }
                    },
                ) { change, dragAmount ->
                    change.consume()
                    swipeCoroutineScope.launch {
                        swipeOffset.snapTo((swipeOffset.value + dragAmount).coerceIn(0f, maxSwipePx))
                    }
                }
            },
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ReplyAll,
                contentDescription = stringResource(R.string.console_home_action_reply),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(20.dp)
                    .graphicsLayer { alpha = (swipeOffset.value / replyTriggerPx).coerceIn(0f, 1f) },
            )
            Card(
                shape = messageBubbleShape(message.isFromSelf),
                colors = CardDefaults.cardColors(containerColor = bubbleColor),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .offset { IntOffset(swipeOffset.value.roundToInt(), 0) }
                    .graphicsLayer(alpha = if (message.deliveryState == MessageDeliveryState.SENDING) 0.6f else 1f)
                    .combinedClickable(
                        onClick = {
                            when {
                                isSelectionMode -> onToggleSelect()
                                // No viewer to open for a document — only its inline Save button
                                // does anything (see the DOCUMENT branch in the attachment
                                // rendering below).
                                message.attachment?.kind == MessageAttachmentKind.DOCUMENT -> Unit
                                else -> message.attachment?.let(onImageClick)
                            }
                        },
                        onLongClick = { if (!isSelectionMode && !message.isDeleted) onOpenReactionPicker() },
                    ),
            ) {
                MessageBubbleContent(
                    message = message,
                    isPlayingVoiceNote = isPlayingVoiceNote,
                    onPlayVoiceNote = onPlayVoiceNote,
                    onVotePoll = onVotePoll,
                    onClosePoll = onClosePoll,
                    onReplyPreviewClick = onReplyPreviewClick,
                    onSaveDocument = onSaveDocument,
                    onSaveDocumentToVault = onSaveDocumentToVault,
                )
            }
            MessageReactionPills(
                reactions = message.reactions,
                onTapMine = onRemoveMyReaction,
                modifier = Modifier.align(Alignment.BottomStart).offset(x = 8.dp, y = 10.dp),
            )
        }
        if (!isSelectionMode && !message.isFromSelf) {
            MessageMenuButton(onOpenMenu)
        }
    }
    if (message.deliveryState == MessageDeliveryState.FAILED) {
        Text(
            text = stringResource(R.string.console_home_message_not_sent),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth().padding(end = 12.dp, top = 2.dp),
        )
    }
    }
}

/** The bubble's actual visual content (reply preview, attachment, voice note, body text, link
 * preview, poll, footer/ticks) — split out of [MessageBubble] so [MessageContextOverlayHost] can
 * render an accurate duplicate of the tapped bubble without re-implementing this ~150-line block.
 * The overlay calls this with no-op interaction callbacks (see its own call site) — it's a visual
 * snapshot, not a second live copy of the real bubble; any embedded control (voice-note play, poll
 * vote) that's still technically tappable there just does nothing, matching how WhatsApp's own
 * long-press preview bubble behaves. Reactions render as their own hooked pill row *outside* this
 * content, not inside it — see [MessageReactionPills]'s own doc comment for why. */
@Composable
private fun MessageBubbleContent(
    message: ConsoleMessage,
    isPlayingVoiceNote: Boolean,
    onPlayVoiceNote: (String) -> Unit,
    onVotePoll: (List<String>) -> Unit,
    onClosePoll: () -> Unit,
    onReplyPreviewClick: (String) -> Unit,
    onSaveDocument: (MessageAttachment) -> Unit,
    onSaveDocumentToVault: (MessageAttachment) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        message.replyPreview?.let { reply ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .clickable { onReplyPreviewClick(reply.messageId) }
                    .padding(8.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ReplyAll,
                    contentDescription = null,
                    modifier = Modifier.height(14.dp),
                )
                Text(
                    text = reply.bodyPreview ?: stringResource(
                        if (reply.isDeleted) R.string.console_home_deleted_message else R.string.console_home_reply_preview_media,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        if (!message.isDeleted) {
            message.attachment?.let { attachment ->
                if (attachment.kind == MessageAttachmentKind.DOCUMENT) {
                    DocumentAttachmentRow(
                        attachment = attachment,
                        onSave = onSaveDocument,
                        onSaveToVault = onSaveDocumentToVault,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                } else {
                    Box(modifier = Modifier.size(200.dp).clip(RoundedCornerShape(14.dp))) {
                        AsyncImage(
                            model = attachment.filePath,
                            contentDescription = null,
                            modifier = Modifier.matchParentSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        )
                        if (attachment.kind == MessageAttachmentKind.VIDEO) {
                            Icon(
                                imageVector = Icons.Rounded.PlayCircle,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.align(Alignment.Center).size(48.dp),
                            )
                            attachment.durationMillis?.let { durationMillis ->
                                Text(
                                    text = formatDuration(durationMillis),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(6.dp)
                                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 4.dp, vertical = 1.dp),
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
            message.voiceNote?.let { voiceNote ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 2.dp),
                ) {
                    IconButton(
                        onClick = { onPlayVoiceNote(voiceNote.filePath) },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = if (isPlayingVoiceNote) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = stringResource(
                                if (isPlayingVoiceNote) {
                                    R.string.console_home_pause_voice_note_action
                                } else {
                                    R.string.console_home_play_voice_note_action
                                },
                            ),
                        )
                    }
                    Text(
                        text = formatDuration(voiceNote.durationMillis),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
            }
        }

        if (message.isDeleted) {
            Text(
                text = stringResource(R.string.console_home_deleted_message),
                style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            message.body?.let { body ->
                Text(text = annotateLinks(body), style = MaterialTheme.typography.bodyMedium)
            }
            message.linkPreview?.let { preview ->
                Spacer(modifier = Modifier.height(4.dp))
                LinkPreviewCard(preview)
            }
            message.poll?.let { poll ->
                Spacer(modifier = Modifier.height(6.dp))
                PollOptionsList(
                    poll = poll,
                    isFromSelf = message.isFromSelf,
                    onVote = onVotePoll,
                    onClose = onClosePoll,
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 2.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            if (message.isStarred) {
                Icon(
                    imageVector = Icons.Rounded.Star,
                    contentDescription = stringResource(R.string.console_home_action_star),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.height(12.dp).width(12.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            val timeText = DateFormat.getTimeFormat(LocalContext.current).format(Date(message.sentAtEpochMillis))
            val label = if (message.editedAtEpochMillis != null) {
                "$timeText · " + stringResource(R.string.console_home_edited_suffix)
            } else {
                timeText
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (message.isFromSelf && !message.isDeleted && message.deliveryState != MessageDeliveryState.FAILED) {
                Spacer(modifier = Modifier.width(4.dp))
                val deliveredTint = MaterialTheme.colorScheme.onSurfaceVariant
                Icon(
                    imageVector = when {
                        message.deliveryState == MessageDeliveryState.SENDING -> Icons.Rounded.Schedule
                        message.isRead || message.deliveryState == MessageDeliveryState.DELIVERED -> Icons.Rounded.DoneAll
                        else -> Icons.Rounded.Check
                    },
                    contentDescription = stringResource(
                        when {
                            message.deliveryState == MessageDeliveryState.SENDING -> R.string.console_home_message_sending
                            message.isRead -> R.string.console_home_read_receipt_read
                            message.deliveryState == MessageDeliveryState.DELIVERED -> R.string.console_home_read_receipt_delivered
                            else -> R.string.console_home_read_receipt_sent
                        },
                    ),
                    tint = if (message.isRead) {
                        if (deliveredTint.luminance() > 0.5f) DarkMessageReadTick else MessageReadTick
                    } else {
                        deliveredTint
                    },
                    modifier = Modifier.height(14.dp).width(14.dp),
                )
            }
        }
    }
}

/** Reactions hooked onto the bubble's bottom-leading (start-side) corner, WhatsApp/Signal-style —
 * always the leading corner regardless of self/partner, per on-device feedback that a trailing
 * placement read wrong. One pill
 * per reactor, never more than 2 (this app hard-caps a reaction at one per partner per message,
 * both server- and client-side, see `MessageReaction`'s own uniqueness constraint), so there's no
 * WhatsApp-style "group several reactors' same emoji into one badge with a count" problem to
 * solve here. Deliberately called as a sibling of the `Card`, not from inside
 * [MessageBubbleContent] — a `Card` clips its own content to its shape, so a pill placed *inside*
 * that content column could never visually overlap the bubble's outline the way this needs to;
 * only rendering it in the same outer `Box` the `Card` itself sits in lets it hang half outside
 * the bubble. Each pill is colored by *who placed it*, not by which side sent the underlying
 * message — a self-authored reaction always gets [MaterialTheme.colorScheme.primaryContainer]
 * (the same color self's own bubbles use), a partner-authored one always gets
 * `surfaceContainerHigh`, matching [MessageReaction.isMine] rather than [ConsoleMessage.isFromSelf].
 * [onTapMine] fires only for the self-authored pill, if present — a one-tap "remove my reaction"
 * shortcut reusing `HomeViewModel.onReactionSelected`'s own toggle-off logic, so undoing a
 * reaction doesn't require reopening the long-press picker just to tap the same emoji again. Pass
 * a no-op when this is rendered read-only, e.g. [MessageContextOverlayHost]'s bubble preview. */
@Composable
private fun MessageReactionPills(reactions: List<MessageReaction>, onTapMine: () -> Unit, modifier: Modifier = Modifier) {
    if (reactions.isEmpty()) return
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        reactions.forEach { reaction ->
            val containerColor = if (reaction.isMine) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            }
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(containerColor)
                    .border(1.dp, MaterialTheme.colorScheme.surface, CircleShape)
                    .then(if (reaction.isMine) Modifier.clickable(onClick = onTapMine) else Modifier)
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            ) {
                Text(text = reaction.emoji, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private val MESSAGE_URL_REGEX = Regex("""https?://\S+""")

/** Turns every `http(s)://` substring in [body] into a tappable [LinkAnnotation.Url] span — a
 * bare, unstyled `Text(body)` before this, no link in message text was tappable at all. A null
 * `linkInteractionListener` (the default) opens the link via the ambient `LocalUriHandler`, so no
 * click handling needs wiring up here. This regex is deliberately simpler than the server's own
 * URL-matching in `link_preview_service.py` — this one only decides where to draw a tap target,
 * it never fetches anything, so it doesn't need that one's SSRF-safety concerns. */
private fun annotateLinks(body: String): AnnotatedString = buildAnnotatedString {
    var lastIndex = 0
    for (match in MESSAGE_URL_REGEX.findAll(body)) {
        append(body, lastIndex, match.range.first)
        val url = match.value
        withLink(
            LinkAnnotation.Url(
                url,
                styles = TextLinkStyles(style = SpanStyle(textDecoration = TextDecoration.Underline)),
            ),
        ) {
            append(url)
        }
        lastIndex = match.range.last + 1
    }
    append(body, lastIndex, body.length)
}

/** WhatsApp/iMessage-style link preview, shown under a text message's body once
 * [ConsoleMessage.linkPreview] arrives (it's fetched server-side, asynchronously, after the
 * message itself already sent — see `MessageRepository.applyRemoteMessage`/the
 * `message.link_preview_ready` WS event, so this card may simply not be here yet on a message
 * that was just sent). Tapping it opens the same link the inline text already does. [imagePath]
 * is a locally-downloaded file (proxied through the server's media pipeline), same as
 * [message.attachment]'s thumbnail — never loaded directly from [preview.url]'s own host. */
@Composable
private fun LinkPreviewCard(preview: MessageLinkPreview, modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
            .clickable { runCatching { uriHandler.openUri(preview.url) } },
    ) {
        preview.imagePath?.let { imagePath ->
            AsyncImage(
                model = imagePath,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().height(140.dp),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            )
        }
        Column(modifier = Modifier.padding(8.dp)) {
            preview.title?.let { title ->
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            preview.description?.let { description ->
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Text(
                text = runCatching { android.net.Uri.parse(preview.url).host }.getOrNull() ?: preview.url,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** The question itself already renders via [message.body]'s own normal `Text` right above this
 * (polls never got a separate "title" field — see `MessageRepository.sendPoll`'s doc comment) —
 * this is just the option list + vote UI. Tapping an option while the poll is open reports this
 * device's *complete* new selection via [onVote], not an incremental toggle (matching
 * `PollVoteUpdateDto`'s own shape): for a single-select poll, tapping a different option moves
 * the vote there, tapping the already-selected one retracts it; for multi-select, tapping toggles
 * just that one option within whatever else is already selected. */
@Composable
private fun PollOptionsList(
    poll: MessagePoll,
    isFromSelf: Boolean,
    onVote: (List<String>) -> Unit,
    onClose: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        val myCurrentVotes = poll.options.filter { it.votedBySelf }.map { it.id }
        poll.options.forEach { option ->
            val fraction = if (poll.totalVotes > 0) option.voteCount.toFloat() / poll.totalVotes else 0f
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                    .clickable(enabled = !poll.isClosed) {
                        val newVotes = when {
                            poll.allowsMultiple && option.votedBySelf -> myCurrentVotes - option.id
                            poll.allowsMultiple -> myCurrentVotes + option.id
                            option.votedBySelf -> emptyList()
                            else -> listOf(option.id)
                        }
                        onVote(newVotes)
                    },
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    Icon(
                        imageVector = if (option.votedBySelf) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (option.votedBySelf) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = option.text,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    )
                    Text(
                        text = option.voteCount.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 2.dp)) {
            Text(
                text = stringResource(R.string.console_home_poll_vote_count, poll.totalVotes),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            when {
                poll.isClosed -> Text(
                    text = stringResource(R.string.console_home_poll_closed_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                isFromSelf -> TextButton(onClick = onClose, contentPadding = PaddingValues(horizontal = 4.dp)) {
                    Text(stringResource(R.string.console_home_poll_close_action), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun MessageMenuButton(onOpenMenu: () -> Unit) {
    IconButton(onClick = onOpenMenu) {
        Icon(
            imageVector = Icons.Rounded.MoreVert,
            contentDescription = stringResource(R.string.console_home_message_options),
            modifier = Modifier.width(18.dp),
        )
    }
}

/** The 3-dot menu's item list — pulled out of the old anchored [DropdownMenu] so
 * [MessageContextOverlayHost] can host it inside its own fixed-position [Surface] instead. Every
 * callback already carries its own dismiss (see that host's call site building them), so items
 * here just invoke the callback directly. */
@Composable
private fun MessageContextMenuItems(
    message: ConsoleMessage,
    onReply: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onInfo: () -> Unit,
    onToggleStar: () -> Unit,
    onTogglePin: () -> Unit,
    onCopy: () -> Unit,
    onHideForMe: () -> Unit,
    onSelect: () -> Unit,
) {
    if (!message.isDeleted) {
        // Replying references this message's id as reply_to_id server-side, which 404s once the
        // message is actually deleted (_get_or_404 in message_service.py) — and a locally
        // hidden-for-me tombstone renders identically to a real delete (see the "Delete for me"
        // comment below), so there's no way to tell the two apart here to allow it selectively.
        // Hiding it on any tombstone keeps this consistent with Copy/Edit below rather than
        // leaving a menu item that silently error-toasts.
        DropdownMenuItem(text = { Text(stringResource(R.string.console_home_action_reply)) }, onClick = onReply)
    }
    if (message.isFromSelf && !message.isDeleted && message.body != null) {
        DropdownMenuItem(text = { Text(stringResource(R.string.console_home_action_edit)) }, onClick = onEdit)
    }
    if (!message.isDeleted && message.body != null) {
        DropdownMenuItem(text = { Text(stringResource(R.string.console_home_action_copy)) }, onClick = onCopy)
    }
    if (message.isFromSelf && !message.isDeleted) {
        // Own messages only, like Edit — but unlike Edit/Copy, not gated on `body != null`:
        // delivery/read status is identical regardless of whether the message has text, so this
        // applies to media messages too. Grouped with Copy as the two non-mutating "inspect this
        // message" actions, ahead of the organizational (Star/Pin) and destructive (Delete)
        // groups below.
        DropdownMenuItem(text = { Text(stringResource(R.string.console_home_action_info)) }, onClick = onInfo)
    }
    if (!message.isDeleted) {
        // Same 404-on-a-tombstone reasoning as Reply above — set_starred/set_pinned both go
        // through _get_or_404 server-side too. Ordered ahead of the two Delete items below —
        // Star/Pin are reversible, organizational actions and Delete is not, so grouping the
        // destructive pair together at the bottom (right before the similarly bulk/meta Select)
        // reads more logically than interleaving them.
        DropdownMenuItem(
            text = {
                Text(
                    stringResource(
                        if (message.isStarred) R.string.console_home_action_unstar else R.string.console_home_action_star,
                    ),
                )
            },
            onClick = onToggleStar,
        )
        DropdownMenuItem(
            text = {
                Text(
                    stringResource(
                        if (message.isPinned) R.string.console_home_action_unpin else R.string.console_home_action_pin,
                    ),
                )
            },
            onClick = onTogglePin,
        )
    }
    if (!message.isDeleted) {
        // Purely local — works on any message, own or partner's; never touches the server. Once
        // hidden, `message.isDeleted` becomes true from the UI's perspective, which naturally
        // removes this item from reappearing on it. Deliberately not gated on `message.body !=
        // null` like Copy/Edit above — deleting a media message is exactly as valid as deleting a
        // text one, unlike copying or editing its (currently nonexistent) text content.
        DropdownMenuItem(text = { Text(stringResource(R.string.console_home_action_delete_for_me)) }, onClick = onHideForMe)
    }
    if (message.isFromSelf && !message.isDeleted) {
        DropdownMenuItem(text = { Text(stringResource(R.string.console_home_action_delete)) }, onClick = onDelete)
    }
    DropdownMenuItem(text = { Text(stringResource(R.string.console_home_action_select)) }, onClick = onSelect)
}

/** WhatsApp-style centered context view, replacing the old anchored [DropdownMenu]s on both the
 * 3-dot menu and the long-press reaction picker (see TODOS.md's writeup this was built from for
 * why: an anchored popup has no notion of "stay clear of the header/composer/system nav bar," and
 * could render clipped or partly hidden behind the on-screen nav buttons near the bottom of the
 * list). A full-screen [Dialog] rather than a [Popup] anchored to the tapped bubble — matches this
 * app's other full-bleed dialogs (`CreatePollDialog`, `MediaPagerDialog`) and, being a real Dialog
 * window, always renders above the top bar/composer regardless of where in the tree it's called
 * from. [contentPadding] is the same `PaddingValues` the Scaffold hands its own content lambda —
 * since that already equals the topBar/bottomBar heights, padding this Dialog's own content box by
 * it and centering within what's left gives "vertically centered between the header and the
 * composer" for free, with no manual header/composer position measurement needed. The scrim itself
 * still covers the *entire* screen (including the header/composer), matching WhatsApp's own
 * treatment — only the centered content is inset. */
@Composable
private fun MessageContextOverlayHost(
    message: ConsoleMessage,
    kind: MessageContextOverlayKind,
    contentPadding: PaddingValues,
    onDismiss: () -> Unit,
    onReply: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onInfo: () -> Unit,
    onToggleStar: () -> Unit,
    onTogglePin: () -> Unit,
    onCopy: () -> Unit,
    onHideForMe: () -> Unit,
    onSelect: () -> Unit,
    onReact: (String) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        ) {
            Box(
                modifier = Modifier.fillMaxSize().padding(contentPadding),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    // Swallows taps on the bubble/menu itself so they don't fall through to the
                    // scrim's own dismiss-click behind it.
                    modifier = Modifier
                        .widthIn(max = 320.dp)
                        .padding(horizontal = 24.dp)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (message.isFromSelf) Arrangement.End else Arrangement.Start,
                    ) {
                        Box {
                            Card(
                                shape = messageBubbleShape(message.isFromSelf),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (message.isFromSelf) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                    },
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                modifier = Modifier.widthIn(max = 280.dp),
                            ) {
                                MessageBubbleContent(
                                    message = message,
                                    isPlayingVoiceNote = false,
                                    onPlayVoiceNote = {},
                                    onVotePoll = {},
                                    onClosePoll = {},
                                    onReplyPreviewClick = {},
                                    onSaveDocument = {},
                                    onSaveDocumentToVault = {},
                                )
                            }
                            // Read-only, unlike the real bubble's own pills — this is a visual
                            // snapshot for context while choosing a menu/reaction action, not a
                            // second live surface to remove a reaction from.
                            MessageReactionPills(
                                reactions = message.reactions,
                                onTapMine = {},
                                modifier = Modifier.align(Alignment.BottomStart).offset(x = 8.dp, y = 10.dp),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(if (message.reactions.isNotEmpty()) 16.dp else 8.dp))
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        tonalElevation = 3.dp,
                        shadowElevation = 3.dp,
                    ) {
                        when (kind) {
                            MessageContextOverlayKind.MENU -> Column {
                                MessageContextMenuItems(
                                    message = message,
                                    onReply = onReply,
                                    onEdit = onEdit,
                                    onDelete = onDelete,
                                    onInfo = onInfo,
                                    onToggleStar = onToggleStar,
                                    onTogglePin = onTogglePin,
                                    onCopy = onCopy,
                                    onHideForMe = onHideForMe,
                                    onSelect = onSelect,
                                )
                            }
                            MessageContextOverlayKind.REACTION_PICKER -> Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            ) {
                                REACTION_EMOJIS.forEach { emoji ->
                                    Text(
                                        text = emoji,
                                        style = MaterialTheme.typography.headlineSmall,
                                        modifier = Modifier
                                            .padding(horizontal = 4.dp)
                                            .clickable { onReact(emoji) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ComposerBar(
    replyTarget: ConsoleMessage?,
    editingMessage: ConsoleMessage?,
    onCancelReply: () -> Unit,
    onCancelEdit: () -> Unit,
    onSubmit: (String) -> Unit,
    onSchedule: (String, Long) -> Unit,
    onTextChanged: (String) -> Unit,
    recentEmojis: List<String>,
    onEmojiUsed: (String) -> Unit,
    onAttachImage: (android.net.Uri) -> Unit,
    onAttachVideo: (android.net.Uri) -> Unit,
    onAttachDocument: (android.net.Uri) -> Unit,
    onCreatePoll: () -> Unit,
    onPickerLaunch: () -> Unit,
    onPickerResult: () -> Unit,
    isRecording: Boolean,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onCancelRecording: () -> Unit,
    onExpandedChanged: (Boolean) -> Unit,
    // Set true while a message's context overlay (see MessageContextOverlayHost) is open —
    // ConsoleNavigationBar hides for that case too (HomeScreen ORs it into the same signal
    // onExpandedChanged reports), but this composer has no way to know that on its own since it
    // isn't one of showEmojiPicker/showAttachPanel/isImeVisible below. Without this, the space
    // ConsoleNavigationBar would normally reserve for the system nav bar goes unclaimed during
    // that window and this composer can render underneath the on-screen nav buttons.
    forceNavigationBarsPadding: Boolean = false,
) {
    // TextFieldValue (not a plain String) so entering edit mode can explicitly place the cursor
    // at the end of the message body — with a bare String value, Compose's TextField has no
    // reliable way to infer where the cursor should land on a value it's never seen before (this
    // remember block is keyed on editingMessage?.id, so it's a brand-new value each time edit
    // mode starts, not an incremental edit of what was already there), and defaults to the start.
    var text by remember(editingMessage?.id) {
        val body = editingMessage?.body.orEmpty()
        mutableStateOf(TextFieldValue(text = body, selection = TextRange(body.length)))
    }
    var showEmojiPicker by remember { mutableStateOf(false) }
    var showAttachPanel by remember { mutableStateOf(false) }
    var showSendOptionsMenu by remember { mutableStateOf(false) }
    var showScheduleSendDialog by remember { mutableStateOf(false) }
    val androidContext = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // Reactive IME-visibility check — WindowInsets.ime is backed by live inset state, so reading
    // its bottom value here (rather than LocalSoftwareKeyboardController's fire-and-forget
    // show()/hide()) recomposes whenever the system keyboard actually opens or closes.
    val isImeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val isExpanded = isImeVisible || showEmojiPicker || showAttachPanel
    LaunchedEffect(isExpanded) { onExpandedChanged(isExpanded) }
    // Reset immediately if this composer leaves composition (e.g. switching away from the
    // Messages tab) while a panel or the keyboard was open, so the host doesn't keep its nav bar
    // hidden for a tab this composer no longer belongs to.
    DisposableEffect(Unit) { onDispose { onExpandedChanged(false) } }
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        onPickerResult()
        uri?.let {
            val mimeType = androidContext.contentResolver.getType(it)
            if (mimeType?.startsWith("video/") == true) onAttachVideo(it) else onAttachImage(it)
        }
    }
    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        onPickerResult()
        uri?.let(onAttachDocument)
    }
    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) onStartRecording() }

    val cameraCapture = rememberCameraCapture(
        onImageCaptured = onAttachImage,
        onVideoCaptured = onAttachVideo,
        onPickerLaunch = onPickerLaunch,
        onPickerResult = onPickerResult,
    )

    var recordingElapsedSeconds by remember { mutableStateOf(0) }
    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingElapsedSeconds = 0
            while (true) {
                delay(1_000)
                recordingElapsedSeconds++
            }
        }
    }

    // Deliberately NO plain imePadding() here — ConsoleTabsScreen's outer Column owns that (see
    // its own comment for why keyboard-avoidance has to happen there, not here, for the whole tab
    // strip to move together). navigationBarsPadding() is normally skipped too, for the same
    // reason: ConsoleNavigationBar already reserves the system nav bar's height via
    // NavigationBarDefaults.windowInsets, so adding it here as well would double-count that inset.
    // But that only holds while the nav bar is actually visible — while it's hidden (see
    // ConsoleTabsScreen's isMessagesComposerExpanded), nothing else is left to reserve that space,
    // so this composer (or the emoji picker/attach panel below it) would otherwise render
    // underneath the system nav bar and be unreachable there. The plain keyboard doesn't need
    // this: WindowInsets.ime's value already accounts for the space behind it, imePadding() alone
    // clears the nav bar too. forceNavigationBarsPadding covers the one case that isn't otherwise
    // visible to this composer at all — a message's context overlay open elsewhere in the list
    // (see that param's own doc comment) — this was the gap behind an earlier report of the
    // composer rendering behind the system nav buttons specifically while that overlay was open.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .then(
                if (showEmojiPicker || showAttachPanel || forceNavigationBarsPadding) {
                    Modifier.navigationBarsPadding()
                } else {
                    Modifier
                },
            ),
    ) {
        val replyOrEditPreview = replyTarget?.let { it to R.string.console_home_replying_to_label }
            ?: editingMessage?.let { it to R.string.console_home_editing_label }
        if (replyOrEditPreview != null) {
            val (previewMessage, labelRes) = replyOrEditPreview
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(labelRes),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        // A currently-selected reply/edit target is always a live, non-deleted
                        // message (you can't long-press a tombstone to reply/edit it) — so a null
                        // body here only ever means "media, no text," never "deleted."
                        text = previewMessage.body ?: stringResource(R.string.console_home_reply_preview_media),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                    )
                }
                IconButton(onClick = { if (replyTarget != null) onCancelReply() else onCancelEdit() }) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.console_pin_cancel))
                }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            if (isRecording) {
                IconButton(onClick = onCancelRecording) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.console_home_cancel_recording_action))
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Mic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        text = stringResource(R.string.console_home_recording_label, formatDuration(recordingElapsedSeconds * 1_000L)),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                FilledIconButton(
                    onClick = onStopRecording,
                    shape = CircleShape,
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(Icons.Rounded.Stop, contentDescription = stringResource(R.string.console_home_stop_recording_action))
                }
            } else {
                IconButton(
                    onClick = {
                        val opening = !showEmojiPicker
                        showEmojiPicker = opening
                        if (opening) {
                            // Replaces the keyboard rather than stacking above it — clearing
                            // focus collapses the IME so the panel takes its spot instead of
                            // both eating screen space at once.
                            showAttachPanel = false
                            focusManager.clearFocus()
                            keyboardController?.hide()
                        } else {
                            keyboardController?.show()
                        }
                    },
                ) {
                    Icon(Icons.Rounded.EmojiEmotions, contentDescription = stringResource(R.string.console_home_emoji_action))
                }
                // Signal-style pill: text field, emoji button, and attach button share one
                // fully-rounded container instead of each sitting in its own square field/button.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                ) {
                    TextField(
                        value = text,
                        onValueChange = {
                            text = it
                            onTextChanged(it.text)
                        },
                        placeholder = { Text(stringResource(R.string.console_home_composer_hint)) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                            .onFocusChanged {
                                if (it.isFocused) {
                                    showEmojiPicker = false
                                    showAttachPanel = false
                                }
                            },
                        maxLines = 4,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                        ),
                    )
                    IconButton(
                        onClick = {
                            val opening = !showAttachPanel
                            showAttachPanel = opening
                            if (opening) {
                                showEmojiPicker = false
                                focusManager.clearFocus()
                                keyboardController?.hide()
                            } else {
                                keyboardController?.show()
                            }
                        },
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.console_home_attach_action))
                    }
                }
                Spacer(modifier = Modifier.width(4.dp))
                Box {
                    // FilledIconButton has no onLongClick param, so the button itself is a plain
                    // Box styled to match FilledIconButton's own look (primary container + circle
                    // shape), driven by Modifier.combinedClickable instead — same "drop the
                    // component-level click param, apply combinedClickable by hand" shape the
                    // message bubble's own Card already uses for its tap/long-press pair.
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .combinedClickable(
                                onClick = {
                                    if (text.text.isNotBlank()) {
                                        onSubmit(text.text)
                                        text = TextFieldValue("")
                                        onTextChanged("")
                                        showEmojiPicker = false
                                    } else {
                                        val hasPermission = ContextCompat.checkSelfPermission(
                                            androidContext,
                                            Manifest.permission.RECORD_AUDIO,
                                        ) == PackageManager.PERMISSION_GRANTED
                                        if (hasPermission) {
                                            onStartRecording()
                                        } else {
                                            recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                    }
                                },
                                // Only meaningful with real text typed — scheduling a voice note
                                // isn't a thing this feature covers (see the TODOS.md writeup:
                                // text-only for v1), so a long-press while the button shows the
                                // mic icon does nothing.
                                onLongClick = { if (text.text.isNotBlank()) showSendOptionsMenu = true },
                            ),
                    ) {
                        if (text.text.isNotBlank()) {
                            Icon(
                                Icons.AutoMirrored.Rounded.Send,
                                contentDescription = stringResource(R.string.console_home_send_action),
                                tint = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Icon(
                                Icons.Rounded.Mic,
                                contentDescription = stringResource(R.string.console_home_record_voice_action),
                                tint = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                    DropdownMenu(expanded = showSendOptionsMenu, onDismissRequest = { showSendOptionsMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.console_home_send_action)) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null) },
                            onClick = {
                                showSendOptionsMenu = false
                                onSubmit(text.text)
                                text = TextFieldValue("")
                                onTextChanged("")
                                showEmojiPicker = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.console_home_schedule_send_action)) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.ScheduleSend, contentDescription = null) },
                            onClick = {
                                showSendOptionsMenu = false
                                showScheduleSendDialog = true
                            },
                        )
                    }
                }
            }
        }

        if (showScheduleSendDialog) {
            ScheduleSendDialog(
                onDismiss = { showScheduleSendDialog = false },
                onConfirm = { scheduledAtEpochMillis ->
                    onSchedule(text.text, scheduledAtEpochMillis)
                    text = TextFieldValue("")
                    onTextChanged("")
                    showEmojiPicker = false
                    showScheduleSendDialog = false
                },
            )
        }

        // Below the input row, WhatsApp/Signal-style — was above it before. Wrapped in
        // AnimatedVisibility (rather than a plain `if`) so it expands/collapses in place instead
        // of popping in abruptly; the shared COMPOSER_PANEL_ANIMATION_MILLIS duration keeps this
        // in sync with ConsoleTabsScreen's bottom nav bar collapsing away at the same time.
        AnimatedVisibility(
            visible = showEmojiPicker,
            enter = expandVertically(animationSpec = tween(COMPOSER_PANEL_ANIMATION_MILLIS)),
            exit = shrinkVertically(animationSpec = tween(COMPOSER_PANEL_ANIMATION_MILLIS)),
        ) {
            EmojiPickerPanel(
                recentEmojis = recentEmojis,
                onEmojiSelected = { emoji ->
                    val newText = text.text + emoji
                    text = TextFieldValue(text = newText, selection = TextRange(newText.length))
                    onTextChanged(newText)
                },
                onEmojiUsed = onEmojiUsed,
            )
        }
        AnimatedVisibility(
            visible = showAttachPanel,
            enter = expandVertically(animationSpec = tween(COMPOSER_PANEL_ANIMATION_MILLIS)),
            exit = shrinkVertically(animationSpec = tween(COMPOSER_PANEL_ANIMATION_MILLIS)),
        ) {
            AttachPanel(
                onGalleryClick = {
                    showAttachPanel = false
                    onPickerLaunch()
                    imagePickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                },
                onCameraClick = {
                    showAttachPanel = false
                    cameraCapture.launchCamera()
                },
                onRecordVideoClick = {
                    showAttachPanel = false
                    cameraCapture.launchVideoCamera()
                },
                onDocumentClick = {
                    showAttachPanel = false
                    onPickerLaunch()
                    documentPickerLauncher.launch(arrayOf("*/*"))
                },
                onPollClick = {
                    showAttachPanel = false
                    onCreatePoll()
                },
            )
        }
    }
}

/** How far in the future a Schedule Send must land, at minimum — comfortably clears the server's
 * own 60s sweep interval, so nothing scheduled reads as "basically immediate" (see the TODOS.md
 * writeup this was built from). Re-checked live against the actual current time (not just as the
 * time picker's own floor, which Material3's bare [TimePicker] has no date/time-of-day concept at
 * all — it only ever picks an hour/minute) so leaving the dialog open past the original floor
 * doesn't let a stale selection slip through unnoticed. */
private const val MIN_SCHEDULE_LEAD_MILLIS = 15 * 60 * 1000L

/** Schedule Send's date+time picker, reached via the composer Send button's long-press menu.
 * Date floor is today (in the user's local calendar — see [todayFloorUtcMillis]'s own comment for
 * why that needs care with Material3's UTC-based [DatePicker]); time floor is
 * [MIN_SCHEDULE_LEAD_MILLIS] from now. The date picker only blocks picking a date before today, and
 * Material3's bare [TimePicker] has no floor concept of its own at all — so today's date combined
 * with an earlier time is fully pickable through the pickers themselves; the combined result is
 * validated live instead (`isTooSoon` below), disabling Schedule and showing why, rather than
 * silently snapping an invalid pick to "now" the way an earlier version of this dialog did. Reuses
 * [AppTimePickerDialog]/[Long.withDate]/[Long.withTime], the same Material3 time-picker wrapper
 * Calendar's own `EventEditorDialog` uses for event start/end times — Material3 ships no
 * ready-made `TimePickerDialog` of its own. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleSendDialog(
    onDismiss: () -> Unit,
    onConfirm: (scheduledAtEpochMillis: Long) -> Unit,
) {
    val context = LocalContext.current
    var selectedEpochMillis by remember { mutableStateOf(System.currentTimeMillis() + MIN_SCHEDULE_LEAD_MILLIS) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val dateFormatter = remember(context) { DateFormat.getMediumDateFormat(context) }
    val timeFormatter = remember(context) { DateFormat.getTimeFormat(context) }
    // Recomputed on every recomposition (cheap, no ticking timer needed) — freshly reflects
    // whether the currently-picked date+time combination is still far enough out, right after
    // picking a new date or time, and again at the moment Schedule is actually tapped.
    val isTooSoon = selectedEpochMillis < System.currentTimeMillis() + MIN_SCHEDULE_LEAD_MILLIS

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.console_home_schedule_dialog_title)) },
        text = {
            Column {
                ScheduleFieldRow(
                    label = stringResource(R.string.console_home_schedule_date_action),
                    value = dateFormatter.format(Date(selectedEpochMillis)),
                    icon = Icons.Rounded.Event,
                    onClick = { showDatePicker = true },
                )
                ScheduleFieldRow(
                    label = stringResource(R.string.console_home_schedule_time_action),
                    value = timeFormatter.format(Date(selectedEpochMillis)),
                    icon = Icons.Rounded.Schedule,
                    onClick = { showTimePicker = true },
                )
                if (isTooSoon) {
                    Text(
                        text = stringResource(R.string.console_home_schedule_too_soon_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selectedEpochMillis) },
                enabled = !isTooSoon,
            ) {
                Text(stringResource(R.string.console_home_schedule_confirm_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.console_pin_cancel)) }
        },
    )

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedEpochMillis,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis >= todayFloorUtcMillis()
            },
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { selectedEpochMillis = selectedEpochMillis.withDate(it) }
                        showDatePicker = false
                    },
                ) { Text(stringResource(R.string.console_pin_continue)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.console_pin_cancel)) }
            },
        ) { DatePicker(state = datePickerState) }
    }

    if (showTimePicker) {
        AppTimePickerDialog(
            initialEpochMillis = selectedEpochMillis,
            onDismissRequest = { showTimePicker = false },
            onConfirm = { hour, minute ->
                selectedEpochMillis = selectedEpochMillis.withTime(hour, minute)
                showTimePicker = false
            },
        )
    }
}

/** Today's local calendar date, expressed as the UTC-midnight epoch millis Material3's
 * [DatePicker]/[SelectableDates] actually compares against — its grid operates in UTC regardless
 * of device timezone (a documented Material3 design choice, to sidestep DST arithmetic), so
 * comparing against a plain local-timezone `Calendar` floor would be off by the local UTC offset
 * near a day boundary. [java.time.LocalDate.now] with an explicit [java.time.ZoneOffset.UTC]
 * anchor is the standard pattern for this. */
private fun todayFloorUtcMillis(): Long =
    java.time.LocalDate.now().atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()

@Composable
private fun ScheduleFieldRow(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(modifier = Modifier.padding(start = 16.dp)) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = value, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/** WhatsApp-style separator between groups of messages sent on different calendar days —
 * rendered in the message list wherever a message's day differs from the one before it. */
@Composable
private fun DateDivider(epochMillis: Long) {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Text(
            text = dateDividerLabel(epochMillis),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/** "Today"/"Yesterday" for the last 2 days, the weekday name for the rest of the past week, and
 * a locale-formatted date beyond that — same day-bucket boundaries WhatsApp itself uses. */
@Composable
private fun dateDividerLabel(epochMillis: Long): String {
    val context = LocalContext.current
    val now = System.currentTimeMillis()
    return when {
        isSameDay(epochMillis, now) -> stringResource(R.string.console_home_date_divider_today)
        isSameDay(epochMillis, now - DateUtils.DAY_IN_MILLIS) -> stringResource(R.string.console_home_date_divider_yesterday)
        now - epochMillis < 6 * DateUtils.DAY_IN_MILLIS -> DateFormat.format("EEEE", Date(epochMillis)).toString()
        else -> DateFormat.getDateFormat(context).format(Date(epochMillis))
    }
}

internal fun isSameDay(epochMillisA: Long, epochMillisB: Long): Boolean {
    val calendarA = Calendar.getInstance().apply { timeInMillis = epochMillisA }
    val calendarB = Calendar.getInstance().apply { timeInMillis = epochMillisB }
    return calendarA.get(Calendar.YEAR) == calendarB.get(Calendar.YEAR) &&
        calendarA.get(Calendar.DAY_OF_YEAR) == calendarB.get(Calendar.DAY_OF_YEAR)
}

/** Formats a duration for both voice-note playback labels and the live recording ticker. */
private fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1_000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
}

