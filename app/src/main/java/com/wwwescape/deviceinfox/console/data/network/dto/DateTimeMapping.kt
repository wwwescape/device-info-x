package com.wwwescape.deviceinfox.console.data.network.dto

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** The server's `DateTime(timezone=True)` fields serialize as ISO-8601 with a numeric offset
 * (Python's `datetime.isoformat()`, e.g. `2026-07-26T12:34:56.789012+00:00`) — `+00:00`, not a
 * bare `Z`, and microsecond-precision fractional seconds, so [OffsetDateTime.parse] (which
 * accepts both) is used rather than the stricter [Instant.parse] (`Z`-only). Requires core
 * library desugaring on minSdk 24 — see `app/build.gradle.kts`. */
fun String.isoDateTimeToEpochMillis(): Long = OffsetDateTime.parse(this).toInstant().toEpochMilli()

fun Long.toIsoDateTimeString(): String =
    Instant.ofEpochMilli(this).atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
