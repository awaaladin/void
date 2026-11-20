package com.vaultkeep.app.data.destination

import com.vaultkeep.app.data.remote.DriveApi
import com.vaultkeep.app.data.remote.DriveFileMetadataRequest

private const val FOLDER_MIME_TYPE = "application/vnd.google-apps.folder"

/**
 * Shared folder/file lookup logic for [GoogleDriveDestination] and
 * [GoogleDriveSource] — both need to turn a `relativePath` into Drive
 * folder ids (creating folders as needed on the write side, read-only on
 * the restore side) and find files by name within a parent. One instance
 * per backup/restore run; its folder-id cache only needs to live that long.
 */
class DriveFolderResolver(
    private val api: DriveApi,
    private val bearer: String,
) {
    private val folderIdCache = mutableMapOf<String, String>()
    private var rootFolderId: String? = null

    /** The "Void Backups" folder at the root of the user's Drive, created once and reused. */
    suspend fun rootFolder(createIfMissing: Boolean): String? {
        rootFolderId?.let { return it }
        val existing = findChild(ROOT_FOLDER_NAME, parentId = "root", folderOnly = true)
        val id = existing ?: if (createIfMissing) createFolder(ROOT_FOLDER_NAME, parentId = "root") else null
        if (id != null) rootFolderId = id
        return id
    }

    /** Resolves every folder segment of [relativePath] except the last (the file name), creating
     * them if [createIfMissing]. Returns null if any segment is missing and creation wasn't allowed. */
    suspend fun resolveParentFolder(relativePath: String, createIfMissing: Boolean): String? {
        val segments = relativePath.split("/").filter { it.isNotBlank() }
        var parentId = rootFolder(createIfMissing) ?: return null
        for (folder in segments.dropLast(1)) {
            val cacheKey = "$parentId/$folder"
            val cached = folderIdCache[cacheKey]
            val id = cached ?: findChild(folder, parentId, folderOnly = true)
            ?: if (createIfMissing) createFolder(folder, parentId) else return null
            folderIdCache[cacheKey] = id
            parentId = id
        }
        return parentId
    }

    suspend fun findFile(name: String, parentId: String): String? = findChild(name, parentId, folderOnly = false)

    private suspend fun findChild(name: String, parentId: String, folderOnly: Boolean): String? {
        val escaped = name.replace("\\", "\\\\").replace("'", "\\'")
        val mimeClause = if (folderOnly) " and mimeType = '$FOLDER_MIME_TYPE'" else ""
        val query = "name = '$escaped' and '$parentId' in parents and trashed = false$mimeClause"
        val response = runCatching { api.listFiles(bearer, query) }.getOrNull() ?: return null
        if (!response.isSuccessful) return null
        return response.body()?.files?.firstOrNull()?.id
    }

    private suspend fun createFolder(name: String, parentId: String): String {
        val response = api.createFolder(
            bearer,
            DriveFileMetadataRequest(name = name, mimeType = FOLDER_MIME_TYPE, parents = listOf(parentId)),
        )
        if (!response.isSuccessful) error("Could not create Drive folder '$name': HTTP ${response.code()}")
        return response.body()?.id ?: error("Empty response creating Drive folder '$name'")
    }

    companion object {
        private const val ROOT_FOLDER_NAME = "Void Backups"
    }
}
