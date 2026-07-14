package com.wwwescape.deviceinfox.console.data.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class GenderDto {
    @SerialName("male") MALE,
    @SerialName("female") FEMALE,
    @SerialName("unspecified") UNSPECIFIED,
}

/** Mirrors the server's `UserPublic` schema (`app/schemas/user.py`) — the shape returned for
 * "myself" (`/auth/register`, `/auth/me`, `/users/me`). */
@Serializable
data class UserPublicDto(
    val id: String,
    val username: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("first_name") val firstName: String,
    @SerialName("last_name") val lastName: String,
    @SerialName("avatar_media_id") val avatarMediaId: String? = null,
    @SerialName("partner_id") val partnerId: String? = null,
    val gender: GenderDto,
    @SerialName("birthday_date") val birthdayDate: String,
    @SerialName("last_seen_at") val lastSeenAt: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("must_change_password") val mustChangePassword: Boolean,
    @SerialName("birthday_message") val birthdayMessage: String? = null,
)

/** Mirrors `PartnerPublic` — what one partner is allowed to see about the other
 * (`GET /users/partner`). No username — only presence/profile fields already shared. */
@Serializable
data class PartnerPublicDto(
    val id: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("first_name") val firstName: String,
    @SerialName("last_name") val lastName: String,
    @SerialName("avatar_media_id") val avatarMediaId: String? = null,
    val gender: GenderDto,
    @SerialName("birthday_date") val birthdayDate: String,
    @SerialName("last_seen_at") val lastSeenAt: String? = null,
    // Authored by *this* partner, meant to be shown on the local user's own birthday popup —
    // never on this partner's own. See ConsoleTabsViewModel.checkBirthday.
    @SerialName("birthday_message") val birthdayMessage: String? = null,
)

/** All fields optional — `PATCH /users/me` only updates what's non-null, per the server's
 * `UpdateProfileRequest`. */
@Serializable
data class UpdateProfileRequestDto(
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("first_name") val firstName: String? = null,
    @SerialName("last_name") val lastName: String? = null,
    val gender: GenderDto? = null,
    @SerialName("birthday_date") val birthdayDate: String? = null,
    @SerialName("birthday_message") val birthdayMessage: String? = null,
)

/** Mirrors `ChangePasswordRequest` (`POST /users/me/change-password`). */
@Serializable
data class ChangePasswordRequestDto(
    @SerialName("current_password") val currentPassword: String,
    @SerialName("new_password") val newPassword: String,
)

/** Mirrors `SeenToursOut` (`GET /users/me/seen-tours`) — which guided-tour `tourKey`s this
 * account has already dismissed, per [com.wwwescape.deviceinfox.console.data.tour.FeatureTourRepository]. */
@Serializable
data class SeenToursDto(
    @SerialName("seen_tour_keys") val seenTourKeys: List<String>,
)

/** Mirrors `SeenWhatsNewOut` (`GET /users/me/seen-whats-new`) — which "What's New" entry `tag`s
 * this account has already dismissed, per
 * [com.wwwescape.deviceinfox.console.data.whatsnew.WhatsNewRepository]. Exact structural mirror
 * of [SeenToursDto] above — the entry copy itself lives client-side in `WHATS_NEW_ENTRIES`, never
 * fetched. */
@Serializable
data class SeenWhatsNewDto(
    @SerialName("seen_tags") val seenTags: List<String>,
)
