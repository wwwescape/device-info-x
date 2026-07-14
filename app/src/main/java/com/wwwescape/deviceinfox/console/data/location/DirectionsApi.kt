package com.wwwescape.deviceinfox.console.data.location

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

/** Google's Directions API — called directly from the client with its own restricted API key
 * (see TODOS.md's "direct from client" decision), not proxied through the self-hosted server.
 * Deliberately a *separate* Retrofit instance ([DirectionsRetrofit]) from every other API in this
 * app: those all go through [com.wwwescape.deviceinfox.console.data.network.BaseUrlInterceptor]/
 * bearer-token auth pointed at the user's own configured server, whereas this hits Google's fixed
 * host with no auth beyond the query-string API key. */
interface DirectionsApi {
    @GET("maps/api/directions/json")
    suspend fun getDirections(
        @Query("origin") origin: String,
        @Query("destination") destination: String,
        @Query("key") apiKey: String,
        @Query("mode") mode: String = "driving",
    ): DirectionsResponseDto
}

@Serializable
data class DirectionsResponseDto(
    val status: String,
    val routes: List<DirectionsRouteDto> = emptyList(),
)

@Serializable
data class DirectionsRouteDto(
    @SerialName("overview_polyline") val overviewPolyline: DirectionsPolylineDto,
    val legs: List<DirectionsLegDto> = emptyList(),
)

@Serializable
data class DirectionsPolylineDto(val points: String)

@Serializable
data class DirectionsLegDto(
    val distance: DirectionsValueDto,
    val duration: DirectionsValueDto,
)

@Serializable
data class DirectionsValueDto(val value: Long)
