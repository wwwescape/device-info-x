package com.wwwescape.deviceinfox.console.data.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Mirrors `app/models/notepad.py`'s `NotepadEntryType`. */
@Serializable
enum class NotepadEntryTypeDto {
    @SerialName("private") PRIVATE,
    @SerialName("shared") SHARED,
}

@Serializable
data class NotepadEntryCreateDto(
    val type: NotepadEntryTypeDto,
    // Both allowed blank — the FAB creates a new entry immediately, before the user has typed
    // anything (see the server's own `NotepadEntryCreate.body` doc comment).
    val title: String = "",
    val body: String = "",
)

/** [expectedUpdatedAt] is the raw `updated_at` string this device last read for the entry being
 * saved — the optimistic-concurrency token, echoed back verbatim (see
 * `NotepadEntryEntity.updatedAtRaw`'s own doc comment for why it must be verbatim, not
 * reconstructed from a millis-precision value). The server 409s if the entry has moved since. */
@Serializable
data class NotepadEntryUpdateDto(
    val title: String,
    val body: String,
    @SerialName("expected_updated_at") val expectedUpdatedAt: String,
)

@Serializable
data class NotepadEntryOutDto(
    val id: String,
    val type: NotepadEntryTypeDto,
    @SerialName("owner_id") val ownerId: String? = null,
    val title: String = "",
    val body: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("updated_by") val updatedBy: String? = null,
)
