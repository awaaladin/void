package com.vaultkeep.app.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vaultkeep.app.ui.components.BackupHeroCard
import com.vaultkeep.app.ui.components.BackupSetToggleRow
import com.vaultkeep.app.ui.components.BackupSetType
import com.vaultkeep.app.ui.components.DestinationCard
import com.vaultkeep.app.ui.components.DestinationKind
import com.vaultkeep.app.ui.theme.VaultTheme

private fun mediaPermissionFor(type: BackupSetType): String? = when (type) {
    BackupSetType.PHOTOS -> if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
    BackupSetType.VIDEOS -> if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_EXTERNAL_STORAGE
    else -> null
}

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onOpenBackupSetup: () -> Unit = {},
    onOpenNasSetup: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    fun hasPermission(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    val photoPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.onPermissionResult(BackupSetType.PHOTOS, granted)
        viewModel.refreshPhotoCount(granted)
    }
    val videoPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.onPermissionResult(BackupSetType.VIDEOS, granted)
        viewModel.refreshVideoCount(granted)
    }

    LaunchedEffect(Unit) {
        mediaPermissionFor(BackupSetType.PHOTOS)?.let { viewModel.refreshPhotoCount(hasPermission(it)) }
        mediaPermissionFor(BackupSetType.VIDEOS)?.let { viewModel.refreshVideoCount(hasPermission(it)) }
    }

    LaunchedEffect(uiState.permissionRequest) {
        val kind = uiState.permissionRequest ?: return@LaunchedEffect
        when (kind) {
            com.vaultkeep.app.domain.model.BackupSetKind.PHOTOS -> mediaPermissionFor(BackupSetType.PHOTOS)?.let(photoPermissionLauncher::launch)
            com.vaultkeep.app.domain.model.BackupSetKind.VIDEOS -> mediaPermissionFor(BackupSetType.VIDEOS)?.let(videoPermissionLauncher::launch)
            else -> {}
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = VaultTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(VaultTheme.spacing.xl),
        ) {
            item {
                Text(
                    "Void",
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = VaultTheme.spacing.xl),
                )
            }

            item {
                StaggeredEntrance(index = 0) {
                    BackupHeroCard(state = uiState.hero)
                }
            }

            item {
                Text(
                    "Destinations",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            itemsIndexed(uiState.destinations) { index, destination ->
                StaggeredEntrance(index = index + 1) {
                    DestinationCard(
                        model = destination,
                        onClick = {
                            when (destination.kind) {
                                DestinationKind.USB -> onOpenBackupSetup()
                                DestinationKind.NAS -> onOpenNasSetup()
                                DestinationKind.CLOUD, DestinationKind.DRIVE ->
                                    Toast.makeText(context, "${destination.displayName} isn't built yet — coming in a later phase", Toast.LENGTH_SHORT).show()
                            }
                        },
                    )
                }
            }

            item {
                Button(onClick = onOpenBackupSetup, modifier = Modifier.fillMaxWidth()) {
                    Text("Set up USB/SSD backup")
                }
            }

            item {
                Button(onClick = onOpenNasSetup, modifier = Modifier.fillMaxWidth()) {
                    Text("Set up home NAS")
                }
            }

            item {
                Text(
                    "Backup sets",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            items(uiState.backupSets, key = { it.type }) { set ->
                BackupSetToggleRow(
                    model = set,
                    onToggle = { enabled ->
                        val permission = mediaPermissionFor(set.type)
                        val granted = permission == null || hasPermission(permission)
                        viewModel.onToggleBackupSet(set.type, enabled, hasPermission = granted)
                    },
                )
            }

            item {
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(bottom = VaultTheme.spacing.xxxl))
            }
        }
    }
}


/**
 * Staggered list entrance: each item fades + slides up with a short delay
 * proportional to its index. Pure Compose AnimatedVisibility-style entrance
 * via a one-shot LaunchedEffect-driven visibility flag, so it never replays
 * on recomposition (only on first composition of this node).
 */
@Composable
private fun StaggeredEntrance(index: Int, content: @Composable () -> Unit) {
    var visible by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(30L * index)
        visible = true
    }
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(220)) + slideInVertically(
            animationSpec = tween(220),
            initialOffsetY = { it / 4 },
        ),
    ) {
        content()
    }
}
