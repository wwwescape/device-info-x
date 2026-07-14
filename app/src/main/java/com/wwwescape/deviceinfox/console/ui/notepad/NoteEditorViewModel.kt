package com.wwwescape.deviceinfox.console.ui.notepad

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wwwescape.deviceinfox.console.data.network.ConsoleApiException
import com.wwwescape.deviceinfox.console.data.notepad.NotepadEntry
import com.wwwescape.deviceinfox.console.data.notepad.NotepadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val AUTOSAVE_DEBOUNCE_MILLIS = 1_500L

/** The partner's version of a note that's currently in conflict — see [NoteEditorViewModel.conflictingRemote]. */
data class ConflictingRemoteVersion(val title: String, val body: String)

/** Backs the note editor — [entryId] pulled straight off this ViewModel's own injected
 * [SavedStateHandle] (the standard Hilt-nav-compose mechanism), same as
 * [com.wwwescape.deviceinfox.console.ui.home.MessageInfoViewModel]'s own precedent, the first
 * screen in this app to use it. [entry] observes the same live Room-backed row every other screen
 * would, but [titleText]/[bodyText] are separate, locally-owned drafts — they only ever seed
 * themselves from the *first* non-null [entry] emission ([hasInitializedText]), so a live update
 * arriving later (e.g. this device's own autosave landing, or — while a save is pending — a v1
 * that hadn't been observed as a conflict yet) never silently overwrites text the user is actively
 * typing.
 *
 * Saving is still debounced autosave ~1.5s after typing pauses (see the TODOS.md writeup this was
 * built from), *plus* an explicit Save action ([saveNowExplicit]) for immediate, visible feedback
 * ([isSaving]/[saveSucceeded]/[saveFailed], same shape as [com.wwwescape.deviceinfox.console.ui.calendar.CalendarViewModel]'s
 * save flow) — autosave itself stays silent on failure (the next tick just retries), matching its
 * original unobtrusive design.
 *
 * [saveMutex] serializes every write path (debounce timer, explicit flush/save, conflict
 * resolution) so at most one [saveNow] body runs at a time — without it, a debounce-triggered save
 * and a leave-triggered [flushPendingSave] could both read [lastKnownUpdatedAtRaw] before either
 * had round-tripped, then the second to land would 409 against its own sibling's just-applied
 * update: a *self*-race, not a real conflict, but indistinguishable from one once the response
 * comes back. The mutex means the second call only starts once the first has fully applied its
 * result, so it always re-reads a fresh token — a 409 past this point means a genuinely different
 * writer moved the entry (the partner, for a `SHARED` note; this same account's *other device*,
 * for a `PRIVATE` one — the one case a private note can still legitimately conflict). */
@HiltViewModel
class NoteEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val notepadRepository: NotepadRepository,
) : ViewModel() {
    private val entryId: String = checkNotNull(savedStateHandle["entryId"])

    val entry: StateFlow<NotepadEntry?> = notepadRepository.observeById(entryId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _titleText = MutableStateFlow(TextFieldValue())
    val titleText: StateFlow<TextFieldValue> = _titleText.asStateFlow()

    private val _bodyText = MutableStateFlow(TextFieldValue())
    val bodyText: StateFlow<TextFieldValue> = _bodyText.asStateFlow()

    private var hasInitializedText = false

    /** The `updated_at` this device last confirmed the server holds — the optimistic-concurrency
     * token the next save must echo back. Advances on every successful save and on every conflict
     * resolution, never on a bare `entry` observation alone (see this class's own doc comment). */
    private var lastKnownUpdatedAtRaw: String? = null
    private var saveJob: Job? = null
    private val saveMutex = Mutex()

    private val _conflictEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val conflictEvent: SharedFlow<Unit> = _conflictEvent.asSharedFlow()

    /** The partner's title+body once a conflict is detected (SHARED notes only — see
     * `notepad_service.update_entry`'s own doc comment for why PRIVATE notes can no longer 409 at
     * all) — already sitting in Room (the very write that caused the conflict), so showing it
     * needs no extra round-trip. Both fields, not just body: a save conflicts as one whole row
     * (title+body share a single `updated_at`), so the partner's title may have changed too. */
    private val _conflictingRemote = MutableStateFlow<ConflictingRemoteVersion?>(null)
    val conflictingRemote: StateFlow<ConflictingRemoteVersion?> = _conflictingRemote.asStateFlow()

    /** Drives the explicit Save button's disabled/spinner state — never set for a background
     * autosave tick, only for [saveNowExplicit], same as [saveSucceeded]/[saveFailed] below. */
    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _saveSucceeded = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val saveSucceeded: SharedFlow<Unit> = _saveSucceeded.asSharedFlow()

    private val _saveFailed = MutableSharedFlow<String?>(extraBufferCapacity = 1)
    val saveFailed: SharedFlow<String?> = _saveFailed.asSharedFlow()

    init {
        viewModelScope.launch {
            entry.collect { current ->
                if (current != null && !hasInitializedText) {
                    hasInitializedText = true
                    _titleText.value = TextFieldValue(text = current.title, selection = TextRange(current.title.length))
                    _bodyText.value = TextFieldValue(text = current.body, selection = TextRange(current.body.length))
                    lastKnownUpdatedAtRaw = current.updatedAtRaw
                }
            }
        }
    }

    fun onTitleChanged(value: TextFieldValue) {
        _titleText.value = value
        scheduleAutosave()
    }

    fun onBodyChanged(value: TextFieldValue) {
        _bodyText.value = value
        scheduleAutosave()
    }

    private fun scheduleAutosave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(AUTOSAVE_DEBOUNCE_MILLIS)
            saveNow()
        }
    }

    /** Called by the editor screen on leaving (`DisposableEffect.onDispose`). If the note is still
     * completely empty — blank title *and* blank body, e.g. created via the FAB and never typed
     * into, or fully cleared out before leaving — deletes it outright rather than persisting an
     * empty row, matching Keep/Notes' own "an empty note never sticks around" convention.
     * Deliberately only checked here, at the moment of leaving, not on every autosave tick while
     * still actively in the editor — clearing text mid-edit to retype something shouldn't risk
     * deletion while the screen is still open. Otherwise, cancels any pending debounce and saves
     * immediately so the last few keystrokes before navigating back aren't left stranded inside an
     * unfired debounce window. Both paths are silent, same as autosave — the screen is already
     * gone by the time either resolves, so there's nothing to show feedback on. */
    fun flushPendingSave() {
        saveJob?.cancel()
        if (_titleText.value.text.isBlank() && _bodyText.value.text.isBlank()) {
            viewModelScope.launch { runCatching { notepadRepository.deleteEntry(entryId) } }
        } else {
            saveJob = viewModelScope.launch { saveNow() }
        }
    }

    /** The explicit Save button — same underlying save, but with visible [isSaving]/[saveSucceeded]/
     * [saveFailed] feedback, matching [com.wwwescape.deviceinfox.console.ui.calendar.CalendarViewModel.saveEvent]'s shape. */
    fun saveNowExplicit() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch { saveNow(showFeedback = true) }
    }

    private suspend fun saveNow(showFeedback: Boolean = false) {
        saveMutex.withLock {
            // Re-read the token *after* acquiring the lock, not before — an earlier save queued
            // ahead of this one may have just advanced it while this call was waiting, which is
            // exactly the self-race this mutex exists to prevent (see this class's own doc comment).
            val expected = lastKnownUpdatedAtRaw ?: return@withLock
            val title = _titleText.value.text
            val body = _bodyText.value.text
            if (showFeedback) _isSaving.value = true
            runCatching { notepadRepository.saveEntry(entryId, title, body, expected) }
                .onSuccess { saved ->
                    lastKnownUpdatedAtRaw = saved.updatedAtRaw
                    if (showFeedback) _saveSucceeded.tryEmit(Unit)
                }
                .onFailure { e ->
                    if ((e as? ConsoleApiException)?.httpStatusCode == 409) {
                        val current = notepadRepository.getById(entryId)
                        _conflictingRemote.value = current?.let { ConflictingRemoteVersion(it.title, it.body) }
                        _conflictEvent.tryEmit(Unit)
                    } else if (showFeedback) {
                        _saveFailed.tryEmit((e as? ConsoleApiException)?.detail)
                    }
                    // A silent (autosave) failure that isn't a conflict is otherwise swallowed —
                    // the next autosave tick retries with whatever's currently typed, the same way
                    // a debounce naturally self-heals from a single missed save.
                }
            if (showFeedback) _isSaving.value = false
        }
    }

    /** Conflict resolution: a genuine manual merge, not a forced exclusive pick — nothing is
     * saved automatically here. This device's own currently-typed title/body stay exactly as
     * they are, untouched, so the user can manually incorporate whatever they want from the
     * partner's version (just shown to them in the conflict dialog) into their own draft by hand.
     * Only [lastKnownUpdatedAtRaw] advances, to the partner's fresh token, so the *next*
     * autosave/explicit save — with whatever the user ends up typing — succeeds cleanly instead
     * of immediately 409ing again against the same stale value. */
    fun resolveConflictContinueEditing() {
        viewModelScope.launch {
            val current = notepadRepository.getById(entryId) ?: return@launch
            lastKnownUpdatedAtRaw = current.updatedAtRaw
            _conflictingRemote.value = null
        }
    }

    /** Conflict resolution: discard what's currently typed here, load the partner's version
     * instead — for when there's nothing worth merging and the partner's edit should simply win
     * outright. Still only ever reached by an explicit tap, never automatic. */
    fun resolveConflictUseTheirVersion() {
        viewModelScope.launch {
            val current = notepadRepository.getById(entryId) ?: return@launch
            _titleText.value = TextFieldValue(text = current.title, selection = TextRange(current.title.length))
            _bodyText.value = TextFieldValue(text = current.body, selection = TextRange(current.body.length))
            lastKnownUpdatedAtRaw = current.updatedAtRaw
            _conflictingRemote.value = null
        }
    }
}
