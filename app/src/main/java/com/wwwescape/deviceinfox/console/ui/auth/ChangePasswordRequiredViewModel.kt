package com.wwwescape.deviceinfox.console.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wwwescape.deviceinfox.console.data.network.AccountRepository
import com.wwwescape.deviceinfox.console.data.network.ConsoleApiException
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Backs [ChangePasswordRequiredDialog] — the mandatory screen `PinGateDialog` shows in place of
 * the normal PIN flow whenever the server reports `must_change_password` (set by an admin's
 * `python -m app.cli reset-password`). [isComplete] flipping true is what lets the caller drop
 * back into the normal PIN gate — [AccountRepository.changePassword] already clears the
 * server-side flag and the locally-cached copy that drives that gate. */
@HiltViewModel
class ChangePasswordRequiredViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<AccountSetupUiState>(AccountSetupUiState.Idle)
    val uiState: StateFlow<AccountSetupUiState> = _uiState.asStateFlow()

    private val _isComplete = MutableStateFlow(false)
    val isComplete: StateFlow<Boolean> = _isComplete.asStateFlow()

    fun submit(currentPassword: String, newPassword: String) {
        viewModelScope.launch {
            _uiState.value = AccountSetupUiState.Loading
            runCatching { accountRepository.changePassword(currentPassword, newPassword) }
                .onSuccess {
                    _uiState.value = AccountSetupUiState.Idle
                    _isComplete.value = true
                }
                .onFailure { e -> _uiState.value = AccountSetupUiState.Error((e as? ConsoleApiException)?.detail) }
        }
    }
}
