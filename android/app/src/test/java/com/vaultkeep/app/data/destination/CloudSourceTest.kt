package com.vaultkeep.app.data.destination

import com.vaultkeep.app.data.remote.CloudFileDto
import com.vaultkeep.app.data.remote.VoidApi
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import retrofit2.Response

/**
 * [CloudSource] reassembles a file from several independently-fetched HTTP
 * chunk responses — the part worth a regression test is that it does so in
 * order and without silently truncating, since an earlier draft had a bug
 * (treating a chunk whose InputStream.available() read 0 as "empty" and
 * skipping it, which would have dropped real data on a slow connection).
 */
class CloudSourceTest {

    private fun dto(relativePath: String, status: String, chunkCount: Int) = CloudFileDto(
        id = "file-1",
        relativePath = relativePath,
        sizeBytes = 0,
        sha256 = "",
        status = status,
        createdAt = "2026-01-01T00:00:00Z",
        chunkCount = chunkCount,
    )

    private fun chunkResponse(bytes: ByteArray): Response<okhttp3.ResponseBody> =
        Response.success(bytes.toResponseBody("application/octet-stream".toMediaType()))

    @Test
    fun `reassembles chunks in order into one continuous stream`() = runBlocking {
        val api = mockk<VoidApi>()
        coEvery { api.listFiles() } returns Response.success(listOf(dto("photo.jpg", "VERIFIED", chunkCount = 3)))
        coEvery { api.downloadChunk("file-1", 0) } returns chunkResponse("AAA".toByteArray())
        coEvery { api.downloadChunk("file-1", 1) } returns chunkResponse("BBB".toByteArray())
        coEvery { api.downloadChunk("file-1", 2) } returns chunkResponse("CCC".toByteArray())

        val source = CloudSource(api)
        val bytes = source.readFile("photo.jpg").use { it.readBytes() }

        assertEquals("AAABBBCCC", String(bytes))
    }

    @Test
    fun `a file with zero chunks reads as empty, not as an error`() = runBlocking {
        val api = mockk<VoidApi>()
        coEvery { api.listFiles() } returns Response.success(listOf(dto("empty.txt", "VERIFIED", chunkCount = 0)))

        val source = CloudSource(api)
        val bytes = source.readFile("empty.txt").use { it.readBytes() }

        assertEquals(0, bytes.size)
    }

    @Test
    fun `a file that is not VERIFIED is treated as not found`() {
        val api = mockk<VoidApi>()
        coEvery { api.listFiles() } returns Response.success(listOf(dto("still_uploading.jpg", "UPLOADING", chunkCount = 2)))

        val source = CloudSource(api)
        assertThrows(IllegalStateException::class.java) {
            runBlocking { source.readFile("still_uploading.jpg") }
        }
    }
}
