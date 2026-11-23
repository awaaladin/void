package com.vaultkeep.app.ui.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultkeep.app.data.auth.DriveAuthManager
import com.vaultkeep.app.data.local.BackupSetsPreferences
import com.vaultkeep.app.data.local.BackupSetupPreferences
import com.vaultkeep.app.data.local.CloudAuthPreferences
import com.vaultkeep.app.data.local.CustomSourcePreferences
import com.vaultkeep.app.data.local.MediaStoreCounts
import com.vaultkeep.app.data.local.NasCredentialsStore
import com.vaultkeep.app.ui.components.BackupHeroState
import com.vaultkeep.app.ui.components.BackupSetType
import com.vaultkeep.app.ui.components.BackupSetUiModel
import com.vaultkeep.app.ui.components.DestinationKind
import com.vaultkeep.app.ui.components.DestinationStatus
import com.vaultkeep.app.ui.components.DestinationUiModel
import com.vaultkeep.app.domain.model.BackupSetKind
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val hero: BackupHeroState = BackupHeroState.Empty,
    val destinations: List<DestinationUiModel> = emptyList(),
    val backupSets: List<BackupSetUiModel> = emptyList(),
    val permissionRequest: BackupSetKind? = null,
    /** A folder-based set (Documents/Downloads/WhatsApp) was just turned on but has no folder yet. */
    val folderPickRequest: BackupSetKind? = null,
)

/**
 * Photos/Videos toggle state and counts are real (DataStore + MediaStore
 * queries). Documents/Downloads/WhatsApp media have no MediaStore collection
 * Android will hand over without extra permission, so turning one on asks
 * the user to pick that real folder once (see [CustomSourcePreferences]) —
 * same mechanism USB/SSD's source folder already uses, not a dead toggle.
 * The destination cards reflect actual signed-in/configured state, not a
 * permanent placeholder.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val backupSetsPreferences: BackupSetsPreferences,
    private val mediaStoreCounts: MediaStoreCounts,
    private val customSourcePreferences: CustomSourcePreferences,
    private val backupSetupPreferences: BackupSetupPreferences,
    private val nasCredentialsStore: NasCredentialsStore,
    private val cloudAuthPreferences: CloudAuthPreferences,
    private val driveAuthManager: DriveAuthManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        HomeUiState(
            hero = BackupHeroState.Empty,
            destinations = listOf(
                DestinationUiModel(DestinationKind.USB, "USB/SSD", "Set up below", DestinationStatus.Disconnected),
                DestinationUiModel(DestinationKind.NAS, "Home NAS", "Set up below", DestinationStatus.Disconnected),
                DestinationUiModel(DestinationKind.CLOUD, "Void Cloud", "Sign in to use it", DestinationStatus.Disconnected),
                DestinationUiModel(DestinationKind.DRIVE, "Google Drive", "Connect to use it", DestinationStatus.Disconnected),
            ),
            backupSets = listOf(
                BackupSetUiModel(BackupSetType.PHOTOS, "Photos", "Checking…", enabled = false),
                BackupSetUiModel(BackupSetType.VIDEOS, "Videos", "Checking…", enabled = false),
                BackupSetUiModel(BackupSetType.DOCUMENTS, "Documents", "Tap to pick a folder", enabled = false),
                BackupSetUiModel(BackupSetType.DOWNLOADS, "Downloads", "Tap to pick a folder", enabled = false),
                BackupSetUiModel(BackupSetType.WHATSAPP, "WhatsApp media", "Tap to pick a folder", enabled = false),
            ),
        ),
    )
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            backupSetsPreferences.isEnabled(BackupSetKind.PHOTOS).collect { enabled ->
                updateSet(BackupSetType.PHOTOS, enabled = enabled)
            }
        }
        viewModelScope.launch {
            backupSetsPreferences.isEnabled(BackupSetKind.VIDEOS).collect { enabled ->
                updateSet(BackupSetType.VIDEOS, enabled = enabled)
            }
        }
        observeFolderSet(BackupSetType.DOCUMENTS, BackupSetKind.DOCUMENTS)
        observeFolderSet(BackupSetType.DOWNLOADS, BackupSetKind.DOWNLOADS)
        observeFolderSet(BackupSetType.WHATSAPP, BackupSetKind.WHATSAPP)
        observeDestinations()
    }

    private fun observeFolderSet(type: BackupSetType, kind: BackupSetKind) {
        viewModelScope.launch {
            combine(
                backupSetsPreferences.isEnabled(kind),
                customSourcePreferences.sourceUri(kind),
            ) { enabled, uri -> enabled to uri }.collect { (enabled, uri) ->
                val label = when {
                    uri == null -> "Tap to pick a folder"
                    else -> "✓ ${friendlyFolderName(uri)}"
                }
                _uiState.value = _uiState.value.copy(
                    backupSets = _uiState.value.backupSets.map {
                        if (it.type == type) it.copy(enabled = enabled && uri != null, sizeLabel = label) else it
                    },
                )
            }
        }
    }

    private fun observeDestinations() {
        viewModelScope.launch {
            backupSetupPreferences.destinationUri.collect { uri ->
                updateDestination(DestinationKind.USB) {
                    if (uri != null) it.copy(status = DestinationStatus.Connected, detail = "Ready")
                    else it.copy(status = DestinationStatus.Disconnected)
                }
            }
        }
        // NAS credentials and Cloud sign-in are both read synchronously from encrypted storage
        // (no live session to observe), so a one-time check on open reflects real state.
        val nasConfigured = nasCredentialsStore.load() != null
        updateDestination(DestinationKind.NAS) {
            if (nasConfigured) it.copy(status = DestinationStatus.Connected, detail = "Ready") else it
        }
        val cloudSignedIn = cloudAuthPreferences.isSignedIn()
        updateDestination(DestinationKind.CLOUD) {
            if (cloudSignedIn) it.copy(status = DestinationStatus.Connected, detail = "Signed in") else it
        }
        // Google's Identity Services caches an earlier grant, so this resolves non-interactively
        // (no prompt) when the user already connected Drive before — it only reports status here.
        viewModelScope.launch {
            val authorized = runCatching { driveAuthManager.authorize() }
                .getOrNull() is DriveAuthManager.AuthorizeOutcome.Authorized
            if (authorized) {
                updateDestination(DestinationKind.DRIVE) { it.copy(status = DestinationStatus.Connected, detail = "Connected") }
            }
        }
    }

    private fun updateDestination(kind: DestinationKind, transform: (DestinationUiModel) -> DestinationUiModel) {
        _uiState.value = _uiState.value.copy(
            destinations = _uiState.value.destinations.map { if (it.kind == kind) transform(it) else it },
        )
    }

    /** Called once READ_MEDIA_IMAGES/VIDEO permission state is known, to fill in real counts. */
    fun refreshPhotoCount(granted: Boolean) = viewModelScope.launch {
        val label = if (!granted) "Permission needed" else {
            val stats = mediaStoreCounts.imageStats()
            "${stats.fileCount} files · ${formatBytes(stats.totalBytes)}"
        }
        updateSetLabel(BackupSetType.PHOTOS, label)
    }

    fun refreshVideoCount(granted: Boolean) = viewModelScope.launch {
        val label = if (!granted) "Permission needed" else {
            val stats = mediaStoreCounts.videoStats()
            "${stats.fileCount} files · ${formatBytes(stats.totalBytes)}"
        }
        updateSetLabel(BackupSetType.VIDEOS, label)
    }

    /**
     * UI calls this on toggle. Photos/Videos may need a runtime permission first
     * ([permissionRequest]); Documents/Downloads/WhatsApp need a real folder picked
     * first ([folderPickRequest]) since there's no MediaStore collection for them.
     */
    fun onToggleBackupSet(type: BackupSetType, enabled: Boolean, hasPermission: Boolean) {
        val kind = type.toBackupSetKind() ?: return
        if (type.isFolderBased()) {
            if (enabled) {
                _uiState.value = _uiState.value.copy(folderPickRequest = kind)
            } else {
                viewModelScope.launch { backupSetsPreferences.setEnabled(kind, false) }
            }
            return
        }
        if (enabled && !hasPermission) {
            _uiState.value = _uiState.value.copy(permissionRequest = kind)
            return
        }
        viewModelScope.launch { backupSetsPreferences.setEnabled(kind, enabled) }
    }

    fun onFolderPickRequestConsumed() {
        _uiState.value = _uiState.value.copy(folderPickRequest = null)
    }

    /** Called after the folder picker returns a tree the user chose (and granted persistable access to). */
    fun onCustomFolderPicked(kind: BackupSetKind, uri: Uri) {
        onFolderPickRequestConsumed()
        viewModelScope.launch {
            customSourcePreferences.setSourceUri(kind, uri.toString())
            backupSetsPreferences.setEnabled(kind, true)
        }
    }

    fun onPermissionRequestConsumed() {
        _uiState.value = _uiState.value.copy(permissionRequest = null)
    }

    fun onPermissionResult(type: BackupSetType, granted: Boolean) {
        val kind = type.toBackupSetKind() ?: return
        if (granted) {
            viewModelScope.launch { backupSetsPreferences.setEnabled(kind, true) }
        }
        onPermissionRequestConsumed()
    }

    private fun updateSet(type: BackupSetType, enabled: Boolean) {
        _uiState.value = _uiState.value.copy(
            backupSets = _uiState.value.backupSets.map { if (it.type == type) it.copy(enabled = enabled) else it },
        )
    }

    private fun updateSetLabel(type: BackupSetType, label: String) {
        _uiState.value = _uiState.value.copy(
            backupSets = _uiState.value.backupSets.map { if (it.type == type) it.copy(sizeLabel = label) else it },
        )
    }

    private fun BackupSetType.isFolderBased() =
        this == BackupSetType.DOCUMENTS || this == BackupSetType.DOWNLOADS || this == BackupSetType.WHATSAPP

    private fun BackupSetType.toBackupSetKind(): BackupSetKind? = when (this) {
        BackupSetType.PHOTOS -> BackupSetKind.PHOTOS
        BackupSetType.VIDEOS -> BackupSetKind.VIDEOS
        BackupSetType.DOCUMENTS -> BackupSetKind.DOCUMENTS
        BackupSetType.DOWNLOADS -> BackupSetKind.DOWNLOADS
        BackupSetType.WHATSAPP -> BackupSetKind.WHATSAPP
        else -> null
    }

    private fun friendlyFolderName(uriString: String): String = runCatching {
        val docId = android.provider.DocumentsContract.getTreeDocumentId(Uri.parse(uriString))
        docId.substringAfterLast('/').substringAfterLast(':')
    }.getOrDefault(uriString)

    private fun formatBytes(bytes: Long): String {
        val gb = bytes / 1_000_000_000.0
        if (gb >= 1.0) return "%.1f GB".format(gb)
        val mb = bytes / 1_000_000.0
        return "%.0f MB".format(mb)
    }
}
