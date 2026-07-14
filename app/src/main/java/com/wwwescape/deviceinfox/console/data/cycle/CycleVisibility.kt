package com.wwwescape.deviceinfox.console.data.cycle

/** Driven entirely by [com.wwwescape.deviceinfox.console.data.db.PartnerEntity.gender] on self
 * and (if paired) the partner — see [CycleRepository.visibility] for the exact rule. */
sealed interface CycleVisibility {
    data object Hidden : CycleVisibility

    /** [isSelf] is which side is actually FEMALE and being tracked — the other partner sees this
     * cycle read-only (Phase 11.7: the server only lets the cycle's own owner write to it). */
    data class SingleCycle(val trackedPartnerId: String, val isSelf: Boolean) : CycleVisibility
    data class TwoCycles(val selfId: String, val partnerId: String) : CycleVisibility
}
