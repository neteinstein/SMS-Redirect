package com.neteinstein.smsredirect.presentation

import com.neteinstein.smsredirect.FakeContactNameResolver
import com.neteinstein.smsredirect.FakeRuleRepository
import com.neteinstein.smsredirect.FakeSettingsRepository
import com.neteinstein.smsredirect.domain.model.Rule
import com.neteinstein.smsredirect.domain.usecase.AddRuleUseCase
import com.neteinstein.smsredirect.domain.usecase.GetDefaultPrefixNowUseCase
import com.neteinstein.smsredirect.domain.usecase.GetRuleUseCase
import com.neteinstein.smsredirect.domain.usecase.UpdateRuleUseCase
import com.neteinstein.smsredirect.presentation.rule.RuleViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RuleViewModelContactNameTest {
    private val dispatcher = StandardTestDispatcher()
    private val settingsRepository = FakeSettingsRepository()
    private val getDefaultPrefixNowUseCase = GetDefaultPrefixNowUseCase(settingsRepository)

    @Before
    fun setUp() {
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    private fun createViewModel(
        repository: FakeRuleRepository = FakeRuleRepository(),
        contacts: Map<String, String> = emptyMap(),
    ): RuleViewModel =
        RuleViewModel(
            getRuleUseCase = GetRuleUseCase(repository),
            addRuleUseCase = AddRuleUseCase(repository),
            updateRuleUseCase = UpdateRuleUseCase(repository),
            ruleRepository = repository,
            getDefaultPrefixNowUseCase = getDefaultPrefixNowUseCase,
            contactNameResolver = FakeContactNameResolver(contacts),
        )

    @Test
    fun `sender contact pick does not change rule name`() =
        runTest {
            val vm = createViewModel()

            vm.onSenderContactPicked("+351912345678", "John Doe")

            assertEquals("", vm.uiState.value.name)
            assertEquals("+351912345678", vm.uiState.value.senderText)
            assertEquals("John Doe", vm.uiState.value.senderContactName)
        }

    @Test
    fun `target contact pick does not change rule name`() =
        runTest {
            val vm = createViewModel()

            vm.onTargetContactPicked("+351912345678", "Mom")

            assertEquals("", vm.uiState.value.name)
            assertEquals("Mom", vm.uiState.value.targetContactName)
        }

    @Test
    fun `contact pick does not override manual name`() =
        runTest {
            val vm = createViewModel()

            vm.onNameChanged("My Custom Rule")
            vm.onSenderContactPicked("+351912345678", "Bank XYZ")

            assertEquals("My Custom Rule", vm.uiState.value.name)
            assertEquals("Bank XYZ", vm.uiState.value.senderContactName)
        }

    @Test
    fun `manual name change is independent of contacts`() =
        runTest {
            val vm = createViewModel()

            vm.onSenderContactPicked("+351912345678", "John")
            vm.onNameChanged("Custom Name")

            assertEquals("Custom Name", vm.uiState.value.name)
            assertEquals("John", vm.uiState.value.senderContactName)
        }

    @Test
    fun `sender number change clears sender contact name`() =
        runTest {
            val vm = createViewModel()

            vm.onSenderContactPicked("+351912345678", "John")
            assertEquals("John", vm.uiState.value.senderContactName)

            vm.onSenderChanged("+351888888888")
            assertNull(vm.uiState.value.senderContactName)
        }

    @Test
    fun `target number change clears target contact name`() =
        runTest {
            val vm = createViewModel()

            vm.onTargetContactPicked("+351912345678", "Mom")
            assertEquals("Mom", vm.uiState.value.targetContactName)

            vm.onTargetChanged("+351888888888")
            assertNull(vm.uiState.value.targetContactName)
        }

    @Test
    fun `sender contact name not cleared when target changes`() =
        runTest {
            val vm = createViewModel()

            vm.onSenderContactPicked("+351912345678", "Bank")
            vm.onTargetChanged("+351999999999")
            advanceUntilIdle()

            assertEquals("Bank", vm.uiState.value.senderContactName)
        }

    @Test
    fun `manual sender number triggers contact lookup for display`() =
        runTest {
            val vm = createViewModel(contacts = mapOf("+351912345678" to "John"))

            vm.onSenderChanged("+351912345678")
            advanceTimeBy(350)
            advanceUntilIdle()

            assertEquals("John", vm.uiState.value.senderContactName)
            assertEquals("", vm.uiState.value.name)
        }

    @Test
    fun `manual target number triggers contact lookup for display`() =
        runTest {
            val vm = createViewModel(contacts = mapOf("+351912345678" to "Mom"))

            vm.onTargetChanged("+351912345678")
            advanceTimeBy(350)
            advanceUntilIdle()

            assertEquals("Mom", vm.uiState.value.targetContactName)
            assertEquals("", vm.uiState.value.name)
        }

    @Test
    fun `lookup does not override rule name`() =
        runTest {
            val vm = createViewModel(contacts = mapOf("+351912345678" to "John"))

            vm.onNameChanged("My Rule")
            vm.onSenderChanged("+351912345678")
            advanceTimeBy(350)
            advanceUntilIdle()

            assertEquals("My Rule", vm.uiState.value.name)
            assertEquals("John", vm.uiState.value.senderContactName)
        }

    @Test
    fun `loaded rule name preserved on number change`() =
        runTest {
            val repository =
                FakeRuleRepository(
                    listOf(Rule(id = 1, name = "Existing", senderText = "BANK", phoneNumber = "BANK", targetNumber = "+351912345678")),
                )
            val vm = createViewModel(repository = repository)

            vm.loadRule(1)
            advanceUntilIdle()

            assertEquals("Existing", vm.uiState.value.name)

            vm.onTargetChanged("+351999999999")
            advanceUntilIdle()

            assertEquals("Existing", vm.uiState.value.name)
        }

    @Test
    fun `second contact pick updates respective field only`() =
        runTest {
            val vm = createViewModel()

            vm.onSenderContactPicked("+351912345678", "John")
            vm.onTargetContactPicked("+351999888777", "Mom")

            assertEquals("John", vm.uiState.value.senderContactName)
            assertEquals("Mom", vm.uiState.value.targetContactName)
            assertEquals("", vm.uiState.value.name)
        }

    @Test
    fun `save does not use contact name as rule name`() =
        runTest {
            val repository = FakeRuleRepository()
            val vm = createViewModel(repository = repository)

            vm.onSenderContactPicked("+351912345678", "Bank SMS")
            vm.onTargetChanged("+351999888777")
            vm.saveRule()
            advanceUntilIdle()

            val saved = repository.getRulesSnapshot().first()
            // Rule name should be auto-generated, NOT "Bank SMS"
            assertEquals("Rule #1", saved.name)
        }

    @Test
    fun `debounce prevents lookup on rapid typing`() =
        runTest {
            val vm = createViewModel(contacts = mapOf("+351912345678" to "John", "+3519123" to "Partial"))

            vm.onSenderChanged("+3519123")
            advanceTimeBy(100) // less than debounce
            vm.onSenderChanged("+351912345678")
            advanceTimeBy(350)
            advanceUntilIdle()

            assertEquals("John", vm.uiState.value.senderContactName)
        }

    @Test
    fun `load rule resolves contact names`() =
        runTest {
            val repository =
                FakeRuleRepository(
                    listOf(Rule(id = 1, name = "My Rule", senderText = "+351912345678", phoneNumber = "+351912345678", targetNumber = "+351999888777")),
                )
            val vm = createViewModel(repository = repository, contacts = mapOf("+351912345678" to "John", "+351999888777" to "Mom"))

            vm.loadRule(1)
            advanceUntilIdle()

            assertEquals("My Rule", vm.uiState.value.name)
            assertEquals("John", vm.uiState.value.senderContactName)
            assertEquals("Mom", vm.uiState.value.targetContactName)
        }

    @Test
    fun `rule name stays empty when contacts are picked without manual name`() =
        runTest {
            val vm = createViewModel()

            vm.onSenderContactPicked("+351912345678", "John")
            assertEquals("", vm.uiState.value.name)

            vm.onTargetContactPicked("+351999888777", "Mom")
            assertEquals("", vm.uiState.value.name)
        }
}
