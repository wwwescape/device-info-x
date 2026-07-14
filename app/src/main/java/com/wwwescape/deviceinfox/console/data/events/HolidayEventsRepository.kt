package com.wwwescape.deviceinfox.console.data.events

import android.content.Context
import android.util.Log
import com.wwwescape.deviceinfox.R
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json

/** National/catholic/hindu holidays bundled at `res/raw/events.json` — the client's own copy;
 * `device-info-x-server`'s `app/data/events.json` is a second, server-side copy the reminder
 * sweep reads to drive the push side of this feature (see `_sweep_holiday_events`), kept in sync
 * by hand since this is small, static reference data that only changes once a year (several
 * entries are movable-calendar holidays, so next year's file needs new dates either way — there's
 * no way to compute them, just to remember to update both copies together).
 *
 * Parsed once and cached for the process lifetime — 18 small entries, no reason to re-parse on
 * every lookup. Parse failures are swallowed to an empty list (`runCatching`) rather than crashing
 * the console on a malformed file — a missing holiday popup is a minor miss, not worth losing the
 * whole app over. */
@Singleton
class HolidayEventsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val events: List<HolidayEvent> by lazy { loadEvents() }

    /** The one event (if any) falling on [date] — `events.json` entries carry a real full date
     * per year (not just month/day, unlike a birthday), since several are movable-calendar
     * holidays that don't recur on the same day each year, so this is a straightforward equality
     * check rather than a month/day comparison. At most one entry is expected to match any given
     * date in practice; if more than one somehow does, the first one in file order wins. */
    fun eventForDate(date: LocalDate): HolidayEvent? = events.firstOrNull { it.date == date }

    private fun loadEvents(): List<HolidayEvent> = runCatching {
        val raw = context.resources.openRawResource(R.raw.events).bufferedReader().use { it.readText() }
        val dtos = json.decodeFromString<List<HolidayEventDto>>(raw)
        dtos.mapNotNull { dto ->
            runCatching {
                val type = HolidayEventType.fromJson(dto.type) ?: return@runCatching null
                HolidayEvent(name = dto.name, date = LocalDate.parse(dto.date), type = type, wishes = dto.wishes)
            }.getOrNull()
        }
    }.onFailure { Log.w(TAG, "Failed to load events.json — holiday popups/pushes disabled this session", it) }
        .getOrDefault(emptyList())

    private companion object {
        const val TAG = "HolidayEventsRepo"
    }
}
