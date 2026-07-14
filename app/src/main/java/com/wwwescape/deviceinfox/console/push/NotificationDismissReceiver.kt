package com.wwwescape.deviceinfox.console.push

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.wwwescape.deviceinfox.console.data.settings.ConsoleSettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Fired by [ConsoleFcmService.showNotification]'s `setDeleteIntent` when a category's
 * notification is swiped out of the shade — resets that category's persisted unseen count back to
 * 0, same as [com.wwwescape.deviceinfox.MainActivity] does when the notification is *tapped*
 * instead (either dismissal path counts as "seen," so a stale count can't climb forever between
 * visits regardless of which way the user clears the shade).
 *
 * Never externally launchable (`android:exported="false"` in the manifest) — only ever fired via
 * the `PendingIntent` this app itself constructs.
 */
@AndroidEntryPoint
class NotificationDismissReceiver : BroadcastReceiver() {

    @Inject lateinit var settingsRepository: ConsoleSettingsRepository

    /** [BroadcastReceiver.goAsync] is required here: the DataStore write is suspend, and this
     * receiver's process is otherwise free to be killed the instant [onReceive] returns. */
    override fun onReceive(context: Context, intent: Intent) {
        val category = intent.getStringExtra(ConsoleFcmService.EXTRA_NOTIFICATION_CATEGORY) ?: return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { settingsRepository.resetNotificationCount(category) }
            pendingResult.finish()
        }
    }
}
