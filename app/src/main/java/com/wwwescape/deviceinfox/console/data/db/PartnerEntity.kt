package com.wwwescape.deviceinfox.console.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One row per paired user — including a row for the local user themselves ([isSelf]), since
 * both partners' profiles (gender, birthday, display name) are needed locally for features like
 * the period tracker's visibility rules and the shared calendar/events. */
@Entity(tableName = "partners")
data class PartnerEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val firstName: String,
    val lastName: String,
    val photoUri: String?,
    val birthdayEpochMillis: Long?,
    val gender: PartnerGender,
    val isSelf: Boolean,
    val pairedAtEpochMillis: Long?,
    // Authored by *this* row's person, shown on the *other* person's birthday popup — see
    // PartnerPublicDto.birthdayMessage and ConsoleTabsViewModel.checkBirthday.
    val birthdayMessage: String? = null,
)
