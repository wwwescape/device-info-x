package com.wwwescape.deviceinfox.console.data.network

import com.wwwescape.deviceinfox.console.data.network.dto.MediaAssetOutDto
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Streaming
import retrofit2.http.GET

/** Mirrors `app/api/v1/routers/media.py`. A received message's image dimensions/voice-note
 * duration are still derived locally after downloading (see `MessageMediaSync`) — [getMetadata]
 * exists only for the one case that can't be resolved that way: learning a *video* message's
 * `thumbnailMediaId` before its bytes are ever downloaded (video downloads lazily, unlike
 * image/voice). */
interface MediaApi {
    @Multipart
    @POST("media")
    suspend fun upload(
        @Part("category") category: RequestBody,
        @Part file: MultipartBody.Part,
        @Part("duration_ms") durationMs: RequestBody? = null,
        @Part("thumbnail_media_id") thumbnailMediaId: RequestBody? = null,
    ): MediaAssetOutDto

    @GET("media/{id}")
    suspend fun getMetadata(@Path("id") id: String): MediaAssetOutDto

    @Streaming
    @GET("media/{id}/download")
    suspend fun download(@Path("id") id: String): ResponseBody
}
