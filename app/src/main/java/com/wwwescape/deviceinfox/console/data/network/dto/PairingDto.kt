package com.wwwescape.deviceinfox.console.data.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The only response that ever carries the full plaintext code — the server never stores it and
 * can't return it again after this, see `app/schemas/pairing.py`. */
@Serializable
data class PartnerCodeCreateResponseDto(
    val code: String,
    @SerialName("code_preview") val codePreview: String,
    @SerialName("expires_at") val expiresAt: String,
)

@Serializable
data class PartnerCodeStatusResponseDto(
    @SerialName("has_active_code") val hasActiveCode: Boolean,
    @SerialName("code_preview") val codePreview: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
)

@Serializable
data class PairRequestDto(val code: String)

@Serializable
data class PairingStatusResponseDto(
    val paired: Boolean,
    val partner: PartnerPublicDto? = null,
)
