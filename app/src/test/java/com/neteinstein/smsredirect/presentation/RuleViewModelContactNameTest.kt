package com.neteinstein.smsredirect.presentation

import com.neteinstein.smsredirect.FakeContactNameResolver
import com.neteinstein.smsredirect.FakeRuleRepository
import com.neteinstein.smsredirect.FakeSettingsRepository
import com.neteinstein.smsredirect.domain.model.Rule
import com.neteinstein.smsredirect.domain.usecase.AddRuleUseCase
import com.neteinstein.smsredirect.domain.usecase.GetDefaultPrefixNowUseCase
import com.neteinstein.smsredirect.domain.usecase.GetRuleUseCase
import com.neteinstein.smsredirect.domain.usecase.UpdateRuleUseCase
import com.neteinstein.smsredirect.presentation.rule.NameField
import com.neteinstein.smsredirect.presentation.rule.NameSource
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
        contacts: Map<String, String> = emptyMap()
    ): RuleViewModel {
        return RuleViewModel(
            getRuleUseCase = GetRuleUseCase(repository),
            addRuleUseCase = AddRuleUseCase(repository),
            updateRuleUseCase = UpdateRuleUseCase(repository),
            ruleRepository = repository,
            getDefaultPrefixNowUseCase = getDefaultPrefixNowUseCase,
            contactNameResolver = FakeContactNameResolver(contacts)
        )
    }

    @Test
    fun `sender contact pick sets name from contact`() = runTest {
        val vm = createViewModel()

        vm.onSenderContactPicked("+351912345678", "John Doe")

        assertEquals("John Doe", vm.uiState.value.name)
        assertEquals(NameSource.CONTACT, vm.uiState.value.nameSource)
        assertEquals(NameField.SENDER, vm.uiState.value.nameLinkedField)
    }

    @Test
    fun `target contact pick sets name from contact`() = runTest {
        val vm = createViewModel()

        vm.onTargetContactPicked("+351912345678", "Mom")

        assertEquals("Mom", vm.uiState.value.name)
        assertEquals(NameSource.CONTACT, vm.uiState.value.nameSource)
        assertEquals(NameField.TARGET, vm.uiState.value.nameLinkedField)
    }

    @Test
    fun `contact pick overrides manual name`() = runTest {
        val vm = createViewModel()

        vm.onNameChanged("My Custom Rule")
        assertEquals(NameSource.MANUAL, vm.uiState.value.nameSource)

        vm.onSenderContactPicked("+351912345678", "Bank XYZ")

        assertEquals("Bank XYZ", vm.uiState.value.name)
        assertEquals(NameSource.CONTACT, vm.uiState.value.nameSource)
    }

    @Test
    fun `manual name change after contact pick marks as manual`() = runTest {
        val vm = createViewModel()

        vm.onSenderContactPicked("+351912345678", "John")
        vm.onNameChanged("Custom Name")

        assertEquals("Custom Name", vm.uiState.value.name)
        assertEquals(NameSource.MANUAL, vm.uiState.value.nameSource)
    }

    @Test
    fun `manual name then sender number change clears name`() = runTest {
        val vm = createViewModel()

        vm.onSenderChanged("+351111111111")
        advanceUntilIdle()
        vm.onNameChanged("My Rule")
        assertEquals(NameSource.MANUAL, vm.uiState.value.nameSource)

        vm.onSenderChanged("+351999999999")
        advanceUntilIdle()

        assertEquals("", vm.uiState.value.name)
        assertEquals(NameSource.NONE, vm.uiState.value.nameSource)
    }

    @Test
    fun `manual name then target number change clears name`() = runTest {
        val vm = createViewModel()

        vm.onTargetChanged("+351111111111")
        advanceUntilIdle()
        vm.onNameChanged("My Rule")
        vm.onTargetChanged("+351999999999")
        advanceUntilIdle()

        assertEquals("", vm.uiState.value.name)
        assertEquals(NameSource.NONE, vm.uiState.value.nameSource)
    }

    @Test
    fun `contact name from sender not cleared when target changes`() = runTest {
        val vm = createViewModel()

        vm.onSenderContactPicked("+351912345678", "Bank")
        vm.onTargetChanged("+351999999999")
        advanceUntilIdle()

        assertEquals("Bank", vm.uiState.value.name)
        assertEquals(NameSource.CONTACT, vm.uiState.value.nameSource)
        assertEquals(NameField.SENDER, vm.uiState.value.nameLinkedField)
    }

    @Test
    fun `contact name from sender cleared when sender changes`() = runTest {
        val vm = createViewModel()

        vm.onSenderContactPicked("+351912345678", "Bank")
        assertEquals("Bank", vm.uiState.value.name)

        vm.onSenderChanged("+351888888888")
        advanceUntilIdle()

        assertEquals("", vm.uiState.value.name)
    }

    @Test
    fun `manual sender number triggers contact lookup`() = runTest {
        val vm = createViewModel(contacts = mapOf("+351912345678" to "John"))

        vm.onSenderChanged("+351912345678")
        advanceTimeBy(350)
        advanceUntilIdle()

        assertEquals("John", vm.uiState.value.name)
        assertEquals(NameSource.CONTACT, vm.uiState.value.nameSource)
        assertEquals(NameField.SENDER, vm.uiState.value.nameLinkedField)
    }

    @Test
    fun `manual target number triggers contact lookup`() = runTest {
        val vm = createViewModel(contacts = mapOf("+351912345678" to "Mom"))

        vm.onTargetChanged("+351912345678")
        advanceTimeBy(350)
        advanceUntilIdle()

        assertEquals("Mom", vm.uiState.value.name)
        assertEquals(NameSource.CONTACT, vm.uiState.value.nameSource)
        assertEquals(NameField.TARGET, vm.uiState.value.nameLinkedField)
    }

    @Test
    fun `lookup does not override manual name when field initially set`() = runTest {
        val vm = createViewModel(contacts = mapOf("+351912345678" to "John"))

        vm.onNameChanged("My Rule")
        // Initial fill of sender - should NOT clear manual name
        vm.onSenderChanged("+351912345678")
        advanceTimeBy(350)
        advanceUntilIdle()

        // Manual name persists because it's an initial fill (previous was blank)
        assertEquals("My Rule", vm.uiState.value.name)
        assertEquals(NameSource.MANUAL, vm.uiState.value.nameSource)
    }

    @Test
    fun `lookup fills name after manual name cleared by number change`() = runTest {
        val vm = createViewModel(contacts = mapOf("+351912345678" to "John"))

        // Set sender first, then name, then CHANGE sender → clears manual name → lookup fills it
        vm.onSenderChanged("+351111111111")
        advanceUntilIdle()
        vm.onNameChanged("My Rule")
        vm.onSenderChanged("+351912345678")
        advanceTimeBy(350)
        advanceUntilIdle()

        assertEquals("John", vm.uiState.value.name)
        assertEquals(NameSource.CONTACT, vm.uiState.value.nameSource)
    }

    @Test
    fun `loaded rule name preserved on number change`() = runTest {
        val repository = FakeRuleRepository(
            listOf(Rule(id = 1, name = "Existing", senderText = "BANK", phoneNumber = "BANK", targetNumber = "+351912345678"))
        )
        val vm = createViewModel(repository = repository)

        vm.loadRule(1)
        advanceUntilIdle()

        assertEquals("Existing", vm.uiState.value.name)
        assertEquals(NameSource.NONE, vm.uiState.value.nameSource)

        vm.onTargetChanged("+351999999999")
        advanceUntilIdle()

        // Loaded name (NONE source, no linked field) should not be cleared
        assertEquals("Existing", vm.uiState.value.name)
    }

    @Test
    fun `second contact pick overrides first`() = runTest {
        val vm = createViewModel()

        vm.onSenderContactPicked("+351912345678", "John")
        vm.onTargetContactPicked("+351999888777", "Mom")

        assertEquals("Mom", vm.uiState.value.name)
        assertEquals(NameSource.CONTACT, vm.uiState.value.nameSource)
        assertEquals(NameField.TARGET, vm.uiState.value.nameLinkedField)
    }

    @Test
    fun `clearing name resets source to none`() = runTest {
        val vm = createViewModel()

        vm.onSenderContactPicked("+351912345678", "John")
        vm.onNameChanged("")

        assertEquals("", vm.uiState.value.name)
        assertEquals(NameSource.NONE, vm.uiState.value.nameSource)
    }

    @Test
    fun `save uses contact name from pick`() = runTest {
        val repository = FakeRuleRepository()
        val vm = createViewModel(repository = repository)

        vm.onSenderContactPicked("+351912345678", "Bank SMS")
        vm.onTargetChanged("+351999888777")
        vm.saveRule()
        advanceUntilIdle()

        val saved = repository.getRulesSnapshot().first()
        assertEquals("Bank SMS", saved.name)
    }

    @Test
    fun `debounce prevents lookup on rapid typing`() = runTest {
        val vm = createViewModel(contacts = mapOf("+351912345678" to "John", "+3519123" to "Partial"))

        vm.onSenderChanged("+3519123")
        advanceTimeBy(100) // less than debounce
        vm.onSenderChanged("+351912345678")
        advanceTimeBy(350)
        advanceUntilIdle()

        // Only the final number should be looked up
        assertEquals("John", vm.uiState.value.name)
    }

    @Test
    fun `contact name override kept until number changes`() = runTest {
        val vm = createViewModel()

        // Pick contact → get contact name
        vm.onSenderContactPicked("+351912345678", "John")
        assertEquals("John", vm.uiState.value.name)

        // Override name manually
        vm.onNameChanged("Custom")
        assertEquals("Custom", vm.uiState.value.name)
        assertEquals(NameSource.MANUAL, vm.uiState.value.nameSource)

        // Initial target fill doesn't clear manual name (previous was blank)
        vm.onTargetChanged("+351111111111")
        advanceUntilIdle()
        assertEquals("Custom", vm.uiState.value.name)

        // Now changing target (from non-empty) clears manual name
        vm.onTargetChanged("+351999888777")
        advanceUntilIdle()
        assertEquals("", vm.uiState.value.name)
    }
}
