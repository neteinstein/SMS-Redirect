package com.neteinstein.smsredirect.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.neteinstein.smsredirect.domain.model.Rule
import com.neteinstein.smsredirect.domain.usecase.DeleteRuleUseCase
import com.neteinstein.smsredirect.domain.usecase.GetRulesUseCase
import com.neteinstein.smsredirect.domain.usecase.ReorderRulesUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class HomeViewModel(
    getRulesUseCase: GetRulesUseCase,
    private val deleteRuleUseCase: DeleteRuleUseCase,
    private val reorderRulesUseCase: ReorderRulesUseCase,
) : ViewModel() {
    private val _rules = MutableStateFlow<List<Rule>>(emptyList())
    val rules: StateFlow<List<Rule>> = _rules.asStateFlow()

    init {
        getRulesUseCase().onEach { _rules.value = it }.launchIn(viewModelScope)
    }

    fun deleteRule(rule: Rule) {
        viewModelScope.launch {
            deleteRuleUseCase(rule)
        }
    }

    fun moveRule(
        fromIndex: Int,
        toIndex: Int,
    ) {
        val current = _rules.value.toMutableList()
        if (fromIndex !in current.indices || toIndex !in current.indices) return
        val item = current.removeAt(fromIndex)
        current.add(toIndex, item)
        _rules.value = current
    }

    fun commitReorder() {
        viewModelScope.launch {
            reorderRulesUseCase(_rules.value)
        }
    }
}

class HomeViewModelFactory(
    private val getRulesUseCase: GetRulesUseCase,
    private val deleteRuleUseCase: DeleteRuleUseCase,
    private val reorderRulesUseCase: ReorderRulesUseCase,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return HomeViewModel(getRulesUseCase, deleteRuleUseCase, reorderRulesUseCase) as T
    }
}
