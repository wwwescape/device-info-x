package com.wwwescape.deviceinfox.console.data.pairing

sealed interface PairingStatus {
    data object Unpaired : PairingStatus

    /** A redeem is in flight — a real round-trip to the server as of Phase 11. */
    data object Pending : PairingStatus

    data class Paired(val partnerDisplayName: String) : PairingStatus
}
