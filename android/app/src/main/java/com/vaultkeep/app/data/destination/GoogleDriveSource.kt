package com.vaultkeep.app.data.destination

import com.vaultkeep.app.data.remote.DriveApi
import com.vaultkeep.app.domain.BackupSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

/** Reads files back from the "Void Backups" folder in Drive — the download-side mirror of [GoogleDriveDestination]. */
class GoogleDriveSource(
    private val api: DriveApi,
    accessToken: String,
    override val id: String = "drive:void",
) : BackupSource {

    private val bearer = "Bearer $accessToken"
    private val resolver = DriveFolderResolver(api, bearer)

    override suspend fun readFile(remoteKey: String): InputStream = withContext(Dispatchers.IO) {
        val parentId = resolver.resolveParentFolder(remoteKey, createIfMissing = false)
            ?: error("'$remoteKey' is no longer on Drive")
        val fileName = remoteKey.substringAfterLast('/')
        val fileId = resolver.findFile(fileName, parentId) ?: error("'$remoteKey' is no longer on Drive")

        val response = api.downloadMedia(bearer, fileId)
        if (!response.isSuccessful) {
            error("Drive download failed for '$remoteKey': HTTP ${response.code()}")
        }
        response.body()?.byteStream() ?: error("Drive returned an empty body for '$remoteKey'")
    }
}
