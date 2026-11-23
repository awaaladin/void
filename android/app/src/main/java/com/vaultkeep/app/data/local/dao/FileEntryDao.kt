package com.vaultkeep.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.vaultkeep.app.data.local.entity.FileEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FileEntryDao {
    @Query("SELECT * FROM file_entries WHERE relativePath = :relativePath LIMIT 1")
    suspend fun findByRelativePath(relativePath: String): FileEntryEntity?

    @Query("SELECT * FROM file_entries WHERE sha256 = :sha256 LIMIT 1")
    suspend fun findByHash(sha256: String): FileEntryEntity?

    @Insert
    suspend fun insert(entity: FileEntryEntity): Long

    @Update
    suspend fun update(entity: FileEntryEntity)

    /**
     * Updates the existing row for this relativePath in place, or inserts a new
     * one. Deliberately never INSERT OR REPLACE: that does a delete+insert under
     * the hood, and backup_records.fileId is ON DELETE CASCADE — a REPLACE here
     * would silently wipe out every other destination's already-verified record
     * for this same file. Wrapped in a transaction so two destinations backing
     * up the same file concurrently can't both see "no existing row" and insert
     * duplicates.
     */
    @Transaction
    suspend fun upsert(entity: FileEntryEntity): Long {
        val existing = findByRelativePath(entity.relativePath)
        return if (existing != null) {
            update(entity.copy(id = existing.id))
            existing.id
        } else {
            insert(entity.copy(id = 0))
        }
    }

    @Query("SELECT * FROM file_entries ORDER BY modifiedAt DESC")
    fun observeAll(): Flow<List<FileEntryEntity>>

    @Query("SELECT * FROM file_entries")
    suspend fun getAll(): List<FileEntryEntity>
}
