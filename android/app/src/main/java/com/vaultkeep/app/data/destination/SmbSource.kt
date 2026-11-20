package com.vaultkeep.app.data.destination

import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare
import com.vaultkeep.app.domain.BackupSource
import com.vaultkeep.app.domain.model.NasConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FilterInputStream
import java.io.InputStream
import java.util.EnumSet

/** Reads files back from an SMB share previously used as an [SmbDestination]. */
class SmbSource(
    private val config: NasConfig,
    override val id: String,
) : BackupSource {

    override suspend fun readFile(remoteKey: String): InputStream = withContext(Dispatchers.IO) {
        val smbPath = remoteKey.replace('/', '\\')
        val client = SMBClient()
        val connection = client.connect(config.host, config.port)
        val auth = AuthenticationContext(config.username, config.password.toCharArray(), null)
        val session = connection.authenticate(auth)
        val share = session.connectShare(config.shareOrPath) as DiskShare
        val file = share.openFile(
            smbPath,
            EnumSet.of(AccessMask.GENERIC_READ),
            EnumSet.of(FileAttributes.FILE_ATTRIBUTE_NORMAL),
            EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ),
            SMB2CreateDisposition.FILE_OPEN,
            null,
        )

        // Keep the file/share/connection open for the lifetime of this stream — closing it
        // closes all three in order, instead of buffering the whole file into memory.
        object : FilterInputStream(file.inputStream) {
            override fun close() {
                try {
                    super.close()
                } finally {
                    file.close()
                    share.close()
                    connection.close()
                }
            }
        }
    }
}
