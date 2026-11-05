package com.vaultkeep.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultkeep.app.data.local.BackupSetsPreferences
import com.vaultkeep.app.data.local.MediaStoreCounts
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
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val hero: BackupHeroState = BackupHeroState.Empty,
    /** Still placeholder — real once Phase 4/5 destinations (NAS/Cloud/Drive) exist. USB is real; see BackupSetupScreen. */
    val destinations: List<DestinationUiModel> = emptyList(),
    val backupSets: List<BackupSetUiModel> = emptyList(),
    val permissionRequest: BackupSetKind? = null,
)

/**
 * Photos/Videos toggle state and counts are real (DataStore + MediaStore
 * queries). The destination list stays placeholder — those destinations
 * (NAS/Cloud/Drive) aren't built yet; USB already is, but it's reached via
 * the "Set up USB/SSD backup" button, not this card list, until Phase 4
 * folds destination management into one place.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val backupSetsPreferences: BackupSetsPreferences,
    private val mediaStoreCounts: MediaStoreCounts,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        HomeUiState(
            hero = BackupHeroState.Empty,
            destinations = listOf(
                DestinationUiModel(DestinationKind.USB, "USB/SSD", "Set up below", DestinationStatus.Disconnected),
                DestinationUiModel(DestinationKind.NAS, "Home NAS", "Not built yet (Phase 4)", DestinationStatus.Disconnected),
                DestinationUiModel(DestinationKind.CLOUD, "Void Cloud", "Not built yet (Phase 5)", DestinationStatus.Disconnected),
                DestinationUiModel(DestinationKind.DRIVE, "Google Drive", "Not built yet (Phase 5)", DestinationStatus.Disconnected),
            ),
            backupSets = listOf(
                BackupSetUiModel(BackupSetType.PHOTOS, "Photos", "Checking…", enabled = false),
                BackupSetUiModel(BackupSetType.VIDEOS, "Videos", "Checking…", enabled = false),
                BackupSetUiModel(BackupSetType.DOCUMENTS, "Documents", "Not wired yet — use USB setup below", enabled = false, interactive = false),
                BackupSetUiModel(BackupSetType.DOWNLOADS, "Downloads", "Not wired yet — use USB setup below", enabled = false, interactive = false),
                BackupSetUiModel(BackupSetType.WHATSAPP, "WhatsApp media", "Not wired yet — use USB setup below", enabled = false, interactive = false),
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

    /** UI calls this on toggle; if turning on needs a permission the UI doesn't have yet, it asks via [permissionRequest] first. */
    fun onToggleBackupSet(type: BackupSetType, enabled: Boolean, hasPermission: Boolean) {
        val kind = type.toBackupSetKind() ?: return
        if (enabled && !hasPermission) {
            _uiState.value = _uiState.value.copy(permissionRequest = kind)
            return
        }
        viewModelScope.launch { backupSetsPreferences.setEnabled(kind, enabled) }
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

    private fun BackupSetType.toBackupSetKind(): BackupSetKind? = when (this) {
        BackupSetType.PHOTOS -> BackupSetKind.PHOTOS
        BackupSetType.VIDEOS -> BackupSetKind.VIDEOS
        else -> null
    }

    private fun formatBytes(bytes: Long): String {
        val gb = bytes / 1_000_000_000.0
        if (gb >= 1.0) return "%.1f GB".format(gb)
        val mb = bytes / 1_000_000.0
        return "%.0f MB".format(mb)
    }
}
