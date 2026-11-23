package com.vaultkeep.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vaultkeep.app.data.local.ThemeMode
import com.vaultkeep.app.ui.components.ScreenHeader
import com.vaultkeep.app.ui.components.StaggeredEntrance
import com.vaultkeep.app.ui.theme.VaultColorTokens
import com.vaultkeep.app.ui.theme.VaultTheme

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(VaultTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(VaultTheme.spacing.lg),
        ) {
            StaggeredEntrance(index = 0) {
                ScreenHeader(icon = Icons.Outlined.DarkMode, tint = VaultColorTokens.Signal500, title = "Settings")
            }

            StaggeredEntrance(index = 1) {
                Column(verticalArrangement = Arrangement.spacedBy(VaultTheme.spacing.sm)) {
                    Text("Appearance", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        "Dark matches Void's own look regardless of your phone's setting. Pick Light, or System to follow your phone instead.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(VaultTheme.spacing.sm)) {
                        ThemeModeChip("Dark", ThemeMode.DARK, themeMode, viewModel::setThemeMode)
                        ThemeModeChip("Light", ThemeMode.LIGHT, themeMode, viewModel::setThemeMode)
                        ThemeModeChip("System", ThemeMode.SYSTEM, themeMode, viewModel::setThemeMode)
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeModeChip(label: String, value: ThemeMode, current: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val selected = value == current
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(VaultTheme.radii.pill))
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
            .clickable { onSelect(value) }
            .padding(horizontal = VaultTheme.spacing.lg, vertical = VaultTheme.spacing.sm),
    ) {
        Text(
            label,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        )
    }
}
