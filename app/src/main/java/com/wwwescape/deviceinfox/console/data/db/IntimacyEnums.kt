package com.wwwescape.deviceinfox.console.data.db

/** Fixed vocabulary, no custom entries (confirmed) — deliberately plain [String] labels, not
 * string resources: kept English-only regardless of the device's locale, matching the reference
 * design verbatim rather than risking a mistranslation of specialized/colloquial terms. */
enum class IntimacyPosition(val label: String) {
    SIXTY_NINE("69"),
    SIXTY_NINE_SPELLED_OUT("69 Sixty Nine"),
    BALLERINA("Ballerina"),
    COWGIRL("Cowgirl"),
    DOGGY_STYLE("Doggy Style"),
    LOTUS("Lotus"),
    MISSIONARY("Missionary"),
    MUTUAL_MASTURBATION("Mutual Masturbation"),
    ORAL("Oral"),
    PRETZEL_DIP("Pretzel Dip"),
    PRONE_BONING("Prone Boning"),
    REVERSE_COWGIRL("Reverse Cowgirl"),
    SCISSORING("Scissoring"),
    SPOONING("Spooning"),
    STANDING("Standing"),
    WHEELBARROW("Wheelbarrow"),
    ;

    companion object {
        fun fromLabel(label: String): IntimacyPosition? = entries.find { it.label == label }
    }
}

enum class IntimacyLocation(val label: String) {
    BALCONY("Balcony"),
    BATHROOM("Bathroom"),
    BEACH("Beach"),
    BEDROOM("Bedroom"),
    CAR("Car"),
    ELEVATOR("Elevator"),
    GYM("Gym"),
    HOTEL("Hotel"),
    KITCHEN("Kitchen"),
    LIVING_ROOM("Living Room"),
    OFFICE("Office"),
    OUTDOORS("Outdoors"),
    POOL("Pool"),
    ROOFTOP("Rooftop"),
    ;

    companion object {
        fun fromLabel(label: String): IntimacyLocation? = entries.find { it.label == label }
    }
}

/** Same "deliberately unlocalized, fixed vocabulary" reasoning as [IntimacyPosition]/
 * [IntimacyLocation] above. */
enum class IntimacyMood(val label: String) {
    ROMANTIC("Romantic"),
    HORNY("Horny"),
    PLAYFUL("Playful"),
    PASSIONATE("Passionate"),
    SPONTANEOUS("Spontaneous"),
    RELAXED("Relaxed"),
    ADVENTUROUS("Adventurous"),
    ;

    companion object {
        fun fromLabel(label: String): IntimacyMood? = entries.find { it.label == label }
    }
}

/** Sentinel stored in [CalendarEventEntity.intimacyInitiatedByPartnerId] meaning "both partners"
 * — the only other valid values are a real partner id. Mirrors the server's
 * `INITIATED_BY_BOTH` constant in `app/models/intimacy_log.py`. */
const val INTIMACY_INITIATED_BY_BOTH = "both"

/** Sentinel stored in [CalendarEventEntity.intimacyOrgasmedByPartnerId] meaning "neither
 * partner" — a real answer, distinct from the field simply being unset. The other valid values
 * are [INTIMACY_INITIATED_BY_BOTH] or a real partner id. Mirrors the server's
 * `ORGASMED_BY_NONE` constant in `app/models/intimacy_log.py`. */
const val INTIMACY_ORGASMED_BY_NONE = "none"
