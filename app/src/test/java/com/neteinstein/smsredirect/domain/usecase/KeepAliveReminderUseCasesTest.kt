package com.neteinstein.smsredirect.domain.usecase

import com.neteinstein.smsredirect.FakeKeepAliveReminderRepository
import com.neteinstein.smsredirect.FakeUnusedAppRestrictionChecker
import com.neteinstein.smsredirect.domain.hibernation.UnusedAppRestrictionStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeepAliveReminderUseCasesTest {
    @Test
    fun `reminder is sent while manage app if unused is on`() {
        val useCase = ShouldSendKeepAliveReminderUseCase(FakeUnusedAppRestrictionChecker(UnusedAppRestrictionStatus.ENABLED))

        assertTrue(useCase())
    }

    @Test
    fun `reminder is sent when the setting cannot be read`() {
        val useCase = ShouldSendKeepAliveReminderUseCase(FakeUnusedAppRestrictionChecker(UnusedAppRestrictionStatus.UNKNOWN))

        assertTrue(useCase())
    }

    @Test
    fun `reminder is skipped once the user disabled manage app if unused`() {
        val useCase = ShouldSendKeepAliveReminderUseCase(FakeUnusedAppRestrictionChecker(UnusedAppRestrictionStatus.DISABLED))

        assertFalse(useCase())
    }

    @Test
    fun `info is offered the first time only`() =
        runTest {
            // Given
            val repository = FakeKeepAliveReminderRepository()
            val shouldShow = ShouldShowUnusedAppInfoUseCase(FakeUnusedAppRestrictionChecker(), repository)
            val markShown = MarkUnusedAppInfoShownUseCase(repository)

            // When
            val first = shouldShow()
            markShown()
            val second = shouldShow()

            // Then
            assertTrue(first)
            assertFalse(second)
        }

    @Test
    fun `info is not offered when the setting is already disabled`() =
        runTest {
            val repository = FakeKeepAliveReminderRepository()
            val shouldShow =
                ShouldShowUnusedAppInfoUseCase(FakeUnusedAppRestrictionChecker(UnusedAppRestrictionStatus.DISABLED), repository)

            assertEquals(false, shouldShow())
        }
}
