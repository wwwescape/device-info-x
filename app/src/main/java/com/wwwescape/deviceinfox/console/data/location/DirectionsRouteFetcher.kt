package com.wwwescape.deviceinfox.console.data.location

import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.PolyUtil
import com.wwwescape.deviceinfox.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

data class DirectionsRoute(
    val polylinePoints: List<LatLng>,
    val remainingDistanceMeters: Long,
)

/** Thin wrapper around [DirectionsApi] — decodes the response's encoded polyline (Google Maps'
 * own compact path format) via [PolyUtil], the exact "automatic fit, no manual crop/clip" pattern
 * the TODOS.md writeup already confirmed this app has a working precedent for elsewhere. Only the
 * road distance is used (for [computeEtaState]'s own math) — the API's own `duration` figure is
 * deliberately not surfaced anywhere, since it assumes a single, stationary destination, which
 * doesn't hold once both partners can be moving. */
@Singleton
class DirectionsRouteFetcher @Inject constructor(private val directionsApi: DirectionsApi) {
    suspend fun fetchRoute(originLat: Double, originLng: Double, destinationLat: Double, destinationLng: Double): DirectionsRoute? {
        val response = directionsApi.getDirections(
            origin = "$originLat,$originLng",
            destination = "$destinationLat,$destinationLng",
            apiKey = BuildConfig.MAPS_API_KEY,
        )
        if (response.status != "OK") return null
        val route = response.routes.firstOrNull() ?: return null
        val leg = route.legs.firstOrNull() ?: return null
        val points = runCatching { PolyUtil.decode(route.overviewPolyline.points) }.getOrNull() ?: return null
        return DirectionsRoute(polylinePoints = points, remainingDistanceMeters = leg.distance.value)
    }
}
