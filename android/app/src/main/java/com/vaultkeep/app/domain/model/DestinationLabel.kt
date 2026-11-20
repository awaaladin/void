package com.vaultkeep.app.domain.model

/**
 * `backup_records.destinationId` is whatever each destination's constructor
 * stamped it with (see [com.vaultkeep.app.data.destination]) — e.g.
 * `"usb:content://..."`, `"smb:192.168.1.50"`. This turns that back into
 * something a person reads on the Restore and Storage Insights screens,
 * without needing the `destinations` table (which nothing populates yet).
 */
fun destinationLabel(destinationId: String): String = when {
    destinationId.startsWith("usb:") -> "USB/SSD drive"
    destinationId.startsWith("smb:") -> "Home NAS (SMB) — ${destinationId.removePrefix("smb:")}"
    destinationId.startsWith("webdav:") -> "Home NAS (WebDAV) — ${destinationId.removePrefix("webdav:")}"
    destinationId.startsWith("cloud:") -> "Void Cloud"
    else -> destinationId
}
