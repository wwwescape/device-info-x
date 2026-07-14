package com.wwwescape.deviceinfox.console.data.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class MessageTypeDto {
    @SerialName("text") TEXT,
    @SerialName("image") IMAGE,
    @SerialName("voice") VOICE,
    @SerialName("video") VIDEO,
    @SerialName("document") DOCUMENT,
    @SerialName("poll") POLL,
    @SerialName("system") SYSTEM,
}

@Serializable
data class PollOptionCreateDto(val text: String)

@Serializable
data class PollCreateDto(
    val options: List<PollOptionCreateDto>,
    @SerialName("allows_multiple") val allowsMultiple: Boolean = false,
)

@Serializable
data class MessageCreateDto(
    val type: MessageTypeDto,
    val body: String? = null,
    @SerialName("media_id") val mediaId: String? = null,
    @SerialName("reply_to_id") val replyToId: String? = null,
    @SerialName("client_message_id") val clientMessageId: String,
    val poll: PollCreateDto? = null,
)

@Serializable
data class MessageUpdateDto(val body: String)

/** Body for `PUT /messages/{id}/poll/vote` — the voter's complete current selection, not an
 * incremental toggle. See the server's own `PollVoteUpdate` doc comment for why. */
@Serializable
data class PollVoteUpdateDto(@SerialName("option_ids") val optionIds: List<String>)

@Serializable
data class ReactionCreateDto(val emoji: String)

@Serializable
data class ReactionOutDto(
    @SerialName("user_id") val userId: String,
    val emoji: String,
)

@Serializable
data class PollOptionOutDto(
    val id: String,
    val text: String,
    @SerialName("order_index") val orderIndex: Int,
    @SerialName("vote_count") val voteCount: Int,
    @SerialName("voted_by") val votedBy: List<String> = emptyList(),
)

@Serializable
data class PollOutDto(
    @SerialName("allows_multiple") val allowsMultiple: Boolean,
    @SerialName("closed_at") val closedAt: String? = null,
    val options: List<PollOptionOutDto> = emptyList(),
)

/** Mirrors `app/schemas/message.py`'s `MessageOut`. */
@Serializable
data class MessageOutDto(
    val id: String,
    @SerialName("sender_id") val senderId: String,
    val type: MessageTypeDto,
    val body: String? = null,
    @SerialName("media_id") val mediaId: String? = null,
    @SerialName("reply_to_id") val replyToId: String? = null,
    @SerialName("client_message_id") val clientMessageId: String,
    @SerialName("edited_at") val editedAt: String? = null,
    @SerialName("deleted_at") val deletedAt: String? = null,
    @SerialName("read_at") val readAt: String? = null,
    @SerialName("delivered_at") val deliveredAt: String? = null,
    @SerialName("is_pinned") val isPinned: Boolean = false,
    @SerialName("pinned_at") val pinnedAt: String? = null,
    @SerialName("created_at") val createdAt: String,
    val reactions: List<ReactionOutDto> = emptyList(),
    @SerialName("is_starred_by_me") val isStarredByMe: Boolean = false,
    @SerialName("link_preview_url") val linkPreviewUrl: String? = null,
    @SerialName("link_preview_title") val linkPreviewTitle: String? = null,
    @SerialName("link_preview_description") val linkPreviewDescription: String? = null,
    @SerialName("link_preview_media_id") val linkPreviewMediaId: String? = null,
    val poll: PollOutDto? = null,
)
