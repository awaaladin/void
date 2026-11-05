package com.vaultkeep.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.vaultkeep.app.data.local.entity.BackupRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BackupRecordDao {
    @Query("SELECT * FROM backup_records WHERE fileId = :fileId AND destinationId = :destinationId LIMIT 1")
    suspend fun find(fileId: Long, destinationId: String): BackupRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: BackupRecordEntity): Long

    @Update
    suspend fun update(entity: BackupRecordEntity)

    @Query("SELECT * FROM backup_records WHERE destinationId = :destinationId ORDER BY updatedAt DESC")
    fun observeForDestination(destinationId: String): Flow<List<BackupRecordEntity>>

    @Query("SELECT * FROM backup_records WHERE status = 'FAILED'")
    fun observeFailed(): Flow<List<BackupRecordEntity>>
}
