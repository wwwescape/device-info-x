package com.wwwescape.deviceinfox.console.ui.auth

sealed interface AccountSetupUiState {
    data object Idle : AccountSetupUiState
    data object Loading : AccountSetupUiState
    data class Error(val message: String?) : AccountSetupUiState
}
