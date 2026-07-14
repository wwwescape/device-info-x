package com.wwwescape.deviceinfox.console.data.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Mirrors `app/models/locker.py`'s `LockerCategory`. The console imports photos, videos, and
 * documents (see `VaultRepository.importImages`/`importVideos`/`importDocuments`) — [NOTE]/
 * [OTHER] are still read-only round-trip support only, in case some other client ever creates
 * one. */
@Serializable
enum class LockerCategoryDto {
    @SerialName("document") DOCUMENT,
    @SerialName("photo") PHOTO,
    @SerialName("video") VIDEO,
    @SerialName("note") NOTE,
    @SerialName("other") OTHER,
}

@Serializable
data class LockerAlbumCreateDto(val name: String)

@Serializable
data class LockerAlbumUpdateDto(val name: String)

@Serializable
data class LockerAlbumOutDto(
    val id: String,
    @SerialName("owner_id") val ownerId: String,
    val name: String,
    @SerialName("created_at") val createdAt: String,
)

/** `title` has no console-side field (only [description], which is the vault item's optional
 * caption) — the server requires a non-blank title, so callers synthesize one from the caption
 * (see `VaultRepository`). `is_encrypted`/`encryption_metadata` are unused server-side
 * placeholders for a future feature (per `locker.py`'s model doc comment) and are omitted here,
 * same reasoning as the other DTOs' server-only fields. */
@Serializable
data class LockerItemCreateDto(
    val title: String,
    val description: String? = null,
    @SerialName("media_id") val mediaId: String? = null,
    @SerialName("album_id") val albumId: String? = null,
    val category: LockerCategoryDto,
    @SerialName("is_favorite") val isFavorite: Boolean = false,
    // Set only when saving from an existing message/event attachment — backs the server-side
    // dedupe unique constraint on locker_items (see VaultRepository.saveAttachmentToVault),
    // independent of this device's own local Room dedupe check.
    @SerialName("original_message_id") val originalMessageId: String? = null,
    @SerialName("original_event_id") val originalEventId: String? = null,
    @SerialName("original_attachment_media_id") val originalAttachmentMediaId: String? = null,
)

/** Every field is always sent with its full current-or-new value (never a bare null standing in
 * for "leave untouched") — `VaultRepository`'s three separate mutation entry points
 * (favorite/album/caption) each resend the item's other current fields alongside the one that
 * actually changed, the same "full replace" approach `CalendarEventUpdateDto`/
 * `PeriodDayLogUpdateDto` use. That sidesteps `encodeDefaults = true` always emitting a `null`
 * for any field left at its Kotlin default, which the server's `exclude_unset` can't tell apart
 * from "not sent". */
@Serializable
data class LockerItemUpdateDto(
    val title: String,
    val description: String? = null,
    @SerialName("album_id") val albumId: String? = null,
    @SerialName("is_favorite") val isFavorite: Boolean,
)

@Serializable
data class LockerItemOutDto(
    val id: String,
    @SerialName("owner_id") val ownerId: String,
    val title: String,
    val description: String? = null,
    @SerialName("media_id") val mediaId: String? = null,
    @SerialName("album_id") val albumId: String? = null,
    val category: LockerCategoryDto,
    @SerialName("is_favorite") val isFavorite: Boolean = false,
    @SerialName("created_at") val createdAt: String,
)
