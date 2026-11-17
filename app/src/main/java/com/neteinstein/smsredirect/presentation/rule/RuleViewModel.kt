package com.neteinstein.smsredirect.presentation.rule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.neteinstein.smsredirect.domain.contacts.ContactNameResolver
import com.neteinstein.smsredirect.domain.device.PhoneActionScheduler
import com.neteinstein.smsredirect.domain.device.SoundModeAccessChecker
import com.neteinstein.smsredirect.domain.model.PhoneAction
import com.neteinstein.smsredirect.domain.model.Rule
import com.neteinstein.smsredirect.domain.model.RuleType
import com.neteinstein.smsredirect.domain.model.SoundMode
import com.neteinstein.smsredirect.domain.model.VibrationPattern
import com.neteinstein.smsredirect.domain.repository.RuleRepository
import com.neteinstein.smsredirect.domain.usecase.AddRuleUseCase
import com.neteinstein.smsredirect.domain.usecase.GetCategoriesUseCase
import com.neteinstein.smsredirect.domain.usecase.GetDefaultPrefixNowUseCase
import com.neteinstein.smsredirect.domain.usecase.GetRuleUseCase
import com.neteinstein.smsredirect.domain.usecase.UpdateRuleUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RuleUiState(
    val id: Long = 0,
    val name: String = "",
    val senderText: String = "",
    val senderContactName: String? = null,
    val targetNumber: String = "",
    val targetContactName: String? = null,
    val category: String = "",
    val existingCategories: List<String> = emptyList(),
    val error: String? = null,
    val saved: Boolean = false,
    val type: RuleType = RuleType.REDIRECT,
    val messageText: String = "",
    /** One of [PhoneAction.KIND_RING], [PhoneAction.KIND_VIBRATE], [PhoneAction.KIND_SOUND_MODE]. */
    val actionKind: String = PhoneAction.KIND_RING,
    val soundUri: String = "",
    val soundName: String = "",
    val vibrationPattern: VibrationPattern = VibrationPattern.LONG,
    val soundMode: SoundMode = SoundMode.SILENT,
    val canChangeSoundMode: Boolean = true,
) {
    /** The phone action the form describes; each kind keeps its own settings while switching. */
    val phoneAction: PhoneAction
        get() =
            when (actionKind) {
                PhoneAction.KIND_VIBRATE -> PhoneAction.Vibrate(vibrationPattern)
                PhoneAction.KIND_SOUND_MODE -> PhoneAction.SetSoundMode(soundMode)
                else -> PhoneAction.Ring(soundUri, soundName)
            }

    val showSoundModeAccessWarning: Boolean
        get() = type == RuleType.PHONE_ACTION && actionKind == PhoneAction.KIND_SOUND_MODE && !canChangeSoundMode
}

class RuleViewModel(
    private val getRuleUseCase: GetRuleUseCase,
    private val addRuleUseCase: AddRuleUseCase,
    private val updateRuleUseCase: UpdateRuleUseCase,
    private val ruleRepository: RuleRepository,
    private val getDefaultPrefixNowUseCase: GetDefaultPrefixNowUseCase,
    private val getCategoriesUseCase: GetCategoriesUseCase,
    private val contactNameResolver: ContactNameResolver? = null,
    private val defaultRuleNameProvider: (Int) -> String = { "Rule #$it" },
    private val phoneActionScheduler: PhoneActionScheduler? = null,
    private val soundModeAccessChecker: SoundModeAccessChecker? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(RuleUiState())
    val uiState: StateFlow<RuleUiState> = _uiState.asStateFlow()

    private var senderLookupJob: Job? = null
    private var targetLookupJob: Job? = null

    init {
        refreshSoundModeAccess()
        getCategoriesUseCase()
            .onEach { categories ->
                _uiState.update { it.copy(existingCategories = categories) }
            }.launchIn(viewModelScope)
    }

    fun loadRule(ruleId: Long) {
        if (ruleId <= 0) return
        viewModelScope.launch {
            val rule = getRuleUseCase(ruleId) ?: return@launch
            val sender = rule.senderText.ifBlank { rule.phoneNumber }
            _uiState.update {
                it
                    .copy(
                        id = rule.id,
                        name = rule.name,
                        senderText = sender,
                        targetNumber = rule.targetNumber,
                        category = rule.category,
                        type = rule.type,
                        messageText = rule.messageText,
                    ).withPhoneAction(rule.phoneAction)
            }
            // Resolve contact names for loaded numbers
            lookupContactName(sender) { name ->
                _uiState.update { it.copy(senderContactName = name) }
            }
            lookupContactName(rule.targetNumber) { name ->
                _uiState.update { it.copy(targetContactName = name) }
            }
        }
    }

    fun onTypeChanged(type: RuleType) {
        _uiState.update { it.copy(type = type, error = null) }
    }

    fun onMessageTextChanged(value: String) {
        _uiState.update { it.copy(messageText = value, error = null) }
    }

    fun onActionKindChanged(kind: String) {
        _uiState.update { it.copy(actionKind = kind, error = null) }
    }

    fun onSoundPicked(
        uri: String,
        name: String,
    ) {
        _uiState.update { it.copy(soundUri = uri, soundName = name) }
    }

    fun onDefaultSoundSelected() {
        _uiState.update { it.copy(soundUri = "", soundName = "") }
    }

    fun onVibrationPatternChanged(pattern: VibrationPattern) {
        _uiState.update { it.copy(vibrationPattern = pattern) }
    }

    fun onSoundModeChanged(mode: SoundMode) {
        _uiState.update { it.copy(soundMode = mode) }
    }

    /** Call on resume: the user may have just granted Do Not Disturb access in settings. */
    fun refreshSoundModeAccess() {
        val granted = soundModeAccessChecker?.canChangeSoundMode() ?: true
        _uiState.update { it.copy(canChangeSoundMode = granted) }
    }

    /** Runs the configured phone action right away, so the user can hear or feel it. */
    fun tryPhoneAction(ruleName: String) {
        val current = uiState.value
        phoneActionScheduler?.schedule(current.phoneAction, current.name.trim().ifBlank { ruleName })
    }

    fun onNameChanged(value: String) {
        _uiState.update {
            it.copy(name = value, error = null)
        }
    }

    fun onCategoryChanged(value: String) {
        _uiState.update {
            it.copy(category = value, error = null)
        }
    }

    fun onSenderChanged(value: String) {
        _uiState.update { it.copy(senderText = value, senderContactName = null, error = null) }
        scheduleSenderLookup(value)
    }

    fun onTargetChanged(value: String) {
        val filtered = value.filter { it.isDigit() || it in "+- ()" }
        _uiState.update { it.copy(targetNumber = filtered, targetContactName = null, error = null) }
        scheduleTargetLookup(filtered)
    }

    fun onSenderContactPicked(
        number: String,
        contactName: String,
    ) {
        senderLookupJob?.cancel()
        _uiState.update {
            it.copy(
                senderText = number,
                senderContactName = contactName,
                error = null,
            )
        }
    }

    fun onTargetContactPicked(
        number: String,
        contactName: String,
    ) {
        targetLookupJob?.cancel()
        val filtered = number.filter { it.isDigit() || it in "+- ()" }
        _uiState.update {
            it.copy(
                targetNumber = filtered,
                targetContactName = contactName,
                error = null,
            )
        }
    }

    private fun scheduleSenderLookup(phoneNumber: String) {
        if (contactNameResolver == null) return
        senderLookupJob?.cancel()
        senderLookupJob =
            viewModelScope.launch {
                delay(LOOKUP_DEBOUNCE_MS)
                val contactName = contactNameResolver.lookupNameByNumber(phoneNumber)
                _uiState.update { it.copy(senderContactName = contactName) }
            }
    }

    private fun scheduleTargetLookup(phoneNumber: String) {
        if (contactNameResolver == null) return
        targetLookupJob?.cancel()
        targetLookupJob =
            viewModelScope.launch {
                delay(LOOKUP_DEBOUNCE_MS)
                val contactName = contactNameResolver.lookupNameByNumber(phoneNumber)
                _uiState.update { it.copy(targetContactName = contactName) }
            }
    }

    private fun lookupContactName(
        phoneNumber: String,
        onResult: (String?) -> Unit,
    ) {
        if (contactNameResolver == null || phoneNumber.isBlank()) return
        viewModelScope.launch {
            val name = contactNameResolver.lookupNameByNumber(phoneNumber)
            onResult(name)
        }
    }

    fun saveRule(
        invalidTargetError: String = "Target must be a valid phone number",
        duplicateRuleError: String = "This rule already exists",
        missingConditionError: String = "Enter a sender, a message text, or both",
    ) {
        viewModelScope.launch {
            val current = uiState.value
            val prefix = getDefaultPrefixNowUseCase()
            val senderValue = normalizePhoneNumber(current.senderText.trim(), prefix)
            val candidate =
                when (current.type) {
                    RuleType.REDIRECT -> {
                        if (!isValidPhoneNumber(current.targetNumber.trim())) {
                            _uiState.update { it.copy(error = invalidTargetError) }
                            return@launch
                        }
                        Rule(
                            senderText = senderValue,
                            phoneNumber = senderValue,
                            targetNumber = normalizeTargetNumber(current.targetNumber.trim(), prefix),
                        )
                    }

                    RuleType.PHONE_ACTION -> {
                        val messageText = current.messageText.trim()
                        if (senderValue.isBlank() && messageText.isBlank()) {
                            _uiState.update { it.copy(error = missingConditionError) }
                            return@launch
                        }
                        Rule(
                            senderText = senderValue,
                            phoneNumber = senderValue,
                            type = RuleType.PHONE_ACTION,
                            messageText = messageText,
                            phoneAction = current.phoneAction,
                        )
                    }
                }

            val existingRules = ruleRepository.getRulesSnapshot()
            val isDuplicate =
                existingRules.any { existing ->
                    existing.id != current.id &&
                        existing.type == candidate.type &&
                        existing.senderText == candidate.senderText &&
                        existing.targetNumber == candidate.targetNumber &&
                        existing.messageText == candidate.messageText &&
                        existing.phoneAction == candidate.phoneAction
                }
            if (isDuplicate) {
                _uiState.update { it.copy(error = duplicateRuleError) }
                return@launch
            }

            val name =
                current.name.trim().ifBlank {
                    val count = ruleRepository.getRuleCount()
                    defaultRuleNameProvider(count + 1)
                }
            val rule =
                candidate.copy(
                    id = current.id,
                    name = name,
                    category = current.category.trim(),
                )
            val result = if (rule.id == 0L) addRuleUseCase(rule) else updateRuleUseCase(rule)
            if (result.isSuccess) {
                _uiState.update { it.copy(saved = true, error = null) }
            } else {
                _uiState.update { it.copy(error = result.exceptionOrNull()?.message) }
            }
        }
    }

    fun clearSavedFlag() {
        _uiState.update { it.copy(saved = false) }
    }

    companion object {
        internal const val LOOKUP_DEBOUNCE_MS = 300L

        private fun RuleUiState.withPhoneAction(action: PhoneAction?): RuleUiState =
            when (action) {
                null -> {
                    this
                }

                is PhoneAction.Ring -> {
                    copy(actionKind = action.kind, soundUri = action.soundUri, soundName = action.soundName)
                }

                is PhoneAction.Vibrate -> {
                    copy(actionKind = action.kind, vibrationPattern = action.pattern)
                }

                is PhoneAction.SetSoundMode -> {
                    copy(actionKind = action.kind, soundMode = action.mode)
                }
            }

        /**
         * If the value looks like a phone number (digits only, optionally starting with 0)
         * but has no country code prefix (+), prepend the configured prefix.
         * When prefix is empty (None), the number is returned as-is.
         */
        fun normalizePhoneNumber(
            value: String,
            prefix: String = "",
        ): String {
            if (value.isBlank()) return value
            if (value.startsWith("+")) return value
            if (prefix.isBlank()) return value
            val digitsOnly = value.filter(Char::isDigit)
            if (digitsOnly.length < 4) return value
            val nonDigits = value.count { !it.isDigit() && it != ' ' && it != '-' && it != '(' && it != ')' }
            if (nonDigits > 0) return value
            return "$prefix$digitsOnly"
        }

        /**
         * Normalizes a target phone number by prepending the configured prefix.
         * Unlike [normalizePhoneNumber], this always applies the prefix to digit-only
         * values since the target field is validated to only contain phone characters.
         */
        fun normalizeTargetNumber(
            value: String,
            prefix: String = "",
        ): String {
            if (value.isBlank()) return value
            if (value.trimStart().startsWith("+")) return value.trimStart()
            if (prefix.isBlank()) return value
            val digitsOnly = value.filter(Char::isDigit)
            if (digitsOnly.length < 4) return value
            return "$prefix$digitsOnly"
        }

        /**
         * Validates that the value is a phone number pattern:
         * must contain at least 4 digits, and only digits, +, spaces, dashes, and parentheses.
         */
        fun isValidPhoneNumber(value: String): Boolean {
            if (value.isBlank()) return false
            val allowed = value.all { it.isDigit() || it in "+- ()" }
            if (!allowed) return false
            val digitCount = value.count(Char::isDigit)
            return digitCount >= 4
        }
    }
}

class RuleViewModelFactory(
    private val getRuleUseCase: GetRuleUseCase,
    private val addRuleUseCase: AddRuleUseCase,
    private val updateRuleUseCase: UpdateRuleUseCase,
    private val ruleRepository: RuleRepository,
    private val getDefaultPrefixNowUseCase: GetDefaultPrefixNowUseCase,
    private val getCategoriesUseCase: GetCategoriesUseCase,
    private val contactNameResolver: ContactNameResolver? = null,
    private val defaultRuleNameProvider: (Int) -> String = { "Rule #$it" },
    private val phoneActionScheduler: PhoneActionScheduler? = null,
    private val soundModeAccessChecker: SoundModeAccessChecker? = null,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return RuleViewModel(
            getRuleUseCase = getRuleUseCase,
            addRuleUseCase = addRuleUseCase,
            updateRuleUseCase = updateRuleUseCase,
            ruleRepository = ruleRepository,
            getDefaultPrefixNowUseCase = getDefaultPrefixNowUseCase,
            getCategoriesUseCase = getCategoriesUseCase,
            contactNameResolver = contactNameResolver,
            defaultRuleNameProvider = defaultRuleNameProvider,
            phoneActionScheduler = phoneActionScheduler,
            soundModeAccessChecker = soundModeAccessChecker,
        ) as T
    }
}
