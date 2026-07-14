package com.wwwescape.deviceinfox.console.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** One row per logged day, not per whole period — [partnerId] scopes each entry to whichever
 * partner it belongs to, same as the old cycle-level table it replaces. A "period day" (what
 * [com.wwwescape.deviceinfox.console.data.cycle.CycleRepository]'s prediction math groups into
 * cycles) is any row with [flowIntensity] set; a row with only [symptoms]/[notes] (e.g. spotting,
 * or a symptom logged on a day that isn't part of a period at all) doesn't count until/unless
 * it's edited to add a real flow value. Cycle start/end/length are never stored here — they're
 * derived at read time from contiguous period-day runs. */
@Entity(
    tableName = "period_day_logs",
    foreignKeys = [
        ForeignKey(
            entity = PartnerEntity::class,
            parentColumns = ["id"],
            childColumns = ["partnerId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("partnerId"), Index(value = ["partnerId", "dateEpochMillis"], unique = true)],
)
data class PeriodDayLogEntity(
    @PrimaryKey val id: String,
    val partnerId: String,
    val dateEpochMillis: Long,
    val flowIntensity: CycleFlowIntensity?,
    val symptoms: String?,
    val notes: String?,
)
