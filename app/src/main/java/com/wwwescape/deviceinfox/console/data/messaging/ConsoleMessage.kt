package com.wwwescape.deviceinfox.console.data.messaging

import com.wwwescape.deviceinfox.console.data.db.MessageDeliveryState

/** UI-friendly projection of [com.wwwescape.deviceinfox.console.data.db.MessageWithDetails] —
 * built in [MessageRepository], so nothing above the data layer needs to know about Room
 * relations or which partner id is "self". */
data class ConsoleMessage(
    val id: String,
    val senderId: String,
    val isFromSelf: Boolean,
    val body: String?,
    val sentAtEpochMillis: Long,
    val editedAtEpochMillis: Long?,
    val isDeleted: Boolean,
    val isStarred: Boolean,
    val isPinned: Boolean,
    val isRead: Boolean,
    val replyPreview: ReplyPreview?,
    val attachment: MessageAttachment?,
    val voiceNote: MessageVoiceNote?,
    val linkPreview: MessageLinkPreview?,
    val reactions: List<MessageReaction>,
    /** The self partner's own reaction emoji on this message, if any — lets the UI toggle
     * (tapping the same emoji again removes it) without needing to know its own partner id. */
    val myReaction: String?,
    /** Always [MessageDeliveryState.SENT] for a message from the partner or one already synced
     * from the server — only a message this device just sent can be [MessageDeliveryState.SENDING]
     * (optimistic, not yet round-tripped) or [MessageDeliveryState.FAILED]. */
    val deliveryState: MessageDeliveryState,
    /** Backs the "Message info" screen — null means "hasn't happened yet" (or, for a message sent
     * before this field existed, "happened but the timestamp itself was never recorded locally,"
     * see [com.wwwescape.deviceinfox.console.data.db.MIGRATION_20_21]'s own doc comment). */
    val readAtEpochMillis: Long?,
    val deliveredAtEpochMillis: Long?,
    /** Null for every non-poll message — presence of this, not a stored "type" field, is what
     * tells the UI a message is a poll, same "inferred from a related row" shape [attachment]/
     * [voiceNote] already use. */
    val poll: MessagePoll?,
)

/** [bodyPreview] null doesn't by itself mean "deleted" — a media message (image/video/voice
 * note/document) has no text body either, and [isDeleted]/[hasMedia] are what the UI needs to
 * tell those two null-body cases apart (see `HomeScreen.kt`'s reply-preview `Text`). */
data class ReplyPreview(
    val messageId: String,
    val senderId: String,
    val bodyPreview: String?,
    val isDeleted: Boolean,
    val hasMedia: Boolean,
)

/** Set once, at insert time, from the already-known [com.wwwescape.deviceinfox.console.data.network.dto.MessageTypeDto]
 * the message was sent/received as — deliberately never re-derived from [MessageAttachment.mimeType]
 * (the way an earlier version of this code detected video via `mimeType.startsWith("video/")`):
 * a document picked through a generic file picker can carry *any* mime type, including one that
 * would collide with that sniff (e.g. picking an actual .mp4 as a "document"). */
enum class MessageAttachmentKind { IMAGE, VIDEO, DOCUMENT }

/** Despite the name, also used verbatim for Calendar event attachments
 * ([com.wwwescape.deviceinfox.console.data.calendar.CalendarItem.attachments]) — nothing here is
 * actually message-specific (no `messageId`, no message-only field), so Calendar reuses this
 * type rather than duplicating an identical class under a different name. */
data class MessageAttachment(
    val kind: MessageAttachmentKind,
    /** For an image, the image itself; for a video, the poster-frame thumbnail; for a document,
     * unused (empty string) — see
     * [com.wwwescape.deviceinfox.console.data.db.AttachmentEntity.filePath]'s doc comment. */
    val filePath: String,
    val mimeType: String,
    val widthPx: Int?,
    val heightPx: Int?,
    /** Document only today — an image/video's size was never previously needed by the UI. */
    val sizeBytes: Long? = null,
    /** Video/document only — the asset's own remote id, needed to lazily fetch it. */
    val mediaId: String? = null,
    /** Video only. */
    val durationMillis: Long? = null,
    /** Video only, null until lazily downloaded — see
     * [com.wwwescape.deviceinfox.console.data.db.AttachmentEntity.localVideoFilePath]. */
    val localVideoFilePath: String? = null,
    /** Document only — the picked file's real name, as opposed to the randomized local storage
     * filename every attachment type uses on disk. */
    val originalFilename: String? = null,
    /** Document only, null until lazily downloaded — same "sender has it immediately, receiver
     * doesn't until it's fetched" shape as [localVideoFilePath]. */
    val localDocumentFilePath: String? = null,
)

data class MessageVoiceNote(
    val filePath: String,
    val durationMillis: Long,
)

/** [imagePath] is the locally-downloaded thumbnail — proxied server-side through the media-asset
 * pipeline rather than loaded straight from [url]'s own host, so the recipient's device never
 * contacts an arbitrary third-party URL (same reasoning the OG-tag fetch itself is server-side
 * for). Null [imagePath] just means the fetched page had no og:image, not a failure. */
data class MessageLinkPreview(
    val url: String,
    val title: String?,
    val description: String?,
    val imagePath: String?,
)

data class MessageReaction(
    val partnerId: String,
    val emoji: String,
    /** Whether this specific reaction is the self partner's own — [ConsoleMessage.myReaction]
     * alone can't disambiguate which reaction is self's once a message could carry 2 different
     * reactions (one per partner, this app's hard cap — see [MessageReaction]'s own uniqueness
     * constraint server- and client-side). Computed identically to [MessagePollOption.votedBySelf]. */
    val isMine: Boolean,
)

/** [closedAtEpochMillis] non-null means neither partner can vote anymore (results stay visible) —
 * only the poll's own sender can close it, matching WhatsApp. */
data class MessagePoll(
    val allowsMultiple: Boolean,
    val closedAtEpochMillis: Long?,
    val options: List<MessagePollOption>,
) {
    val totalVotes: Int get() = options.sumOf { it.voteCount }
    val isClosed: Boolean get() = closedAtEpochMillis != null
}

data class MessagePollOption(
    val id: String,
    val text: String,
    val orderIndex: Int,
    val votedByPartnerIds: List<String>,
    val votedBySelf: Boolean,
) {
    val voteCount: Int get() = votedByPartnerIds.size
}
