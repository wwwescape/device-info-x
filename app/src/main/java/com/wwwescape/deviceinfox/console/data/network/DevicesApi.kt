package com.wwwescape.deviceinfox.console.data.network

import com.wwwescape.deviceinfox.console.data.network.dto.DeviceRegisterRequestDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.POST
import retrofit2.http.Path

/** Mirrors `app/api/v1/routers/devices.py`. Both endpoints require only [get_current_user], not
 * `require_paired` — a token can register/unregister before pairing completes. */
interface DevicesApi {
    @POST("devices")
    suspend fun register(@Body body: DeviceRegisterRequestDto)

    @DELETE("devices/{fcmToken}")
    suspend fun unregister(@Path("fcmToken") fcmToken: String)
}
