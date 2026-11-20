package com.vaultkeep.app.data.destination

import com.vaultkeep.app.data.remote.VoidApi
import com.vaultkeep.app.domain.BackupSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.InputStream

/**
 * Reads files back from Void Cloud — the download-side mirror of
 * [CloudDestination]. `remoteKey` is the file's relative path (the same
 * value every destination stores it as); this looks the file up by that
 * path, then fetches its chunks 0..chunk_count-1 in order and presents them
 * as one continuous stream, same shape the original plaintext had.
 */
class CloudSource(
    private val api: VoidApi,
    override val id: String = "cloud:void",
) : BackupSource {

    override suspend fun readFile(remoteKey: String): InputStream = withContext(Dispatchers.IO) {
        val listResponse = api.listFiles()
        if (!listResponse.isSuccessful) {
            error("Could not list Void Cloud files: HTTP ${listResponse.code()}")
        }
        val file = listResponse.body().orEmpty().firstOrNull { it.relativePath == remoteKey && it.status == "VERIFIED" }
            ?: error("'$remoteKey' is no longer on Void Cloud")

        ChunkedCloudInputStream(api, file.id, file.chunkCount)
    }
}

/**
 * Fetches one chunk at a time as the previous one is exhausted, instead of
 * opening every chunk's HTTP response up front — a 2GB file at 4MB/chunk is
 * 500 chunks, and holding 500 connections open simultaneously would be a
 * real problem, not a theoretical one.
 */
private class ChunkedCloudInputStream(
    private val api: VoidApi,
    private val fileId: String,
    private val totalChunks: Int,
) : InputStream() {
    private var nextChunkIndex = 0
    private var current: InputStream? = null

    private fun ensureCurrent(): InputStream? {
        while (current == null && nextChunkIndex < totalChunks) {
            val response = runBlocking { api.downloadChunk(fileId, nextChunkIndex) }
            if (!response.isSuccessful) {
                error("Chunk $nextChunkIndex download failed: HTTP ${response.code()}")
            }
            current = response.body()?.byteStream() ?: error("Chunk $nextChunkIndex returned an empty body")
            nextChunkIndex++
        }
        return current
    }

    override fun read(): Int {
        val stream = ensureCurrent() ?: return -1
        val byte = stream.read()
        if (byte == -1) {
            stream.close()
            current = null
            return read()
        }
        return byte
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val stream = ensureCurrent() ?: return -1
        val n = stream.read(b, off, len)
        if (n == -1) {
            stream.close()
            current = null
            return read(b, off, len)
        }
        return n
    }

    override fun close() {
        current?.close()
        current = null
    }
}
