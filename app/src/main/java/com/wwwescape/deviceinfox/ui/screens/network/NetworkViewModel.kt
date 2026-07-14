package com.wwwescape.deviceinfox.ui.screens.network

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wwwescape.deviceinfox.data.network.NetworkRepository
import com.wwwescape.deviceinfox.data.network.NetworkSnapshot
import com.wwwescape.deviceinfox.data.network.NetworkThroughput
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

private const val MAX_THROUGHPUT_HISTORY = 30

@HiltViewModel
class NetworkViewModel @Inject constructor(
    private val networkRepository: NetworkRepository,
) : ViewModel() {

    private val refreshRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    // Every upstream here — the network callback, the 1s traffic sampler — only runs while the
    // screen is observed, so nothing keeps polling once the app is backgrounded or the screen is
    // covered by another destination. Re-subscribing takes a fresh snapshot right away.
    val snapshot: StateFlow<NetworkSnapshot> =
        merge(flowOf(Unit), networkRepository.networkChangeEvents(), refreshRequests)
            .conflate()
            .map { networkRepository.snapshot() }
            .flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), networkRepository.snapshot())

    private val _throughputHistory = MutableStateFlow<List<NetworkThroughput>>(emptyList())
    val throughputHistory: StateFlow<List<NetworkThroughput>> = _throughputHistory.asStateFlow()

    val throughput: StateFlow<NetworkThroughput> = networkRepository.trafficUpdates()
        .onEach { sample -> _throughputHistory.update { (it + sample).takeLast(MAX_THROUGHPUT_HISTORY) } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = NetworkThroughput(downMbps = 0f, upMbps = 0f),
        )

    /** Called after a permission grant/deny result; active-network changes refresh on their own. */
    fun refresh() {
        refreshRequests.tryEmit(Unit)
    }
}
