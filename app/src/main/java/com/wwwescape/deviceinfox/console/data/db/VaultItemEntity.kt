package com.wwwescape.deviceinfox.console.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** [originalMessageId] is a soft, unenforced backlink (set when an item is saved from a chat
 * attachment) rather than a foreign key — a vault item should outlive the message it came from
 * if that message is later deleted, not cascade away with it. */
@Entity(
    tableName = "vault_items",
    foreignKeys = [
        ForeignKey(
            entity = VaultAlbumEntity::class,
            parentColumns = ["id"],
            childColumns = ["albumId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("albumId"), Index("addedAtEpochMillis")],
)
data class VaultItemEntity(
    @PrimaryKey val id: String,
    val albumId: String?,
    val filePath: String,
    val mimeType: String,
    val isFavorite: Boolean = false,
    val addedAtEpochMillis: Long,
    val originalMessageId: String?,
    /** Added in the Phase 10 (v1 -> v2) migration — the only free-text field on a vault item,
     * so it's what "search" (per the design spec) filters on. */
    val caption: String? = null,
    /** Added in the Phase 11.8 (v2 -> v3) migration — the server's real owner user id (self's or
     * the partner's). Null only for rows imported before this migration ran, before the Safe
     * Locker synced to the server at all; [com.wwwescape.deviceinfox.console.data.vault.VaultRepository]
     * treats a null the same as "mine", matching what was implicitly true before this phase
     * (every local item was private to this device). */
    val ownerId: String? = null,
    /** `"IMAGE"`/`"VIDEO"`/`"DOCUMENT"` — every pre-existing row is implicitly `"IMAGE"`
     * (photo-only before this column existed), same string-based-kind convention as
     * [AttachmentEntity.attachmentKind]. */
    val attachmentKind: String = "IMAGE",
    /** For a photo, the photo itself. For a video, the poster-frame thumbnail. For a document,
     * unused (empty string) — see [AttachmentEntity.filePath]'s doc comment, same reasoning. */
    val mediaId: String? = null,
    /** Video only. */
    val durationMs: Int? = null,
    /** Video only, null until lazily downloaded — see [AttachmentEntity.localVideoFilePath]. */
    val localVideoFilePath: String? = null,
    /** Document only. */
    val originalFilename: String? = null,
    /** Document only, null until lazily downloaded. */
    val localDocumentFilePath: String? = null,
    /** Not previously tracked at all (a photo's byte size was never shown anywhere) — needed to
     * show a document's size in the viewer. */
    val sizeBytes: Long? = null,
    /** Same soft, unenforced-backlink shape as [originalMessageId] (a parallel column, not a
     * replacement — a "Save to Vault" can originate from either a chat message or a calendar
     * event's attachment, never both) — set when an item is saved from a calendar event's
     * attachment. */
    val originalEventId: String? = null,
    /** The *source* attachment's own remote media id, at the moment it was saved — deliberately
     * distinct from [mediaId] (this vault item's *own*, freshly-uploaded copy's asset id, used
     * for lazy video/document download). Needed only alongside [originalEventId]: unlike a
     * message (at most one attachment), an event can have several, so dedupe needs to identify
     * *which* attachment was saved, not just that the event has *a* saved item. Always null for
     * a message-origin save — one attachment per message means [originalMessageId] alone is
     * already unambiguous. */
    val originalAttachmentMediaId: String? = null,
)
