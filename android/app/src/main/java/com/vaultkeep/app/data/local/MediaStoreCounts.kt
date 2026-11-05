package com.vaultkeep.app.data.local

import android.content.Context
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class MediaSetStats(val fileCount: Int, val totalBytes: Long)

/**
 * Real MediaStore queries — not placeholder numbers. Requires the matching
 * READ_MEDIA_IMAGES / READ_MEDIA_VIDEO runtime permission to return anything
 * other than an empty set; callers are expected to have already checked
 * that (see HomeViewModel), since MediaStore silently returns zero rows
 * without the permission rather than throwing.
 */
@Singleton
class MediaStoreCounts @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun imageStats(): MediaSetStats = statsFor(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, MediaStore.Images.Media.SIZE)

    suspend fun videoStats(): MediaSetStats = statsFor(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, MediaStore.Video.Media.SIZE)

    private suspend fun statsFor(uri: android.net.Uri, sizeColumn: String): MediaSetStats = withContext(Dispatchers.IO) {
        var count = 0
        var totalBytes = 0L
        context.contentResolver.query(uri, arrayOf(sizeColumn), null, null, null)?.use { cursor ->
            val sizeIndex = cursor.getColumnIndexOrThrow(sizeColumn)
            while (cursor.moveToNext()) {
                count++
                totalBytes += cursor.getLong(sizeIndex)
            }
        }
        MediaSetStats(fileCount = count, totalBytes = totalBytes)
    }
}
