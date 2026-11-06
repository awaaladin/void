package com.vaultkeep.app.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

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

    @POST("api/backup/uploads/presign-part/")
    suspend fun presignPart(@Body body: PresignPartRequest): Response<PresignPartResponse>

    @POST("api/backup/uploads/complete/")
    suspend fun completeUpload(@Body body: CompleteUploadRequest): Response<CloudFileDto>

    @POST("api/backup/uploads/abort/")
    suspend fun abortUpload(@Body body: AbortUploadRequest): Response<Unit>

    @GET("api/backup/files/")
    suspend fun listFiles(): Response<List<CloudFileDto>>

    @GET("api/backup/files/{fileId}/presign-download/")
    suspend fun presignDownload(@Path("fileId") fileId: String): Response<PresignDownloadResponse>

    @GET("api/backup/usage/")
    suspend fun usage(): Response<UsageResponse>
}
