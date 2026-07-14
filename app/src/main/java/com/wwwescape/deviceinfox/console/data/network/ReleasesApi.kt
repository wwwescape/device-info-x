package com.wwwescape.deviceinfox.console.data.network

import com.wwwescape.deviceinfox.console.data.network.dto.ReleaseOutDto
import retrofit2.http.GET

/** Mirrors `app/api/v1/routers/releases.py`. Requires a valid access token (`get_current_user`)
 * but not pairing — checking for an update shouldn't be gated on having a partner paired yet. */
interface ReleasesApi {
    @GET("releases/latest")
    suspend fun latest(): ReleaseOutDto
}
