package com.vaultkeep.app.domain.model

enum class NasProtocol { SMB, WEBDAV }

/**
 * Everything needed to connect to a NAS share. Never held in a Room column —
 * see [com.vaultkeep.app.data.local.NasCredentialsStore], which is the only
 * place this touches disk, and does so through EncryptedSharedPreferences.
 */
data class NasConfig(
    val protocol: NasProtocol,
    val host: String,
    val port: Int,
    /** SMB share name (e.g. "backups") or WebDAV base path (e.g. "/remote.php/dav/files/me/"). */
    val shareOrPath: String,
    val username: String,
    val password: String,
    val useHttps: Boolean = false,
) {
    fun displayName(): String = "$host${if (shareOrPath.isNotBlank()) "/$shareOrPath" else ""}"
}

data class DiscoveredNasHost(
    val name: String,
    val host: String,
    val port: Int,
    val protocol: NasProtocol,
)
