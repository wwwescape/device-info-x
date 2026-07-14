package com.wwwescape.deviceinfox.console.data.network.dto

import kotlinx.serialization.Serializable

/** Mirrors `app/schemas/turn.py`'s `TurnCredentialsResponse` — HMAC-signed, short-lived coturn
 * REST-API-style credentials. Fetched fresh at the start of every call (Phase 2) rather than
 * cached, since `ttl` is deliberately short. */
@Serializable
data class TurnCredentialsDto(
    val urls: List<String>,
    val username: String,
    val credential: String,
    val ttl: Int,
)
