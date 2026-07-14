package com.wwwescape.deviceinfox.console.data.events

import androidx.annotation.DrawableRes
import com.wwwescape.deviceinfox.R
import java.time.LocalDate
import kotlinx.serialization.Serializable

/** One row of `res/raw/events.json` as parsed off disk — [date]/[type] stay raw strings here
 * ([HolidayEventsRepository] does the real parsing into [java.time.LocalDate]/[HolidayEventType]),
 * so a single malformed entry can be skipped individually rather than failing the whole file. */
@Serializable
data class HolidayEventDto(
    val name: String,
    val date: String,
    val type: String,
    val note: String? = null,
    val wishes: String,
)

/** [type] string in `events.json`, and the icon shown for it in [com.wwwescape.deviceinfox.console.ui.tabs.EventPopup]
 * — mirrors the four PNGs under `res/drawable/` (`birthday`/`national`/`catholic`/`hindu`), moved
 * there from the repo-root `assets/` folder since nothing in this app has ever loaded images from
 * that location (everything else goes through `res/drawable/`). [BIRTHDAY] isn't a real
 * `events.json` category — birthdays are per-user data from `User.birthday_date`, not a bundled
 * static file — but it shares the same popup shape and icon-per-type concept, so it lives in the
 * same enum rather than a parallel one-off constant. */
enum class HolidayEventType(@param:DrawableRes val iconRes: Int) {
    BIRTHDAY(R.drawable.birthday),
    NATIONAL(R.drawable.national),
    CATHOLIC(R.drawable.catholic),
    HINDU(R.drawable.hindu),
    ;

    companion object {
        fun fromJson(value: String): HolidayEventType? = when (value) {
            "national" -> NATIONAL
            "catholic" -> CATHOLIC
            "hindu" -> HINDU
            else -> null
        }
    }
}

/** A single national/catholic/hindu holiday, as actually usable by the UI/popup logic —
 * [date] parsed, [type] resolved to a real [HolidayEventType]. */
data class HolidayEvent(
    val name: String,
    val date: LocalDate,
    val type: HolidayEventType,
    val wishes: String,
)
