package com.wwwescape.deviceinfox.console.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wwwescape.deviceinfox.console.data.auth.AuthRepository
import com.wwwescape.deviceinfox.console.data.auth.AuthResult
import com.wwwescape.deviceinfox.console.data.network.AccountRepository
import com.wwwescape.deviceinfox.console.data.network.ConsoleApiException
import com.wwwescape.deviceinfox.console.data.network.ServerAuthRepository
import com.wwwescape.deviceinfox.console.session.ConsoleSessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class PinGateViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val accountRepository: AccountRepository,
    private val sessionManager: ConsoleSessionManager,
    serverAuthRepository: ServerAuthRepository,
) : ViewModel() {

    /** Gates entry ahead of the PIN itself, per the Phase 11 design — see
     * [com.wwwescape.deviceinfox.console.ui.auth.AccountSetupDialog]. */
    val hasServerSession: StateFlow<Boolean> = serverAuthRepository.hasSession

    /** Gates entry *after* [hasServerSession] but still ahead of the PIN — see
     * [com.wwwescape.deviceinfox.console.ui.auth.ChangePasswordRequiredDialog]. */
    val mustChangePasswordRequired: StateFlow<Boolean> = serverAuthRepository.mustChangePasswordRequired

    /** Null while the first read from EncryptedSharedPreferences is still in flight. */
    private val _isPinSet = MutableStateFlow<Boolean?>(null)
    val isPinSet: StateFlow<Boolean?> = _isPinSet.asStateFlow()

    private val _uiState = MutableStateFlow<PinGateUiState>(PinGateUiState.Idle)
    val uiState: StateFlow<PinGateUiState> = _uiState.asStateFlow()

    val isAuthenticated: StateFlow<Boolean> = sessionManager.isAuthenticated

    /** True right after a new code is confirmed during setup, until [acknowledgeDuressInfo] —
     * [markAuthenticated] is deliberately withheld until then, so [PinGateDialog] shows the
     * explainer instead of dismissing straight into the console. */
    private val _showDuressInfo = MutableStateFlow(false)
    val showDuressInfo: StateFlow<Boolean> = _showDuressInfo.asStateFlow()

    init {
        viewModelScope.launch { _isPinSet.value = authRepository.isPinSet() }
    }

    fun setPin(pin: String) {
        viewModelScope.launch {
            authRepository.setPin(pin)
            _isPinSet.value = true
            _showDuressInfo.value = true
        }
    }

    fun acknowledgeDuressInfo() {
        _showDuressInfo.value = false
        sessionManager.markAuthenticated()
    }

    fun submitPin(pin: String) {
        viewModelScope.launch {
            when (val result = authRepository.verifyPin(pin)) {
                AuthResult.Success -> {
                    _uiState.value = PinGateUiState.Idle
                    sessionManager.markAuthenticated()
                }
                is AuthResult.Duress -> {
                    // The visible outcome must render immediately and look identical to a genuine
                    // wrong attempt — the wipe itself runs fire-and-forget alongside it, never
                    // ahead of it, so there's no perceptible delay to give it away.
                    applyVisibleOutcome(result.visibleOutcome)
                    viewModelScope.launch { runCatching { accountRepository.panicWipeLocalData() } }
                }
                else -> applyVisibleOutcome(result)
            }
        }
    }

    private fun applyVisibleOutcome(outcome: AuthResult) {
        when (outcome) {
            AuthResult.IncorrectCode -> _uiState.value = PinGateUiState.Error
            is AuthResult.LockedOut -> _uiState.value = PinGateUiState.Locked(outcome.retryAfterSeconds)
            else -> Unit
        }
    }

    fun resetUiState() {
        _uiState.value = PinGateUiState.Idle
    }

    private val _forgotCodeState = MutableStateFlow<AccountSetupUiState>(AccountSetupUiState.Idle)
    val forgotCodeState: StateFlow<AccountSetupUiState> = _forgotCodeState.asStateFlow()

    /** "Forgot code?" — [AccountRepository.resetPin] re-verifies [accountPassword] against the
     * server before wiping the local PIN hash; flipping [_isPinSet] false here (rather than
     * waiting on a fresh [authRepository.isPinSet] read) is what makes [PinGateDialog]
     * immediately fall through to its existing "set a new code" flow. */
    fun resetCodeWithPassword(accountPassword: String) {
        viewModelScope.launch {
            _forgotCodeState.value = AccountSetupUiState.Loading
            runCatching { accountRepository.resetPin(accountPassword) }
                .onSuccess {
                    _forgotCodeState.value = AccountSetupUiState.Idle
                    _isPinSet.value = false
                }
                .onFailure { e -> _forgotCodeState.value = AccountSetupUiState.Error((e as? ConsoleApiException)?.detail) }
        }
    }

    fun resetForgotCodeState() {
        _forgotCodeState.value = AccountSetupUiState.Idle
    }
}
