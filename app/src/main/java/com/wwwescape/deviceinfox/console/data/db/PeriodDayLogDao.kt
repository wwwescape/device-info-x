package com.wwwescape.deviceinfox.console.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PeriodDayLogDao {
    @Query("SELECT * FROM period_day_logs WHERE partnerId = :partnerId ORDER BY dateEpochMillis DESC")
    fun observeForPartner(partnerId: String): Flow<List<PeriodDayLogEntity>>

    @Upsert
    suspend fun upsert(entry: PeriodDayLogEntity)

    /** Room wraps a list-parameter `@Upsert` in a single transaction, so this fires exactly one
     * invalidation of [observeForPartner]'s `Flow` for the whole batch — used by onboarding (see
     * `CycleRepository.saveOnboardingDayLogs`) so its several day logs appear atomically instead
     * of trickling in one `Flow` emission at a time. */
    @Upsert
    suspend fun upsertAll(entries: List<PeriodDayLogEntity>)

    @Query("DELETE FROM period_day_logs WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM period_day_logs WHERE partnerId = :partnerId")
    suspend fun deleteAllForPartner(partnerId: String)

    /** The "Delete data" action for Period Tracker in Settings — wipes logged days for both
     * partners (no `WHERE`, unlike [deleteAllForPartner]), mirroring
     * `period_repo.delete_all_day_logs` on the server. */
    @Query("DELETE FROM period_day_logs")
    suspend fun deleteAll()

    /** Full-sync replace scoped to a single partner's tab — each tab is fetched independently
     * (lazily, only when its tab is actually viewed), so this must not touch the other
     * partner's already-cached rows. */
    @Transaction
    suspend fun replaceForPartner(partnerId: String, entries: List<PeriodDayLogEntity>) {
        deleteAllForPartner(partnerId)
        entries.forEach { upsert(it) }
    }
}
