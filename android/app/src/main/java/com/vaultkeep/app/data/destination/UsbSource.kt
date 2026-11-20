package com.vaultkeep.app.data.destination

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.vaultkeep.app.domain.BackupSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

/** Reads files back from a SAF tree previously used as a [UsbDestination]. */
class UsbSource(
    private val context: Context,
    override val id: String,
    private val treeUri: Uri,
) : BackupSource {

    override suspend fun readFile(remoteKey: String): InputStream = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: error("Source tree is no longer accessible: $treeUri")

        var node: DocumentFile = root
        for (segment in remoteKey.split("/").filter { it.isNotBlank() }) {
            node = node.findFile(segment) ?: error("'$remoteKey' is no longer on this destination")
        }
        context.contentResolver.openInputStream(node.uri)
            ?: error("Could not open '$remoteKey' for reading")
    }
}
