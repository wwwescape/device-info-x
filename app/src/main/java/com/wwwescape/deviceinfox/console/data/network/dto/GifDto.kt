package com.wwwescape.deviceinfox.console.data.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Mirrors `app/schemas/gifs.py`'s `GifResult` — [previewUrl]/[fullUrl] are always this server's
 * own signed `/gifs/proxy` links, never Klipy's CDN directly (see that schema's own doc comment
 * on the privacy invariant this preserves). */
@Serializable
data class GifResultDto(
    val id: String,
    val title: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    @SerialName("preview_url") val previewUrl: String,
    @SerialName("full_url") val fullUrl: String,
)

@Serializable
data class GifListResponseDto(
    val items: List<GifResultDto>,
)
