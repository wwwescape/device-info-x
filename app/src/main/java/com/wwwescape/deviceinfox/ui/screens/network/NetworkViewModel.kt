package com.wwwescape.deviceinfox.ui.screens.network

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wwwescape.deviceinfox.data.network.NetworkRepository
import com.wwwescape.deviceinfox.data.network.NetworkSnapshot
import com.wwwescape.deviceinfox.data.network.NetworkThroughput
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val MAX_THROUGHPUT_HISTORY = 30

@HiltViewModel
class NetworkViewModel @Inject constructor(
    private val networkRepository: NetworkRepository,
) : ViewModel() {

    private val _snapshot = MutableStateFlow(networkRepository.snapshot())
    val snapshot: StateFlow<NetworkSnapshot> = _snapshot.asStateFlow()

    val throughput: StateFlow<NetworkThroughput> = networkRepository.trafficUpdates()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = NetworkThroughput(downMbps = 0f, upMbps = 0f),
        )

    private val _throughputHistory = MutableStateFlow<List<NetworkThroughput>>(emptyList())
    val throughputHistory: StateFlow<List<NetworkThroughput>> = _throughputHistory.asStateFlow()

    init {
        viewModelScope.launch {
            networkRepository.networkChangeEvents().collect { refresh() }
        }
        viewModelScope.launch {
            throughput.collect { sample ->
                _throughputHistory.value = (_throughputHistory.value + sample).takeLast(MAX_THROUGHPUT_HISTORY)
            }
        }
    }

    /** Called on active-network change, and also after a permission grant/deny result. */
    fun refresh() {
        _snapshot.value = networkRepository.snapshot()
    }
}
