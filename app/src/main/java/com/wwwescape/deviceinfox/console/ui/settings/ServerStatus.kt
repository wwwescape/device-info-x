package com.wwwescape.deviceinfox.console.ui.settings

/** [CHECKING] is only ever the very first value shown, before the first poll in
 * [ConsoleSettingsViewModel] resolves — every poll after that lands on [ONLINE] or [OFFLINE],
 * never back to [CHECKING], so the dot never flickers "checking" again while Settings stays open. */
enum class ServerStatus { CHECKING, ONLINE, OFFLINE }
