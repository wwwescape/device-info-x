package com.wwwescape.deviceinfox.console.ui.auth

sealed interface PinGateUiState {
    data object Idle : PinGateUiState
    data object Error : PinGateUiState
    data class Locked(val retryAfterSeconds: Long) : PinGateUiState
}
