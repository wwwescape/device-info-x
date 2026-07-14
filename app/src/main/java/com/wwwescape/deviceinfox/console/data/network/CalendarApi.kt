package com.wwwescape.deviceinfox.console.data.network

import com.wwwescape.deviceinfox.console.data.network.dto.CalendarEventCreateDto
import com.wwwescape.deviceinfox.console.data.network.dto.CalendarEventOutDto
import com.wwwescape.deviceinfox.console.data.network.dto.CalendarEventUpdateDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** Mirrors `app/api/v1/routers/calendar.py`. Planned dates have no WebSocket push (confirmed —
 * `calendar_service.py` never calls into `ws/`), so freshness relies entirely on [list] being
 * re-run (see `CalendarRepository`), not realtime delivery like messaging. */
interface CalendarApi {
    @GET("calendar/events")
    suspend fun list(@Query("from") from: String, @Query("to") to: String): List<CalendarEventOutDto>

    @POST("calendar/events")
    suspend fun create(@Body body: CalendarEventCreateDto): CalendarEventOutDto

    @PATCH("calendar/events/{id}")
    suspend fun update(@Path("id") id: String, @Body body: CalendarEventUpdateDto): CalendarEventOutDto

    @DELETE("calendar/events/{id}")
    suspend fun delete(@Path("id") id: String)

    @DELETE("calendar")
    suspend fun wipeAll()
}
