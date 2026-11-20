package com.vaultkeep.app.data.destination

import com.vaultkeep.app.domain.BackupSource
import com.vaultkeep.app.domain.model.NasConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream
import java.util.concurrent.TimeUnit

/** Reads files back from a WebDAV server previously used as a [WebDavDestination]. */
class WebDavSource(
    private val config: NasConfig,
    override val id: String,
) : BackupSource {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val baseUrl: String by lazy {
        val scheme = if (config.useHttps) "https" else "http"
        val path = config.shareOrPath.trim('/')
        "$scheme://${config.host}:${config.port}/$path".trimEnd('/')
    }

    private val authHeader = Credentials.basic(config.username, config.password)

    override suspend fun readFile(remoteKey: String): InputStream = withContext(Dispatchers.IO) {
        val url = "$baseUrl/" + remoteKey.split("/").joinToString("/") { java.net.URLEncoder.encode(it, "UTF-8") }
        val request = Request.Builder().url(url).header("Authorization", authHeader).get().build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            response.close()
            error("WebDAV GET failed for '$remoteKey': HTTP ${response.code}")
        }
        response.body?.byteStream() ?: run {
            response.close()
            error("WebDAV GET returned an empty body for '$remoteKey'")
        }
    }
}
