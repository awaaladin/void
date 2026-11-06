package com.vaultkeep.app.ui.scheduling

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vaultkeep.app.data.local.ScheduleMode
import com.vaultkeep.app.ui.components.VoidButton
import com.vaultkeep.app.ui.components.VoidSecondaryButton
import com.vaultkeep.app.ui.theme.VaultTheme

@Composable
fun SchedulingScreen(
    viewModel: SchedulingViewModel = hiltViewModel(),
    onOpenBatteryGuide: () -> Unit = {},
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(VaultTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(VaultTheme.spacing.lg),
        ) {
            Text("Scheduling", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
            Text(
                "Applies to the USB/SSD backup you set up. These are real WorkManager jobs — changing a mode here enqueues or cancels an actual scheduled task.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(VaultTheme.radii.md))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(VaultTheme.spacing.md),
            ) {
                ScheduleModeRow(ScheduleMode.MANUAL, "Manual", settings.mode, viewModel::setMode)
                ScheduleModeRow(ScheduleMode.DAILY, "Daily", settings.mode, viewModel::setMode)
                ScheduleModeRow(ScheduleMode.ON_CHARGE, "While charging", settings.mode, viewModel::setMode)
                ScheduleModeRow(ScheduleMode.ON_WIFI, "On Wi-Fi only", settings.mode, viewModel::setMode)
            }

            VoidButton(text = "Run backup now", onClick = viewModel::runNow, modifier = Modifier.fillMaxWidth())

            VoidSecondaryButton(text = "Battery optimization guide", onClick = onOpenBatteryGuide, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ScheduleModeRow(mode: ScheduleMode, label: String, current: ScheduleMode, onSelect: (ScheduleMode) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = VaultTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = mode == current, onClick = { onSelect(mode) })
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}
