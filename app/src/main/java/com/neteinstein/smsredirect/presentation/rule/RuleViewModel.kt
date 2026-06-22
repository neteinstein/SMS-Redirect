package com.neteinstein.smsredirect.presentation.rule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.neteinstein.smsredirect.domain.model.Rule
import com.neteinstein.smsredirect.domain.usecase.AddRuleUseCase
import com.neteinstein.smsredirect.domain.usecase.GetRuleUseCase
import com.neteinstein.smsredirect.domain.usecase.UpdateRuleUseCase
import com.neteinstein.smsredirect.domain.repository.RuleRepository
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
    val saved: Boolean = false
)

class RuleViewModel(
    private val getRuleUseCase: GetRuleUseCase,
    private val addRuleUseCase: AddRuleUseCase,
    private val updateRuleUseCase: UpdateRuleUseCase,
    private val ruleRepository: RuleRepository,
    private val defaultRuleNameProvider: (Int) -> String = { "Rule #$it" }
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
                    targetNumber = rule.targetNumber
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
        _uiState.update { it.copy(targetNumber = value, error = null) }
    }

    fun saveRule() {
        viewModelScope.launch {
            val current = uiState.value
            val senderValue = normalizePhoneNumber(current.senderText.trim())
            val targetValue = normalizePhoneNumber(current.targetNumber.trim())
            val name = current.name.trim().ifBlank {
                val count = ruleRepository.getRuleCount()
                defaultRuleNameProvider(count + 1)
            }
            val rule = Rule(
                id = current.id,
                name = name,
                senderText = senderValue,
                phoneNumber = senderValue,
                targetNumber = targetValue
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
        private const val DEFAULT_COUNTRY_CODE = "+351"

        /**
         * If the value looks like a phone number (digits only, optionally starting with 0)
         * but has no country code prefix (+), prepend the default country code.
         */
        fun normalizePhoneNumber(value: String): String {
            if (value.isBlank()) return value
            if (value.startsWith("+")) return value
            val digitsOnly = value.filter(Char::isDigit)
            if (digitsOnly.length < 4) return value
            // Only prepend if it looks like a phone number (mostly digits)
            val nonDigits = value.count { !it.isDigit() && it != ' ' && it != '-' && it != '(' && it != ')' }
            if (nonDigits > 0) return value
            return "$DEFAULT_COUNTRY_CODE$digitsOnly"
        }
    }
}

class RuleViewModelFactory(
    private val getRuleUseCase: GetRuleUseCase,
    private val addRuleUseCase: AddRuleUseCase,
    private val updateRuleUseCase: UpdateRuleUseCase,
    private val ruleRepository: RuleRepository,
    private val defaultRuleNameProvider: (Int) -> String = { "Rule #$it" }
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return RuleViewModel(
            getRuleUseCase = getRuleUseCase,
            addRuleUseCase = addRuleUseCase,
            updateRuleUseCase = updateRuleUseCase,
            ruleRepository = ruleRepository,
            defaultRuleNameProvider = defaultRuleNameProvider
        ) as T
    }
}
