package com.wwwescape.deviceinfox.console.data.messaging

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class RecordedVoiceNote(val filePath: String, val durationMillis: Long)

/** One recording at a time — a second `start()` while already recording is a no-op that
 * returns false. Recordings land in the same private `console_media/` directory as image
 * attachments, for the same "never touches public storage" reason (see [MediaStorage]). */
@Singleton
class VoiceRecorder @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startTimeMillis: Long = 0L

    fun start(): Boolean {
        if (recorder != null) return false

        val mediaDir = File(context.filesDir, MEDIA_DIR_NAME).apply { mkdirs() }
        val file = File(mediaDir, "${UUID.randomUUID()}.m4a")
        @Suppress("DEPRECATION")
        val newRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()

        val started = runCatching {
            newRecorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
        }.isSuccess

        if (!started) {
            newRecorder.release()
            return false
        }
        recorder = newRecorder
        outputFile = file
        startTimeMillis = System.currentTimeMillis()
        return true
    }

    /** Null if nothing was recording, or if the recording failed to finalize (MediaRecorder can
     * throw on `stop()` if called too soon after `start()`). */
    fun stop(): RecordedVoiceNote? {
        val current = recorder ?: return null
        val file = outputFile
        val stopped = runCatching { current.stop() }.isSuccess
        current.release()
        recorder = null
        outputFile = null
        if (!stopped || file == null) {
            file?.delete()
            return null
        }
        return RecordedVoiceNote(filePath = file.absolutePath, durationMillis = System.currentTimeMillis() - startTimeMillis)
    }

    fun cancel() {
        val current = recorder ?: return
        runCatching { current.stop() }
        current.release()
        recorder = null
        outputFile?.delete()
        outputFile = null
    }

    private companion object {
        const val MEDIA_DIR_NAME = "console_media"
    }
}
