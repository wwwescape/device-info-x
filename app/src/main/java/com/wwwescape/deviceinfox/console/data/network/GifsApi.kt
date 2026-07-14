package com.wwwescape.deviceinfox.console.data.network

import com.wwwescape.deviceinfox.console.data.network.dto.GifListResponseDto
import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Streaming
import retrofit2.http.Url

/** Mirrors `app/api/v1/routers/gifs.py`. Every URL a [GifListResponseDto] item carries is already
 * one of this server's own signed `/gifs/proxy` links (an absolute path, e.g.
 * `/api/v1/gifs/proxy?u=...&exp=...&sig=...`) — [proxy] just streams whatever it's given via
 * Retrofit's [Url], the same way [MediaApi.download] streams a known media id, rather than
 * needing its own understanding of the `u`/`exp`/`sig` query params. */
interface GifsApi {
    @GET("gifs/search")
    suspend fun search(
        @Query("q") query: String,
        @Query("page") page: Int = 1,
        @Query("mature") mature: Boolean = false,
    ): GifListResponseDto

    @GET("gifs/trending")
    suspend fun trending(
        @Query("page") page: Int = 1,
        @Query("mature") mature: Boolean = false,
    ): GifListResponseDto

    @Streaming
    @GET
    suspend fun proxy(@Url proxyPath: String): ResponseBody
}
