package com.vaultkeep.app.data.remote

import kotlinx.serialization.Serializable

/** `name` is only required when creating a new file/folder — updating an existing file's
 * content (resumable PATCH) needs no metadata change, so it sends this with every field null. */
@Serializable
data class DriveFileMetadataRequest(
    val name: String? = null,
    val mimeType: String? = null,
    val parents: List<String>? = null,
)

@Serializable
data class DriveFileIdResponse(val id: String)

@Serializable
data class DriveFileListResponse(val files: List<DriveFileIdNameEntry> = emptyList())

@Serializable
data class DriveFileIdNameEntry(val id: String, val name: String)
