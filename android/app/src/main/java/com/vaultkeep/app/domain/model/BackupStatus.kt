package com.vaultkeep.app.domain.model

/** Mirrors Room's `BackupRecord.status` per the shared data model. */
enum class BackupStatus {
    PENDING,
    UPLOADING,
    VERIFIED,
    FAILED,
}

/** Mirrors Room's `FileEntry.setType` — which backup set a file belongs to. */
enum class BackupSetKind {
    PHOTOS,
    VIDEOS,
    DOCUMENTS,
    DOWNLOADS,
    WHATSAPP,
    CUSTOM,
}

enum class DestinationType {
    USB,
    NAS_SMB,
    NAS_WEBDAV,
    S3,
    DRIVE,
}
