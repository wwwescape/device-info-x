package com.wwwescape.deviceinfox.console.push

import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.wwwescape.deviceinfox.MainActivity
import com.wwwescape.deviceinfox.R
import com.wwwescape.deviceinfox.console.data.network.MessagesApi
import com.wwwescape.deviceinfox.console.data.network.consoleApiCall
import com.wwwescape.deviceinfox.console.data.push.ConsolePushRepository
import com.wwwescape.deviceinfox.console.data.settings.ConsoleSettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Receives every push the server sends this device (Phase 11's deferred FCM item). Payloads are
 * always data-only (`app/integrations/fcm.py`'s own doc comment: "never message content") — the
 * server has already decided a data message is worth sending at all (see the `fcm_data=` call
 * sites across `message_service.py`/`pairing_service.py`/`calendar_service.py`/
 * `special_event_service.py`/`locker_service.py`; reactions/pins/stars/read-receipts/automatic
 * online-offline presence deliberately pass `fcm_data=None` and never reach this service — the
 * one presence-related payload that does is `presence_service.send_online_ping`'s manual
 * `"presence_online_ping"`, the Messages header icon's push, see [PushCategory.ONLINE]), so any
 * recognized payload
 * (`data["type"]` present) surfaces exactly one notification, worded per
 * [ConsoleSettingsRepository.notificationTier]. Wording varies by broad category
 * ([PushCategory] — message vs. an event's reminder firing now vs. ahead of time vs. everything
 * else) but deliberately not by specific event (a locker upload and a pairing change both fall
 * into [PushCategory.OTHER] and read identically), so the category itself never gets more
 * specific than what's already implied by "this app has categories of updates at all."
 *
 * Two deliberate, user-approved exceptions bypass the tier entirely: `type == "birthday"` always
 * shows a literal "Happy Birthday" notification, and `type == "holiday_event"` (national/catholic/
 * hindu holidays from `res/raw/events.json`, `HOLIDAY_EVENT_NOTIFICATION_ID`) shows the real
 * event name/wish carried in the payload's `title`/`body` fields. Unlike every other event, both
 * reveal nothing about the console's existence to anyone who isn't the account holder — a
 * birthday or holiday reminder reads as ordinary regardless of which app sent it.
 *
 * `type == "message_new"` also does one thing besides notify: [ackMessageDelivered] fires a
 * lightweight background delivery ack, so the sender's tick flips to delivered as soon as this
 * device is merely online, matching WhatsApp/etc., instead of only once the console is actually
 * opened. See that function's own doc comment for why it stays a bare API call rather than
 * pulling in the full `MessageRepository`.
 *
 * Tapping the notification opens [MainActivity] — never `ConsoleActivity` directly. A
 * notification is exactly the kind of thing someone other than the account holder could see and
 * tap, so it must never bypass the long-press-to-enter gesture the rest of the console's
 * disguise depends on.
 */
@AndroidEntryPoint
class ConsoleFcmService : FirebaseMessagingService() {

    @Inject lateinit var pushRepository: ConsolePushRepository

    /** Owns the actual notification-building/showing now — see its own class doc for why this
     * moved out of here (so a live-WebSocket-delivered message can show the identical notification
     * an FCM one would, via [com.wwwescape.deviceinfox.console.data.messaging.MessageRepository]
     * calling the same shared class instead of a second copy of this logic). */
    @Inject lateinit var pushNotifier: ConsolePushNotifier

    /** Only for [ackMessageDelivered]'s one narrow call — deliberately not the full
     * `MessageRepository` (also `@Singleton`-injectable here), whose constructor kicks off a
     * multi-page conversation resync and WebSocket-event subscriptions as soon as it's first
     * created. If this service is what wakes an otherwise-dead app process, injecting that would
     * turn "one lightweight background POST" into a full sync racing the ~10s window Android
     * grants FCM handling — this stays exactly as small as the ack itself. */
    @Inject lateinit var messagesApi: MessagesApi

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        scope.launch { runCatching { pushRepository.onNewToken(token) } }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val type = message.data["type"] ?: return
        val minutesBefore = message.data["minutes_before"]?.toIntOrNull()
        scope.launch { runCatching { showNotification(type, minutesBefore, message.data) } }

        // Mirrors MessageRepository.ackDeliveryIfIncoming's live in-app path, just triggered by
        // the push instead of a live WebSocket event — so the sender's delivered tick flips as
        // soon as this device is merely online and receives the push, the same as WhatsApp/etc.,
        // instead of only once the console is actually opened in the foreground (the only other
        // time this device's WebSocket connects at all — see ConsoleWebSocketClient's own doc
        // comment on why there's no persistent background connection here to rely on instead).
        if (type == "message_new") {
            val messageId = message.data["message_id"] ?: return
            scope.launch { ackMessageDelivered(messageId) }
        }
    }

    /** Best-effort: no network at this exact instant just means nothing happens here, same as
     * any other failure — the existing reconnect-triggered sweep (`MessageRepository.markIncomingAsDelivered`)
     * still catches it the next time the console is actually opened, unchanged from before this
     * existed. The server endpoint itself is idempotent (a no-op past the first successful call),
     * so there's no dedup concern racing against that in-app path either. */
    private suspend fun ackMessageDelivered(messageId: String) {
        runCatching { consoleApiCall { messagesApi.markDelivered(messageId) } }
    }

    private suspend fun showNotification(type: String, minutesBefore: Int?, data: Map<String, String>) {
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return

        if (type == "birthday") {
            showBirthdayNotification()
            return
        }

        if (type == "holiday_event") {
            val title = data["title"] ?: return
            val body = data["body"] ?: return
            showHolidayEventNotification(title, body)
            return
        }

        val category = pushCategoryOf(type, minutesBefore)
        pushNotifier.showCategoryNotification(category)
    }

    /** Bypasses [PushCategory] entirely (see the class doc) — its own fixed id, distinct from
     * every category's `NOTIFICATION_ID_BASE + ordinal`, and no count-suffix machinery: a second
     * birthday push landing before the first is dismissed isn't a real scenario worth building
     * for, unlike every other category.
     *
     * Re-checks [NotificationManagerCompat.areNotificationsEnabled] itself even though
     * [showNotification] already did before delegating here — lint's `MissingPermission` check
     * for `notify()` only recognizes that guard when it's local to the same function, not across
     * a call into a separate one. */
    private suspend fun showBirthdayNotification() {
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return

        val title = getString(R.string.console_push_notification_birthday_title)
        val body = getString(R.string.console_push_notification_birthday_body)

        val contentIntent = PendingIntent.getActivity(
            this,
            BIRTHDAY_NOTIFICATION_ID,
            Intent(this, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, pushNotifier.resolveChannelId(category = null))
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .setPriority(pushNotifier.resolvePriority())
            .build()
        NotificationManagerCompat.from(this).notify(BIRTHDAY_NOTIFICATION_ID, notification)
    }

    /** National/catholic/hindu holiday greeting — same "bypass [PushCategory]/the tier wording
     * system entirely" treatment as [showBirthdayNotification] and for the identical reason: a
     * holiday greeting from an ordinary app doesn't hint at anything either (calendar/utility apps
     * routinely send these), so disguising it would only make it look more suspicious, not less.
     * Unlike birthday, though, there's no single fixed string to hardcode — `events.json` has 18
     * different entries — so [title]/[body] arrive via the push payload itself
     * (`presence_service`-style small data fields, not message content, matching this service's
     * own class-doc reasoning on what's safe to carry in `fcm_data`) rather than being resolved
     * from a string resource here. */
    private suspend fun showHolidayEventNotification(title: String, body: String) {
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return

        val contentIntent = PendingIntent.getActivity(
            this,
            HOLIDAY_EVENT_NOTIFICATION_ID,
            Intent(this, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, pushNotifier.resolveChannelId(category = null))
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .setPriority(pushNotifier.resolvePriority())
            .build()
        NotificationManagerCompat.from(this).notify(HOLIDAY_EVENT_NOTIFICATION_ID, notification)
    }

    private fun pushCategoryOf(type: String, minutesBefore: Int?): PushCategory = when {
        type in MESSAGE_TYPES -> PushCategory.MESSAGE
        type == "reminder" && minutesBefore == 0 -> PushCategory.EVENT_NOW
        type == "reminder" -> PushCategory.EVENT_UPCOMING
        type == "period_upcoming" -> PushCategory.PERIOD_UPCOMING
        type == "period_not_logged" -> PushCategory.PERIOD_NOT_LOGGED
        type == "presence_online_ping" -> PushCategory.ONLINE
        else -> PushCategory.OTHER
    }

    companion object {
        /** No longer a literal channel id on its own — the audible channel now has one distinct id
         * per (importance, [com.wwwescape.deviceinfox.console.data.settings.NotificationSoundTone])
         * combination ([ConsolePushChannelManager.audibleChannelId], built off this as a prefix;
         * see that class's own doc comment for why a single fixed audible id can't reliably have
         * its importance or sound changed in place). Kept as a bare constant purely so
         * [ConsolePushChannelManager.syncChannels] can clean up this exact id — every install from
         * before the very first per-tone split created a real channel here, now permanently
         * orphaned. */
        const val CHANNEL_ID = "console_updates"

        /** Sound-disabled sibling of the audible channel family — one distinct id per importance
         * ([ConsolePushChannelManager.silentChannelId], built off this as a prefix), same reasoning
         * as [CHANNEL_ID]. See [ConsolePushChannelManager]'s class doc and
         * [ConsolePushNotifier.resolveChannelId] for how a push ends up on this family or the
         * audible one. */
        const val CHANNEL_ID_SILENT = "console_updates_silent"

        /** [PushCategory.MESSAGE]'s id is this exact value (`+ ordinal(0)`) — kept numerically
         * identical to the old single shared `NOTIFICATION_ID` on purpose, so an in-flight
         * notification from before this change doesn't become an orphaned, un-replaceable entry
         * in the shade after an app update. Not `private` — [ConsolePushNotifier.showCategoryNotification]
         * needs it too, now that it's the one actually building the id. */
        internal const val NOTIFICATION_ID_BASE = 4210

        /** Bypasses [PushCategory] (see [showBirthdayNotification]), so it needs its own id,
         * outside the `NOTIFICATION_ID_BASE + ordinal` range every real category occupies. */
        private const val BIRTHDAY_NOTIFICATION_ID = 4209

        /** Same reasoning as [BIRTHDAY_NOTIFICATION_ID] — [showHolidayEventNotification] also
         * bypasses [PushCategory] entirely, so it needs its own id outside that range too. */
        private const val HOLIDAY_EVENT_NOTIFICATION_ID = 4207

        /** Set on both the tap (`contentIntent`, read by `MainActivity`) and swipe-away
         * (`deleteIntent`, read by [NotificationDismissReceiver]) paths — whichever happens first
         * resets that category's unseen count (`ConsoleSettingsRepository.resetNotificationCount`). */
        const val EXTRA_NOTIFICATION_CATEGORY = "notification_category"

        /** Every `message_service.py` fcm_data `type` that reaches this service — new/updated/
         * deleted/bulk-deleted all read as the same [PushCategory.MESSAGE] wording. */
        private val MESSAGE_TYPES = setOf("message_new", "message_updated", "message_deleted", "message_bulk_deleted")
    }
}
