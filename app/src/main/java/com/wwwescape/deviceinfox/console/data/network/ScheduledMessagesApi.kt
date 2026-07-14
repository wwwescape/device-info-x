package com.wwwescape.deviceinfox.console.data.network

import com.wwwescape.deviceinfox.console.data.network.dto.ScheduledMessageCreateDto
import com.wwwescape.deviceinfox.console.data.network.dto.ScheduledMessageOutDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/** Mirrors `app/api/v1/routers/scheduled_messages.py`. Create/cancel/send-now all push over the
 * WebSocket (`scheduled_message_service.py`'s `_push`, sender's own other devices only — see its
 * own doc comment), same as `NotepadApi`/`LockerApi`/`CalendarApi`. */
interface ScheduledMessagesApi {
    @GET("messages/scheduled")
    suspend fun list(): List<ScheduledMessageOutDto>

    @POST("messages/scheduled")
    suspend fun create(@Body body: ScheduledMessageCreateDto): ScheduledMessageOutDto

    @DELETE("messages/scheduled/{id}")
    suspend fun cancel(@Path("id") id: String)

    @POST("messages/scheduled/{id}/send-now")
    suspend fun sendNow(@Path("id") id: String)
}
