package com.wwwescape.deviceinfox.console.data.network

import com.wwwescape.deviceinfox.console.data.network.dto.PeriodDayLogCreateDto
import com.wwwescape.deviceinfox.console.data.network.dto.PeriodDayLogOutDto
import com.wwwescape.deviceinfox.console.data.network.dto.PeriodDayLogUpdateDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** Mirrors `app/api/v1/routers/period.py`. [list] with `userId` omitted defaults to the caller's
 * own day logs server-side; passing the partner's id is allowed for read (mutual visibility, per
 * the locked-in `SERVER_PHASES.md` decision), but [create]/[update]/[delete] are owner-only —
 * `create` always attaches to the authenticated user regardless of any client-supplied target,
 * and `update`/`delete` 403 if the day log isn't the caller's own (`period_service.py`). No
 * WebSocket push exists for this either (unlike `CalendarApi`). */
interface PeriodApi {
    @GET("period/days")
    suspend fun list(@Query("user_id") userId: String? = null): List<PeriodDayLogOutDto>

    @POST("period/days")
    suspend fun create(@Body body: PeriodDayLogCreateDto): PeriodDayLogOutDto

    @PATCH("period/days/{id}")
    suspend fun update(@Path("id") id: String, @Body body: PeriodDayLogUpdateDto): PeriodDayLogOutDto

    @DELETE("period/days/{id}")
    suspend fun delete(@Path("id") id: String)

    @DELETE("period/days")
    suspend fun deleteAllMine()
}
