package com.wwwescape.deviceinfox.console.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Same column shape as [AttachmentEntity], keyed to [CalendarEventEntity] instead of
 * `MessageEntity` — Room ties a `@ForeignKey` to exactly one parent table per entity, so this
 * can't just be `AttachmentEntity` reused with a different FK; the duplication mirrors how
 * `AttachmentEntity`/`VoiceNoteEntity` are already separate tables despite conceptual overlap.
 *
 * One deliberate divergence from [AttachmentEntity]: [mediaId] is populated for *every* kind,
 * including images (not just video/document) — `CalendarRepository`'s sync reconciliation needs
 * to diff "which attachments does the server say exist now" by media id across all three kinds,
 * not just two. */
@Entity(
    tableName = "calendar_attachments",
    foreignKeys = [
        ForeignKey(
            entity = CalendarEventEntity::class,
            parentColumns = ["id"],
            childColumns = ["eventId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("eventId")],
)
data class CalendarAttachmentEntity(
    @PrimaryKey val id: String,
    val eventId: String,
    val attachmentKind: String = "IMAGE",
    /** For an image, the image itself. For a video, the poster-frame thumbnail. For a document,
     * unused (empty string) — see [AttachmentEntity.filePath]'s doc comment, same reasoning. */
    val filePath: String,
    val mimeType: String,
    val widthPx: Int?,
    val heightPx: Int?,
    val sizeBytes: Long?,
    val mediaId: String? = null,
    /** Video only. */
    val durationMs: Int? = null,
    /** Video only, null until lazily downloaded — see [AttachmentEntity.localVideoFilePath]. */
    val localVideoFilePath: String? = null,
    /** Document only. */
    val originalFilename: String? = null,
    /** Document only, null until lazily downloaded. */
    val localDocumentFilePath: String? = null,
)
