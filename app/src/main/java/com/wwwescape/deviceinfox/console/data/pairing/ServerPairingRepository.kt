package com.wwwescape.deviceinfox.console.data.pairing

import com.wwwescape.deviceinfox.console.data.db.PartnerDao
import com.wwwescape.deviceinfox.console.data.db.PartnerRepository
import com.wwwescape.deviceinfox.console.data.network.ConsoleApiException
import com.wwwescape.deviceinfox.console.data.network.ConsoleWebSocketClient
import com.wwwescape.deviceinfox.console.data.network.PairingApi
import com.wwwescape.deviceinfox.console.data.network.consoleApiCall
import com.wwwescape.deviceinfox.console.data.network.dto.PairRequestDto
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Real, server-backed [PairingRepository] — replaces [MockPairingRepository]'s Hilt binding
 * (see `PairingModule`).
 *
 * [pairingStatus] is a `StateFlow` refreshed from `GET /pairing/status` on first use, after this
 * device's own [redeemPartnerCode]/[unpair] calls, and now (Phase 11.5) on the WebSocket's
 * `pairing.updated` event — which only carries `{paired, partner: {id, display_name}}`, a
 * smaller shape than [com.wwwescape.deviceinfox.console.data.network.dto.PartnerPublicDto], so a
 * full [refreshStatus] re-fetch is simpler and more robust than trying to decode it directly. A
 * singleton-scoped [CoroutineScope] (not tied to any one ViewModel) drives all of this, since
 * this repository — unlike the Room-Flow-backed ones — needs to kick off work the moment it's
 * created rather than only reacting to something always immediately available.
 */
@Singleton
class ServerPairingRepository @Inject constructor(
    private val pairingApi: PairingApi,
    private val partnerRepository: PartnerRepository,
    private val partnerDao: PartnerDao,
    private val webSocketClient: ConsoleWebSocketClient,
) : PairingRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _pairingStatus = MutableStateFlow<PairingStatus>(PairingStatus.Unpaired)
    override val pairingStatus: Flow<PairingStatus> = _pairingStatus.asStateFlow()

    init {
        scope.launch { refreshStatus() }
        scope.launch {
            webSocketClient.events.collect { event ->
                if (event.type == "pairing.updated") runCatching { refreshStatus() }
            }
        }
    }

    private suspend fun refreshStatus() {
        val response = runCatching { consoleApiCall { pairingApi.status() } }.getOrNull() ?: return
        val partner = response.partner
        _pairingStatus.value = if (response.paired && partner != null) {
            partnerRepository.syncPartnerProfileFromServer(partner)
            PairingStatus.Paired(partner.displayName)
        } else {
            PairingStatus.Unpaired
        }
    }

    override suspend fun redeemPartnerCode(code: String): PairingResult {
        _pairingStatus.value = PairingStatus.Pending
        return try {
            val partner = consoleApiCall { pairingApi.pair(PairRequestDto(code.trim())) }
            partnerRepository.syncPartnerProfileFromServer(partner)
            _pairingStatus.value = PairingStatus.Paired(partner.displayName)
            PairingResult.Success
        } catch (e: ConsoleApiException) {
            refreshStatus() // fall back to whatever pairing state actually is server-side
            when (e.httpStatusCode) {
                404 -> PairingResult.InvalidCode // code unknown/expired
                409 -> PairingResult.AlreadyPaired
                else -> PairingResult.Error(e.detail)
            }
        }
    }

    override suspend fun unpair() {
        consoleApiCall { pairingApi.unpair() }
        partnerDao.deletePartner()
        _pairingStatus.value = PairingStatus.Unpaired
    }
}
