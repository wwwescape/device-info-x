package com.wwwescape.deviceinfox.console.data.auth

/** A palindrome code is its own reverse, so it can never work as a duress trigger (see
 * [AuthRepository.setPin]) — blocked at set/change time rather than silently leaving duress
 * inert for whoever picks that code. */
fun isPalindromeCode(code: String): Boolean = code == code.reversed()
