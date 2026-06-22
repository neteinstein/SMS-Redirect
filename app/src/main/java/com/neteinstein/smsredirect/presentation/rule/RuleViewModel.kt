package com.neteinstein.smsredirect.presentation.rule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.neteinstein.smsredirect.domain.model.Rule
import com.neteinstein.smsredirect.domain.usecase.AddRuleUseCase
import com.neteinstein.smsredirect.domain.usecase.GetRuleUseCase
import com.neteinstein.smsredirect.domain.usecase.UpdateRuleUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RuleUiState(
    val id: Long = 0,
    val senderText: String = "",
    val targetNumber: String = "",
    val error: String? = null,
    val saved: Boolean = false
)

class RuleViewModel(
    private val getRuleUseCase: GetRuleUseCase,
    private val addRuleUseCase: AddRuleUseCase,
    private val updateRuleUseCase: UpdateRuleUseCase
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
                    senderText = sender,
                    targetNumber = rule.targetNumber
                )
            }
        }
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
            val senderValue = current.senderText.trim()
            val rule = Rule(
                id = current.id,
                senderText = senderValue,
                phoneNumber = senderValue,
                targetNumber = current.targetNumber.trim()
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
}

class RuleViewModelFactory(
    private val getRuleUseCase: GetRuleUseCase,
    private val addRuleUseCase: AddRuleUseCase,
    private val updateRuleUseCase: UpdateRuleUseCase
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return RuleViewModel(getRuleUseCase, addRuleUseCase, updateRuleUseCase) as T
    }
}
