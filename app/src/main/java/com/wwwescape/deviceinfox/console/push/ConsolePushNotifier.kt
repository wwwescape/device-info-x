package com.wwwescape.deviceinfox.console.push

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.wwwescape.deviceinfox.MainActivity
import com.wwwescape.deviceinfox.R
import com.wwwescape.deviceinfox.console.data.settings.ConsoleSettingsRepository
import com.wwwescape.deviceinfox.console.data.settings.NotificationImportance
import com.wwwescape.deviceinfox.console.data.settings.NotificationSoundMode
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Builds and shows a [PushCategory] notification — pulled out of [ConsoleFcmService] so a message
 * that arrives over a *live* WebSocket connection
 * ([com.wwwescape.deviceinfox.console.data.messaging.MessageRepository.handleWsEvent]) shows the
 * exact same notification an FCM-delivered one would (wording, channel/sound, per-category unseen
 * count), rather than needing a second, driftable copy of that logic. Both delivery paths exist
 * because `notification_service.notify_user` (server) only sends an FCM push when it *couldn't*
 * deliver the event live over the WebSocket — before this class existed, that meant a message
 * delivered live (the common case whenever the recipient's console has any live connection, not
 * just when it's actually foregrounded) produced no notification at all, since only
 * [ConsoleFcmService] knew how to show one.
 *
 * [ConsoleFcmService] still owns deciding *when* to call in here (only for a push whose `type` maps
 * to a real [PushCategory]) and still owns `birthday`/`holiday_event`'s own bypass notifications
 * (see its class doc for why those stay separate, unaffected by any of this) — this class only
 * knows how to actually build+post one, given an already-resolved category.
 */
@Singleton
class ConsolePushNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: ConsoleSettingsRepository,
) {
    suspend fun showCategoryNotification(category: PushCategory) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val tier = settingsRepository.notificationTier.first()
        val (baseTitle, body) = resolveTitleAndBody(context, category, tier)
        val count = settingsRepository.incrementAndGetNotificationCount(category.name)
        val title = if (count > 1) {
            context.getString(R.string.console_push_notification_count_suffix, baseTitle, count)
        } else {
            baseTitle
        }

        val notificationId = ConsoleFcmService.NOTIFICATION_ID_BASE + category.ordinal
        val contentIntent = PendingIntent.getActivity(
            context,
            notificationId,
            Intent(context, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(ConsoleFcmService.EXTRA_NOTIFICATION_CATEGORY, category.name),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val deleteIntent = PendingIntent.getBroadcast(
            context,
            notificationId,
            Intent(context, NotificationDismissReceiver::class.java)
                .putExtra(ConsoleFcmService.EXTRA_NOTIFICATION_CATEGORY, category.name),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, resolveChannelId(category))
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .setDeleteIntent(deleteIntent)
            .setPriority(resolvePriority(category))
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    /** Picks which of [ConsolePushChannelManager]'s channels a push posts to, per the user's
     * [com.wwwescape.deviceinfox.console.data.settings.NotificationSoundMode] — moved verbatim
     * from [ConsoleFcmService], which still calls this too for [ConsoleFcmService.showBirthdayNotification]/
     * [ConsoleFcmService.showHolidayEventNotification] ([category] `null` for both, same as before).
     * [NotificationSoundMode.THROTTLED] stamps [ConsoleSettingsRepository.setLastSoundPlayedAtEpochMillis]
     * only when it actually picks the audible channel, so [ConsoleSettingsRepository.notificationSoundThrottleMinutes]'s
     * window is measured from the last sound that really played, not from every push regardless of
     * whether it made noise. The audible/silent channels' literal ids depend on
     * [com.wwwescape.deviceinfox.console.data.settings.NotificationImportance]/[com.wwwescape.deviceinfox.console.data.settings.NotificationSoundTone]
     * too (see [ConsolePushChannelManager.audibleChannelId]'s own doc comment for why they aren't
     * just fixed ids) — reading those settings here as well as [ConsolePushChannelManager] doing
     * the same is what keeps the two agreeing on which literal channel id "the audible"/"the
     * silent" one currently is.
     *
     * [PushCategory.ONLINE] is the one deliberate, user-confirmed exception: it always resolves to
     * [ConsolePushChannelManager.onlineChannelId] — a dedicated channel fixed at `IMPORTANCE_HIGH`
     * — bypassing both [NotificationSoundMode] (including [NotificationSoundMode.MUTED]) *and*
     * [com.wwwescape.deviceinfox.console.data.settings.NotificationImportance] entirely. The ping
     * already has its own independent 15-minute send cooldown
     * (`presence_service.ONLINE_PING_COOLDOWN`), so gating it behind the shared sound-mode/throttle
     * or alert-style state too meant it could silently land silent, or non-heads-up, even right
     * after clearing that cooldown — defeating the point of it being an instant, reliably-audible,
     * always-heads-up signal. It also never reads or stamps
     * [ConsoleSettingsRepository.lastSoundPlayedAtEpochMillis], so it neither consumes nor is
     * blocked by the throttle window every other category shares. */
    suspend fun resolveChannelId(category: PushCategory?): String {
        if (category == PushCategory.ONLINE) {
            return ConsolePushChannelManager.onlineChannelId(settingsRepository.notificationSoundTone.first())
        }
        val importance = settingsRepository.notificationImportance.first()
        val audibleChannelId = ConsolePushChannelManager.audibleChannelId(importance, settingsRepository.notificationSoundTone.first())
        return when (settingsRepository.notificationSoundMode.first()) {
            NotificationSoundMode.ALWAYS -> audibleChannelId
            NotificationSoundMode.MUTED -> ConsolePushChannelManager.silentChannelId(importance)
            NotificationSoundMode.THROTTLED -> {
                val now = System.currentTimeMillis()
                val lastPlayedAt = settingsRepository.lastSoundPlayedAtEpochMillis.first()
                val windowMillis = settingsRepository.notificationSoundThrottleMinutes.first() * 60_000L
                if (lastPlayedAt != null && now - lastPlayedAt < windowMillis) {
                    ConsolePushChannelManager.silentChannelId(importance)
                } else {
                    settingsRepository.setLastSoundPlayedAtEpochMillis(now)
                    audibleChannelId
                }
            }
        }
    }

    /** [NotificationCompat.Builder.setPriority] is only actually consulted on API < 26 — on 26+ a
     * notification posted to a channel (every notification this service builds) takes its real
     * importance/heads-up behavior from that channel's own configured importance instead, per
     * Android's own docs. Kept correct anyway rather than left at a fixed default: cheap, and it's
     * one less place that visibly disagrees with what [ConsolePushChannelManager] actually
     * configured the channel to do — including [PushCategory.ONLINE], which is always
     * [NotificationCompat.PRIORITY_HIGH] here for the same reason [resolveChannelId] always routes
     * it to the fixed-`IMPORTANCE_HIGH` channel, so pre-26 devices get the same always-heads-up
     * behavior as 26+ ones. */
    suspend fun resolvePriority(category: PushCategory? = null): Int {
        if (category == PushCategory.ONLINE) return NotificationCompat.PRIORITY_HIGH
        return when (settingsRepository.notificationImportance.first()) {
            NotificationImportance.HIGH -> NotificationCompat.PRIORITY_HIGH
            NotificationImportance.DEFAULT -> NotificationCompat.PRIORITY_DEFAULT
        }
    }
}
