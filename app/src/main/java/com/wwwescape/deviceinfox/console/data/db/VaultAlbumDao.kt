package com.wwwescape.deviceinfox.console.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultAlbumDao {
    @Query("SELECT * FROM vault_albums ORDER BY createdAtEpochMillis ASC")
    fun observeAll(): Flow<List<VaultAlbumEntity>>

    @Upsert
    suspend fun upsert(album: VaultAlbumEntity)

    @Query("UPDATE vault_albums SET name = :name WHERE id = :id")
    suspend fun rename(id: String, name: String)

    /** Items in this album survive — [VaultItemEntity.albumId]'s foreign key is
     * `onDelete = SET_NULL`, so they fall back to "no album" rather than being deleted. */
    @Query("DELETE FROM vault_albums WHERE id = :id")
    suspend fun delete(id: String)

    /** One-shot snapshot, same purpose as `VaultItemDao.getAllSnapshot`. */
    @Query("SELECT * FROM vault_albums")
    suspend fun getAllSnapshot(): List<VaultAlbumEntity>
}
