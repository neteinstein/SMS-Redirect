package com.neteinstein.smsredirect.presentation.rule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.neteinstein.smsredirect.domain.model.Rule
import com.neteinstein.smsredirect.domain.repository.RuleRepository
import com.neteinstein.smsredirect.domain.usecase.AddRuleUseCase
import com.neteinstein.smsredirect.domain.usecase.GetDefaultPrefixNowUseCase
import com.neteinstein.smsredirect.domain.usecase.GetRuleUseCase
import com.neteinstein.smsredirect.domain.usecase.UpdateRuleUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RuleUiState(
    val id: Long = 0,
    val name: String = "",
    val senderText: String = "",
    val targetNumber: String = "",
    val error: String? = null,
    val saved: Boolean = false,
)

class RuleViewModel(
    private val getRuleUseCase: GetRuleUseCase,
    private val addRuleUseCase: AddRuleUseCase,
    private val updateRuleUseCase: UpdateRuleUseCase,
    private val ruleRepository: RuleRepository,
    private val getDefaultPrefixNowUseCase: GetDefaultPrefixNowUseCase,
    private val defaultRuleNameProvider: (Int) -> String = { "Rule #$it" },
) : ViewModel() {
    private val _uiState = MutableStateFlow(RuleUiState())
    val uiState: StateFlow<RuleUiState> = _uiState.asStateFlow()

    fun loadRule(ruleId: Long) {
        if (ruleId <= 0) return
        viewModelScope.launch {
            val rule = getRuleUseCase(ruleId) ?: return@launch
            val sender = rule.senderText.ifBlank { rule.phoneNumber }
            _uiState.update {
                it.copy(
                    id = rule.id,
                    name = rule.name,
                    senderText = sender,
                    targetNumber = rule.targetNumber,
                )
            }
        }
    }

    fun onNameChanged(value: String) {
        _uiState.update { it.copy(name = value, error = null) }
    }

    fun onSenderChanged(value: String) {
        _uiState.update { it.copy(senderText = value, error = null) }
    }

    fun onTargetChanged(value: String) {
        val filtered = value.filter { it.isDigit() || it in "+- ()" }
        _uiState.update { it.copy(targetNumber = filtered, error = null) }
    }

    fun saveRule(
        invalidTargetError: String = "Target must be a valid phone number",
        duplicateRuleError: String = "This rule already exists",
    ) {
        viewModelScope.launch {
            val current = uiState.value
            if (!isValidPhoneNumber(current.targetNumber.trim())) {
                _uiState.update { it.copy(error = invalidTargetError) }
                return@launch
            }
            val prefix = getDefaultPrefixNowUseCase()
            val senderValue = normalizePhoneNumber(current.senderText.trim(), prefix)
            val targetValue = normalizeTargetNumber(current.targetNumber.trim(), prefix)

            val existingRules = ruleRepository.getRulesSnapshot()
            val isDuplicate =
                existingRules.any { existing ->
                    existing.id != current.id &&
                        existing.senderText == senderValue &&
                        existing.targetNumber == targetValue
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
                Rule(
                    id = current.id,
                    name = name,
                    senderText = senderValue,
                    phoneNumber = senderValue,
                    targetNumber = targetValue,
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
    private val defaultRuleNameProvider: (Int) -> String = { "Rule #$it" },
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return RuleViewModel(
            getRuleUseCase = getRuleUseCase,
            addRuleUseCase = addRuleUseCase,
            updateRuleUseCase = updateRuleUseCase,
            ruleRepository = ruleRepository,
            getDefaultPrefixNowUseCase = getDefaultPrefixNowUseCase,
            defaultRuleNameProvider = defaultRuleNameProvider,
        ) as T
    }
}
