package com.wwwescape.deviceinfox.console.data.network.dto

import kotlinx.serialization.Serializable

/** Mirrors `app/schemas/assets.py`'s `AssetListResponse`. */
@Serializable
data class AssetListResponseDto(
    val standard: List<String> = emptyList(),
    val nsfw: List<String> = emptyList(),
)
