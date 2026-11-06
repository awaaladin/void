package com.vaultkeep.app.ui.batteryguide

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import com.vaultkeep.app.ui.components.VoidButton
import com.vaultkeep.app.ui.theme.VaultTheme

/**
 * Real, actionable battery-optimization guidance — not a static "allow
 * background activity" screen for show. Opens the actual system settings
 * screen via intent and tells the user, by manufacturer, which extra OEM
 * screen also needs a tap (OEM background-activity restrictions are not
 * controllable through any public API, so a direct intent isn't possible
 * for those — this is the honest limit of what Android lets an app do).
 */
@Composable
fun BatteryGuideScreen() {
    val context = LocalContext.current
    val manufacturer = Build.MANUFACTURER?.lowercase().orEmpty()

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(VaultTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(VaultTheme.spacing.lg),
        ) {
            Text(
                "Keep backups reliable",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                "Some phones aggressively kill background apps to save battery, which can stop a backup mid-transfer. Two steps fix that.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            StepCard(
                step = "1",
                title = "Allow Void to ignore battery optimization",
                body = "Opens Android's own battery settings list. Find Void and set it to \"Don't optimize\" / \"Unrestricted\".",
                actionLabel = "Open battery settings",
                onClick = {
                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    context.startActivity(intent)
                },
            )

            val oemGuidance = oemGuidanceFor(manufacturer)
            if (oemGuidance != null) {
                StepCard(
                    step = "2",
                    title = oemGuidance.title,
                    body = oemGuidance.body,
                    actionLabel = null,
                    onClick = null,
                )
            }
        }
    }
}

@Composable
private fun StepCard(step: String, title: String, body: String, actionLabel: String?, onClick: (() -> Unit)?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(VaultTheme.radii.md))
            .background(MaterialTheme.colorScheme.surface)
            .padding(VaultTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(VaultTheme.spacing.sm),
    ) {
        Text("Step $step", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (actionLabel != null && onClick != null) {
            VoidButton(text = actionLabel, onClick = onClick, modifier = Modifier.fillMaxWidth())
        }
    }
}

private data class OemGuidance(val title: String, val body: String)

/**
 * No public API exists for these OEM-specific screens (Transsion's
 * HiOS/XOS, MIUI) — this is textual navigation guidance only, because that
 * is genuinely the only thing an app is allowed to give the user here.
 */
private fun oemGuidanceFor(manufacturer: String): OemGuidance? = when {
    manufacturer.contains("tecno") || manufacturer.contains("infinix") || manufacturer.contains("itel") ->
        OemGuidance(
            title = "Also turn off \"Auto-start management\" (HiOS/XOS)",
            body = "Settings → Apps → Void → Battery → set \"Auto-start\" and \"Background activity\" to allowed/unrestricted. Menu wording varies by phone and OS version.",
        )
    manufacturer.contains("xiaomi") ->
        OemGuidance(
            title = "Also turn off MIUI's battery saver for this app",
            body = "Settings → Apps → Manage apps → Void → Battery saver → set to \"No restrictions\", and enable \"Autostart\" in Permissions.",
        )
    else -> null
}
