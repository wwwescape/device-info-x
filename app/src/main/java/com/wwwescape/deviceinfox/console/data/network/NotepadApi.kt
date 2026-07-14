package com.wwwescape.deviceinfox.console.data.network

import com.wwwescape.deviceinfox.console.data.network.dto.NotepadEntryCreateDto
import com.wwwescape.deviceinfox.console.data.network.dto.NotepadEntryOutDto
import com.wwwescape.deviceinfox.console.data.network.dto.NotepadEntryUpdateDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

/** Mirrors `app/api/v1/routers/notepad.py`. Both create/update/delete push over the WebSocket
 * (`notepad_service.py` calls `notification_service.notify_user` on every mutation), same as
 * `LockerApi`/`CalendarApi` — see `NotepadRepository`'s own doc comment for the privacy-scoped
 * push shape. */
interface NotepadApi {
    @GET("notepad/private")
    suspend fun listPrivate(): List<NotepadEntryOutDto>

    @GET("notepad/shared")
    suspend fun listShared(): List<NotepadEntryOutDto>

    @POST("notepad")
    suspend fun create(@Body body: NotepadEntryCreateDto): NotepadEntryOutDto

    @PATCH("notepad/{id}")
    suspend fun update(@Path("id") id: String, @Body body: NotepadEntryUpdateDto): NotepadEntryOutDto

    @DELETE("notepad/{id}")
    suspend fun delete(@Path("id") id: String)

    @POST("notepad/{id}/duplicate")
    suspend fun duplicate(@Path("id") id: String): NotepadEntryOutDto
}
