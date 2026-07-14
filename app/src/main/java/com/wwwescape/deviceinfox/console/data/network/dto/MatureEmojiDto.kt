package com.wwwescape.deviceinfox.console.data.network.dto

import kotlinx.serialization.Serializable

/** Mirrors `app/schemas/emojis.py`. */
@Serializable
data class MatureEmojiDto(
    val id: String,
)

@Serializable
data class MatureEmojiListResponseDto(
    val items: List<MatureEmojiDto>,
)
