package com.wwwescape.deviceinfox.console.push

import android.content.Context
import com.wwwescape.deviceinfox.R
import com.wwwescape.deviceinfox.console.data.settings.NotificationTier

/**
 * Broad wording bucket a push maps to, independent of the specific event that caused it — see
 * [ConsoleFcmService]'s own class doc for why this stays coarser than the FCM payload's actual
 * `type` values. Not private to [ConsoleFcmService] (unlike before): since there's no deep linking
 * of any kind here, [ConsoleFcmService.EXTRA_NOTIFICATION_CATEGORY] on the tap `Intent` is the
 * *only* thing that survives from a real push through to [MainActivity][com.wwwescape.deviceinfox.MainActivity]
 * reopening the app, so the category needs to be resolvable there too, not just inside the service
 * that first assigned it.
 */
enum class PushCategory { MESSAGE, EVENT_NOW, EVENT_UPCOMING, PERIOD_UPCOMING, PERIOD_NOT_LOGGED, ONLINE, OTHER }

/**
 * [ConsoleFcmService]'s wording table, pulled out to a pure function of (category, tier) so
 * [MainActivity][com.wwwescape.deviceinfox.MainActivity] can reconstruct the *exact* same
 * title/body for the Dashboard's fake card from nothing but the category on a notification tap —
 * a tap carries no more than that (see [PushCategory]'s own doc comment), so the fake card is
 * regenerated on the spot rather than passed along from whenever the real push originally showed.
 */
fun resolveTitleAndBody(context: Context, category: PushCategory, tier: NotificationTier): Pair<String, String> = when (category) {
    PushCategory.MESSAGE -> when (tier) {
        NotificationTier.GENERIC -> context.getString(R.string.app_name) to context.getString(R.string.console_push_notification_message_generic_body)
        NotificationTier.DISGUISED ->
            context.getString(R.string.console_push_notification_message_disguised_title) to
                context.getString(R.string.console_push_notification_message_disguised_body)
    }
    PushCategory.EVENT_NOW -> when (tier) {
        NotificationTier.GENERIC -> context.getString(R.string.app_name) to context.getString(R.string.console_push_notification_event_now_generic_body)
        NotificationTier.DISGUISED ->
            context.getString(R.string.console_push_notification_event_disguised_title) to
                context.getString(R.string.console_push_notification_event_now_disguised_body)
    }
    PushCategory.EVENT_UPCOMING -> when (tier) {
        NotificationTier.GENERIC ->
            context.getString(R.string.app_name) to context.getString(R.string.console_push_notification_event_upcoming_generic_body)
        NotificationTier.DISGUISED ->
            context.getString(R.string.console_push_notification_event_disguised_title) to
                context.getString(R.string.console_push_notification_event_upcoming_disguised_body)
    }
    PushCategory.PERIOD_UPCOMING -> when (tier) {
        NotificationTier.GENERIC ->
            context.getString(R.string.app_name) to context.getString(R.string.console_push_notification_period_upcoming_generic_body)
        NotificationTier.DISGUISED ->
            context.getString(R.string.console_push_notification_period_upcoming_disguised_title) to
                context.getString(R.string.console_push_notification_period_upcoming_disguised_body)
    }
    PushCategory.PERIOD_NOT_LOGGED -> when (tier) {
        NotificationTier.GENERIC ->
            context.getString(R.string.app_name) to context.getString(R.string.console_push_notification_period_not_logged_generic_body)
        NotificationTier.DISGUISED ->
            context.getString(R.string.console_push_notification_period_not_logged_disguised_title) to
                context.getString(R.string.console_push_notification_period_not_logged_disguised_body)
    }
    PushCategory.OTHER -> when (tier) {
        NotificationTier.GENERIC -> context.getString(R.string.app_name) to context.getString(R.string.console_push_notification_generic_body)
        NotificationTier.DISGUISED ->
            context.getString(R.string.console_push_notification_disguised_title) to context.getString(R.string.console_push_notification_disguised_body)
    }
    PushCategory.ONLINE -> when (tier) {
        NotificationTier.GENERIC -> context.getString(R.string.app_name) to context.getString(R.string.console_push_notification_online_generic_body)
        NotificationTier.DISGUISED ->
            context.getString(R.string.console_push_notification_online_disguised_title) to
                context.getString(R.string.console_push_notification_online_disguised_body)
    }
}
