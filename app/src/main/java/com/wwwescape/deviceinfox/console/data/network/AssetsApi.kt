package com.wwwescape.deviceinfox.console.data.network

import com.wwwescape.deviceinfox.console.data.network.dto.AssetListResponseDto
import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

/** Mirrors `app/api/v1/routers/assets.py`. Only list calls and a sticker's bytes go through
 * Retrofit — every image *displayed* in a picker or pop animation is loaded straight by Coil from
 * `assets/{kind}/{tier}/{id}` (see `assetImageUrl`), same as the GIF picker's thumbnails. */
interface AssetsApi {
    @GET("assets/{kind}")
    suspend fun list(
        @Path("kind") kind: String,
        @Query("mature") mature: Boolean = false,
    ): AssetListResponseDto

    /** A sticker's PNG bytes, for sending it as an ordinary image message. [id] may contain one
     * `/` (`style/name`), so it's `encoded = true` — otherwise Retrofit would send it as `%2F`. Ids
     * are pre-validated (`isValidAssetId`), so nothing here needs escaping. */
    @Streaming
    @GET("assets/stickers/{tier}/{id}")
    suspend fun sticker(
        @Path("tier") tier: String,
        @Path(value = "id", encoded = true) id: String,
    ): ResponseBody
}
