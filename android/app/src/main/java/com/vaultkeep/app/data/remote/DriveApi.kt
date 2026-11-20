package com.vaultkeep.app.data.remote

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

/**
 * Plain Drive v3 REST calls — not the heavier google-api-client library,
 * same lightweight-REST approach as [VoidApi]. The "drive.file" scope this
 * app requests only ever sees files/folders it created itself, so every
 * list/read/write call here is implicitly scoped to Void's own backup tree.
 *
 * Uploads use the resumable protocol (startResumableUpload here, then a
 * raw PUT to the returned Location URL — see GoogleDriveDestination) rather
 * than one-shot multipart, since Google recommends resumable for anything
 * that isn't tiny and Void's own files (photos, videos) routinely aren't.
 */
interface DriveApi {
    @GET("drive/v3/files")
    suspend fun listFiles(
        @Header("Authorization") bearer: String,
        @Query("q") query: String,
        @Query("fields") fields: String = "files(id,name)",
        @Query("spaces") spaces: String = "drive",
    ): Response<DriveFileListResponse>

    @POST("drive/v3/files")
    suspend fun createFolder(
        @Header("Authorization") bearer: String,
        @Body body: DriveFileMetadataRequest,
        @Query("fields") fields: String = "id",
    ): Response<DriveFileIdResponse>

    /** Returns an empty body; the session URI to PUT content to is in the `Location` response header. */
    @POST("upload/drive/v3/files")
    suspend fun startResumableUpload(
        @Header("Authorization") bearer: String,
        @Header("X-Upload-Content-Type") uploadContentType: String,
        @Header("X-Upload-Content-Length") uploadContentLength: Long,
        @Query("uploadType") uploadType: String = "resumable",
        @Body body: DriveFileMetadataRequest,
    ): Response<Unit>

    /** Same as [startResumableUpload] but replacing an existing file's content. */
    @PATCH("upload/drive/v3/files/{fileId}")
    suspend fun startResumableUpdate(
        @Header("Authorization") bearer: String,
        @Path("fileId") fileId: String,
        @Header("X-Upload-Content-Type") uploadContentType: String,
        @Header("X-Upload-Content-Length") uploadContentLength: Long,
        @Query("uploadType") uploadType: String = "resumable",
        @Body body: DriveFileMetadataRequest = DriveFileMetadataRequest(),
    ): Response<Unit>

    @Streaming
    @GET("drive/v3/files/{fileId}")
    suspend fun downloadMedia(
        @Header("Authorization") bearer: String,
        @Path("fileId") fileId: String,
        @Query("alt") alt: String = "media",
    ): Response<ResponseBody>
}
