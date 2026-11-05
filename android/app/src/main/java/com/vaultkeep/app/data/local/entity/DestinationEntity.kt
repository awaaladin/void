package com.vaultkeep.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.vaultkeep.app.domain.model.DestinationType

/**
 * `configJson` holds destination-specific config (SAF tree URI for USB, host
 * for NAS, bucket for cloud, etc). Kept opaque at the Room layer so adding a
 * new destination type never needs a schema migration.
 */
@Entity(tableName = "destinations")
data class DestinationEntity(
    @PrimaryKey
    val id: String,
    val type: DestinationType,
    val displayName: String,
    val configJson: String,
    val createdAt: Long,
)
