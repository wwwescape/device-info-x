package com.wwwescape.deviceinfox.console.data.network

import com.wwwescape.deviceinfox.console.data.network.dto.LocationStatusResponseDto
import com.wwwescape.deviceinfox.console.data.network.dto.LocationUpdateDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/** Mirrors `app/api/v1/routers/location.py`. Deliberately REST, not a WS `send()` like typing/
 * read-receipts — [update] is called from [com.wwwescape.deviceinfox.console.data.location.LiveLocationService],
 * a foreground service that must keep working while the app (and its WebSocket connection) is
 * backgrounded; see `ConsoleWebSocketClient`'s own doc comment on why the socket doesn't survive
 * that. */
interface LocationApi {
    @POST("location/enable")
    suspend fun enable()

    @POST("location/disable")
    suspend fun disable()

    @POST("location/update")
    suspend fun update(@Body body: LocationUpdateDto)

    @GET("location/status")
    suspend fun status(): LocationStatusResponseDto
}
