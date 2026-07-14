package com.wwwescape.deviceinfox.console.ui.settings

/** The 4 sections in Settings that each get their own "Delete data" action — lets one
 * [DataDeletionConfirmationDialog] instance serve all of them (plus "Delete all data") rather
 * than needing separate dialog state per section. */
enum class DataSection { MESSAGES, CALENDAR, PERIOD, LOCKER }
