package com.wwwescape.deviceinfox.console.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wwwescape.deviceinfox.console.data.auth.AuthRepository
import com.wwwescape.deviceinfox.console.data.auth.AuthResult
import com.wwwescape.deviceinfox.console.data.network.AccountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** "Destroy all data" — a single PIN-re-verify step (mirrors only [ChangePinViewModel]'s CURRENT
 * step, nothing new is being set here), followed by one more plain confirmation before actually
 * invoking [AccountRepository.destroyAllDataAndAccount]. PIN re-entry uses the same
 * [AuthRepository.verifyPin] lockout policy as the main gate and [ChangePinViewModel]. */
@HiltViewModel
class DestroyAccountViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val accountRepository: AccountRepository,
) : ViewModel() {

    private val _step = MutableStateFlow(DestroyAccountStep.PIN)
    val step: StateFlow<DestroyAccountStep> = _step.asStateFlow()

    private val _uiState = MutableStateFlow<PinGateUiState>(PinGateUiState.Idle)
    val uiState: StateFlow<PinGateUiState> = _uiState.asStateFlow()

    private val _isDestroying = MutableStateFlow(false)
    val isDestroying: StateFlow<Boolean> = _isDestroying.asStateFlow()

    private val _destroyFailed = MutableStateFlow(false)
    val destroyFailed: StateFlow<Boolean> = _destroyFailed.asStateFlow()

    private val _isComplete = MutableStateFlow(false)
    val isComplete: StateFlow<Boolean> = _isComplete.asStateFlow()

    fun submitPin(pin: String) {
        viewModelScope.launch {
            // The reverse-code duress trigger only ever fires from the main lock screen (see
            // AuthRepository.verifyPin's doc comment) — re-verification here treats a Duress result
            // as nothing more than its visibleOutcome, same as any other wrong attempt, with no wipe.
            when (val result = authRepository.verifyPin(pin)) {
                AuthResult.Success -> {
                    _uiState.value = PinGateUiState.Idle
                    _step.value = DestroyAccountStep.CONFIRM
                }
                AuthResult.IncorrectCode -> _uiState.value = PinGateUiState.Error
                is AuthResult.LockedOut -> _uiState.value = PinGateUiState.Locked(result.retryAfterSeconds)
                is AuthResult.Duress -> when (val outcome = result.visibleOutcome) {
                    AuthResult.IncorrectCode -> _uiState.value = PinGateUiState.Error
                    is AuthResult.LockedOut -> _uiState.value = PinGateUiState.Locked(outcome.retryAfterSeconds)
                    else -> Unit
                }
            }
        }
    }

    fun resetUiState() {
        _uiState.value = PinGateUiState.Idle
    }

    fun confirmDestroy() {
        viewModelScope.launch {
            _isDestroying.value = true
            _destroyFailed.value = false
            runCatching { accountRepository.destroyAllDataAndAccount() }
                .onSuccess { _isComplete.value = true }
                .onFailure { _destroyFailed.value = true }
            _isDestroying.value = false
        }
    }
}
