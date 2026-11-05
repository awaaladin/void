package com.vaultkeep.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.vaultkeep.app.domain.model.BackupStatus

/**
 * One row per (file, destination) pair — this is what makes the index
 * per-destination: the same file can be VERIFIED on the USB drive and
 * PENDING on the cloud destination at the same time.
 */
@Entity(
    tableName = "backup_records",
    foreignKeys = [
        ForeignKey(
            entity = FileEntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["fileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("fileId"), Index("destinationId"), Index(value = ["fileId", "destinationId"], unique = true)],
)
data class BackupRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val fileId: Long,
    val destinationId: String,
    val status: BackupStatus,
    val remoteKey: String?,
    val chunksDone: Int = 0,
    val lastError: String? = null,
    val updatedAt: Long,
)
