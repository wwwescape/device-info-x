package com.wwwescape.deviceinfox.console.data.network

import com.wwwescape.deviceinfox.console.data.network.dto.MatureEmojiListResponseDto
import retrofit2.http.GET

/** Mirrors `app/api/v1/routers/emojis.py`. Only the list call goes through Retrofit — each
 * emoji's PNG is loaded straight by Coil from `emojis/nsfw/{id}` (see [matureEmojiImageUrl]),
 * same as the GIF picker's thumbnails. */
interface MatureEmojiApi {
    @GET("emojis/nsfw")
    suspend fun list(): MatureEmojiListResponseDto
}
