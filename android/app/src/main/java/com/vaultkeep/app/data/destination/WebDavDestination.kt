package com.vaultkeep.app.data.destination

import com.vaultkeep.app.domain.BackupDestination
import com.vaultkeep.app.domain.model.NasConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Writes files to a WebDAV server (e.g. Nextcloud, a NAS's built-in WebDAV
 * service) over HTTP(S) with HTTP Basic auth. Uses MKCOL to create folders
 * (idempotent — a 405 on an already-existing collection is treated as
 * success, per the WebDAV spec) and PUT to write files.
 */
class WebDavDestination(
    private val config: NasConfig,
    override val id: String,
    override val displayName: String,
) : BackupDestination {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val baseUrl: String by lazy {
        val scheme = if (config.useHttps) "https" else "http"
        val path = config.shareOrPath.trim('/')
        "$scheme://${config.host}:${config.port}/$path".trimEnd('/')
    }

    private val authHeader = Credentials.basic(config.username, config.password)

    override suspend fun exists(relativePath: String): Boolean = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(urlFor(relativePath))
            .header("Authorization", authHeader)
            .method("PROPFIND", emptyXmlBody())
            .header("Depth", "0")
            .build()
        runCatching { client.newCall(request).execute().use { it.isSuccessful } }.getOrDefault(false)
    }

    override suspend fun testConnection() = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(baseUrl)
            .header("Authorization", authHeader)
            .method("PROPFIND", emptyXmlBody())
            .header("Depth", "0")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("WebDAV connection failed: HTTP ${response.code} ${response.message}")
            }
        }
    }

    override suspend fun writeFile(
        relativePath: String,
        sizeBytes: Long,
        openInput: () -> InputStream,
    ): BackupDestination.WriteResult = withContext(Dispatchers.IO) {
        try {
            val segments = relativePath.split("/").filter { it.isNotBlank() }
            require(segments.isNotEmpty()) { "Empty relative path" }

            // Ensure each intermediate collection exists. WebDAV MKCOL is not
            // recursive, so we create them one level at a time.
            var pathSoFar = ""
            for (folder in segments.dropLast(1)) {
                pathSoFar = if (pathSoFar.isEmpty()) folder else "$pathSoFar/$folder"
                ensureCollection(pathSoFar)
            }

            val digest = MessageDigest.getInstance("SHA-256")
            val bytes = openInput().use { it.readBytes() }
            digest.update(bytes)
            val hashHex = digest.digest().joinToString("") { "%02x".format(it) }

            val body = bytes.toRequestBody("application/octet-stream".toMediaType())
            val request = Request.Builder()
                .url(urlFor(relativePath))
                .header("Authorization", authHeader)
                .put(body)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    BackupDestination.WriteResult.Success(writtenSha256 = hashHex, bytesWritten = bytes.size.toLong())
                } else {
                    BackupDestination.WriteResult.Failure("WebDAV PUT failed: HTTP ${response.code}")
                }
            }
        } catch (e: Exception) {
            BackupDestination.WriteResult.Failure(e.message ?: "WebDAV write failed")
        }
    }

    /** MKCOL returns 405 if the collection already exists — that's success, not an error, per RFC 4918. */
    private fun ensureCollection(path: String) {
        val request = Request.Builder()
            .url(urlFor(path))
            .header("Authorization", authHeader)
            .method("MKCOL", null)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful && response.code != 405) {
                error("Could not create WebDAV folder '$path': HTTP ${response.code}")
            }
        }
    }

    private fun urlFor(relativePath: String): String =
        "$baseUrl/" + relativePath.split("/").joinToString("/") { java.net.URLEncoder.encode(it, "UTF-8") }

    private fun emptyXmlBody(): RequestBody =
        "<?xml version=\"1.0\"?><propfind xmlns=\"DAV:\"><allprop/></propfind>"
            .toRequestBody("application/xml".toMediaType())
}
