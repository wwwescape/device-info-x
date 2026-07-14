package com.wwwescape.deviceinfox.console.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vault_albums")
data class VaultAlbumEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAtEpochMillis: Long,
    /** See [VaultItemEntity.ownerId]'s doc comment — same v2 -> v3 migration, same null-means-
     * mine fallback for pre-Phase-11.8 rows. */
    val ownerId: String? = null,
)
