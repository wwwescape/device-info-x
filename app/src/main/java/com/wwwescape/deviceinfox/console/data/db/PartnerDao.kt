package com.wwwescape.deviceinfox.console.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PartnerDao {
    @Query("SELECT * FROM partners WHERE isSelf = 1 LIMIT 1")
    fun observeSelf(): Flow<PartnerEntity?>

    @Query("SELECT * FROM partners WHERE isSelf = 1 LIMIT 1")
    suspend fun getSelf(): PartnerEntity?

    @Query("SELECT * FROM partners WHERE isSelf = 0 LIMIT 1")
    fun observePartner(): Flow<PartnerEntity?>

    @Query("SELECT * FROM partners WHERE isSelf = 0 LIMIT 1")
    suspend fun getPartner(): PartnerEntity?

    @Upsert
    suspend fun upsert(partner: PartnerEntity)

    @Query("DELETE FROM partners WHERE isSelf = 0")
    suspend fun deletePartner()
}
