package com.wwwescape.deviceinfox.console.data.network

import retrofit2.http.GET

/** Mirrors `app/api/v1/routers/health.py`. `/health/ready` (not `/live`) — Settings' "Server
 * status" row means "can I actually use this server right now," which needs the DB reachability
 * check `/live` deliberately skips. No auth required server-side; the shared Retrofit client
 * attaches a Bearer header anyway, which the endpoint just ignores. */
interface HealthApi {
    @GET("health/ready")
    suspend fun ready()
}
