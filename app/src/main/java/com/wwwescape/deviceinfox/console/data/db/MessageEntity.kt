package com.wwwescape.deviceinfox.console.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** [body] is nullable for messages that are image/voice-only. Deletion is soft ([isDeleted])
 * rather than a row delete, so "message deleted" can still render a tombstone placeholder like
 * real messaging apps do, and so [ReactionEntity]/[AttachmentEntity]/[VoiceNoteEntity] rows
 * (CASCADE on messageId) aren't lost the moment a message is deleted. */
@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = MessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["replyToId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("replyToId"), Index("senderId"), Index("sentAtEpochMillis")],
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val senderId: String,
    val body: String?,
    val replyToId: String?,
    val sentAtEpochMillis: Long,
    val editedAtEpochMillis: Long?,
    val isDeleted: Boolean = false,
    val isStarred: Boolean = false,
    val isPinned: Boolean = false,
    val isRead: Boolean = false,
    val deliveryState: MessageDeliveryState = MessageDeliveryState.SENT,
    // Fetched asynchronously server-side shortly after send — all null means either "no link in
    // the body" or "the fetch found nothing usable," which aren't distinguished (no retry exists
    // to care which). linkPreviewImagePath is the locally-downloaded file, same shape as
    // AttachmentEntity.filePath.
    val linkPreviewUrl: String? = null,
    val linkPreviewTitle: String? = null,
    val linkPreviewDescription: String? = null,
    val linkPreviewImagePath: String? = null,
    // The raw timestamps behind isRead/deliveryState — kept alongside those derived fields rather
    // than instead of them (both still updated together everywhere) purely so the "Message info"
    // screen has something to actually show; nothing before that screen ever needed more than the
    // booleans. Null means "hasn't happened yet," same as the server's own read_at/delivered_at.
    val readAtEpochMillis: Long? = null,
    val deliveredAtEpochMillis: Long? = null,
    // Poll-only — sit unused (default) on every other message, same treatment the linkPreview*
    // fields above already get for text-only data. Whether this message even *is* a poll is
    // inferred the same way image/video/document/voice already are: by whether a related
    // PollOptionEntity row exists, not from a stored "type" column (this table has never had one).
    val pollAllowsMultiple: Boolean = false,
    val pollClosedAtEpochMillis: Long? = null,
    // LOCATION-only — a one-time GPS snapshot captured at send time, same "unused/null on every
    // other type" treatment as the poll* fields above. Whether this message even *is* a location
    // share is inferred the same way image/video/document/voice/poll already are: by whether
    // these are non-null, not from a stored "type" column (this table has never had one).
    val locationLat: Double? = null,
    val locationLng: Double? = null,
)
