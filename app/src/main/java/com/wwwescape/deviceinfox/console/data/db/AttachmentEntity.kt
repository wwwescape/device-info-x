package com.wwwescape.deviceinfox.console.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "attachments",
    foreignKeys = [
        ForeignKey(
            entity = MessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["messageId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("messageId")],
)
data class AttachmentEntity(
    @PrimaryKey val id: String,
    val messageId: String,
    /** `"IMAGE"`/`"VIDEO"`/`"DOCUMENT"` — a plain string rather than a Room `@TypeConverter`'d
     * enum, kept local to this table rather than touching the shared `Converters` class. Set
     * once at insert time from the already-known message type; never re-derived from [mimeType]
     * (a document's mime type can be anything, including one that would collide with a
     * `mimeType.startsWith("video/")`-style sniff). */
    val attachmentKind: String = "IMAGE",
    /** For an image, the image itself. For a video, the poster-frame thumbnail — always present,
     * downloaded eagerly like [heightPx]/[widthPx] describe it; the full video only lives at
     * [localVideoFilePath], and only once that's been lazily fetched. For a document, unused
     * (empty string) — there's nothing to preview. */
    val filePath: String,
    /** The attachment's real mime type. */
    val mimeType: String,
    val widthPx: Int?,
    val heightPx: Int?,
    val sizeBytes: Long?,
    /** Video/document only — the remote asset id of the real file (not a video's thumbnail),
     * needed to lazily fetch it later. Null for images, which never need a second download. */
    val mediaId: String? = null,
    /** Video only. */
    val durationMs: Int? = null,
    /** Video only, null until [com.wwwescape.deviceinfox.console.data.messaging.MediaStorage.downloadVideoToMediaDir]
     * has fetched it — the sender's own row has this populated immediately (they already have
     * the file), a receiver's stays null until the viewer is opened. */
    val localVideoFilePath: String? = null,
    /** Document only — the picked file's real name, distinct from the randomized on-disk
     * filename every attachment type uses for local storage. */
    val originalFilename: String? = null,
    /** Document only, null until lazily downloaded — same shape as [localVideoFilePath]. */
    val localDocumentFilePath: String? = null,
)
