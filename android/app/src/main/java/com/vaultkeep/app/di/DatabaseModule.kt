package com.vaultkeep.app.di

import android.content.Context
import androidx.room.Room
import com.vaultkeep.app.data.local.VaultDatabase
import com.vaultkeep.app.data.local.dao.BackupRecordDao
import com.vaultkeep.app.data.local.dao.BackupRunDao
import com.vaultkeep.app.data.local.dao.DestinationDao
import com.vaultkeep.app.data.local.dao.FileEntryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideVaultDatabase(@ApplicationContext context: Context): VaultDatabase =
        Room.databaseBuilder(context, VaultDatabase::class.java, VaultDatabase.DATABASE_NAME)
            .build()

    @Provides
    fun provideFileEntryDao(db: VaultDatabase): FileEntryDao = db.fileEntryDao()

    @Provides
    fun provideBackupRecordDao(db: VaultDatabase): BackupRecordDao = db.backupRecordDao()

    @Provides
    fun provideDestinationDao(db: VaultDatabase): DestinationDao = db.destinationDao()

    @Provides
    fun provideBackupRunDao(db: VaultDatabase): BackupRunDao = db.backupRunDao()
}
