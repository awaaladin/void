package com.vaultkeep.app.data.destination

import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare
import com.vaultkeep.app.domain.BackupDestination
import com.vaultkeep.app.domain.model.NasConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.security.MessageDigest
import java.util.EnumSet

/**
 * Writes files to an SMB (Windows file sharing / Samba) share, e.g. a home
 * NAS exposing an SMB2/3 share. Opens a fresh connection per call rather
 * than holding one open across a whole backup run — simpler and more
 * resilient to Wi-Fi drops; WorkManager's retry already covers reconnects.
 */
class SmbDestination(
    private val config: NasConfig,
    override val id: String,
    override val displayName: String,
) : BackupDestination {

    private fun <T> withShare(block: (DiskShare) -> T): T {
        val client = SMBClient()
        val connection = client.connect(config.host, config.port)
        try {
            val auth = AuthenticationContext(config.username, config.password.toCharArray(), null)
            val session = connection.authenticate(auth)
            val share = session.connectShare(config.shareOrPath) as DiskShare
            return share.use(block)
        } finally {
            connection.close()
        }
    }

    override suspend fun exists(relativePath: String): Boolean = withContext(Dispatchers.IO) {
        runCatching { withShare { share -> share.fileExists(relativePath.replace('/', '\\')) } }.getOrDefault(false)
    }

    /** Connecting, authenticating, and opening the share without throwing is the whole test. */
    override suspend fun testConnection() = withContext(Dispatchers.IO) {
        withShare { /* connection + auth + share-connect already happened to get here */ }
    }

    override suspend fun writeFile(
        relativePath: String,
        sizeBytes: Long,
        openInput: () -> InputStream,
    ): BackupDestination.WriteResult = withContext(Dispatchers.IO) {
        try {
            val smbPath = relativePath.replace('/', '\\')
            withShare { share ->
                val segments = smbPath.split('\\').filter { it.isNotBlank() }
                var dirSoFar = ""
                for (folder in segments.dropLast(1)) {
                    dirSoFar = if (dirSoFar.isEmpty()) folder else "$dirSoFar\\$folder"
                    if (!share.folderExists(dirSoFar)) {
                        share.mkdir(dirSoFar)
                    }
                }

                val digest = MessageDigest.getInstance("SHA-256")
                val file = share.openFile(
                    smbPath,
                    EnumSet.of(AccessMask.GENERIC_WRITE),
                    EnumSet.of(FileAttributes.FILE_ATTRIBUTE_NORMAL),
                    EnumSet.of(SMB2ShareAccess.FILE_SHARE_WRITE),
                    SMB2CreateDisposition.FILE_OVERWRITE_IF,
                    null,
                )
                var bytesWritten = 0L
                try {
                    file.outputStream.use { out ->
                        openInput().use { input ->
                            val buffer = ByteArray(8 * 1024)
                            while (true) {
                                val read = input.read(buffer)
                                if (read == -1) break
                                out.write(buffer, 0, read)
                                digest.update(buffer, 0, read)
                                bytesWritten += read
                            }
                        }
                    }
                } finally {
                    file.close()
                }

                val hashHex = digest.digest().joinToString("") { "%02x".format(it) }
                BackupDestination.WriteResult.Success(writtenSha256 = hashHex, bytesWritten = bytesWritten)
            }
        } catch (e: Exception) {
            BackupDestination.WriteResult.Failure(e.message ?: "SMB write failed")
        }
    }
}
