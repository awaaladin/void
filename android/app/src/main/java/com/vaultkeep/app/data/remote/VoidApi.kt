package com.vaultkeep.app.data.remote

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Streaming

/** Retrofit interface for the Void Cloud (Django) backend. */
interface VoidApi {
    @POST("api/accounts/register/")
    suspend fun register(@Body body: RegisterRequest): Response<TokenPairResponse>

    @POST("api/accounts/login/")
    suspend fun login(@Body body: LoginRequest): Response<TokenPairResponse>

    @POST("api/accounts/login/refresh/")
    suspend fun refresh(@Body body: RefreshRequest): Response<RefreshResponse>

    @POST("api/backup/uploads/create/")
    suspend fun createUpload(@Body body: CreateUploadRequest): Response<CreateUploadResponse>

    /** Body is raw chunk bytes (<=4MB) — not JSON, see RawBinaryParser on the backend. */
    @POST("api/backup/uploads/chunk/")
    suspend fun uploadChunk(
        @Query("file_id") fileId: String,
        @Query("chunk_index") chunkIndex: Int,
        @Body body: RequestBody,
    ): Response<ChunkUploadResponse>

    @POST("api/backup/uploads/complete/")
    suspend fun completeUpload(@Body body: CompleteUploadRequest): Response<CloudFileDto>

    @POST("api/backup/uploads/abort/")
    suspend fun abortUpload(@Body body: AbortUploadRequest): Response<Unit>

    @Streaming
    @GET("api/backup/uploads/chunk/download/")
    suspend fun downloadChunk(
        @Query("file_id") fileId: String,
        @Query("chunk_index") chunkIndex: Int,
    ): Response<ResponseBody>

    @GET("api/backup/files/")
    suspend fun listFiles(): Response<List<CloudFileDto>>

    @GET("api/backup/usage/")
    suspend fun usage(): Response<UsageResponse>
}
