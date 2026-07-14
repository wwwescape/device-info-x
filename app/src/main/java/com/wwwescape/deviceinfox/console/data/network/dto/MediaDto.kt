package com.wwwescape.deviceinfox.console.data.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class MediaCategoryDto {
    @SerialName("message_image") MESSAGE_IMAGE,
    @SerialName("message_voice") MESSAGE_VOICE,
    @SerialName("message_video") MESSAGE_VIDEO,
    @SerialName("message_document") MESSAGE_DOCUMENT,
    @SerialName("calendar_image") CALENDAR_IMAGE,
    @SerialName("calendar_video") CALENDAR_VIDEO,
    @SerialName("calendar_document") CALENDAR_DOCUMENT,
    @SerialName("avatar") AVATAR,
    @SerialName("locker_file") LOCKER_FILE,
}

/** The multipart form field the server expects (`app/models/media_asset.py`'s enum values) —
 * kept as an explicit mapping rather than round-tripping through the JSON encoder, since this
 * is a form field, not a JSON body. */
val MediaCategoryDto.wireValue: String
    get() = when (this) {
        MediaCategoryDto.MESSAGE_IMAGE -> "message_image"
        MediaCategoryDto.MESSAGE_VOICE -> "message_voice"
        MediaCategoryDto.MESSAGE_VIDEO -> "message_video"
        MediaCategoryDto.MESSAGE_DOCUMENT -> "message_document"
        MediaCategoryDto.CALENDAR_IMAGE -> "calendar_image"
        MediaCategoryDto.CALENDAR_VIDEO -> "calendar_video"
        MediaCategoryDto.CALENDAR_DOCUMENT -> "calendar_document"
        MediaCategoryDto.AVATAR -> "avatar"
        MediaCategoryDto.LOCKER_FILE -> "locker_file"
    }

/** Mirrors `app/schemas/media.py`'s `MediaAssetOut` — width/height are probed server-side for
 * images (never sent by the client), `duration_ms` is client-supplied for voice/video uploads
 * only, `thumbnail_media_id` is set only on `MESSAGE_VIDEO` assets, pointing at a separate
 * `MESSAGE_IMAGE` asset holding the poster frame, and `original_filename` is only meaningful for
 * `MESSAGE_DOCUMENT` assets (every other category names its own file predictably). */
@Serializable
data class MediaAssetOutDto(
    val id: String,
    @SerialName("owner_id") val ownerId: String,
    val category: MediaCategoryDto,
    @SerialName("original_filename") val originalFilename: String? = null,
    @SerialName("mime_type") val mimeType: String,
    @SerialName("size_bytes") val sizeBytes: Long,
    val width: Int? = null,
    val height: Int? = null,
    @SerialName("duration_ms") val durationMs: Int? = null,
    @SerialName("thumbnail_media_id") val thumbnailMediaId: String? = null,
    @SerialName("created_at") val createdAt: String,
)
