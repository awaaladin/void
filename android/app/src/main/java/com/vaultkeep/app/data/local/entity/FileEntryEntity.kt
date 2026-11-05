package com.vaultkeep.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.vaultkeep.app.domain.model.BackupSetKind

/**
 * One row per source file VaultKeep knows about, regardless of destination.
 * `sha256` is nullable until the first hash pass completes; `uri` is the
 * SAF document URI (as a string) the file was last seen at, which lets us
 * detect renames (same hash+size, different uri) without re-reading content.
 */
@Entity(tableName = "file_entries")
data class FileEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val uri: String,
    val relativePath: String,
    val sizeBytes: Long,
    val modifiedAt: Long,
    val sha256: String?,
    val setType: BackupSetKind,
)
