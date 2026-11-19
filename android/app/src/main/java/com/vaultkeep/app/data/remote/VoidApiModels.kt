package com.vaultkeep.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(val username: String, val email: String, val password: String)

@Serializable
data class LoginRequest(val username: String, val password: String)

@Serializable
data class TokenPairResponse(val access: String, val refresh: String)

@Serializable
data class RefreshRequest(val refresh: String)

@Serializable
data class RefreshResponse(val access: String)

@Serializable
data class CreateUploadRequest(
    @SerialName("relative_path") val relativePath: String,
    @SerialName("size_bytes") val sizeBytes: Long,
    val sha256: String = "",
)

@Serializable
data class CreateUploadResponse(
    @SerialName("file_id") val fileId: String,
    val key: String,
    /** True when the backend found an existing VERIFIED file with the same plaintext hash and
     * linked this one to its chunks instead — no chunk upload needed, already done server-side. */
    val deduped: Boolean = false,
)

@Serializable
data class ChunkUploadResponse(
    @SerialName("chunk_index") val chunkIndex: Int,
    @SerialName("size_bytes") val sizeBytes: Long,
)

@Serializable
data class CompleteUploadRequest(
    @SerialName("file_id") val fileId: String,
    @SerialName("total_chunks") val totalChunks: Int,
    val sha256: String = "",
)

@Serializable
data class AbortUploadRequest(@SerialName("file_id") val fileId: String)

@Serializable
data class CloudFileDto(
    val id: String,
    @SerialName("relative_path") val relativePath: String,
    @SerialName("size_bytes") val sizeBytes: Long,
    val sha256: String,
    val status: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("completed_at") val completedAt: String? = null,
)

@Serializable
data class UsageResponse(
    @SerialName("used_bytes") val usedBytes: Long,
    @SerialName("quota_bytes") val quotaBytes: Long,
    val plan: String,
)

@Serializable
data class ApiErrorBody(val error: String? = null)
