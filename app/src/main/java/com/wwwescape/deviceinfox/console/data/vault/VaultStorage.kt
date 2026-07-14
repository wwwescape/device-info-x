package com.wwwescape.deviceinfox.console.data.vault

import android.content.Context
import android.net.Uri
import com.wwwescape.deviceinfox.console.data.network.MediaApi
import com.wwwescape.deviceinfox.console.data.network.consoleApiCall
import com.wwwescape.deviceinfox.console.data.network.dto.MediaAssetOutDto
import com.wwwescape.deviceinfox.console.data.network.dto.MediaCategoryDto
import com.wwwescape.deviceinfox.console.data.network.dto.wireValue
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody

data class ImportedVaultImage(val filePath: String, val mimeType: String)

data class DownloadedVaultImage(val filePath: String, val mimeType: String)

/**
 * Imported images live in their own private `console_vault/` directory (never the public
 * MediaStore/Downloads) — the same "safe" rule as message attachments, see
 * [com.wwwescape.deviceinfox.console.data.messaging.MediaStorage]'s doc comment. Export (the one
 * deliberate exception, writing into wherever the user picks via the system "Save As" picker) now
 * lives on [com.wwwescape.deviceinfox.console.data.messaging.MediaStorage.exportToDocument]
 * instead — it never touched anything Vault-specific, so Messages/Calendar/Vault all share the
 * one implementation there.
 */
@Singleton
class VaultStorage @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val mediaApi: MediaApi,
) {
    suspend fun importImage(sourceUri: Uri): ImportedVaultImage = withContext(Dispatchers.IO) {
        val vaultDir = File(context.filesDir, VAULT_DIR_NAME).apply { mkdirs() }
        val mimeType = context.contentResolver.getType(sourceUri) ?: "image/jpeg"
        val extension = mimeType.substringAfter('/', "jpg")
        val destFile = File(vaultDir, "${UUID.randomUUID()}.$extension")
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            destFile.outputStream().use { output -> input.copyTo(output) }
        }
        ImportedVaultImage(filePath = destFile.absolutePath, mimeType = mimeType)
    }

    /** Same "copy into `console_vault/`" step as [importImage], but the source is already a
     * local file path (e.g. a chat attachment already downloaded into `console_media/`) rather
     * than a content [Uri] from a system picker — no [android.content.ContentResolver] needed. */
    suspend fun importFromFile(sourceFilePath: String, mimeType: String): ImportedVaultImage = withContext(Dispatchers.IO) {
        val vaultDir = File(context.filesDir, VAULT_DIR_NAME).apply { mkdirs() }
        val extension = mimeType.substringAfter('/', "jpg")
        val destFile = File(vaultDir, "${UUID.randomUUID()}.$extension")
        File(sourceFilePath).inputStream().use { input -> destFile.outputStream().use { output -> input.copyTo(output) } }
        ImportedVaultImage(filePath = destFile.absolutePath, mimeType = mimeType)
    }

    suspend fun deleteFile(filePath: String) = withContext(Dispatchers.IO) {
        File(filePath).delete()
        Unit
    }

    /** Uploads an already-locally-imported image as a `locker_file`-category asset (Phase
     * 11.8) — mirrors `MediaStorage.uploadToServer`, no `duration_ms` since the locker is
     * images-only. */
    suspend fun uploadToServer(filePath: String, mimeType: String): MediaAssetOutDto = withContext(Dispatchers.IO) {
        val file = File(filePath)
        val filePart = MultipartBody.Part.createFormData("file", file.name, file.asRequestBody(mimeType.toMediaType()))
        val categoryBody = MediaCategoryDto.LOCKER_FILE.wireValue.toRequestBody("text/plain".toMediaType())
        consoleApiCall { mediaApi.upload(categoryBody, filePart) }
    }

    /** Downloads a partner-owned locker item's file the first time it's seen locally — mirrors
     * `MediaStorage.downloadToMediaDir`, but simpler: the vault has no width/height/duration
     * metadata to derive, only the file itself and its real `Content-Type`. */
    suspend fun downloadToVaultDir(mediaId: String): DownloadedVaultImage = withContext(Dispatchers.IO) {
        val vaultDir = File(context.filesDir, VAULT_DIR_NAME).apply { mkdirs() }
        val body = consoleApiCall { mediaApi.download(mediaId) }
        val mimeType = body.contentType()?.toString()?.substringBefore(';')?.trim() ?: "image/jpeg"
        val extension = mimeType.substringAfter('/', "jpg")
        val destFile = File(vaultDir, "${UUID.randomUUID()}.$extension")
        body.byteStream().use { input -> destFile.outputStream().use { output -> input.copyTo(output) } }
        DownloadedVaultImage(filePath = destFile.absolutePath, mimeType = mimeType)
    }

    private companion object {
        const val VAULT_DIR_NAME = "console_vault"
    }
}
