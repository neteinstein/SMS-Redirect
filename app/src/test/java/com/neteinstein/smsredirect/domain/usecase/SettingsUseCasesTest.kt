package com.neteinstein.smsredirect.domain.usecase

import app.cash.turbine.test
import com.neteinstein.smsredirect.FakeSettingsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsUseCasesTest {

    private val repository = FakeSettingsRepository()

    // --- SendWhileRoaming ---

    @Test
    fun `setSendWhileRoaming persists enabled state`() = runTest {
        val useCase = SetSendWhileRoamingUseCase(repository)
        useCase(true)
        assertTrue(repository.getSendWhileRoaming())
    }

    @Test
    fun `setSendWhileRoaming persists disabled state`() = runTest {
        val useCase = SetSendWhileRoamingUseCase(repository)
        useCase(true)
        useCase(false)
        assertFalse(repository.getSendWhileRoaming())
    }

    @Test
    fun `getSendWhileRoaming observes state changes`() = runTest {
        val useCase = GetSendWhileRoamingUseCase(repository)
        useCase().test {
            assertFalse(awaitItem())
            repository.setSendWhileRoaming(true)
            assertTrue(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- KeepHistory ---

    @Test
    fun `setKeepHistory persists enabled state`() = runTest {
        val useCase = SetKeepHistoryUseCase(repository)
        useCase(false)
        useCase(true)
        assertTrue(repository.getKeepHistory())
    }

    @Test
    fun `setKeepHistory persists disabled state`() = runTest {
        val useCase = SetKeepHistoryUseCase(repository)
        useCase(false)
        assertFalse(repository.getKeepHistory())
    }

    @Test
    fun `getKeepHistory observes state changes`() = runTest {
        val useCase = GetKeepHistoryUseCase(repository)
        useCase().test {
            assertTrue(awaitItem()) // default is true
            repository.setKeepHistory(false)
            assertFalse(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getKeepHistoryNow returns current value`() = runTest {
        val useCase = GetKeepHistoryNowUseCase(repository)
        assertTrue(useCase())
        repository.setKeepHistory(false)
        assertFalse(useCase())
    }

    // --- DefaultPrefix ---

    @Test
    fun `setDefaultPrefix persists value`() = runTest {
        val useCase = SetDefaultPrefixUseCase(repository)
        useCase("+44")
        assertEquals("+44", repository.getDefaultPrefix())
    }

    @Test
    fun `getDefaultPrefix observes changes`() = runTest {
        val useCase = GetDefaultPrefixUseCase(repository)
        useCase().test {
            assertEquals("+351", awaitItem()) // default
            repository.setDefaultPrefix("+1")
            assertEquals("+1", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getDefaultPrefixNow returns current value`() = runTest {
        val useCase = GetDefaultPrefixNowUseCase(repository)
        assertEquals("+351", useCase())
        repository.setDefaultPrefix("")
        assertEquals("", useCase())
    }
}
