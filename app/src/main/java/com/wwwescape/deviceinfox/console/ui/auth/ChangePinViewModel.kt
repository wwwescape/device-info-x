package com.wwwescape.deviceinfox.console.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wwwescape.deviceinfox.console.data.auth.AuthRepository
import com.wwwescape.deviceinfox.console.data.auth.AuthResult
import com.wwwescape.deviceinfox.console.data.auth.isPalindromeCode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Re-verifies the current PIN (through [AuthRepository.verifyPin], so it's still subject to the
 * same lockout policy as the main gate — changing the PIN isn't a way around it) before allowing
 * a new one to be set. All step transitions live here rather than in Composable-local state, so
 * there's exactly one source of truth for where in the flow the dialog currently is. */
@HiltViewModel
class ChangePinViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _step = MutableStateFlow(ChangePinStep.CURRENT)
    val step: StateFlow<ChangePinStep> = _step.asStateFlow()

    private val _uiState = MutableStateFlow<PinGateUiState>(PinGateUiState.Idle)
    val uiState: StateFlow<PinGateUiState> = _uiState.asStateFlow()

    private val _mismatch = MutableStateFlow(false)
    val mismatch: StateFlow<Boolean> = _mismatch.asStateFlow()

    private val _palindromeError = MutableStateFlow(false)
    val palindromeError: StateFlow<Boolean> = _palindromeError.asStateFlow()

    private val _isComplete = MutableStateFlow(false)
    val isComplete: StateFlow<Boolean> = _isComplete.asStateFlow()

    private var pendingNewPin: String? = null

    fun submitCurrentPin(pin: String) {
        viewModelScope.launch {
            // The reverse-code duress trigger only ever fires from the main lock screen (see
            // AuthRepository.verifyPin's doc comment) — re-verification here treats a Duress result
            // as nothing more than its visibleOutcome, same as any other wrong attempt, with no wipe.
            when (val result = authRepository.verifyPin(pin)) {
                AuthResult.Success -> {
                    _uiState.value = PinGateUiState.Idle
                    _step.value = ChangePinStep.NEW
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

    fun submitNewPin(pin: String) {
        pendingNewPin = pin
        _mismatch.value = false
        _palindromeError.value = false
        _step.value = ChangePinStep.CONFIRM
    }

    fun submitConfirmPin(pin: String) {
        val pending = pendingNewPin
        if (pending != null && pending == pin) {
            // A palindrome code can never work as a duress trigger (its reverse is itself) — same
            // rule enforced during first-time setup in PinGateDialog.
            if (isPalindromeCode(pending)) {
                _palindromeError.value = true
                pendingNewPin = null
                _step.value = ChangePinStep.NEW
                return
            }
            viewModelScope.launch {
                authRepository.setPin(pending)
                _isComplete.value = true
            }
        } else {
            _mismatch.value = true
            pendingNewPin = null
            _step.value = ChangePinStep.NEW
        }
    }

    fun resetUiState() {
        _uiState.value = PinGateUiState.Idle
    }
}
