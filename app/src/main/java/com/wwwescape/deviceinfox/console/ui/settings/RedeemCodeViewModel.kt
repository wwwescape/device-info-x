package com.wwwescape.deviceinfox.console.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wwwescape.deviceinfox.console.data.pairing.PairingRepository
import com.wwwescape.deviceinfox.console.data.pairing.PairingResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class RedeemCodeViewModel @Inject constructor(
    private val pairingRepository: PairingRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<RedeemCodeUiState>(RedeemCodeUiState.Idle)
    val uiState: StateFlow<RedeemCodeUiState> = _uiState.asStateFlow()

    fun submit(code: String) {
        viewModelScope.launch {
            _uiState.value = RedeemCodeUiState.Pending
            _uiState.value = when (val result = pairingRepository.redeemPartnerCode(code)) {
                PairingResult.Success -> RedeemCodeUiState.Success
                PairingResult.InvalidCode -> RedeemCodeUiState.InvalidCode
                PairingResult.AlreadyPaired -> RedeemCodeUiState.AlreadyPaired
                is PairingResult.Error -> RedeemCodeUiState.Error(result.message)
            }
        }
    }
}
