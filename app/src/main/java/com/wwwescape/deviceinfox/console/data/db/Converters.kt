package com.wwwescape.deviceinfox.console.data.db

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromPartnerGender(value: PartnerGender): String = value.name

    @TypeConverter
    fun toPartnerGender(value: String): PartnerGender = PartnerGender.valueOf(value)

    @TypeConverter
    fun fromEventType(value: EventType): String = value.name

    @TypeConverter
    fun toEventType(value: String): EventType = EventType.valueOf(value)

    /** Comma-joined — [CalendarEventEntity.reminderMinutesBefore] is always a short, small-int
     * list (typically `[1440, 5]` or empty), not worth a JSON dependency for. */
    @TypeConverter
    fun fromMinutesBeforeList(value: List<Int>): String = value.joinToString(",")

    @TypeConverter
    fun toMinutesBeforeList(value: String): List<Int> =
        if (value.isBlank()) emptyList() else value.split(",").map { it.trim().toInt() }

    /** Comma-joined — same trade-off as [fromMinutesBeforeList]. Used for
     * [CalendarEventEntity.intimacyPositions]/[CalendarEventEntity.intimacyLocations]; none of
     * the fixed vocabulary's labels contain a comma. */
    @TypeConverter
    fun fromStringList(value: List<String>): String = value.joinToString(",")

    @TypeConverter
    fun toStringList(value: String): List<String> =
        if (value.isBlank()) emptyList() else value.split(",").map { it.trim() }

    @TypeConverter
    fun fromCycleFlowIntensity(value: CycleFlowIntensity?): String? = value?.name

    @TypeConverter
    fun toCycleFlowIntensity(value: String?): CycleFlowIntensity? = value?.let { CycleFlowIntensity.valueOf(it) }

    @TypeConverter
    fun fromMessageDeliveryState(value: MessageDeliveryState): String = value.name

    @TypeConverter
    fun toMessageDeliveryState(value: String): MessageDeliveryState = MessageDeliveryState.valueOf(value)

    @TypeConverter
    fun fromNotepadEntryType(value: NotepadEntryType): String = value.name

    @TypeConverter
    fun toNotepadEntryType(value: String): NotepadEntryType = NotepadEntryType.valueOf(value)
}
