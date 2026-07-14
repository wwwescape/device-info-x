package com.wwwescape.deviceinfox.console.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wwwescape.deviceinfox.console.data.db.PartnerGender
import com.wwwescape.deviceinfox.console.data.network.ConsoleApiException
import com.wwwescape.deviceinfox.console.data.network.ServerAuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class AccountSetupViewModel @Inject constructor(
    private val serverAuthRepository: ServerAuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<AccountSetupUiState>(AccountSetupUiState.Idle)
    val uiState: StateFlow<AccountSetupUiState> = _uiState.asStateFlow()

    private val _completed = MutableStateFlow(false)
    val completed: StateFlow<Boolean> = _completed.asStateFlow()

    /** [gender] is part of `RegisterRequest` server-side, same as [firstName]/[lastName]/
     * [birthdayEpochMillis] — one atomic call creates the account with every profile field
     * already set, rather than a second `PATCH /users/me` that could fail after the account (and
     * session) already exist, leaving the user stuck with no way to retry registration. */
    fun register(
        serverUrl: String,
        username: String,
        password: String,
        displayName: String,
        firstName: String,
        lastName: String,
        birthdayEpochMillis: Long,
        setupToken: String,
        gender: PartnerGender,
    ) {
        run {
            serverAuthRepository.setServerUrl(serverUrl)
            serverAuthRepository.register(
                username, password, displayName, firstName, lastName, gender, birthdayEpochMillis, setupToken,
            )
        }
    }

    fun login(serverUrl: String, username: String, password: String) {
        run {
            serverAuthRepository.setServerUrl(serverUrl)
            serverAuthRepository.login(username, password)
        }
    }

    private fun run(block: suspend () -> Unit) {
        viewModelScope.launch {
            _uiState.value = AccountSetupUiState.Loading
            runCatching { block() }
                .onSuccess {
                    _uiState.value = AccountSetupUiState.Idle
                    _completed.value = true
                }
                .onFailure { e -> _uiState.value = AccountSetupUiState.Error((e as? ConsoleApiException)?.detail) }
        }
    }
}
