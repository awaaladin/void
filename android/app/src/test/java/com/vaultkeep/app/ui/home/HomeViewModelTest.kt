package com.vaultkeep.app.ui.home

import app.cash.turbine.test
import com.vaultkeep.app.data.local.BackupSetsPreferences
import com.vaultkeep.app.data.local.MediaSetStats
import com.vaultkeep.app.data.local.MediaStoreCounts
import com.vaultkeep.app.domain.model.BackupSetKind
import com.vaultkeep.app.ui.components.BackupSetType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HomeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel(
        photosEnabled: MutableStateFlow<Boolean> = MutableStateFlow(false),
        videosEnabled: MutableStateFlow<Boolean> = MutableStateFlow(false),
    ): Triple<HomeViewModel, BackupSetsPreferences, MediaStoreCounts> {
        val preferences = mockk<BackupSetsPreferences>(relaxed = true)
        every { preferences.isEnabled(BackupSetKind.PHOTOS) } returns photosEnabled
        every { preferences.isEnabled(BackupSetKind.VIDEOS) } returns videosEnabled
        coEvery { preferences.setEnabled(any(), any()) } returns Unit

        val mediaStoreCounts = mockk<MediaStoreCounts>(relaxed = true)
        coEvery { mediaStoreCounts.imageStats() } returns MediaSetStats(0, 0)
        coEvery { mediaStoreCounts.videoStats() } returns MediaSetStats(0, 0)

        val viewModel = HomeViewModel(preferences, mediaStoreCounts)
        return Triple(viewModel, preferences, mediaStoreCounts)
    }

    @Test
    fun `initial state has placeholder destinations and five backup sets`() = runTest {
        val (viewModel, _, _) = buildViewModel()

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(4, state.destinations.size)
            assertEquals(5, state.backupSets.size)
        }
    }

    @Test
    fun `not-yet-wired backup sets stay non-interactive`() = runTest {
        val (viewModel, _, _) = buildViewModel()

        viewModel.uiState.test {
            val state = awaitItem()
            listOf(BackupSetType.DOCUMENTS, BackupSetType.DOWNLOADS, BackupSetType.WHATSAPP).forEach { type ->
                val set = state.backupSets.first { it.type == type }
                assertTrue("$type should be non-interactive until it's wired up", !set.interactive)
            }
        }
    }

    @Test
    fun `turning on Photos without permission requests it instead of enabling`() = runTest {
        val (viewModel, preferences, _) = buildViewModel()

        viewModel.onToggleBackupSet(BackupSetType.PHOTOS, enabled = true, hasPermission = false)

        assertEquals(BackupSetKind.PHOTOS, viewModel.uiState.value.permissionRequest)
        coVerify(exactly = 0) { preferences.setEnabled(BackupSetKind.PHOTOS, true) }
    }

    @Test
    fun `turning on Photos with permission already granted persists it directly`() = runTest {
        val (viewModel, preferences, _) = buildViewModel()

        viewModel.onToggleBackupSet(BackupSetType.PHOTOS, enabled = true, hasPermission = true)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { preferences.setEnabled(BackupSetKind.PHOTOS, true) }
        assertNull(viewModel.uiState.value.permissionRequest)
    }

    @Test
    fun `permission granted after request persists Photos enabled and clears the request`() = runTest {
        val (viewModel, preferences, _) = buildViewModel()

        viewModel.onPermissionResult(BackupSetType.PHOTOS, granted = true)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { preferences.setEnabled(BackupSetKind.PHOTOS, true) }
        assertNull(viewModel.uiState.value.permissionRequest)
    }

    @Test
    fun `permission denied after request never persists enabled`() = runTest {
        val (viewModel, preferences, _) = buildViewModel()

        viewModel.onPermissionResult(BackupSetType.PHOTOS, granted = false)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { preferences.setEnabled(BackupSetKind.PHOTOS, true) }
        assertNull(viewModel.uiState.value.permissionRequest)
    }
}
