package com.wwwescape.deviceinfox.console.data.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequestDto(
    val username: String,
    val password: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("first_name") val firstName: String,
    @SerialName("last_name") val lastName: String,
    val gender: GenderDto,
    @SerialName("birthday_date") val birthdayDate: String,
    @SerialName("setup_token") val setupToken: String,
)

@Serializable
data class LoginRequestDto(
    val username: String,
    val password: String,
    @SerialName("device_label") val deviceLabel: String? = null,
    // Auto-detected, never asked for — lets the server's birthday sweep compute this user's own
    // local midnight, and self-corrects on every login if the device's timezone has changed.
    @SerialName("timezone") val timezone: String? = null,
)

@Serializable
data class RefreshRequestDto(@SerialName("refresh_token") val refreshToken: String)

@Serializable
data class LogoutRequestDto(@SerialName("refresh_token") val refreshToken: String)

/** Mirrors `VerifyPasswordRequest` (`POST /auth/verify-password`) — re-proves identity for an
 * already-authenticated session (the "Forgot code?" local-PIN-reset flow) without minting new
 * tokens the way a fresh login call would. */
@Serializable
data class VerifyPasswordRequestDto(val password: String)

@Serializable
data class TokenPairDto(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("token_type") val tokenType: String = "bearer",
)
