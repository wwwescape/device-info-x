package com.wwwescape.deviceinfox.console.ui.settings

sealed interface RedeemCodeUiState {
    data object Idle : RedeemCodeUiState
    data object Pending : RedeemCodeUiState
    data object Success : RedeemCodeUiState
    data object InvalidCode : RedeemCodeUiState
    data object AlreadyPaired : RedeemCodeUiState
    data class Error(val message: String?) : RedeemCodeUiState
}
