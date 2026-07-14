package com.wwwescape.deviceinfox.console.data.vault

import com.wwwescape.deviceinfox.console.data.messaging.MessageAttachmentKind

data class VaultItem(
    val id: String,
    val albumId: String?,
    /** For a photo, the photo itself. For a video, the poster-frame thumbnail. For a document,
     * unused (empty string) — see [com.wwwescape.deviceinfox.console.data.db.VaultItemEntity.filePath]'s
     * doc comment. */
    val filePath: String,
    val mimeType: String,
    val isFavorite: Boolean,
    val addedAtEpochMillis: Long,
    val caption: String?,
    /** False only when this is the paired partner's own item, synced read-only (Phase 11.8 —
     * the server only lets an item's owner edit/delete it). */
    val isMine: Boolean = true,
    /** Reuses the Messages/Calendar `MessageAttachmentKind` enum (IMAGE/VIDEO/DOCUMENT) rather
     * than a parallel Locker-specific one — nothing about it was actually message-specific.
     * `LockerCategoryDto.PHOTO` maps to `IMAGE` at the repository's mapping boundary only. */
    val kind: MessageAttachmentKind = MessageAttachmentKind.IMAGE,
    /** Video/document only — the asset's own remote id, needed to lazily fetch it. */
    val mediaId: String? = null,
    /** Video only. */
    val durationMillis: Long? = null,
    /** Video only, null until lazily downloaded. */
    val localVideoFilePath: String? = null,
    /** Document only — the picked file's real name. */
    val originalFilename: String? = null,
    /** Document only, null until lazily downloaded. */
    val localDocumentFilePath: String? = null,
    /** Document only today — a photo/video's size was never previously needed by the UI. */
    val sizeBytes: Long? = null,
)

data class VaultAlbum(
    val id: String,
    val name: String,
    val createdAtEpochMillis: Long,
    /** See [VaultItem.isMine]. */
    val isMine: Boolean = true,
)
