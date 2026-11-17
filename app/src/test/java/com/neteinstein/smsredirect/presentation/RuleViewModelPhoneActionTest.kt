package com.neteinstein.smsredirect.presentation

import com.neteinstein.smsredirect.FakePhoneActionScheduler
import com.neteinstein.smsredirect.FakeRuleRepository
import com.neteinstein.smsredirect.FakeSettingsRepository
import com.neteinstein.smsredirect.FakeSoundModeAccessChecker
import com.neteinstein.smsredirect.domain.model.PhoneAction
import com.neteinstein.smsredirect.domain.model.Rule
import com.neteinstein.smsredirect.domain.model.RuleType
import com.neteinstein.smsredirect.domain.model.SoundMode
import com.neteinstein.smsredirect.domain.model.VibrationPattern
import com.neteinstein.smsredirect.domain.usecase.AddRuleUseCase
import com.neteinstein.smsredirect.domain.usecase.GetCategoriesUseCase
import com.neteinstein.smsredirect.domain.usecase.GetDefaultPrefixNowUseCase
import com.neteinstein.smsredirect.domain.usecase.GetRuleUseCase
import com.neteinstein.smsredirect.domain.usecase.UpdateRuleUseCase
import com.neteinstein.smsredirect.presentation.rule.RuleViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RuleViewModelPhoneActionTest {
    private val dispatcher = StandardTestDispatcher()
    private val settingsRepository = FakeSettingsRepository()
    private val scheduler = FakePhoneActionScheduler()
    private val accessChecker = FakeSoundModeAccessChecker(granted = false)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(repository: FakeRuleRepository) =
        RuleViewModel(
            GetRuleUseCase(repository),
            AddRuleUseCase(repository),
            UpdateRuleUseCase(repository),
            repository,
            GetDefaultPrefixNowUseCase(settingsRepository),
            GetCategoriesUseCase(repository),
            phoneActionScheduler = scheduler,
            soundModeAccessChecker = accessChecker,
        )

    @Test
    fun `saves a message-only ring rule without a target number`() =
        runTest {
            val repository = FakeRuleRepository()
            val vm = viewModel(repository)

            vm.onTypeChanged(RuleType.PHONE_ACTION)
            vm.onNameChanged("Find phone")
            vm.onMessageTextChanged("  where are you  ")
            vm.onSoundPicked("content://media/7", "Song")
            vm.saveRule()
            advanceUntilIdle()

            assertTrue(vm.uiState.value.saved)
            val saved = repository.getRulesSnapshot().single()
            assertEquals(RuleType.PHONE_ACTION, saved.type)
            assertEquals("where are you", saved.messageText)
            assertEquals("", saved.senderText)
            assertEquals("", saved.targetNumber)
            assertEquals(PhoneAction.Ring("content://media/7", "Song"), saved.phoneAction)
        }

    @Test
    fun `saves the selected action kind with its own settings`() =
        runTest {
            val repository = FakeRuleRepository()
            val vm = viewModel(repository)

            vm.onTypeChanged(RuleType.PHONE_ACTION)
            vm.onSenderChanged("+351911111111")
            vm.onSoundPicked("content://media/7", "Song")
            vm.onActionKindChanged(PhoneAction.KIND_VIBRATE)
            vm.onVibrationPatternChanged(VibrationPattern.SOS)
            vm.saveRule()
            advanceUntilIdle()

            val saved = repository.getRulesSnapshot().single()
            assertEquals(PhoneAction.Vibrate(VibrationPattern.SOS), saved.phoneAction)
            assertEquals("+351911111111", saved.senderText)
            assertEquals("", saved.messageText)
        }

    @Test
    fun `phone action rule without sender or message text is rejected`() =
        runTest {
            val repository = FakeRuleRepository()
            val vm = viewModel(repository)

            vm.onTypeChanged(RuleType.PHONE_ACTION)
            vm.onMessageTextChanged("   ")
            vm.saveRule(missingConditionError = "need a condition")
            advanceUntilIdle()

            assertEquals("need a condition", vm.uiState.value.error)
            assertFalse(vm.uiState.value.saved)
            assertTrue(repository.getRulesSnapshot().isEmpty())
        }

    @Test
    fun `phone action rule does not need a valid target number`() =
        runTest {
            val vm = viewModel(FakeRuleRepository())

            vm.onTypeChanged(RuleType.PHONE_ACTION)
            vm.onMessageTextChanged("ring")
            vm.saveRule(invalidTargetError = "bad target")
            advanceUntilIdle()

            assertNull(vm.uiState.value.error)
            assertTrue(vm.uiState.value.saved)
        }

    @Test
    fun `identical phone action rule is a duplicate, a different action is not`() =
        runTest {
            val existing =
                Rule(
                    id = 1,
                    type = RuleType.PHONE_ACTION,
                    messageText = "ring",
                    phoneAction = PhoneAction.Ring(),
                )
            val repository = FakeRuleRepository(listOf(existing))
            val vm = viewModel(repository)

            vm.onTypeChanged(RuleType.PHONE_ACTION)
            vm.onMessageTextChanged("ring")
            vm.saveRule(duplicateRuleError = "dup")
            advanceUntilIdle()
            assertEquals("dup", vm.uiState.value.error)

            vm.onActionKindChanged(PhoneAction.KIND_VIBRATE)
            vm.saveRule(duplicateRuleError = "dup")
            advanceUntilIdle()
            assertTrue(vm.uiState.value.saved)
            assertEquals(2, repository.getRulesSnapshot().size)
        }

    @Test
    fun `a phone action rule does not make a redirect rule a duplicate`() =
        runTest {
            val existing =
                Rule(
                    id = 1,
                    senderText = "BANK",
                    phoneNumber = "BANK",
                    type = RuleType.PHONE_ACTION,
                    phoneAction = PhoneAction.Ring(),
                )
            val repository = FakeRuleRepository(listOf(existing))
            val vm = viewModel(repository)

            vm.onSenderChanged("BANK")
            vm.onTargetChanged("5551234")
            vm.saveRule(duplicateRuleError = "dup")
            advanceUntilIdle()

            assertTrue(vm.uiState.value.saved)
            assertEquals(RuleType.REDIRECT, repository.getRulesSnapshot().first { it.id != 1L }.type)
        }

    @Test
    fun `loading a phone action rule restores its settings`() =
        runTest {
            val repository =
                FakeRuleRepository(
                    listOf(
                        Rule(
                            id = 3,
                            senderText = "BANK",
                            phoneNumber = "BANK",
                            type = RuleType.PHONE_ACTION,
                            messageText = "fraud",
                            phoneAction = PhoneAction.SetSoundMode(SoundMode.RING),
                        ),
                    ),
                )
            val vm = viewModel(repository)

            vm.loadRule(3)
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals(RuleType.PHONE_ACTION, state.type)
            assertEquals("fraud", state.messageText)
            assertEquals(PhoneAction.KIND_SOUND_MODE, state.actionKind)
            assertEquals(SoundMode.RING, state.soundMode)
            assertEquals(PhoneAction.SetSoundMode(SoundMode.RING), state.phoneAction)
        }

    @Test
    fun `editing a phone action rule keeps its id`() =
        runTest {
            val repository =
                FakeRuleRepository(
                    listOf(Rule(id = 3, type = RuleType.PHONE_ACTION, messageText = "a", phoneAction = PhoneAction.Ring())),
                )
            val vm = viewModel(repository)
            vm.loadRule(3)
            advanceUntilIdle()

            vm.onMessageTextChanged("b")
            vm.saveRule()
            advanceUntilIdle()

            val saved = repository.getRulesSnapshot().single()
            assertEquals(3L, saved.id)
            assertEquals("b", saved.messageText)
        }

    @Test
    fun `try schedules the configured action under the rule name or the fallback`() =
        runTest {
            val vm = viewModel(FakeRuleRepository())
            vm.onTypeChanged(RuleType.PHONE_ACTION)
            vm.onActionKindChanged(PhoneAction.KIND_VIBRATE)
            vm.onVibrationPatternChanged(VibrationPattern.DOUBLE)

            vm.tryPhoneAction("Test")
            vm.onNameChanged("Mine")
            vm.tryPhoneAction("Test")

            assertEquals(
                listOf(
                    PhoneAction.Vibrate(VibrationPattern.DOUBLE) to "Test",
                    PhoneAction.Vibrate(VibrationPattern.DOUBLE) to "Mine",
                ),
                scheduler.scheduled,
            )
        }

    @Test
    fun `sound mode access warning shows only for sound mode actions until access is granted`() =
        runTest {
            val vm = viewModel(FakeRuleRepository())
            vm.onTypeChanged(RuleType.PHONE_ACTION)
            assertFalse(vm.uiState.value.showSoundModeAccessWarning)

            vm.onActionKindChanged(PhoneAction.KIND_SOUND_MODE)
            assertTrue(vm.uiState.value.showSoundModeAccessWarning)

            accessChecker.granted = true
            vm.refreshSoundModeAccess()
            assertFalse(vm.uiState.value.showSoundModeAccessWarning)
        }
}
