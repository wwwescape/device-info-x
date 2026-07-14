package com.wwwescape.deviceinfox.console.ui.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wwwescape.deviceinfox.console.data.messaging.ConsoleMessage
import com.wwwescape.deviceinfox.console.data.messaging.MessageAttachment
import com.wwwescape.deviceinfox.console.data.messaging.MessageJumpRequester
import com.wwwescape.deviceinfox.console.data.messaging.MessageRepository
import com.wwwescape.deviceinfox.console.data.messaging.ScheduledMessageRepository
import com.wwwescape.deviceinfox.console.data.messaging.VoicePlayer
import com.wwwescape.deviceinfox.console.data.location.LiveLocationRepository
import com.wwwescape.deviceinfox.console.data.messaging.VoiceRecorder
import com.wwwescape.deviceinfox.console.data.network.ConsoleApiException
import com.wwwescape.deviceinfox.console.data.pairing.PairingRepository
import com.wwwescape.deviceinfox.console.data.pairing.PairingStatus
import com.wwwescape.deviceinfox.console.data.presence.PresenceRepository
import com.wwwescape.deviceinfox.console.data.release.ReleaseRepository
import com.wwwescape.deviceinfox.console.data.settings.ConsoleSettingsRepository
import com.wwwescape.deviceinfox.console.data.vault.VaultRepository
import com.wwwescape.deviceinfox.console.session.ConsoleSessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Matches the server's `presence_service.ONLINE_PING_COOLDOWN` — kept in sync by hand since the
 * client only mirrors this for instant UI feedback; the server enforces the real limit. */
private const val ONLINE_PING_COOLDOWN_MILLIS = 15 * 60 * 1000L

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val messageRepository: MessageRepository,
    private val pairingRepository: PairingRepository,
    private val presenceRepository: PresenceRepository,
    releaseRepository: ReleaseRepository,
    liveLocationRepository: LiveLocationRepository,
    private val vaultRepository: VaultRepository,
    private val consoleSettingsRepository: ConsoleSettingsRepository,
    private val sessionManager: ConsoleSessionManager,
    private val voiceRecorder: VoiceRecorder,
    private val voicePlayer: VoicePlayer,
    private val messageJumpRequester: MessageJumpRequester,
    private val scheduledMessageRepository: ScheduledMessageRepository,
) : ViewModel() {

    private val _imageExportedEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val imageExportedEvent: SharedFlow<Unit> = _imageExportedEvent.asSharedFlow()

    private val _videoExportedEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val videoExportedEvent: SharedFlow<Unit> = _videoExportedEvent.asSharedFlow()

    private val _documentExportedEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val documentExportedEvent: SharedFlow<Unit> = _documentExportedEvent.asSharedFlow()

    /** Fires when the *partner's* device wipes the conversation (Settings → Messages → Delete
     * data, on their end) — this device's own screen already updates reactively via Room, this
     * is purely the one-off "your partner did this" Toast. */
    val conversationWipedEvent: SharedFlow<Unit> = messageRepository.conversationWipedEvent

    /** Drives the plain notification dot on the Settings gear icon (`HomeScreen.kt`'s top bar) —
     * see [ReleaseRepository]'s own doc comment for when/how this actually gets checked. */
    val isUpdateAvailable: StateFlow<Boolean> = releaseRepository.isUpdateAvailable

    /** Takes precedence over [isUpdateAvailable] on the same badge dot — true while *either*
     * partner currently has Live Location Sharing enabled (checking either side, not just self,
     * since the whole point is surfacing "something live is happening" regardless of who started
     * it). */
    val isLiveLocationActive: StateFlow<Boolean> = combine(
        liveLocationRepository.selfSharing,
        liveLocationRepository.partnerLocation,
    ) { self, partner -> self || partner.isSharing }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** `true` when the tap actually created a new locker item, `false` when that message's
     * attachment was already saved (see `VaultRepository.saveMessageAttachmentToVault`'s dedupe
     * check) — `HomeScreen` shows a different toast for each rather than silently no-op'ing the
     * second case, which used to look identical to nothing having happened. */
    private val _vaultSaveEvent = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    val vaultSaveEvent: SharedFlow<Boolean> = _vaultSaveEvent.asSharedFlow()

    /** Signals the *next* failed mutation to the UI — previously every network-backed call here
     * silently swallowed its exception via a bare `runCatching`, so a failed send/attach looked
     * identical to nothing having been tapped at all. Null means "no server-provided detail,
     * show a generic network-error message" (a timeout/IOException, not a
     * [com.wwwescape.deviceinfox.console.data.network.ConsoleApiException]) — resolving that
     * generic string is left to the UI layer (`stringResource`) rather than this ViewModel
     * holding a `Context`. [launchCatching] is what every mutation below now goes through. */
    private val _errorEvent = MutableSharedFlow<String?>(extraBufferCapacity = 1)
    val errorEvent: SharedFlow<String?> = _errorEvent.asSharedFlow()

    private fun launchCatching(block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }.onFailure { e ->
                _errorEvent.tryEmit((e as? ConsoleApiException)?.detail)
            }
        }
    }

    /** Backs the full-screen "Loading…" state shown on cold start, before the first message page
     * has come back — see [MessageRepository.isInitialSyncing]'s doc comment for why this only
     * ever fires once per session, not on every reconnect. Exposed directly rather than through
     * [uiState]'s combine so `HomeScreen` can gate its *entire* content on this, including the
     * composer — folding it into [HomeUiState] would mean briefly rendering a real (empty)
     * `HomeUiState` before this flips, which is exactly the flash this is meant to prevent. */
    val isInitialSyncing: StateFlow<Boolean> = messageRepository.isInitialSyncing

    /** Backs the "Loading…" shown under the partner's name until their real Here/Online/Last-seen
     * status is known — see [PresenceRepository.isInitialPresenceLoading]'s own doc comment. Not
     * folded into [uiState]'s combine for the same 5-flow-cap reason [isLoadingOlderMessages]
     * below isn't either. */
    val isInitialPresenceLoading: StateFlow<Boolean> = presenceRepository.isInitialPresenceLoading
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    // Not folded into HomeUiState/its combine — that combine is already at the 5-flow direct-
    // overload max (see the comment on it below), so this mirrors isRecording/recentEmojis
    // instead: plain top-level StateFlows, collected separately in HomeScreen.
    private val _isLoadingOlderMessages = MutableStateFlow(false)
    val isLoadingOlderMessages: StateFlow<Boolean> = _isLoadingOlderMessages.asStateFlow()

    private val _hasMoreOlderMessages = MutableStateFlow(true)
    val hasMoreOlderMessages: StateFlow<Boolean> = _hasMoreOlderMessages.asStateFlow()

    /** Set once [jumpToMessage] confirms the target is loaded locally — `HomeScreen` animate-
     * scrolls to it and then calls [consumeScrollRequest] so the same id doesn't re-fire the
     * scroll on an unrelated recomposition. */
    private val _scrollToMessageId = MutableStateFlow<String?>(null)
    val scrollToMessageId: StateFlow<String?> = _scrollToMessageId.asStateFlow()

    fun consumeScrollRequest() {
        _scrollToMessageId.value = null
    }

    /** A jump target that's still missing after local history is fully paginated in — e.g. it was
     * "Delete for me"-hidden on this specific device only. Distinct from [errorEvent]: this isn't
     * a network failure, so it doesn't belong in that channel's generic "network error" fallback
     * text. */
    private val _jumpTargetNotFoundEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val jumpTargetNotFoundEvent: SharedFlow<Unit> = _jumpTargetNotFoundEvent.asSharedFlow()

    /** Triggered by scrolling to the top of the Messages list (see `HomeScreen`'s
     * `snapshotFlow` on `firstVisibleItemIndex`). Doesn't reuse [launchCatching] verbatim since
     * that helper doesn't surface the block's return value (needed here to update
     * [hasMoreOlderMessages]) or give a hook to clear [isLoadingOlderMessages] afterward — but it
     * reuses the same [_errorEvent] channel on failure, so a failed page load surfaces exactly
     * like every other failure here (`HomeScreen`'s existing `errorEvent` Toast collector). */
    fun loadOlderMessages() {
        if (_isLoadingOlderMessages.value || !_hasMoreOlderMessages.value) return
        _isLoadingOlderMessages.value = true
        viewModelScope.launch {
            runCatching { messageRepository.loadOlderMessages() }
                .onSuccess { hasMore -> _hasMoreOlderMessages.value = hasMore }
                .onFailure { e -> _errorEvent.tryEmit((e as? ConsoleApiException)?.detail) }
            _isLoadingOlderMessages.value = false
        }
    }

    /** Null once 15 minutes have passed since the last tap (or if it's never been tapped this
     * install) — `HomeScreen` disables the header icon while `now < this`. */
    val onlinePingAvailableAtEpochMillis: StateFlow<Long?> = consoleSettingsRepository.onlinePingLastSentAtEpochMillis
        .map { lastSent -> lastSent?.plus(ONLINE_PING_COOLDOWN_MILLIS) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The Messages header icon's "let them know you're online" ping. The cooldown timestamp is
     * set the moment this is *tapped*, not once the network call actually succeeds — that's what
     * disables the icon immediately (rather than leaving it tappable for the round-trip duration)
     * and is what the feature's own "once it's tapped, it needs to be disabled" spec calls for.
     * The 15-minute cooldown is still really enforced server-side (see
     * [MessageRepository.sendOnlinePing]'s doc comment); this local timestamp is only ever a UI
     * convenience that can be a little stale, never the source of truth. */
    fun sendOnlinePing() {
        viewModelScope.launch {
            consoleSettingsRepository.setOnlinePingLastSentAtEpochMillis(System.currentTimeMillis())
            runCatching { messageRepository.sendOnlinePing() }
                .onFailure { e -> _errorEvent.tryEmit((e as? ConsoleApiException)?.detail) }
        }
    }

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    val playingVoiceNotePath: StateFlow<String?> = voicePlayer.playingFilePath

    /** Drives the voice-note bubble's progress ring/elapsed-timestamp — see [VoicePlayer.activeFilePath]'s
     * own doc comment for why this is a separate flow from [playingVoiceNotePath] rather than
     * folded into it. */
    val activeVoiceNotePath: StateFlow<String?> = voicePlayer.activeFilePath
    val voiceNoteProgress: StateFlow<Float> = voicePlayer.progress

    val recentEmojis: StateFlow<List<String>> = consoleSettingsRepository.recentEmojis
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Bumps [emoji] to the front of [recentEmojis] — called on every tap in [EmojiPickerPanel],
     * grid or skin-tone popup alike. */
    fun onEmojiUsed(emoji: String) {
        viewModelScope.launch { consoleSettingsRepository.recordEmojiUsed(emoji) }
    }

    private val _replyTarget = MutableStateFlow<ConsoleMessage?>(null)
    private val _editingMessage = MutableStateFlow<ConsoleMessage?>(null)
    private val _isSearching = MutableStateFlow(false)
    private val _searchQuery = MutableStateFlow("")

    /** Selection mode is derived (non-empty set), not a separate flag — the instant the last
     * item is deselected, [HomeUiState.isSelectionMode] flips false on its own. */
    private val _selectedMessageIds = MutableStateFlow<Set<String>>(emptySet())

    private data class ComposerState(
        val replyTarget: ConsoleMessage?,
        val editingMessage: ConsoleMessage?,
        val isSearching: Boolean,
        val searchQuery: String,
    )

    // kotlinx.coroutines.flow.combine has direct overloads only up to 5 flows; nesting here
    // (rather than the untyped vararg overload) keeps every branch type-safe.
    private val composerState = combine(
        _replyTarget,
        _editingMessage,
        _isSearching,
        _searchQuery,
    ) { replyTarget, editingMessage, isSearching, searchQuery ->
        ComposerState(replyTarget, editingMessage, isSearching, searchQuery)
    }

    val uiState: StateFlow<HomeUiState> = combine(
        messageRepository.conversation,
        pairingRepository.pairingStatus,
        presenceRepository.partnerPresence,
        composerState,
        _selectedMessageIds,
    ) { messages, pairingStatus, partnerPresence, composer, selectedMessageIds ->
        HomeUiState(
            messages = messages,
            partnerDisplayName = (pairingStatus as? PairingStatus.Paired)?.partnerDisplayName,
            partnerPresence = partnerPresence,
            replyTarget = composer.replyTarget,
            editingMessage = composer.editingMessage,
            isSearching = composer.isSearching,
            searchQuery = composer.searchQuery,
            selectedMessageIds = selectedMessageIds,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /** Mirrors [enterMessagesScreen]/[leaveMessagesScreen] locally so [init] below can gate
     * read-marking on it — separate from telling the *partner's* device via [presenceRepository]
     * (the "Here" tier), since this is purely an internal signal for this ViewModel's own use. */
    private val _isInMessagesScreen = MutableStateFlow(false)

    init {
        // Was a single `markIncomingAsRead()` call here and nowhere else — correct for the very
        // first visit, but since this ViewModel survives tab switches (`ConsoleTabsScreen`'s
        // saveState/restoreState), it never fired again for the rest of the session. Any message
        // that arrived *after* that first visit — including while the user was actively looking
        // at this exact screen the whole time — never got marked read client-side, so the sender
        // never received a `message.read` event and their tick never flipped blue. Replaced with
        // a collector that re-runs `markIncomingAsRead()` on every conversation change while
        // [_isInMessagesScreen] is true (and stops entirely while it's false, via `flatMapLatest`
        // switching to [emptyFlow] — so leaving the screen doesn't leave a stray collector
        // silently marking messages read the user never actually saw). `markIncomingAsRead()`
        // itself already filters to currently-unread messages, so redundant invocations (e.g. a
        // reaction/pin/star change also re-emits the same Room `Flow`) are cheap no-ops.
        viewModelScope.launch {
            _isInMessagesScreen.flatMapLatest { inScreen ->
                if (inScreen) messageRepository.conversation else emptyFlow()
            }.collect { runCatching { messageRepository.markIncomingAsRead() } }
        }
        // The Starred/Pinned screens' "jump back into the conversation" tap — see
        // MessageJumpRequester's own doc comment for why this indirection exists. Runs in
        // viewModelScope, independent of whether HomeScreen is currently composed/collecting
        // anything (it isn't, while one of those screens is what's on screen), which is exactly
        // why performJump reads messageRepository.conversation directly rather than uiState.
        viewModelScope.launch {
            messageJumpRequester.requests.collect { messageId -> performJump(messageId) }
        }
    }

    /** Jump to [messageId] within the conversation — shared entry point for two triggers: a tap
     * on a reply-preview snippet inside a bubble (same screen, `HomeScreen` calls this directly,
     * WhatsApp/Signal-style) and a tap in the Starred/Pinned screens (relayed via
     * [MessageJumpRequester], see [init]'s collector above). */
    fun jumpToMessage(messageId: String) {
        viewModelScope.launch { performJump(messageId) }
    }

    /** Backing [scrollToMessageId] — pages backward via [MessageRepository.loadOlderMessages]
     * until [messageId] is present locally (it may be older than what's currently paginated in)
     * or local history is confirmed to have no more pages. Deliberately reads
     * [MessageRepository.conversation] directly rather than [uiState]: [uiState] is a
     * `stateIn(WhileSubscribed(5_000))` that only stays live while something is actually
     * collecting it (normally `HomeScreen`), which isn't the case while the Starred/Pinned screen
     * that triggers this is what's on screen — `conversation` itself is a plain Flow over Room's
     * own always-live, invalidation-tracked query, so it reflects newly-paginated-in rows
     * immediately regardless of who else is subscribed.
     *
     * Shares [_isLoadingOlderMessages]/[_hasMoreOlderMessages] with the scroll-to-top pagination
     * trigger rather than tracking its own — same cursor (`MessageDao.oldestMessageTimestamp`),
     * so two independent pagination loops running at once would race each other against it. If
     * the scroll-triggered one is already in flight, this waits for it to finish and re-checks
     * for the target rather than firing a redundant concurrent request. */
    private suspend fun performJump(messageId: String) {
        while (messageRepository.conversation.first().none { it.id == messageId }) {
            if (_isLoadingOlderMessages.value) {
                _isLoadingOlderMessages.first { isLoading -> !isLoading }
                continue
            }
            if (!_hasMoreOlderMessages.value) {
                _jumpTargetNotFoundEvent.tryEmit(Unit)
                return
            }
            _isLoadingOlderMessages.value = true
            val hasMore = runCatching { messageRepository.loadOlderMessages() }
                .onFailure { e -> _errorEvent.tryEmit((e as? ConsoleApiException)?.detail) }
                .getOrDefault(false)
            _hasMoreOlderMessages.value = hasMore
            _isLoadingOlderMessages.value = false
        }
        _scrollToMessageId.value = messageId
    }

    /** Sends new text, or — if [HomeUiState.editingMessage] is set — commits an edit to that
     * message instead. Either way, clears whichever of reply/edit state was active.
     *
     * Every network-backed mutation in this ViewModel goes through [launchCatching] (Phase 11,
     * error visibility added after a failed send/attach was found to look identical to nothing
     * having happened at all — a failed call no longer just silently no-ops). */
    fun submitComposerText(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val editing = _editingMessage.value
        launchCatching {
            if (editing != null) {
                messageRepository.editText(editing.id, trimmed)
            } else {
                messageRepository.sendText(trimmed, replyToId = _replyTarget.value?.id)
            }
        }
        _replyTarget.value = null
        _editingMessage.value = null
    }

    /** Fired once a Schedule Send round-trips successfully — `HomeScreen` shows a one-off toast
     * confirmation off this, the same "clears immediately, confirms once it's actually staged"
     * shape [submitComposerText] doesn't need (a normal send's own bubble appearing is
     * confirmation enough). */
    private val _messageScheduledEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val messageScheduledEvent: SharedFlow<Unit> = _messageScheduledEvent.asSharedFlow()

    /** Stages [text] to send at [scheduledAtEpochMillis] instead of sending it now — no
     * placeholder bubble in the chat, nothing appears in the conversation until the server's own
     * sweep (or an on-demand "Send Now" from Settings → Scheduled Messages) actually delivers it.
     * Reply/edit context isn't carried over — Schedule Send is only ever offered from the plain
     * composer state, not while replying to or editing an existing message. */
    fun scheduleMessage(text: String, scheduledAtEpochMillis: Long) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        launchCatching {
            scheduledMessageRepository.create(trimmed, scheduledAtEpochMillis)
            _messageScheduledEvent.tryEmit(Unit)
        }
    }

    fun startReply(message: ConsoleMessage) {
        _editingMessage.value = null
        _replyTarget.value = message
    }

    fun cancelReply() {
        _replyTarget.value = null
    }

    fun startEdit(message: ConsoleMessage) {
        _replyTarget.value = null
        _editingMessage.value = message
    }

    fun cancelEdit() {
        _editingMessage.value = null
    }

    fun deleteMessage(message: ConsoleMessage) {
        launchCatching { messageRepository.deleteMessage(message.id) }
    }

    /** "Delete for me" — see `MessageRepository.hideForMe`'s doc comment. Purely local, so this
     * doesn't need [launchCatching]'s network-failure handling — nothing here can fail the way a
     * server call can. */
    fun hideForMe(message: ConsoleMessage) {
        viewModelScope.launch { messageRepository.hideForMe(message.id) }
    }

    fun toggleStar(message: ConsoleMessage) {
        launchCatching { messageRepository.setStarred(message.id, !message.isStarred) }
    }

    fun togglePin(message: ConsoleMessage) {
        launchCatching { messageRepository.setPinned(message.id, !message.isPinned) }
    }

    /** Entry point into selection mode — only reachable via a message's own "Select" menu item
     * (deliberately not long-press, which already opens the reaction picker; see the plan's
     * reasoning for not repurposing an existing gesture). */
    fun enterSelection(messageId: String) {
        _selectedMessageIds.value = setOf(messageId)
    }

    fun toggleSelection(messageId: String) {
        _selectedMessageIds.update { current ->
            if (messageId in current) current - messageId else current + messageId
        }
    }

    fun clearSelection() {
        _selectedMessageIds.value = emptySet()
    }

    fun bulkDeleteForMe() {
        val ids = _selectedMessageIds.value
        viewModelScope.launch {
            messageRepository.hideForMeBulk(ids.toList())
            clearSelection()
        }
    }

    /** Loops the existing single-message endpoint per selected id — same tolerant-loop shape as
     * [MessageRepository.markIncomingAsRead], since there's no bulk star/pin endpoint and this
     * app's scale (one couple) doesn't need one. One failure doesn't block the rest; if anything
     * failed, [errorEvent] fires once at the end rather than per item. */
    fun bulkSetStarred(starred: Boolean) {
        val ids = _selectedMessageIds.value
        viewModelScope.launch {
            val results = ids.map { id -> runCatching { messageRepository.setStarred(id, starred) } }
            if (results.any { it.isFailure }) _errorEvent.tryEmit(null)
            clearSelection()
        }
    }

    fun bulkSetPinned(pinned: Boolean) {
        val ids = _selectedMessageIds.value
        viewModelScope.launch {
            val results = ids.map { id -> runCatching { messageRepository.setPinned(id, pinned) } }
            if (results.any { it.isFailure }) _errorEvent.tryEmit(null)
            clearSelection()
        }
    }

    /** Tapping the same emoji self already reacted with removes it — a toggle, not a
     * stack of reactions. */
    fun onReactionSelected(message: ConsoleMessage, emoji: String) {
        launchCatching {
            if (message.myReaction == emoji) {
                messageRepository.removeReaction(message.id)
            } else {
                messageRepository.setReaction(message.id, emoji)
            }
        }
    }

    /** Reports self's typing state over the WebSocket every time the composer text changes. */
    fun onComposerTextChanged(text: String) {
        viewModelScope.launch { presenceRepository.reportSelfTyping(text.isNotBlank()) }
    }

    /** Drives the "Here" presence tier the partner sees, and (via [_isInMessagesScreen]) this
     * device's own live read-marking above. Called from a `DisposableEffect` in `HomeScreen`
     * rather than `init`/`onCleared` — this ViewModel survives tab switches (see
     * `ConsoleTabsScreen`'s `saveState`/`restoreState`), so it has no "just entered/left the
     * Messages tab" moment of its own; the screen re-entering/leaving composition is the only
     * signal that actually correlates with the user looking at this tab. */
    fun enterMessagesScreen() {
        _isInMessagesScreen.value = true
        viewModelScope.launch { presenceRepository.reportSelfInMessagesScreen(true) }
    }

    fun leaveMessagesScreen() {
        _isInMessagesScreen.value = false
        viewModelScope.launch { presenceRepository.reportSelfInMessagesScreen(false) }
    }

    fun toggleSearch() {
        _isSearching.value = !_isSearching.value
        _searchQuery.value = ""
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun attachImage(uri: Uri) {
        val replyToId = _replyTarget.value?.id
        launchCatching { messageRepository.sendImage(uri, replyToId) }
        _replyTarget.value = null
    }

    /** Same shape as [attachImage] — used by both the gallery picker (once it also allows
     * video) and the in-app "record video" camera button. */
    fun attachVideo(uri: Uri) {
        val replyToId = _replyTarget.value?.id
        launchCatching { messageRepository.sendVideo(uri, replyToId) }
        _replyTarget.value = null
    }

    /** Same shape as [attachImage]/[attachVideo] — wired to the "Document" attach option. */
    fun attachDocument(uri: Uri) {
        val replyToId = _replyTarget.value?.id
        launchCatching { messageRepository.sendDocument(uri, replyToId) }
        _replyTarget.value = null
    }

    /** Wired to the "Location" attach option, after the composer's own permission-check + one-shot
     * fetch + confirm-dialog flow already resolved a coordinate pair — same shape as [attachImage]
     * otherwise. */
    fun attachLocation(lat: Double, lng: Double) {
        val replyToId = _replyTarget.value?.id
        launchCatching { messageRepository.sendLocation(lat, lng, replyToId) }
        _replyTarget.value = null
    }

    /** Wired to the "Polls" attach option's own creation dialog, not the attach panel directly —
     * unlike the others above, there's a form to fill in first rather than an immediate picker
     * result. */
    fun sendPoll(question: String, options: List<String>, allowsMultiple: Boolean) {
        val replyToId = _replyTarget.value?.id
        launchCatching { messageRepository.sendPoll(question, options, allowsMultiple, replyToId) }
        _replyTarget.value = null
    }

    /** Message ids with a vote request currently in flight — see [votePoll]'s own doc comment for
     * why this exists. `HomeScreen` disables further taps on that message's poll while its id is
     * in here (see `PollOptionsList`'s `isVotePending`). */
    private val _pendingPollVoteMessageIds = MutableStateFlow<Set<String>>(emptySet())
    val pendingPollVoteMessageIds: StateFlow<Set<String>> = _pendingPollVoteMessageIds.asStateFlow()

    /** [votePoll] always sends the voter's *complete* new selection, recomputed by the caller from
     * whatever poll state this device currently has displayed (see `PollVoteUpdate`'s own doc
     * comment server-side) — not an incremental toggle. That makes back-to-back taps on the same
     * poll genuinely racy: a second tap fired before the first request's response has been applied
     * locally recomputes its own "complete selection" from that same still-stale baseline, and
     * whichever response happens to land last silently wins, with no error on either side (both
     * requests are individually valid). Reported symptom: a tapped option visually selects, then a
     * few seconds later reverts on its own, and only "sticks" after several retaps once the
     * flip-flopping settles.
     *
     * Guarding re-entry here — rather than trying to reconcile racing responses after the fact —
     * closes the window entirely: a second tap on a message already mid-vote is simply dropped, so
     * every request the server ever sees was computed from state the device had actually already
     * confirmed. Doesn't reuse [launchCatching] verbatim since that helper gives no hook to clear
     * the pending flag once the call finishes, same reasoning [loadOlderMessages] and
     * [ensureVideoDownloaded] already don't either — but it reuses the same [_errorEvent] channel
     * on failure. */
    fun votePoll(message: ConsoleMessage, optionIds: List<String>) {
        if (message.id in _pendingPollVoteMessageIds.value) return
        _pendingPollVoteMessageIds.update { it + message.id }
        viewModelScope.launch {
            runCatching { messageRepository.votePoll(message.id, optionIds) }
                .onFailure { e -> _errorEvent.tryEmit((e as? ConsoleApiException)?.detail) }
            _pendingPollVoteMessageIds.update { it - message.id }
        }
    }

    /** Same re-entry-guard shape as [votePoll] — a second tap on Close before the first request
     * resolves would just be a redundant no-op server-side, but guarding it avoids a spurious
     * error toast from that second call landing after the poll's already closed. */
    private val _pendingClosePollMessageIds = MutableStateFlow<Set<String>>(emptySet())
    val pendingClosePollMessageIds: StateFlow<Set<String>> = _pendingClosePollMessageIds.asStateFlow()

    fun closePoll(message: ConsoleMessage) {
        if (message.id in _pendingClosePollMessageIds.value) return
        _pendingClosePollMessageIds.update { it + message.id }
        viewModelScope.launch {
            runCatching { messageRepository.closePoll(message.id) }
                .onFailure { e -> _errorEvent.tryEmit((e as? ConsoleApiException)?.detail) }
            _pendingClosePollMessageIds.update { it - message.id }
        }
    }

    /** Message ids with a Save-to-Vault request currently in flight — same re-entry-guard shape
     * as [votePoll], closing the exact race that let a double-tap on the media viewer's
     * "Save to Safe Locker" icon create two duplicate Vault items (the fire-and-forget call had
     * no guard at all, and `VaultRepository.saveAttachmentToVault`'s own dedupe check races
     * itself when invoked twice concurrently). */
    private val _pendingVaultSaveMessageIds = MutableStateFlow<Set<String>>(emptySet())
    val pendingVaultSaveMessageIds: StateFlow<Set<String>> = _pendingVaultSaveMessageIds.asStateFlow()

    fun exportImage(attachment: MessageAttachment, destinationUri: Uri) {
        launchCatching {
            messageRepository.exportImage(attachment, destinationUri)
            _imageExportedEvent.tryEmit(Unit)
        }
    }

    /** Unlike [exportImage] (always already local), a video's bytes may not be downloaded yet —
     * `MediaPagerDialog` (unlike `VideoPreviewDialog`, which pre-resolves via
     * [ensureVideoDownloaded] before its own Export button is even enabled) has no local-path
     * state of its own to guard on, so this always ensures the download itself first, mirroring
     * [exportDocument]'s shape. No-ops quickly if already local. */
    fun exportVideo(messageId: String, attachment: MessageAttachment, destinationUri: Uri) {
        launchCatching {
            val localPath = messageRepository.downloadVideoIfNeeded(messageId, attachment)
            messageRepository.exportVideo(attachment.copy(localVideoFilePath = localPath), destinationUri)
            _videoExportedEvent.tryEmit(Unit)
        }
    }

    /** Suspend rather than fire-and-forget like [exportImage] — `VideoPreviewDialog` needs the
     * resulting local path in hand (to hand off to the player), and needs to show its own
     * loading state while this is in flight, so it calls this directly from a `LaunchedEffect`
     * rather than going through [launchCatching]'s event-only shape. No-ops quickly if the video
     * is already local (sender's own send, or already played once this session). */
    suspend fun ensureVideoDownloaded(messageId: String, attachment: MessageAttachment): String? =
        runCatching { messageRepository.downloadVideoIfNeeded(messageId, attachment) }
            .onFailure { e -> _errorEvent.tryEmit((e as? ConsoleApiException)?.detail) }
            .getOrNull()

    /** Export is one combined fire-and-forget action triggered from `PdfPreviewDialog`'s own
     * Export button: ensure the bytes are local (lazily fetching them if this is the first tap,
     * a quick no-op if [ensureDocumentDownloaded] already ran for the preview), then copy them
     * to [destinationUri], then toast. Mirrors [exportImage]'s "just toast when done" simplicity. */
    fun exportDocument(messageId: String, attachment: MessageAttachment, destinationUri: Uri) {
        launchCatching {
            val localPath = messageRepository.downloadDocumentIfNeeded(messageId, attachment)
            messageRepository.exportDocument(attachment.copy(localDocumentFilePath = localPath), destinationUri)
            _documentExportedEvent.tryEmit(Unit)
        }
    }

    /** Mirrors [ensureVideoDownloaded], document-flavored — `PdfPreviewDialog` calls this from its
     * own `LaunchedEffect` the same way, to show a loading state while the document downloads
     * before `PdfPageViewer` can open it. */
    suspend fun ensureDocumentDownloaded(messageId: String, attachment: MessageAttachment): String? =
        runCatching { messageRepository.downloadDocumentIfNeeded(messageId, attachment) }
            .onFailure { e -> _errorEvent.tryEmit((e as? ConsoleApiException)?.detail) }
            .getOrNull()

    /** The manual "Save to Locker" action in the image preview — a deliberate, per-image choice
     * rather than every chat image landing in the vault automatically (see
     * `VaultRepository.saveMessageAttachmentToVault`'s doc comment for why [messageId] is worth
     * keeping). Same re-entry-guard shape as [votePoll] — closes the exact race that let a
     * double-tap create two duplicate Vault items, since a failed save surfaces the same way a
     * failed send would rather than silently no-op'ing. */
    fun saveAttachmentToVault(messageId: String, attachment: MessageAttachment) {
        if (messageId in _pendingVaultSaveMessageIds.value) return
        _pendingVaultSaveMessageIds.update { it + messageId }
        viewModelScope.launch {
            runCatching { vaultRepository.saveAttachmentToVault(attachment, originalMessageId = messageId) }
                .onSuccess { saved -> _vaultSaveEvent.tryEmit(saved) }
                .onFailure { e -> _errorEvent.tryEmit((e as? ConsoleApiException)?.detail) }
            _pendingVaultSaveMessageIds.update { it - messageId }
        }
    }

    /** Ensures the video's bytes are downloaded first (a receiver may not have played it yet) —
     * mirrors [saveDocument]'s "resolve the download, then act on a copy of the attachment with
     * that path filled in" shape. Same [_pendingVaultSaveMessageIds] guard as [saveAttachmentToVault]. */
    fun saveVideoAttachmentToVault(messageId: String, attachment: MessageAttachment) {
        if (messageId in _pendingVaultSaveMessageIds.value) return
        _pendingVaultSaveMessageIds.update { it + messageId }
        viewModelScope.launch {
            runCatching {
                val localPath = messageRepository.downloadVideoIfNeeded(messageId, attachment)
                vaultRepository.saveAttachmentToVault(attachment.copy(localVideoFilePath = localPath), originalMessageId = messageId)
            }.onSuccess { saved -> _vaultSaveEvent.tryEmit(saved) }
                .onFailure { e -> _errorEvent.tryEmit((e as? ConsoleApiException)?.detail) }
            _pendingVaultSaveMessageIds.update { it - messageId }
        }
    }

    fun saveDocumentAttachmentToVault(messageId: String, attachment: MessageAttachment) {
        if (messageId in _pendingVaultSaveMessageIds.value) return
        _pendingVaultSaveMessageIds.update { it + messageId }
        viewModelScope.launch {
            runCatching {
                val localPath = messageRepository.downloadDocumentIfNeeded(messageId, attachment)
                vaultRepository.saveAttachmentToVault(attachment.copy(localDocumentFilePath = localPath), originalMessageId = messageId)
            }.onSuccess { saved -> _vaultSaveEvent.tryEmit(saved) }
                .onFailure { e -> _errorEvent.tryEmit((e as? ConsoleApiException)?.detail) }
            _pendingVaultSaveMessageIds.update { it - messageId }
        }
    }

    /** Must bracket any launch of a system picker (e.g. the image attach picker) — see
     * [ConsoleSessionManager.isExpectingTransientResult]. */
    fun beginPickerLaunch() = sessionManager.beginExpectingTransientResult()

    fun endPickerLaunch() = sessionManager.endExpectingTransientResult()

    fun startRecording() {
        _isRecording.value = voiceRecorder.start()
    }

    fun stopRecordingAndSend() {
        val recorded = voiceRecorder.stop()
        _isRecording.value = false
        if (recorded != null) {
            val replyToId = _replyTarget.value?.id
            launchCatching { messageRepository.sendVoiceNote(recorded.filePath, recorded.durationMillis, replyToId) }
            _replyTarget.value = null
        }
    }

    fun cancelRecording() {
        voiceRecorder.cancel()
        _isRecording.value = false
    }

    fun togglePlayback(filePath: String) {
        voicePlayer.playOrToggle(filePath)
    }

    override fun onCleared() {
        super.onCleared()
        voiceRecorder.cancel()
        voicePlayer.stop()
    }
}
