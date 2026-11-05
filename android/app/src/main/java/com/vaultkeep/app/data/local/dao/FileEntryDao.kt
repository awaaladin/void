package com.vaultkeep.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.vaultkeep.app.data.local.entity.FileEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FileEntryDao {
    @Query("SELECT * FROM file_entries WHERE relativePath = :relativePath LIMIT 1")
    suspend fun findByRelativePath(relativePath: String): FileEntryEntity?

    @Query("SELECT * FROM file_entries WHERE sha256 = :sha256 LIMIT 1")
    suspend fun findByHash(sha256: String): FileEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: FileEntryEntity): Long

    @Update
    suspend fun update(entity: FileEntryEntity)

    @Query("SELECT * FROM file_entries ORDER BY modifiedAt DESC")
    fun observeAll(): Flow<List<FileEntryEntity>>

    @Query("SELECT * FROM file_entries")
    suspend fun getAll(): List<FileEntryEntity>
}
