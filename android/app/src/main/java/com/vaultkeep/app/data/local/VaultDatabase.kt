package com.vaultkeep.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.vaultkeep.app.data.local.dao.BackupRecordDao
import com.vaultkeep.app.data.local.dao.BackupRunDao
import com.vaultkeep.app.data.local.dao.DestinationDao
import com.vaultkeep.app.data.local.dao.FileEntryDao
import com.vaultkeep.app.data.local.entity.BackupRecordEntity
import com.vaultkeep.app.data.local.entity.BackupRunEntity
import com.vaultkeep.app.data.local.entity.DestinationEntity
import com.vaultkeep.app.data.local.entity.FileEntryEntity

@Database(
    entities = [
        FileEntryEntity::class,
        BackupRecordEntity::class,
        DestinationEntity::class,
        BackupRunEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class VaultDatabase : RoomDatabase() {
    abstract fun fileEntryDao(): FileEntryDao
    abstract fun backupRecordDao(): BackupRecordDao
    abstract fun destinationDao(): DestinationDao
    abstract fun backupRunDao(): BackupRunDao

    companion object {
        const val DATABASE_NAME = "vaultkeep.db"

        /** Adds `encrypted` so Phase 7 restore knows, per record, whether to decrypt on the way back out. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE backup_records ADD COLUMN encrypted INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
