package com.wwwescape.deviceinfox.console.data.settings

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.provider.Settings
import com.wwwescape.deviceinfox.R
import dagger.hilt.android.qualifiers.ApplicationContext
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

/** Auditions a [NotificationSoundTone] on demand from Settings — one playback at a time, with real
 * pause/resume: tapping the preview button again on the tone that's currently playing pauses it
 * in place (position retained), tapping it again resumes from there; previewing a *different* tone
 * stops whatever was playing/paused first outright. Same shape as
 * [com.wwwescape.deviceinfox.console.data.messaging.VoicePlayer]'s toggle-by-file-path pattern,
 * generalized to a [Uri] rather than a bare path string: [NotificationSoundTone.DEFAULT] resolves
 * to [Settings.System.DEFAULT_NOTIFICATION_URI], a `content://` system Uri, which needs
 * [MediaPlayer.setDataSource] with a [Context]/[Uri] pair, not the plain-string overload a local
 * file path would use.
 *
 * Also tracks playback [progress] (elapsed fraction of the tone's duration), which
 * [VoicePlayer][com.wwwescape.deviceinfox.console.data.messaging.VoicePlayer] doesn't need —
 * nothing in this app drives a progress ring off it today, unlike this preview button's ring.
 * [progress] holds its last value while paused rather than resetting to 0, so pausing partway
 * through doesn't visually read as "starting over." */
@Singleton
class TonePreviewPlayer @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private var player: MediaPlayer? = null
    private var loadedTone: NotificationSoundTone? = null
    private var progressJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** The tone currently loaded — playing *or* paused — null once fully stopped, switched away
     * from, or finished. Drives the progress ring, which should stay put while paused. */
    private val _activeTone = MutableStateFlow<NotificationSoundTone?>(null)
    val activeTone: StateFlow<NotificationSoundTone?> = _activeTone.asStateFlow()

    /** True only while [activeTone] is actively playing, not merely loaded-and-paused — what the
     * preview button's Play/Pause icon is keyed off, kept separate from [activeTone] so callers
     * that only care about "which tone is loaded" (the progress ring) don't have to unpack a
     * paused/playing distinction they don't need. */
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    /** Elapsed fraction of [activeTone]'s duration, in `[0f, 1f]` — `0f` whenever nothing is
     * loaded, driving the preview button's idle "faint outline" ring state too. */
    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    fun playOrToggle(tone: NotificationSoundTone) {
        val currentPlayer = player
        if (currentPlayer != null && loadedTone == tone) {
            if (_isPlaying.value) {
                // Same tone, currently playing — pause in place, keep the player and its position.
                progressJob?.cancel()
                progressJob = null
                runCatching { currentPlayer.pause() }
                _isPlaying.value = false
            } else {
                // Same tone, currently paused — resume from where it left off.
                runCatching { currentPlayer.start() }
                _isPlaying.value = true
                progressJob = scope.launch { watchForCompletion() }
            }
            return
        }
        // A different tone (or nothing loaded) — hard reset, start the new one fresh from 0.
        stop()
        val started = runCatching {
            MediaPlayer().apply {
                setDataSource(context, uriFor(tone))
                setOnCompletionListener { stop() }
                prepare()
                start()
            }
        }.getOrNull()
        if (started != null) {
            player = started
            loadedTone = tone
            _activeTone.value = tone
            _isPlaying.value = true
            progressJob = scope.launch { watchForCompletion() }
        }
    }

    private suspend fun watchForCompletion() {
        while (true) {
            val current = player ?: break
            val duration = runCatching { current.duration }.getOrDefault(0)
            if (duration <= 0) break
            val position = runCatching { current.currentPosition }.getOrDefault(0)
            _progress.value = (position.toFloat() / duration).coerceIn(0f, 1f)
            // Belt-and-suspenders against setOnCompletionListener not firing — a known,
            // real-world MediaPlayer flakiness point specifically for system content Uris
            // like NotificationSoundTone.DEFAULT (Settings.System.DEFAULT_NOTIFICATION_URI),
            // less so for a plain bundled resource. Without this, currentPosition can
            // plateau just short of duration forever (rounding on the reported duration for
            // that kind of Uri, not a real end-of-stream signal we're missing) — the ring
            // fills up and visually "finishes," but nothing ever calls stop() to reset the
            // button, exactly the stuck state this guards against. The tolerance is wider
            // than one poll interval so a plateau a little further short of the nominal
            // duration still gets caught, not just an exact/overshoot match. Only runs while
            // actively playing (paused playback cancels this job), so it can't mistake "paused
            // near the end" for finished.
            if (position >= duration - COMPLETION_TOLERANCE_MILLIS) {
                stop()
                break
            }
            delay(PROGRESS_POLL_INTERVAL_MILLIS)
        }
    }

    /** Hard stop — releases the player and forgets both its position and [progress] entirely,
     * unlike pausing via [playOrToggle]. Used when finishing playback, switching to a different
     * tone, or when the Settings screen leaves composition ([MessagesSettingsScreen]'s
     * `DisposableEffect`). */
    fun stop() {
        progressJob?.cancel()
        progressJob = null
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
        loadedTone = null
        _activeTone.value = null
        _isPlaying.value = false
        _progress.value = 0f
    }

    /** `android.resource://` reads fine in-process for a preview like this — it's only unreliable
     * as a *[android.app.NotificationChannel]'s* sound, where a separate system process resolves
     * the Uri (see [ConsolePushChannelManager.chimeSoundUri][com.wwwescape.deviceinfox.console.push.ConsolePushChannelManager]'s
     * own doc comment for why *that* path needs the `FileProvider`/`content://` dance instead).
     * This preview never leaves the app process, so the plain resource Uri is enough. */
    private fun uriFor(tone: NotificationSoundTone): Uri = when (tone) {
        NotificationSoundTone.DEFAULT -> Settings.System.DEFAULT_NOTIFICATION_URI
        NotificationSoundTone.CHIME -> Uri.parse("android.resource://${context.packageName}/${R.raw.chime}")
    }

    companion object {
        private const val PROGRESS_POLL_INTERVAL_MILLIS = 80L

        /** How close [MediaPlayer.getCurrentPosition] needs to get to [MediaPlayer.getDuration]
         * before the polling loop treats playback as finished on its own — see the loop's own
         * comment for why this can't just wait for an exact match. */
        private const val COMPLETION_TOLERANCE_MILLIS = 200
    }
}
