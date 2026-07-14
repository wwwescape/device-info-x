package com.wwwescape.deviceinfox.console.ui.settings

import com.wwwescape.deviceinfox.console.data.db.PartnerGender
import com.wwwescape.deviceinfox.console.data.pairing.PairingStatus
import com.wwwescape.deviceinfox.console.data.settings.NotificationImportance
import com.wwwescape.deviceinfox.console.data.settings.NotificationSoundMode
import com.wwwescape.deviceinfox.console.data.settings.NotificationSoundTone
import com.wwwescape.deviceinfox.console.data.settings.NotificationTier

data class ConsoleSettingsUiState(
    val displayName: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val photoUri: String? = null,
    val birthdayEpochMillis: Long? = null,
    val gender: PartnerGender = PartnerGender.UNSPECIFIED,
    val notificationTier: NotificationTier = NotificationTier.GENERIC,
    val notificationImportance: NotificationImportance = NotificationImportance.HIGH,
    val notificationSoundMode: NotificationSoundMode = NotificationSoundMode.ALWAYS,
    val notificationSoundTone: NotificationSoundTone = NotificationSoundTone.DEFAULT,
    val notificationSoundThrottleMinutes: Int = 15,
    val partnerCode: String? = null,
    val pairingStatus: PairingStatus = PairingStatus.Unpaired,
    val averageCycleLengthDaysSeed: Int? = null,
    val averagePeriodLengthDaysSeed: Int? = null,
    val averageLutealPhaseDaysSeed: Int? = null,
    val birthdayCustomMessage: String? = null,
)
