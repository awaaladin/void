package com.vaultkeep.app.domain.usecase

import android.net.Uri
import com.vaultkeep.app.data.local.BackupSetsPreferences
import com.vaultkeep.app.domain.BackupDestination
import com.vaultkeep.app.domain.model.BackupSetKind
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * What every "Back up now" / "Start backup" button actually runs: the
 * manually-picked SAF folder (if one is set) plus whichever MediaStore
 * sets (Photos/Videos) are enabled on Home — all to the one [destination]
 * passed in. Before this existed, enabling "Photos" on Home only saved a
 * preference that nothing ever read; this is what makes that toggle real.
 */
class RunAllEnabledBackupsUseCase @Inject constructor(
    private val runUsbBackupUseCase: RunUsbBackupUseCase,
    private val runMediaStoreBackupUseCase: RunMediaStoreBackupUseCase,
    private val backupSetsPreferences: BackupSetsPreferences,
) {
    suspend fun execute(
        sourceTreeUri: Uri?,
        destination: BackupDestination,
        onProgress: suspend (BackupProgressEvent) -> Unit,
    ): BackupRunSummary {
        var total = BackupRunSummary(filesCopied = 0, filesSkipped = 0, filesFailed = 0, bytesCopied = 0)

        if (sourceTreeUri != null) {
            total = total.merge(
                runUsbBackupUseCase.execute(sourceTreeUri, destination, BackupSetKind.CUSTOM, onProgress),
            )
        }

        for (kind in listOf(BackupSetKind.PHOTOS, BackupSetKind.VIDEOS)) {
            if (backupSetsPreferences.isEnabled(kind).first()) {
                total = total.merge(runMediaStoreBackupUseCase.execute(kind, destination, onProgress))
            }
        }

        return total
    }

    private fun BackupRunSummary.merge(other: BackupRunSummary) = BackupRunSummary(
        filesCopied = filesCopied + other.filesCopied,
        filesSkipped = filesSkipped + other.filesSkipped,
        filesFailed = filesFailed + other.filesFailed,
        bytesCopied = bytesCopied + other.bytesCopied,
        firstError = firstError ?: other.firstError,
    )
}
