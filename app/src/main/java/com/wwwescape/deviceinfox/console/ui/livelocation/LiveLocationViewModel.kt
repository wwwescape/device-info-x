package com.wwwescape.deviceinfox.console.ui.livelocation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.wwwescape.deviceinfox.console.data.db.PartnerEntity
import com.wwwescape.deviceinfox.console.data.db.PartnerRepository
import com.wwwescape.deviceinfox.console.data.location.DirectionsRouteFetcher
import com.wwwescape.deviceinfox.console.data.location.EtaState
import com.wwwescape.deviceinfox.console.data.location.LiveLocationRepository
import com.wwwescape.deviceinfox.console.data.location.LiveLocationService
import com.wwwescape.deviceinfox.console.data.location.PartnerLiveLocation
import com.wwwescape.deviceinfox.console.data.location.SelfLiveLocation
import com.wwwescape.deviceinfox.console.data.location.computeEtaState
import com.wwwescape.deviceinfox.console.data.location.haversineMeters
import com.wwwescape.deviceinfox.console.data.network.ConsoleApiException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** [polylinePoints] and [remainingDistanceMeters] are null until both partners' positions are
 * known and a Directions API call has actually succeeded; [etaState] separately tracks whether a
 * meaningful "time to meet" can be shown at all — see [EtaState]'s own doc comment. */
data class LiveLocationRouteState(
    val polylinePoints: List<LatLng> = emptyList(),
    val remainingDistanceMeters: Long? = null,
    val etaState: EtaState = EtaState.Unknown,
)

/** [selfInitials]/[partnerInitials] default to "?" — a self/partner profile always exists locally
 * by the time this screen is reachable (pairing/profile setup happens long before), but a bare
 * empty marker label reads worse than an explicit placeholder in the (should-never-happen) gap
 * before that first Room row loads. */
private fun PartnerEntity?.initials(): String {
    val first = this?.firstName?.firstOrNull()?.uppercaseChar()
    val last = this?.lastName?.firstOrNull()?.uppercaseChar()
    return listOfNotNull(first, last).joinToString("").ifEmpty { "?" }
}

@HiltViewModel
class LiveLocationViewModel @Inject constructor(
    private val repository: LiveLocationRepository,
    private val partnerRepository: PartnerRepository,
    private val routeFetcher: DirectionsRouteFetcher,
    @param:ApplicationContext private val context: Context,
) : ViewModel() {

    val selfSharing: Flow<Boolean> = repository.selfSharing
    val selfLocation: Flow<SelfLiveLocation?> = repository.selfLocation
    val partnerLocation: Flow<PartnerLiveLocation> = repository.partnerLocation
    val sharingEndedEvent: SharedFlow<String> = repository.sharingEndedEvent
    val checkInReminderEvent: SharedFlow<Unit> = repository.checkInReminderEvent

    val selfInitials: Flow<String> = partnerRepository.selfProfile.map { it.initials() }
    val partnerInitials: Flow<String> = partnerRepository.partnerProfile.map { it.initials() }

    /** Backs the partner-name-with-status-icon row (`LiveLocationScreen.kt`) — null only in the
     * same should-never-happen gap [selfInitials]/[partnerInitials]'s own doc comment describes. */
    val selfDisplayName: Flow<String?> = partnerRepository.selfProfile.map { it?.displayName }
    val partnerDisplayName: Flow<String?> = partnerRepository.partnerProfile.map { it?.displayName }

    private val _errorEvent = MutableSharedFlow<String?>(extraBufferCapacity = 1)
    val errorEvent: SharedFlow<String?> = _errorEvent.asSharedFlow()

    private val _routeState = MutableStateFlow(LiveLocationRouteState())
    val routeState: StateFlow<LiveLocationRouteState> = _routeState.asStateFlow()

    // Carried across route updates by hand (not folded into a Flow operator's own state) — see
    // computeEtaState's own doc comment for why these two are passed in rather than owned there.
    private var previousGapMeters: Double? = null
    private var previousGapAtEpochMillis: Long? = null

    init {
        viewModelScope.launch { runCatching { repository.refreshStatus() } }

        // collectLatest — a new (self, partner) pair cancels any Directions call still in flight
        // for the previous one, so a burst of fast position updates can't land its route/ETA out
        // of order against a newer pair that already superseded it.
        viewModelScope.launch {
            combine(repository.selfLocation, repository.partnerLocation) { self, partner -> self to partner }
                .collectLatest { (self, partner) -> updateRoute(self, partner) }
        }
    }

    private suspend fun updateRoute(self: SelfLiveLocation?, partner: PartnerLiveLocation) {
        val partnerLat = partner.lat
        val partnerLng = partner.lng
        if (self == null || !partner.isSharing || partnerLat == null || partnerLng == null) {
            previousGapMeters = null
            previousGapAtEpochMillis = null
            _routeState.value = LiveLocationRouteState()
            return
        }

        val currentGapMeters = haversineMeters(self.lat, self.lng, partnerLat, partnerLng)
        val nowMillis = System.currentTimeMillis()
        val route = runCatching {
            routeFetcher.fetchRoute(self.lat, self.lng, partnerLat, partnerLng)
        }.getOrNull()

        val etaState = computeEtaState(
            currentGapMeters = currentGapMeters,
            nowMillis = nowMillis,
            remainingRouteMeters = route?.remainingDistanceMeters,
            previousGapMeters = previousGapMeters,
            previousGapAtEpochMillis = previousGapAtEpochMillis,
        )
        previousGapMeters = currentGapMeters
        previousGapAtEpochMillis = nowMillis

        _routeState.value = LiveLocationRouteState(
            polylinePoints = route?.polylinePoints.orEmpty(),
            remainingDistanceMeters = route?.remainingDistanceMeters,
            etaState = etaState,
        )
    }

    /** Guards the sharing toggle against a double-tap firing two enable/disable requests before
     * [selfSharing] (only flipped by [repository] *after* its network call returns) has had a
     * chance to reflect the first one — previously nothing blocked the Switch/button during that
     * round-trip window. */
    private val _isTogglingSharing = MutableStateFlow(false)
    val isTogglingSharing: StateFlow<Boolean> = _isTogglingSharing.asStateFlow()

    fun enableSharing() {
        if (_isTogglingSharing.value) return
        _isTogglingSharing.value = true
        viewModelScope.launch {
            runCatching { repository.enableSharing() }
                .onSuccess { LiveLocationService.start(context) }
                .onFailure { _errorEvent.tryEmit((it as? ConsoleApiException)?.detail) }
            _isTogglingSharing.value = false
        }
    }

    fun disableSharing() {
        if (_isTogglingSharing.value) return
        _isTogglingSharing.value = true
        LiveLocationService.stop(context)
        viewModelScope.launch {
            runCatching { repository.disableSharing() }
                .onFailure { _errorEvent.tryEmit((it as? ConsoleApiException)?.detail) }
            _isTogglingSharing.value = false
        }
    }
}
