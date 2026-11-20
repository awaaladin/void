package com.vaultkeep.app.ui.insights

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultkeep.app.data.local.dao.BackupRecordDao
import com.vaultkeep.app.data.local.dao.DestinationSummaryRow
import com.vaultkeep.app.data.local.dao.OverallSummaryRow
import com.vaultkeep.app.data.local.dao.SetKindSummaryRow
import com.vaultkeep.app.domain.model.destinationLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class StorageInsightsUiState(
    val overall: OverallSummaryRow = OverallSummaryRow(0, 0, null),
    val byDestination: List<DestinationSummaryRow> = emptyList(),
    val bySetKind: List<SetKindSummaryRow> = emptyList(),
    val exportMessage: String? = null,
)

@HiltViewModel
class StorageInsightsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backupRecordDao: BackupRecordDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow(StorageInsightsUiState())
    val uiState: StateFlow<StorageInsightsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                backupRecordDao.observeOverallSummary(),
                backupRecordDao.observeDestinationSummaries(),
                backupRecordDao.observeSetKindSummaries(),
            ) { overall, byDestination, bySetKind ->
                Triple(overall, byDestination, bySetKind)
            }.collect { (overall, byDestination, bySetKind) ->
                _uiState.value = _uiState.value.copy(overall = overall, byDestination = byDestination, bySetKind = bySetKind)
            }
        }
    }

    /** Writes a plain-text report to a SAF-picked file — the same "no root, user picks where files go" pattern as backups themselves. */
    fun exportReport(targetUri: Uri) {
        viewModelScope.launch {
            try {
                val report = buildReportText()
                context.contentResolver.openOutputStream(targetUri)?.use { it.write(report.toByteArray()) }
                    ?: error("Could not open the chosen file for writing")
                _uiState.value = _uiState.value.copy(exportMessage = "Report saved.")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(exportMessage = "Export failed: ${e.message}")
            }
        }
    }

    private fun buildReportText(): String {
        val state = _uiState.value
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val sb = StringBuilder()
        sb.appendLine("Void backup report")
        sb.appendLine("Generated: ${dateFormat.format(Date())}")
        sb.appendLine()
        sb.appendLine("OVERALL")
        sb.appendLine("  Files backed up: ${state.overall.fileCount}")
        sb.appendLine("  Total size: ${formatBytes(state.overall.totalBytes)}")
        sb.appendLine(
            "  Last backup: " + (state.overall.lastBackupAt?.let { dateFormat.format(Date(it)) } ?: "never"),
        )
        sb.appendLine()
        sb.appendLine("BY DESTINATION")
        if (state.byDestination.isEmpty()) {
            sb.appendLine("  (none yet)")
        }
        state.byDestination.forEach { row ->
            sb.appendLine("  ${destinationLabel(row.destinationId)}")
            sb.appendLine("    ${row.fileCount} file(s), ${formatBytes(row.totalBytes)}, last backup ${dateFormat.format(Date(row.lastBackupAt))}")
        }
        sb.appendLine()
        sb.appendLine("BY BACKUP SET")
        if (state.bySetKind.isEmpty()) {
            sb.appendLine("  (none yet)")
        }
        state.bySetKind.forEach { row ->
            sb.appendLine("  ${row.setType}: ${row.fileCount} file(s), ${formatBytes(row.totalBytes)}")
        }
        return sb.toString()
    }
}

fun formatBytes(bytes: Long): String {
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.lastIndex) {
        value /= 1024
        unitIndex++
    }
    return "%.1f %s".format(value, units[unitIndex])
}
