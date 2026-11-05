package com.vaultkeep.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Update
import com.vaultkeep.app.data.local.entity.BackupRunEntity
import kotlinx.coroutines.flow.Flow
import androidx.room.Query

@Dao
interface BackupRunDao {
    @Insert
    suspend fun insert(entity: BackupRunEntity): Long

    @Update
    suspend fun update(entity: BackupRunEntity)

    @Query("SELECT * FROM backup_runs ORDER BY startedAt DESC LIMIT 20")
    fun observeRecent(): Flow<List<BackupRunEntity>>
}
