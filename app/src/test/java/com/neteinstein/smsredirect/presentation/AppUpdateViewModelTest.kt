package com.neteinstein.smsredirect.presentation

import com.neteinstein.smsredirect.FakeAppReleaseSource
import com.neteinstein.smsredirect.FakeAppUpdateInstaller
import com.neteinstein.smsredirect.FakeAppUpdateRepository
import com.neteinstein.smsredirect.domain.model.AppRelease
import com.neteinstein.smsredirect.domain.update.AppUpdateInstallResult
import com.neteinstein.smsredirect.domain.usecase.CheckForAppUpdateUseCase
import com.neteinstein.smsredirect.domain.usecase.GetAvailableAppUpdateUseCase
import com.neteinstein.smsredirect.presentation.update.AppUpdateViewModel
import com.neteinstein.smsredirect.presentation.update.UpdateCheckStatus
import com.neteinstein.smsredirect.presentation.update.UpdateInstallStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppUpdateViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val newer =
        AppRelease(
            versionName = "2.0.11",
            releaseUrl = "https://github.com/neteinstein/SMS-Redirect/releases/tag/v2.0.11",
            apkUrl = "https://github.com/neteinstein/SMS-Redirect/releases/download/v2.0.11/SMS-Toolbox_v2.0.11.apk",
            notes = "Fixes",
        )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        source: FakeAppReleaseSource,
        repository: FakeAppUpdateRepository = FakeAppUpdateRepository(),
        installer: FakeAppUpdateInstaller = FakeAppUpdateInstaller(),
    ) = AppUpdateViewModel(
        currentVersionName = "2.0.10",
        getAvailableAppUpdateUseCase = GetAvailableAppUpdateUseCase(repository, "2.0.10"),
        checkForAppUpdateUseCase = CheckForAppUpdateUseCase(source, repository, "2.0.10") { 1_000_000_000L },
        installer = installer,
    )

    @Test
    fun `launch check surfaces an available update`() =
        runTest {
            val source = FakeAppReleaseSource(newer)
            val vm = viewModel(source)

            advanceUntilIdle()

            assertEquals(1, source.fetchCalls)
            assertEquals(newer, vm.uiState.value.availableUpdate)
        }

    @Test
    fun `launch check within a day reuses the stored result without a request`() =
        runTest {
            val source = FakeAppReleaseSource(newer)
            val repository = FakeAppUpdateRepository(lastCheck = 1_000_000_000L - 1, latest = newer)
            val vm = viewModel(source, repository)

            advanceUntilIdle()

            assertEquals(0, source.fetchCalls)
            assertEquals(newer, vm.uiState.value.availableUpdate)
        }

    @Test
    fun `check now reports up to date`() =
        runTest {
            val source = FakeAppReleaseSource(newer.copy(versionName = "2.0.10"))
            val vm = viewModel(source)
            advanceUntilIdle()

            vm.checkNow()
            advanceUntilIdle()

            assertNull(vm.uiState.value.availableUpdate)
            assertEquals(UpdateCheckStatus.UpToDate, vm.uiState.value.checkStatus)
            assertEquals(2, source.fetchCalls)
        }

    @Test
    fun `check now reports failure`() =
        runTest {
            val vm = viewModel(FakeAppReleaseSource(release = null))

            vm.checkNow()
            advanceUntilIdle()

            assertEquals(UpdateCheckStatus.Failed, vm.uiState.value.checkStatus)
        }

    @Test
    fun `install hands the available release to the installer`() =
        runTest {
            val installer = FakeAppUpdateInstaller(progress = listOf(null, 50, 100))
            val vm = viewModel(FakeAppReleaseSource(newer), installer = installer)
            advanceUntilIdle()

            vm.installUpdate()
            advanceUntilIdle()

            assertEquals(listOf(newer), installer.installed)
            assertEquals(UpdateInstallStatus.InstallerOpened, vm.uiState.value.installStatus)
        }

    @Test
    fun `install without permission asks the user to allow it`() =
        runTest {
            val installer = FakeAppUpdateInstaller(result = AppUpdateInstallResult.NeedsInstallPermission)
            val vm = viewModel(FakeAppReleaseSource(newer), installer = installer)
            advanceUntilIdle()

            vm.installUpdate()
            advanceUntilIdle()

            assertEquals(UpdateInstallStatus.NeedsPermission, vm.uiState.value.installStatus)
        }

    @Test
    fun `install does nothing when no update is available`() =
        runTest {
            val installer = FakeAppUpdateInstaller()
            val vm = viewModel(FakeAppReleaseSource(newer.copy(versionName = "2.0.9")), installer = installer)
            advanceUntilIdle()

            vm.installUpdate()
            advanceUntilIdle()

            assertTrue(installer.installed.isEmpty())
            assertEquals(UpdateInstallStatus.Idle, vm.uiState.value.installStatus)
        }
}
