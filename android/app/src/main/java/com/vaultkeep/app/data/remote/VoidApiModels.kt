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
    @SerialName("upload_id") val uploadId: String,
)

@Serializable
data class PresignPartRequest(
    @SerialName("file_id") val fileId: String,
    @SerialName("part_number") val partNumber: Int,
)

@Serializable
data class PresignPartResponse(val url: String)

@Serializable
data class CompletedPart(
    @SerialName("PartNumber") val partNumber: Int,
    @SerialName("ETag") val eTag: String,
)

@Serializable
data class CompleteUploadRequest(
    @SerialName("file_id") val fileId: String,
    val parts: List<CompletedPart>,
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
data class PresignDownloadResponse(val url: String)

@Serializable
data class UsageResponse(
    @SerialName("used_bytes") val usedBytes: Long,
    @SerialName("quota_bytes") val quotaBytes: Long,
    val plan: String,
)

@Serializable
data class ApiErrorBody(val error: String? = null)
