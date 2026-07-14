package com.wwwescape.deviceinfox.console.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import com.wwwescape.deviceinfox.R
import com.wwwescape.deviceinfox.console.data.settings.ConsoleSettingsRepository
import com.wwwescape.deviceinfox.console.data.settings.NotificationImportance
import com.wwwescape.deviceinfox.console.data.settings.NotificationSoundTone
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Owns creating/updating the console's notification channels — one audible channel
 * ([audibleChannelId], keyed by *both* [NotificationImportance] and [NotificationSoundTone]),
 * one sound-disabled sibling ([silentChannelId], keyed by [NotificationImportance] alone, used for
 * muted/throttled pushes — see [com.wwwescape.deviceinfox.console.data.settings.NotificationSoundMode]),
 * and one fixed-`IMPORTANCE_HIGH` channel reserved for [com.wwwescape.deviceinfox.console.push.PushCategory.ONLINE]
 * ([onlineChannelId], keyed by [NotificationSoundTone] only — deliberately *not* by
 * [NotificationImportance], since the whole point is that the "I'm Online" ping always posts as
 * heads-up regardless of the user's Alert style setting; see [ConsolePushNotifier.resolveChannelId]
 * for the category check that routes it here instead of the regular audible channel).
 * [syncChannels] reads both settings directly off [ConsoleSettingsRepository] rather than taking
 * them as parameters, so every call site (app startup, and either Settings toggle) stays a
 * one-liner. Shared between app startup ([com.wwwescape.deviceinfox.DeviceInfoXApplication], which
 * applies whatever was last persisted) and the live Settings toggles, which need the change to
 * take effect without an app restart.
 *
 * **Neither a channel's importance nor its sound is ever changed in place — confirmed on a real
 * device, not assumed.** The obvious approach — delete the existing channel and recreate it with
 * the same id but different settings — silently doesn't work: Android keeps a "tombstone" of a
 * just-deleted channel, and recreating with the same id revives its old importance/sound rather
 * than genuinely starting over, with no error surfaced anywhere this app could catch. First
 * confirmed for *sound* (an earlier version of this class deleted+recreated a single fixed audible
 * channel id to switch tones — the newly-selected tone's sound simply never took effect on a
 * device where that id had already been created once with a different sound). Since the same
 * unverified assumption was still being made for *importance* — this class used to delete+recreate
 * to raise it back to HIGH after it had been lowered — and there's no reason to expect Android
 * treats that field any more reliably, both are now handled the same, structurally safer way:
 * [audibleChannelId]/[silentChannelId] fold every setting that affects a channel's actual behavior
 * into the id itself, so a settings change always means switching to a different, either
 * already-correct or genuinely-never-before-seen id — never touching an id that might already
 * carry forward stale settings from an earlier build or an earlier selection. [syncChannels]
 * deletes every id that isn't the current combination (a small, fully enumerable set — 2
 * importances × 2 tones for the audible channel, 2 importances for the silent one) purely so
 * Android's own per-app notification settings screen doesn't accumulate inert "App Updates"-
 * looking duplicates, plus every id from schemes that predate this one; deleting an id nothing is
 * currently posting to never hits the tombstone problem, since nothing ever tries to recreate that
 * exact id with different settings afterward.
 */
@Singleton
class ConsolePushChannelManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsRepository: ConsoleSettingsRepository,
) {
    private val notificationAudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    /** Never allowed to throw — every call site is fire-and-forget with no exception handler
     * ([DeviceInfoXApplication][com.wwwescape.deviceinfox.DeviceInfoXApplication]'s app-startup
     * `scope.launch { }`, and [ConsoleSettingsViewModel][com.wwwescape.deviceinfox.console.ui.settings.ConsoleSettingsViewModel]'s
     * `viewModelScope.launch { }`), so an uncaught failure in here doesn't just fail this one
     * operation — it crashes the entire app process, on whatever background thread the exception
     * happened to surface on, at whatever moment that happened to be. [runCatching] here is a real
     * fix, not caution for its own sake: this is about the least stealthy failure mode a "secret
     * app" could have. */
    suspend fun syncChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        runCatching {
            syncChannelsOrThrow()
        }.onFailure { Log.w(TAG, "syncChannels failed — channels left as-is", it) }
    }

    /** Everything in here is API 26+, same as [createChannel] — see that function's own doc
     * comment for why this needs its own [RequiresApi] rather than relying on [syncChannels]'
     * version check: lint's `NewApi` check doesn't trace a guard through an intermediate,
     * unannotated function call, so without this it flags every API 26+ call in here as if it
     * could run unguarded on API 24-25 (confirmed by CI — this was missing when
     * [syncChannelsOrThrow] was first split out of [syncChannels], and `./gradlew lint` failed on
     * exactly that). */
    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun syncChannelsOrThrow() {
        val manager = context.getSystemService(NotificationManager::class.java)
        val importance = settingsRepository.notificationImportance.first()
        val androidImportance = importance.toAndroidImportance()
        val tone = settingsRepository.notificationSoundTone.first()
        val audibleSoundUri = when (tone) {
            NotificationSoundTone.DEFAULT -> Settings.System.DEFAULT_NOTIFICATION_URI
            NotificationSoundTone.CHIME -> chimeSoundUri()
        }
        val currentAudibleId = audibleChannelId(importance, tone)
        val currentSilentId = silentChannelId(importance)
        val currentOnlineId = onlineChannelId(tone)
        Log.d(TAG, "syncChannels: importance=$androidImportance tone=$tone audibleChannelId=$currentAudibleId")

        createChannel(
            manager,
            channelId = currentAudibleId,
            name = context.getString(R.string.console_push_channel_name),
            description = context.getString(R.string.console_push_channel_description),
            androidImportance = androidImportance,
            soundUri = audibleSoundUri,
        )
        createChannel(
            manager,
            channelId = currentSilentId,
            name = context.getString(R.string.console_push_channel_silent_name),
            description = context.getString(R.string.console_push_channel_silent_description),
            androidImportance = androidImportance,
            soundUri = null,
        )
        // Always IMPORTANCE_HIGH — never androidImportance — and always the audible sound for
        // whichever tone is selected: this channel exists specifically so the "I'm Online" ping
        // can't be downgraded by the user's Alert style setting, the same way it already can't be
        // silenced by their Sound mode setting (ConsolePushNotifier.resolveChannelId).
        createChannel(
            manager,
            channelId = currentOnlineId,
            name = context.getString(R.string.console_push_channel_online_name),
            description = context.getString(R.string.console_push_channel_online_description),
            androidImportance = NotificationManager.IMPORTANCE_HIGH,
            soundUri = audibleSoundUri,
        )

        // Every id this app could ever have created for a combination that isn't the current one
        // — including every id from schemes that predate importance being folded in — is now
        // permanently unused. None of them can be safely "fixed" in place (see class doc), so the
        // only correct move once a combination stops being current is to stop using its id
        // entirely, never touch its content again, and just delete it so it doesn't linger as an
        // inert near-duplicate entry in Android's own per-app notification settings.
        val staleIds = buildSet {
            for (imp in NotificationImportance.entries) {
                for (t in NotificationSoundTone.entries) add(audibleChannelId(imp, t))
                add(silentChannelId(imp))
            }
            for (t in NotificationSoundTone.entries) add(onlineChannelId(t))
            remove(currentAudibleId)
            remove(currentSilentId)
            remove(currentOnlineId)
            // Pre-this-fix scheme: the audible id was keyed by tone alone (no importance), and the
            // silent channel had no per-importance suffix at all.
            add("${ConsoleFcmService.CHANNEL_ID}_default")
            add("${ConsoleFcmService.CHANNEL_ID}_chime")
            add(ConsoleFcmService.CHANNEL_ID_SILENT)
            // The very first scheme, before there was even a per-tone split.
            add(ConsoleFcmService.CHANNEL_ID)
        }
        staleIds.forEach { id ->
            runCatching { manager.deleteNotificationChannel(id) }
                .onFailure { Log.w(TAG, "deleteNotificationChannel($id) failed", it) }
        }
    }

    private fun NotificationImportance.toAndroidImportance(): Int = when (this) {
        NotificationImportance.HIGH -> NotificationManager.IMPORTANCE_HIGH
        NotificationImportance.DEFAULT -> NotificationManager.IMPORTANCE_DEFAULT
    }

    /** `android.resource://` — this app's first attempt at exposing [R.raw.chime] as a channel's
     * sound — reads fine in-process (e.g. previewing a ringtone via `MediaPlayer.setDataSource`)
     * but is unreliable specifically as a *[NotificationChannel]'s* sound: the sound is actually
     * played by a separate system process, which can silently fail to resolve an
     * `android.resource://` Uri pointing at another app's package and fall back to the device's
     * default notification sound instead — with no error surfaced anywhere this app could catch,
     * which is exactly the "Chime never actually plays" symptom this replaced. A `content://` Uri
     * through this app's existing [FileProvider] (already declared in the manifest for the message
     * composer's camera capture) is the reliable, cross-process-supported path instead — which
     * means the sound needs to actually exist as a file the provider can serve, not just live
     * inside the APK as a raw resource, so it's copied out to [Context.getFilesDir] once (cheap,
     * a few KB, and immutable — nothing here ever needs to re-copy it). Explicitly granting read
     * access to `com.android.systemui` (the process that plays a channel's sound on stock/AOSP-
     * derived Android) is required even with `grantUriPermissions="true"` on the provider — that
     * attribute only covers Uris handed out via an [Intent], and nothing here ever puts this one in
     * an [Intent].
     *
     * That `grantUriPermission` call is also the one genuinely OS-dependent step in here: it
     * assumes a `com.android.systemui` package exists and is grantable, which doesn't hold on
     * every OEM skin/emulator image. [syncChannels]'s own [runCatching] is what keeps a failure
     * here from crashing the app; this function has nothing further to fall back to, so on any
     * failure the audible channel just keeps whatever sound it already had (parity with every
     * other channel-recreate failure — e.g. a user-customized channel Android silently won't let
     * this app touch at all). */
    private suspend fun chimeSoundUri(): Uri = withContext(Dispatchers.IO) {
        val file = File(File(context.filesDir, "notification_sounds"), "chime.mp3")
        if (!file.exists()) {
            file.parentFile?.mkdirs()
            context.resources.openRawResource(R.raw.chime).use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        context.grantUriPermission("com.android.systemui", uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        uri
    }

    /** Always a plain create — never a delete+recreate of an existing id, see the class doc for
     * why. Safe (and a documented-idempotent no-op) to call with an id that already exists:
     * [audibleChannelId]/[silentChannelId] fold every setting that determines a channel's actual
     * behavior into the id itself, so an id that already exists can only ever already have exactly
     * these settings — there's never anything to change on it.
     *
     * Everything in here — [NotificationChannel] itself,
     * [NotificationManager.createNotificationChannel]/`deleteNotificationChannel` — is API 26+.
     * The only call site, [syncChannelsOrThrow], is already annotated, but that guard doesn't
     * satisfy lint's `NewApi` check on its own without this function also being annotated —
     * interprocedural analysis isn't part of that check. */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun createChannel(
        manager: NotificationManager,
        channelId: String,
        name: String,
        description: String,
        androidImportance: Int,
        soundUri: Uri?,
    ) {
        val channel = NotificationChannel(channelId, name, androidImportance).apply {
            this.description = description
            setSound(soundUri, if (soundUri != null) notificationAudioAttributes else null)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "ConsolePushChannelMgr"

        /** Distinct, never-reused channel id per ([NotificationImportance], [NotificationSoundTone])
         * combination — see this class's own doc comment for why a channel's id must fully encode
         * every setting that affects its behavior, rather than trying to change either in place.
         * [ConsolePushNotifier.resolveChannelId] calls this too, so both sides always agree on
         * which literal channel id "the audible one" currently is. */
        fun audibleChannelId(importance: NotificationImportance, tone: NotificationSoundTone): String =
            "${ConsoleFcmService.CHANNEL_ID}_${importance.name.lowercase()}_${tone.name.lowercase()}"

        /** Same reasoning as [audibleChannelId], one axis narrower — the silent channel has no
         * sound to key on, only importance. */
        fun silentChannelId(importance: NotificationImportance): String =
            "${ConsoleFcmService.CHANNEL_ID_SILENT}_${importance.name.lowercase()}"

        /** [PushCategory.ONLINE]'s dedicated channel id — keyed by [NotificationSoundTone] only,
         * deliberately never by [NotificationImportance]: this channel is always created at
         * `IMPORTANCE_HIGH` (see [syncChannelsOrThrow]), regardless of what the user's Alert style
         * setting currently is. [ConsolePushNotifier.resolveChannelId] calls this too. */
        fun onlineChannelId(tone: NotificationSoundTone): String =
            "${ConsoleFcmService.CHANNEL_ID}_online_${tone.name.lowercase()}"
    }
}
