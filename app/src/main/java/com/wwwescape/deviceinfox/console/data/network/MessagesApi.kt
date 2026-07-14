package com.wwwescape.deviceinfox.console.data.network

import com.wwwescape.deviceinfox.console.data.network.dto.MessageCreateDto
import com.wwwescape.deviceinfox.console.data.network.dto.MessageOutDto
import com.wwwescape.deviceinfox.console.data.network.dto.MessageUpdateDto
import com.wwwescape.deviceinfox.console.data.network.dto.PollVoteUpdateDto
import com.wwwescape.deviceinfox.console.data.network.dto.ReactionCreateDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/** Mirrors `app/api/v1/routers/messages.py`. Every endpoint here requires the account to be
 * paired server-side (`require_paired`) — a `ConsoleApiException` with `httpStatusCode == 403`
 * is how that surfaces if called before pairing completes. */
interface MessagesApi {
    @GET("messages")
    suspend fun list(
        @Query("before") before: String? = null,
        @Query("after") after: String? = null,
        @Query("limit") limit: Int = 100,
    ): List<MessageOutDto>

    @GET("messages/search")
    suspend fun search(@Query("q") query: String, @Query("limit") limit: Int = 50): List<MessageOutDto>

    @POST("messages")
    suspend fun send(@Body body: MessageCreateDto): MessageOutDto

    @PATCH("messages/{id}")
    suspend fun edit(@Path("id") id: String, @Body body: MessageUpdateDto): MessageOutDto

    @DELETE("messages/{id}")
    suspend fun delete(@Path("id") id: String)

    @DELETE("messages")
    suspend fun deleteAllMine()

    @POST("messages/{id}/pin")
    suspend fun pin(@Path("id") id: String): MessageOutDto

    @DELETE("messages/{id}/pin")
    suspend fun unpin(@Path("id") id: String): MessageOutDto

    @POST("messages/{id}/star")
    suspend fun star(@Path("id") id: String)

    @DELETE("messages/{id}/star")
    suspend fun unstar(@Path("id") id: String)

    @POST("messages/{id}/reactions")
    suspend fun addReaction(@Path("id") id: String, @Body body: ReactionCreateDto): MessageOutDto

    @DELETE("messages/{id}/reactions")
    suspend fun removeReaction(@Path("id") id: String)

    @PUT("messages/{id}/poll/vote")
    suspend fun votePoll(@Path("id") id: String, @Body body: PollVoteUpdateDto): MessageOutDto

    @POST("messages/{id}/poll/close")
    suspend fun closePoll(@Path("id") id: String): MessageOutDto

    @POST("messages/{id}/read")
    suspend fun markRead(@Path("id") id: String): MessageOutDto

    @POST("messages/{id}/delivered")
    suspend fun markDelivered(@Path("id") id: String): MessageOutDto

    /** Mirrors `app/api/v1/routers/presence.py`, not `messages.py` — a manual "I'm online" push
     * to the partner, rate-limited to once per 15 minutes server-side (`ConsoleApiException` with
     * `httpStatusCode == 429` if called again too soon). Declared here rather than a separate
     * `PresenceApi` since it's fired from the same Messages header icon this interface otherwise
     * backs, and it's the only presence-related REST call the client makes. */
    @POST("presence/notify-online")
    suspend fun notifyOnline()
}
