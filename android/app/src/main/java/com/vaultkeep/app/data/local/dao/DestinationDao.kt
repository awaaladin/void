package com.vaultkeep.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.vaultkeep.app.data.local.entity.DestinationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DestinationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DestinationEntity)

    @Query("SELECT * FROM destinations WHERE id = :id LIMIT 1")
    suspend fun find(id: String): DestinationEntity?

    @Query("SELECT * FROM destinations ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DestinationEntity>>
}
