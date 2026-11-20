package com.vaultkeep.app.domain

import java.io.InputStream

/**
 * The read-side mirror of [BackupDestination] — Phase 7's restore flow reads
 * whatever a given destination type already wrote back during Phases 2-5.
 * [readFile] returns exactly the bytes that are stored at [remoteKey] (as
 * recorded in `backup_records.remoteKey`), with no decryption applied —
 * [com.vaultkeep.app.data.crypto.DecryptingBackupSource] wraps this to undo
 * encryption for records written while it was on.
 */
interface BackupSource {
    val id: String

    suspend fun readFile(remoteKey: String): InputStream
}
