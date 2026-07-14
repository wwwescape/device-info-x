package com.wwwescape.deviceinfox.console.data.pairing

sealed interface PairingResult {
    data object Success : PairingResult
    data object InvalidCode : PairingResult
    data object AlreadyPaired : PairingResult

    /** Any server failure that isn't a clean "invalid code"/"already paired" — [message] is the
     * server's own `detail` text when available (see `ConsoleApiException`). */
    data class Error(val message: String? = null) : PairingResult
}
