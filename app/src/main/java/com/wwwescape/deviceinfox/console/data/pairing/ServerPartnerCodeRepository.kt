package com.wwwescape.deviceinfox.console.data.pairing

import com.wwwescape.deviceinfox.console.data.network.PairingApi
import com.wwwescape.deviceinfox.console.data.network.consoleApiCall
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * This install's own Partner Code — generation moved server-side in Phase 11 (was purely local
 * before). Replaces the old `PartnerCodeRepository`.
 *
 * The server only ever returns the full plaintext code once, at the moment it's generated
 * (`POST /pairing/code` — see `PartnerCodeCreateResponseDto`'s doc comment); after that, only a
 * masked preview (`GET /pairing/code`) is retrievable. [fullCode] therefore only ever holds a
 * code generated *this session* — reopening Settings later without regenerating shows the
 * preview instead, via [fetchPreview].
 */
@Singleton
class ServerPartnerCodeRepository @Inject constructor(
    private val pairingApi: PairingApi,
) {
    private val _fullCode = MutableStateFlow<String?>(null)
    val fullCode: StateFlow<String?> = _fullCode.asStateFlow()

    suspend fun generateCode(): String {
        val response = consoleApiCall { pairingApi.generateCode() }
        _fullCode.value = response.code
        return response.code
    }

    /** Null if there's no active (unexpired) code at all. */
    suspend fun fetchPreview(): String? {
        val status = consoleApiCall { pairingApi.codeStatus() }
        return status.codePreview.takeIf { status.hasActiveCode }
    }
}
