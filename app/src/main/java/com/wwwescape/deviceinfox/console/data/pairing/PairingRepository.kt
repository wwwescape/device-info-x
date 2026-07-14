package com.wwwescape.deviceinfox.console.data.pairing

import kotlinx.coroutines.flow.Flow

/**
 * Redeeming a partner's code and the resulting pairing state. Bound (via Hilt `@Binds`, see
 * `PairingModule`) to [ServerPairingRepository] as of Phase 11 — was a local mock before the
 * Device Info X Server backend existed.
 */
interface PairingRepository {
    val pairingStatus: Flow<PairingStatus>

    suspend fun redeemPartnerCode(code: String): PairingResult

    /** A deliberate, confirmed action at the call site — this itself performs no confirmation. */
    suspend fun unpair()
}
