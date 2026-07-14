package com.wwwescape.deviceinfox.console.data.auth

sealed interface AuthResult {
    data object Success : AuthResult
    data object IncorrectCode : AuthResult
    data class LockedOut(val retryAfterSeconds: Long) : AuthResult

    /** The code entered was the real code in reverse — a duress trigger, not a genuine attempt.
     * [visibleOutcome] is whatever [IncorrectCode]/[LockedOut] the normal wrong-attempt bookkeeping
     * produced for this same submission; the caller must render exactly that and nothing else, so a
     * duress entry stays indistinguishable from a wrong one in both appearance and lockout timing.
     * The wipe itself is the caller's job, fired off separately from — not blocking — that render. */
    data class Duress(val visibleOutcome: AuthResult) : AuthResult
}
