package com.vaultkeep.app.data.local

import androidx.room.TypeConverter
import com.vaultkeep.app.domain.model.BackupSetKind
import com.vaultkeep.app.domain.model.BackupStatus
import com.vaultkeep.app.domain.model.DestinationType

class Converters {
    @TypeConverter
    fun backupStatusToString(value: BackupStatus): String = value.name

    @TypeConverter
    fun stringToBackupStatus(value: String): BackupStatus = BackupStatus.valueOf(value)

    @TypeConverter
    fun backupSetKindToString(value: BackupSetKind): String = value.name

    @TypeConverter
    fun stringToBackupSetKind(value: String): BackupSetKind = BackupSetKind.valueOf(value)

    @TypeConverter
    fun destinationTypeToString(value: DestinationType): String = value.name

    @TypeConverter
    fun stringToDestinationType(value: String): DestinationType = DestinationType.valueOf(value)
}
