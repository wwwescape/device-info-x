package com.wwwescape.deviceinfox.console.data.messaging

import android.media.MediaPlayer
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** One playback at a time, with real pause/resume — tapping the currently-playing note again
 * pauses it in place (position retained), tapping it again resumes from there; starting a
 * *different* note stops whatever was playing/paused first outright (positions aren't remembered
 * across notes, only within one). Same shape as
 * [com.wwwescape.deviceinfox.console.data.settings.TonePreviewPlayer]'s toggle-by-tone pattern,
 * including its belt-and-suspenders completion fallback below — see that class's own doc comment
 * for the [MediaPlayer.OnCompletionListener] flakiness this guards against. */
@Singleton
class VoicePlayer @Inject constructor() {
    private var player: MediaPlayer? = null
    private var loadedFilePath: String? = null
    private var completionWatchJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** The file path actively playing right now — null whenever nothing is loaded *or* the loaded
     * note is merely paused, so a bubble's Play/Pause icon (keyed off equality with this) reverts
     * to Play the instant its note is paused, exactly like tapping it hadn't started anything. */
    private val _playingFilePath = MutableStateFlow<String?>(null)
    val playingFilePath: StateFlow<String?> = _playingFilePath.asStateFlow()

    /** The file path currently loaded — playing *or* paused — null once fully stopped, switched
     * away from, or finished. Same "loaded vs. playing" split
     * [TonePreviewPlayer][com.wwwescape.deviceinfox.console.data.settings.TonePreviewPlayer]'s
     * `activeTone`/`isPlaying` pair uses (this is the `activeTone` half — [playingFilePath] above
     * is the `isPlaying` half, pre-existing under its own name), kept as a separate flow from
     * [playingFilePath] rather than folded together so existing [playingFilePath] consumers (the
     * Play/Pause icon) are unaffected — this one drives the progress ring/timestamp, which should
     * stay in place while paused rather than disappearing the way the Play/Pause icon's own state
     * does. */
    private val _activeFilePath = MutableStateFlow<String?>(null)
    val activeFilePath: StateFlow<String?> = _activeFilePath.asStateFlow()

    /** Elapsed fraction of [activeFilePath]'s duration, in `[0f, 1f]` — `0f` whenever nothing is
     * loaded. Holds its last value while paused rather than resetting to 0, matching
     * [TonePreviewPlayer.progress][com.wwwescape.deviceinfox.console.data.settings.TonePreviewPlayer]'s
     * identical reasoning: pausing partway through shouldn't visually read as "starting over." */
    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    fun playOrToggle(filePath: String) {
        val currentPlayer = player
        if (currentPlayer != null && loadedFilePath == filePath) {
            if (_playingFilePath.value == filePath) {
                // Same note, currently playing — pause in place, keep the player and its position.
                completionWatchJob?.cancel()
                completionWatchJob = null
                runCatching { currentPlayer.pause() }
                _playingFilePath.value = null
            } else {
                // Same note, currently paused — resume from where it left off.
                runCatching { currentPlayer.start() }
                _playingFilePath.value = filePath
                completionWatchJob = scope.launch { watchForCompletion() }
            }
            return
        }
        // A different note (or nothing loaded) — hard reset, start the new one fresh from 0.
        stop()
        val started = runCatching {
            MediaPlayer().apply {
                setDataSource(filePath)
                setOnCompletionListener { stop() }
                prepare()
                start()
            }
        }.getOrNull()
        if (started != null) {
            player = started
            loadedFilePath = filePath
            _playingFilePath.value = filePath
            _activeFilePath.value = filePath
            completionWatchJob = scope.launch { watchForCompletion() }
        }
    }

    private suspend fun watchForCompletion() {
        while (true) {
            val current = player ?: break
            val duration = runCatching { current.duration }.getOrDefault(0)
            if (duration <= 0) break
            val position = runCatching { current.currentPosition }.getOrDefault(0)
            _progress.value = (position.toFloat() / duration).coerceIn(0f, 1f)
            // Belt-and-suspenders against setOnCompletionListener not firing — see
            // TonePreviewPlayer's identical loop for why currentPosition can plateau just short of
            // duration without a real end-of-stream callback ever arriving, leaving the bubble
            // stuck showing Pause forever without this. Only runs while actively playing (paused
            // playback cancels this job), so it can't mistake "paused near the end" for finished.
            if (position >= duration - COMPLETION_TOLERANCE_MILLIS) {
                stop()
                break
            }
            delay(COMPLETION_POLL_INTERVAL_MILLIS)
        }
    }

    /** Hard stop — releases the player and forgets its position/[progress] entirely, unlike
     * pausing via [playOrToggle]. Used when finishing playback, switching to a different note, or
     * when a consuming screen tears down ([HomeViewModel.onCleared][com.wwwescape.deviceinfox.console.ui.home.HomeViewModel]). */
    fun stop() {
        completionWatchJob?.cancel()
        completionWatchJob = null
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
        loadedFilePath = null
        _playingFilePath.value = null
        _activeFilePath.value = null
        _progress.value = 0f
    }

    companion object {
        private const val COMPLETION_POLL_INTERVAL_MILLIS = 80L

        /** How close [MediaPlayer.getCurrentPosition] needs to get to [MediaPlayer.getDuration]
         * before the polling loop treats playback as finished on its own — see the loop's own
         * comment for why this can't just wait for an exact match. */
        private const val COMPLETION_TOLERANCE_MILLIS = 200
    }
}
