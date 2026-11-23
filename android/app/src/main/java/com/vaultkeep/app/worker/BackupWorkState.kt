package com.vaultkeep.app.worker

import androidx.work.WorkInfo
import com.vaultkeep.app.domain.usecase.BackupProgressEvent
import com.vaultkeep.app.domain.usecase.BackupRunSummary

/** What a destination's setup screen needs to render, read off a [WorkInfo] for a [BackupWorker] run. */
data class BackupWorkState(
    val isRunning: Boolean,
    val progress: BackupProgressEvent?,
    val summary: BackupRunSummary?,
    val error: String?,
)

fun WorkInfo?.toBackupWorkState(): BackupWorkState {
    if (this == null) return BackupWorkState(isRunning = false, progress = null, summary = null, error = null)

    val progressData = progress
    val progressEvent = progressData.getInt(BackupWorker.KEY_TOTAL_FILES, -1).takeIf { it >= 0 }?.let { total ->
        BackupProgressEvent(
            filesDone = progressData.getInt(BackupWorker.KEY_FILES_DONE, 0),
            totalFiles = total,
            currentFile = progressData.getString(BackupWorker.KEY_CURRENT_FILE) ?: "",
            bytesDone = 0L,
        )
    }

    return when (state) {
        WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED ->
            BackupWorkState(isRunning = true, progress = progressEvent, summary = null, error = null)
        WorkInfo.State.SUCCEEDED -> BackupWorkState(
            isRunning = false,
            progress = null,
            summary = BackupRunSummary(
                filesCopied = outputData.getInt(BackupWorker.KEY_FILES_COPIED, 0),
                filesSkipped = outputData.getInt(BackupWorker.KEY_FILES_SKIPPED, 0),
                filesFailed = outputData.getInt(BackupWorker.KEY_FILES_FAILED, 0),
                bytesCopied = outputData.getLong(BackupWorker.KEY_BYTES_COPIED, 0),
                firstError = outputData.getString(BackupWorker.KEY_FIRST_ERROR),
            ),
            error = null,
        )
        WorkInfo.State.FAILED -> BackupWorkState(
            isRunning = false,
            progress = null,
            summary = null,
            error = outputData.getString(BackupWorker.KEY_FIRST_ERROR) ?: "Backup failed",
        )
        WorkInfo.State.CANCELLED -> BackupWorkState(isRunning = false, progress = null, summary = null, error = null)
    }
}
