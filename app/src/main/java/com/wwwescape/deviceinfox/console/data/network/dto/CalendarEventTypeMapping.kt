package com.wwwescape.deviceinfox.console.data.network.dto

import com.wwwescape.deviceinfox.console.data.db.EventType

fun EventTypeDto.toEventType(): EventType = when (this) {
    EventTypeDto.BIRTHDAY -> EventType.BIRTHDAY
    EventTypeDto.WEDDING_ANNIVERSARY -> EventType.WEDDING_ANNIVERSARY
    EventTypeDto.ANNIVERSARY -> EventType.ANNIVERSARY
    EventTypeDto.VACATION -> EventType.VACATION
    EventTypeDto.PLANNED_TRIP -> EventType.PLANNED_TRIP
    EventTypeDto.PLANNED_DATE -> EventType.PLANNED_DATE
    EventTypeDto.UNPLANNED_DATE -> EventType.UNPLANNED_DATE
    EventTypeDto.PLANNED_DRIVE -> EventType.PLANNED_DRIVE
    EventTypeDto.UNPLANNED_DRIVE -> EventType.UNPLANNED_DRIVE
    EventTypeDto.REMINDER -> EventType.REMINDER
    EventTypeDto.CUSTOM -> EventType.CUSTOM
}

fun EventType.toEventTypeDto(): EventTypeDto = when (this) {
    EventType.BIRTHDAY -> EventTypeDto.BIRTHDAY
    EventType.WEDDING_ANNIVERSARY -> EventTypeDto.WEDDING_ANNIVERSARY
    EventType.ANNIVERSARY -> EventTypeDto.ANNIVERSARY
    EventType.VACATION -> EventTypeDto.VACATION
    EventType.PLANNED_TRIP -> EventTypeDto.PLANNED_TRIP
    EventType.PLANNED_DATE -> EventTypeDto.PLANNED_DATE
    EventType.UNPLANNED_DATE -> EventTypeDto.UNPLANNED_DATE
    EventType.PLANNED_DRIVE -> EventTypeDto.PLANNED_DRIVE
    EventType.UNPLANNED_DRIVE -> EventTypeDto.UNPLANNED_DRIVE
    EventType.REMINDER -> EventTypeDto.REMINDER
    EventType.CUSTOM -> EventTypeDto.CUSTOM
}
