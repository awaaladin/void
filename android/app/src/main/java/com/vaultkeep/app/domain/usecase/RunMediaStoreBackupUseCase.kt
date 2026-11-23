package com.vaultkeep.app.domain.usecase

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.vaultkeep.app.data.crypto.EncryptingBackupDestination
import com.vaultkeep.app.domain.BackupDestination
import com.vaultkeep.app.domain.model.BackupSetKind
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * The MediaStore-sourced counterpart to [RunUsbBackupUseCase]: Photos and
 * Videos live in MediaStore under scoped storage, not somewhere a SAF
 * folder picker naturally reaches, so this queries
 * `MediaStore.Images`/`Video` directly (same tables [MediaStoreCounts]
 * already reads for the Home screen's file counts) instead of walking a
 * DocumentFile tree. Per-file logic is the same [BackupFileWriter] the SAF
 * path uses — hash, incremental-skip, write, verify, Room bookkeeping.
 *
 * Requires READ_MEDIA_IMAGES/READ_MEDIA_VIDEO already granted; like
 * [MediaStoreCounts], it relies on the caller having checked that first —
 * MediaStore returns zero rows rather than throwing without it.
 */
class RunMediaStoreBackupUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fileWriter: BackupFileWriter,
) {
    suspend fun execute(
        setType: BackupSetKind,
        destination: BackupDestination,
        onProgress: suspend (BackupProgressEvent) -> Unit,
    ): BackupRunSummary = withContext(Dispatchers.IO) {
        val collectionUri = collectionUriFor(setType)
        val entries = queryEntries(collectionUri)

        val encrypted = destination is EncryptingBackupDestination

        var copied = 0
        var skipped = 0
        var failed = 0
        var bytesCopied = 0L
        var firstError: String? = null

        for ((index, entry) in entries.withIndex()) {
            val outcome = fileWriter.process(
                relativePath = entry.relativePath,
                sizeBytes = entry.sizeBytes,
                modifiedAt = entry.modifiedAt,
                setType = setType,
                sourceUri = entry.uri.toString(),
                destination = destination,
                encrypted = encrypted,
                openInput = {
                    context.contentResolver.openInputStream(entry.uri)
                        ?: error("Could not open '${entry.relativePath}'")
                },
            )

            when (outcome) {
                is BackupFileWriter.Outcome.Skipped -> {
                    skipped++
                    onProgress(BackupProgressEvent(entry.relativePath, index + 1, entries.size, bytesCopied, skipped = true))
                }
                is BackupFileWriter.Outcome.Copied -> {
                    copied++
                    bytesCopied += outcome.bytesWritten
                    onProgress(BackupProgressEvent(entry.relativePath, index + 1, entries.size, bytesCopied))
                }
                is BackupFileWriter.Outcome.Failed -> {
                    failed++
                    firstError = firstError ?: outcome.message
                    onProgress(BackupProgressEvent(entry.relativePath, index + 1, entries.size, bytesCopied, error = outcome.message))
                }
            }
        }

        BackupRunSummary(filesCopied = copied, filesSkipped = skipped, filesFailed = failed, bytesCopied = bytesCopied, firstError = firstError)
    }

    private fun collectionUriFor(setType: BackupSetKind): Uri = when (setType) {
        BackupSetKind.PHOTOS -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        BackupSetKind.VIDEOS -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        else -> error("RunMediaStoreBackupUseCase only supports PHOTOS or VIDEOS, got $setType")
    }

    private data class MediaEntry(val uri: Uri, val relativePath: String, val sizeBytes: Long, val modifiedAt: Long)

    private fun queryEntries(collectionUri: Uri): List<MediaEntry> {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.RELATIVE_PATH,
        )
        val entries = mutableListOf<MediaEntry>()
        context.contentResolver.query(collectionUri, projection, null, null, null)?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val dateIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
            val relPathIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)

            while (cursor.moveToNext()) {
                val name = cursor.getString(nameIndex) ?: continue
                val id = cursor.getLong(idIndex)
                val uri = ContentUris.withAppendedId(collectionUri, id)
                // RELATIVE_PATH is e.g. "DCIM/Camera/" — the trailing slash is part of the
                // MediaStore convention, trimmed here so the joined path reads normally.
                val relPath = (cursor.getString(relPathIndex) ?: "").trim('/')
                val relativePath = if (relPath.isEmpty()) name else "$relPath/$name"
                entries.add(
                    MediaEntry(
                        uri = uri,
                        relativePath = relativePath,
                        sizeBytes = cursor.getLong(sizeIndex),
                        modifiedAt = cursor.getLong(dateIndex) * 1000,
                    ),
                )
            }
        }
        return entries
    }
}
