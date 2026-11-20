package com.vaultkeep.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.vaultkeep.app.data.local.entity.BackupRecordEntity
import kotlinx.coroutines.flow.Flow

/** One row the restore screen can actually act on: enough of the file + its record on one destination. */
data class RestorableFileRow(
    val fileId: Long,
    val relativePath: String,
    val sizeBytes: Long,
    val sha256: String?,
    val remoteKey: String,
    val encrypted: Boolean,
)

/** One destination's aggregate — the same shape Restore's picker and Storage Insights both need. */
data class DestinationSummaryRow(
    val destinationId: String,
    val fileCount: Int,
    val totalBytes: Long,
    val lastBackupAt: Long,
)

/** Aggregate by backup set (Photos/Videos/Documents/...) for Storage Insights. */
data class SetKindSummaryRow(
    val setType: String,
    val fileCount: Int,
    val totalBytes: Long,
)

/** Deduped across every destination — the "how much do I actually have backed up" headline number. */
data class OverallSummaryRow(
    val fileCount: Int,
    val totalBytes: Long,
    val lastBackupAt: Long?,
)

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

    @Query(
        """
        SELECT DISTINCT r.destinationId
        FROM backup_records r
        WHERE r.status = 'VERIFIED'
        """,
    )
    fun observeRestorableDestinationIds(): Flow<List<String>>

    @Query(
        """
        SELECT f.id AS fileId, f.relativePath AS relativePath, f.sizeBytes AS sizeBytes,
               f.sha256 AS sha256, r.remoteKey AS remoteKey, r.encrypted AS encrypted
        FROM backup_records r
        JOIN file_entries f ON f.id = r.fileId
        WHERE r.destinationId = :destinationId AND r.status = 'VERIFIED' AND r.remoteKey IS NOT NULL
        ORDER BY f.relativePath
        """,
    )
    suspend fun restorableFiles(destinationId: String): List<RestorableFileRow>

    @Query(
        """
        SELECT r.destinationId AS destinationId, COUNT(*) AS fileCount,
               SUM(f.sizeBytes) AS totalBytes, MAX(r.updatedAt) AS lastBackupAt
        FROM backup_records r
        JOIN file_entries f ON f.id = r.fileId
        WHERE r.status = 'VERIFIED'
        GROUP BY r.destinationId
        """,
    )
    fun observeDestinationSummaries(): Flow<List<DestinationSummaryRow>>

    // A file verified on more than one destination must only count once here — the inner
    // DISTINCT collapses (fileId, setType, sizeBytes) to one row per file before summing,
    // so backing the same photo up to both USB and Cloud doesn't double its reported size.
    @Query(
        """
        SELECT setType, COUNT(*) AS fileCount, SUM(sizeBytes) AS totalBytes FROM (
            SELECT DISTINCT f.id AS fileId, f.setType AS setType, f.sizeBytes AS sizeBytes
            FROM backup_records r
            JOIN file_entries f ON f.id = r.fileId
            WHERE r.status = 'VERIFIED'
        )
        GROUP BY setType
        """,
    )
    fun observeSetKindSummaries(): Flow<List<SetKindSummaryRow>>

    @Query(
        """
        SELECT COUNT(*) AS fileCount, COALESCE(SUM(sizeBytes), 0) AS totalBytes,
               (SELECT MAX(updatedAt) FROM backup_records WHERE status = 'VERIFIED') AS lastBackupAt
        FROM (
            SELECT DISTINCT f.id AS id, f.sizeBytes AS sizeBytes
            FROM backup_records r
            JOIN file_entries f ON f.id = r.fileId
            WHERE r.status = 'VERIFIED'
        )
        """,
    )
    fun observeOverallSummary(): Flow<OverallSummaryRow>
}
