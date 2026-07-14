package com.wwwescape.deviceinfox.console.data.messaging

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.wwwescape.deviceinfox.console.data.network.MediaApi
import com.wwwescape.deviceinfox.console.data.network.consoleApiCall
import com.wwwescape.deviceinfox.console.data.network.dto.MediaAssetOutDto
import com.wwwescape.deviceinfox.console.data.network.dto.MediaCategoryDto
import com.wwwescape.deviceinfox.console.data.network.dto.wireValue
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody

data class CopiedImage(
    val filePath: String,
    val mimeType: String,
    val widthPx: Int?,
    val heightPx: Int?,
    val sizeBytes: Long?,
)

data class CopiedVideo(
    val filePath: String,
    val mimeType: String,
    val sizeBytes: Long?,
)

data class CopiedDocument(
    val filePath: String,
    val mimeType: String,
    /** The picked file's real name, resolved from [android.provider.OpenableColumns.DISPLAY_NAME]
     * — null if the content provider didn't expose one, in which case the UI falls back to a
     * generic label. */
    val originalFilename: String?,
    val sizeBytes: Long?,
)

/** The result of probing a just-copied (about to be sent) or just-downloaded (received) video
 * file — mirrors [MediaStorage.probeVoiceDurationMs]'s "derive locally, don't trust the network"
 * reasoning, plus a poster-frame JPEG written alongside it for the bubble thumbnail/preview. */
data class VideoProbeResult(
    val durationMs: Int?,
    val thumbnailFilePath: String?,
    val thumbnailWidthPx: Int?,
    val thumbnailHeightPx: Int?,
)

/** A downloaded (received) attachment, with whichever metadata could only be derived locally —
 * see [MediaStorage.downloadToMediaDir]'s doc comment for why. */
data class DownloadedMedia(
    val filePath: String,
    val mimeType: String,
    val widthPx: Int? = null,
    val heightPx: Int? = null,
    val durationMs: Int? = null,
)

/**
 * Everything image messages touch stays inside the app's private storage
 * (`context.filesDir`) — never the public MediaStore/Downloads, never a location another app's
 * gallery would surface. That's what makes the "safe" in safe image downloading: not encryption
 * (the whole console database and its files inherit the device's normal file permissions same as
 * any app-private file, unlike the SQLCipher-encrypted database itself), but simply never
 * appearing anywhere a family member browsing Photos would stumble onto it.
 */
@Singleton
class MediaStorage @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val mediaApi: MediaApi,
) {
    /** Lets a caller (e.g. `CalendarRepository`, picking a kind for a multi-select gallery
     * result) resolve a picked `Uri`'s mime type without needing its own `Context` — every other
     * function here already does this same `contentResolver.getType` call internally. */
    fun resolveMimeType(uri: Uri): String? = context.contentResolver.getType(uri)

    suspend fun copyImageToPrivateStorage(sourceUri: Uri): CopiedImage = withContext(Dispatchers.IO) {
        val mediaDir = File(context.filesDir, MEDIA_DIR_NAME).apply { mkdirs() }
        val mimeType = context.contentResolver.getType(sourceUri) ?: "image/jpeg"
        val extension = mimeType.substringAfter('/', "jpg")
        val destFile = File(mediaDir, "${UUID.randomUUID()}.$extension")

        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            destFile.outputStream().use { output -> input.copyTo(output) }
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(destFile.absolutePath, bounds)

        CopiedImage(
            filePath = destFile.absolutePath,
            mimeType = mimeType,
            widthPx = bounds.outWidth.takeIf { it > 0 },
            heightPx = bounds.outHeight.takeIf { it > 0 },
            sizeBytes = destFile.length(),
        )
    }

    /** Same shape as [copyImageToPrivateStorage] minus the bitmap-bounds probe — a video's
     * "dimensions" for display purposes come from its poster thumbnail instead, see
     * [probeVideoDurationAndThumbnail]. */
    suspend fun copyVideoToPrivateStorage(sourceUri: Uri): CopiedVideo = withContext(Dispatchers.IO) {
        val mediaDir = File(context.filesDir, MEDIA_DIR_NAME).apply { mkdirs() }
        val mimeType = context.contentResolver.getType(sourceUri) ?: "video/mp4"
        val extension = mimeType.substringAfter('/', "mp4")
        val destFile = File(mediaDir, "${UUID.randomUUID()}.$extension")

        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            destFile.outputStream().use { output -> input.copyTo(output) }
        }

        CopiedVideo(filePath = destFile.absolutePath, mimeType = mimeType, sizeBytes = destFile.length())
    }

    /** Same copy shape as [copyImageToPrivateStorage]/[copyVideoToPrivateStorage], but for an
     * arbitrary file from `ActivityResultContracts.OpenDocument()` — mime fallback is the generic
     * `application/octet-stream` (matching the server's own fallback), not a fixed media type,
     * since there's no sensible default for "any file". Unlike the other copy functions, also
     * resolves the picked file's real display name via [resolveDisplayName] — none of them
     * preserve it today, since nothing before this needed to show a filename anywhere. */
    suspend fun copyDocumentToPrivateStorage(sourceUri: Uri): CopiedDocument = withContext(Dispatchers.IO) {
        val mediaDir = File(context.filesDir, MEDIA_DIR_NAME).apply { mkdirs() }
        val mimeType = context.contentResolver.getType(sourceUri) ?: "application/octet-stream"
        val originalFilename = resolveDisplayName(sourceUri)
        val extension = originalFilename?.substringAfterLast('.', "")?.takeIf { it.isNotBlank() }
            ?: mimeType.substringAfter('/', "bin")
        val destFile = File(mediaDir, "${UUID.randomUUID()}.$extension")

        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            destFile.outputStream().use { output -> input.copyTo(output) }
        }

        CopiedDocument(
            filePath = destFile.absolutePath,
            mimeType = mimeType,
            originalFilename = originalFilename,
            sizeBytes = destFile.length(),
        )
    }

    /** [OpenableColumns.DISPLAY_NAME] is the SAF-standard way to ask a content provider for a
     * human-readable name — unlike [Uri.getLastPathSegment], this works regardless of the
     * provider's own Uri shape (which for `OpenDocument()` results is usually an opaque document
     * id, not a filename). */
    private fun resolveDisplayName(uri: Uri): String? {
        val cursor = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        return cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) it.getString(index) else null
            } else {
                null
            }
        }
    }

    /** "Downloading" an already-private file just copies it into a separate private "saved"
     * folder, distinct from the per-message originals — still never touching public storage.
     * [preferredFileName] (documents only) keeps the original name/extension the sender's file
     * had, rather than the mime-derived-only naming every other attachment type is fine with
     * (an image/video's saved copy has never needed to look like anything but a generic file). */
    suspend fun saveToPrivateDownloads(
        sourceFilePath: String,
        mimeType: String,
        preferredFileName: String? = null,
    ): File = withContext(Dispatchers.IO) {
        val downloadsDir = File(context.filesDir, DOWNLOADS_DIR_NAME).apply { mkdirs() }
        val destFile = if (preferredFileName != null) {
            File(downloadsDir, "${UUID.randomUUID()}_$preferredFileName")
        } else {
            val extension = mimeType.substringAfter('/', "jpg")
            File(downloadsDir, "${UUID.randomUUID()}.$extension")
        }
        File(sourceFilePath).copyTo(destFile, overwrite = true)
        destFile
    }

    /** Uploads an already-locally-captured file (image, voice note, video, or document) ahead of
     * sending a message that references it — the server probes image width/height itself, but
     * [durationMs] (voice/video), [thumbnailMediaId] (video's already-uploaded poster-frame asset
     * id), and [originalFilename] (document's real filename) all have to come from the client
     * (see `media_service.py`). [originalFilename] is used as the multipart part's filename
     * instead of [filePath]'s own randomized on-disk name — image/video callers leave it null and
     * keep today's behavior exactly. */
    suspend fun uploadToServer(
        filePath: String,
        mimeType: String,
        category: MediaCategoryDto,
        durationMs: Int? = null,
        thumbnailMediaId: String? = null,
        originalFilename: String? = null,
    ): MediaAssetOutDto = withContext(Dispatchers.IO) {
        val file = File(filePath)
        val filePart = MultipartBody.Part.createFormData(
            "file",
            originalFilename ?: file.name,
            file.asRequestBody(mimeType.toMediaType()),
        )
        val categoryBody = category.wireValue.toRequestBody("text/plain".toMediaType())
        val durationBody = durationMs?.toString()?.toRequestBody("text/plain".toMediaType())
        val thumbnailBody = thumbnailMediaId?.toRequestBody("text/plain".toMediaType())
        consoleApiCall { mediaApi.upload(categoryBody, filePart, durationBody, thumbnailBody) }
    }

    /** Metadata only, no bytes — used to learn a *received* video message's `thumbnailMediaId`
     * (and duration/size) before its full bytes are ever downloaded. See [MediaApi.getMetadata]. */
    suspend fun getAssetMetadata(mediaId: String): MediaAssetOutDto = withContext(Dispatchers.IO) {
        consoleApiCall { mediaApi.getMetadata(mediaId) }
    }

    /** Beyond [getAssetMetadata]'s thumbnail-id use case, an image's width/height or a voice
     * note's duration for a *received* message is still derived from the downloaded file itself,
     * not trusted from anything the sender's device claimed. The mime type comes from the
     * download response's real `Content-Type` (the server always sets it from the stored asset,
     * see `media.py`'s router), not guessed. Also used to download a video's poster-frame
     * thumbnail (itself just a `message_image` asset), via `isVoice = false`. */
    suspend fun downloadToMediaDir(mediaId: String, isVoice: Boolean): DownloadedMedia =
        withContext(Dispatchers.IO) {
            val mediaDir = File(context.filesDir, MEDIA_DIR_NAME).apply { mkdirs() }
            val body = consoleApiCall { mediaApi.download(mediaId) }
            val mimeType = body.contentType()?.toString()?.substringBefore(';')?.trim()
                ?: if (isVoice) "audio/mp4" else "image/jpeg"
            val extension = mimeType.substringAfter('/', if (isVoice) "m4a" else "jpg")
            val destFile = File(mediaDir, "${UUID.randomUUID()}.$extension")

            body.byteStream().use { input -> destFile.outputStream().use { output -> input.copyTo(output) } }

            if (isVoice) {
                DownloadedMedia(
                    filePath = destFile.absolutePath,
                    mimeType = mimeType,
                    durationMs = probeVoiceDurationMs(destFile.absolutePath),
                )
            } else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(destFile.absolutePath, bounds)
                DownloadedMedia(
                    filePath = destFile.absolutePath,
                    mimeType = mimeType,
                    widthPx = bounds.outWidth.takeIf { it > 0 },
                    heightPx = bounds.outHeight.takeIf { it > 0 },
                )
            }
        }

    /** Downloads the actual video bytes for an already-known asset — deliberately separate from
     * [downloadToMediaDir] (and never called from sync/receive the way that is for image/voice):
     * videos can be up to 150MB, so this is only ever invoked lazily, on tap, from the video
     * viewer. No width/height/duration probing here — those were already learned from the
     * poster-thumbnail asset's metadata (`MediaApi.getMetadata`) before this is ever called. */
    suspend fun downloadVideoToMediaDir(mediaId: String, mimeTypeHint: String): DownloadedMedia =
        withContext(Dispatchers.IO) {
            val mediaDir = File(context.filesDir, MEDIA_DIR_NAME).apply { mkdirs() }
            val body = consoleApiCall { mediaApi.download(mediaId) }
            val mimeType = body.contentType()?.toString()?.substringBefore(';')?.trim() ?: mimeTypeHint
            val extension = mimeType.substringAfter('/', "mp4")
            val destFile = File(mediaDir, "${UUID.randomUUID()}.$extension")

            body.byteStream().use { input -> destFile.outputStream().use { output -> input.copyTo(output) } }

            DownloadedMedia(filePath = destFile.absolutePath, mimeType = mimeType)
        }

    /** Downloads the actual document bytes for an already-known asset — deliberately separate
     * from [downloadToMediaDir], same reasoning as [downloadVideoToMediaDir]: a document can be
     * arbitrarily large (up to `max_document_size_mb`), so this is only ever invoked lazily, on
     * tap of the Save button. */
    suspend fun downloadDocumentToMediaDir(mediaId: String, mimeTypeHint: String): DownloadedMedia =
        withContext(Dispatchers.IO) {
            val mediaDir = File(context.filesDir, MEDIA_DIR_NAME).apply { mkdirs() }
            val body = consoleApiCall { mediaApi.download(mediaId) }
            val mimeType = body.contentType()?.toString()?.substringBefore(';')?.trim() ?: mimeTypeHint
            val extension = mimeType.substringAfter('/', "bin")
            val destFile = File(mediaDir, "${UUID.randomUUID()}.$extension")

            body.byteStream().use { input -> destFile.outputStream().use { output -> input.copyTo(output) } }

            DownloadedMedia(filePath = destFile.absolutePath, mimeType = mimeType)
        }

    /** Mirrors `VaultStorage.deleteFile` — used when wiping the whole conversation, to remove
     * downloaded attachment/voice-note files Room's own FK cascade can't touch (it only removes
     * DB rows, never the files those rows pointed at). */
    suspend fun deleteFile(filePath: String) = withContext(Dispatchers.IO) {
        File(filePath).delete()
        Unit
    }

    /** [MediaMetadataRetriever] only implements [AutoCloseable] from API 29 — minSdk 24 needs
     * the manual `release()` instead of `use {}`. */
    private fun probeVoiceDurationMs(filePath: String): Int? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(filePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toIntOrNull()
        } catch (e: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    /** Same [MediaMetadataRetriever] pattern as [probeVoiceDurationMs], plus a poster frame
     * (`getFrameAtTime(0)`) written out as its own private JPEG — that file is what gets
     * uploaded as the video's `message_image` thumbnail asset and what the message bubble
     * renders before the video itself is ever downloaded. */
    suspend fun probeVideoDurationAndThumbnail(filePath: String): VideoProbeResult = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(filePath)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toIntOrNull()
            val frame: Bitmap? = retriever.getFrameAtTime(0)
            if (frame == null) {
                VideoProbeResult(durationMs, null, null, null)
            } else {
                val mediaDir = File(context.filesDir, MEDIA_DIR_NAME).apply { mkdirs() }
                val thumbFile = File(mediaDir, "${UUID.randomUUID()}_thumb.jpg")
                FileOutputStream(thumbFile).use { out -> frame.compress(Bitmap.CompressFormat.JPEG, 90, out) }
                VideoProbeResult(durationMs, thumbFile.absolutePath, frame.width, frame.height)
            }
        } catch (e: Exception) {
            VideoProbeResult(null, null, null, null)
        } finally {
            retriever.release()
        }
    }

    private companion object {
        const val MEDIA_DIR_NAME = "console_media"
        const val DOWNLOADS_DIR_NAME = "console_downloads"
    }
}
