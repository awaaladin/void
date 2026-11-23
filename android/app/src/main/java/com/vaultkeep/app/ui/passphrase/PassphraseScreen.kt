package com.vaultkeep.app.ui.passphrase

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vaultkeep.app.ui.components.ScreenHeader
import com.vaultkeep.app.ui.components.StaggeredEntrance
import com.vaultkeep.app.ui.components.VoidButton
import com.vaultkeep.app.ui.components.VoidErrorDialog
import com.vaultkeep.app.ui.components.VoidSecondaryButton
import com.vaultkeep.app.ui.theme.VaultColorTokens
import com.vaultkeep.app.ui.theme.VaultTheme

@Composable
fun PassphraseScreen(viewModel: PassphraseViewModel = hiltViewModel(), onBack: () -> Unit = {}) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(VaultTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(VaultTheme.spacing.lg),
        ) {
            StaggeredEntrance(index = 0) {
                ScreenHeader(icon = Icons.Outlined.Lock, tint = VaultColorTokens.Signal500, title = "Encryption", onBack = onBack)
            }

            EncryptionToggleRow(enabled = uiState.encryptionEnabled, onToggle = viewModel::setEncryptionEnabled)

            when {
                !uiState.isPassphraseSet -> SetupPassphraseSection(uiState, viewModel)
                !uiState.isUnlocked -> UnlockSection(uiState, viewModel)
                else -> UnlockedSection(uiState, viewModel)
            }
        }
        VoidErrorDialog(uiState.errorMessage)
    }
}

@Composable
private fun EncryptionToggleRow(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(VaultTheme.radii.md))
            .background(MaterialTheme.colorScheme.surface)
            .padding(VaultTheme.spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text("Encrypt backups", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(
                "On by default. Applies to every destination — USB/SSD, NAS, and Void Cloud.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = enabled, onCheckedChange = onToggle)
    }
}

@Composable
private fun SetupPassphraseSection(uiState: PassphraseUiState, viewModel: PassphraseViewModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(VaultTheme.radii.md))
            .background(VaultColorTokens.EmberSurface)
            .padding(VaultTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(VaultTheme.spacing.sm),
    ) {
        Text("Before you set a passphrase, read this", style = MaterialTheme.typography.titleMedium, color = VaultColorTokens.Ember500)
        Text(
            "Your passphrase is the only key to your encrypted backups. If you lose it, those files " +
                "cannot be recovered — not by Void, not by anyone. There is no \"forgot passphrase\" reset. " +
                "Write it down somewhere safe before continuing.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    OutlinedTextField(
        value = uiState.passphrase,
        onValueChange = viewModel::onPassphraseChanged,
        label = { Text("Passphrase (8+ characters)") },
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = uiState.confirmPassphrase,
        onValueChange = viewModel::onConfirmPassphraseChanged,
        label = { Text("Confirm passphrase") },
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
    )

    VoidButton(
        text = "Set passphrase",
        onClick = viewModel::setupOrChangePassphrase,
        enabled = uiState.canSubmitSetup,
        modifier = Modifier.fillMaxWidth(),
    )

}

@Composable
private fun UnlockSection(uiState: PassphraseUiState, viewModel: PassphraseViewModel) {
    Text(
        "Encryption is set up but locked for this session. Enter your passphrase to back up or restore.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedTextField(
        value = uiState.passphrase,
        onValueChange = viewModel::onPassphraseChanged,
        label = { Text("Passphrase") },
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
    )
    VoidButton(text = "Unlock", onClick = viewModel::unlock, modifier = Modifier.fillMaxWidth())

    ForgetPassphraseSection(uiState, viewModel)
}

@Composable
private fun UnlockedSection(uiState: PassphraseUiState, viewModel: PassphraseViewModel) {
    Text("Unlocked for this session", color = VaultColorTokens.Verdigris500, style = MaterialTheme.typography.titleMedium)
    VoidSecondaryButton(text = "Lock now", onClick = viewModel::lock, modifier = Modifier.fillMaxWidth())

    Text("Change passphrase", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
    Text(
        "Re-wraps your existing key under a new passphrase — already-encrypted files stay readable.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedTextField(
        value = uiState.passphrase,
        onValueChange = viewModel::onPassphraseChanged,
        label = { Text("New passphrase (8+ characters)") },
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = uiState.confirmPassphrase,
        onValueChange = viewModel::onConfirmPassphraseChanged,
        label = { Text("Confirm new passphrase") },
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
    )
    VoidButton(
        text = "Update passphrase",
        onClick = viewModel::setupOrChangePassphrase,
        enabled = uiState.canSubmitSetup,
        modifier = Modifier.fillMaxWidth(),
    )

    ForgetPassphraseSection(uiState, viewModel)
}

@Composable
private fun ForgetPassphraseSection(uiState: PassphraseUiState, viewModel: PassphraseViewModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(VaultTheme.radii.md))
            .background(MaterialTheme.colorScheme.surface)
            .padding(VaultTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(VaultTheme.spacing.sm),
    ) {
        Text("Danger zone", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
        if (!uiState.showResetConfirm) {
            Text(
                "Forgets this device's encryption key entirely. Every file already encrypted anywhere becomes permanently unreadable.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            VoidSecondaryButton(text = "Forget encryption key", onClick = viewModel::requestReset, modifier = Modifier.fillMaxWidth())
        } else {
            Text(
                "Are you sure? This cannot be undone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(VaultTheme.spacing.sm)) {
                VoidSecondaryButton(text = "Cancel", onClick = viewModel::cancelReset, modifier = Modifier.fillMaxWidth())
                VoidButton(text = "Yes, forget it", onClick = viewModel::confirmReset, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
