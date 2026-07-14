package com.wwwescape.deviceinfox.console.data.network

import com.wwwescape.deviceinfox.console.data.network.dto.TurnCredentialsDto
import retrofit2.http.GET

/** Mirrors `app/api/v1/routers/turn.py`. Requires a valid access token but not pairing — matches
 * that router's own `Depends(get_current_user)`, not `require_paired`. */
interface TurnApi {
    @GET("turn/credentials")
    suspend fun credentials(): TurnCredentialsDto
}
