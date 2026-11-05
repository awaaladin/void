package com.vaultkeep.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "backup_runs")
data class BackupRunEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val startedAt: Long,
    val finishedAt: Long?,
    val filesCopied: Int,
    val bytesCopied: Long,
    val result: String, // "SUCCESS" | "PARTIAL" | "FAILED" | "RUNNING"
)
