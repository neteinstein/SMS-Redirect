package com.neteinstein.smsredirect.presentation.schedulehistory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.neteinstein.smsredirect.domain.contacts.ContactNameResolver
import com.neteinstein.smsredirect.domain.model.ScheduledSmsHistory
import com.neteinstein.smsredirect.domain.usecase.DeleteAllScheduledSmsHistoryUseCase
import com.neteinstein.smsredirect.domain.usecase.GetKeepScheduleHistoryUseCase
import com.neteinstein.smsredirect.domain.usecase.GetScheduledSmsHistoryUseCase
import com.neteinstein.smsredirect.domain.usecase.SetKeepScheduleHistoryUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class ScheduleHistoryViewModel(
    getScheduledSmsHistoryUseCase: GetScheduledSmsHistoryUseCase,
    getKeepScheduleHistoryUseCase: GetKeepScheduleHistoryUseCase,
    private val setKeepScheduleHistoryUseCase: SetKeepScheduleHistoryUseCase,
    private val deleteAllScheduledSmsHistoryUseCase: DeleteAllScheduledSmsHistoryUseCase,
    private val contactNameResolver: ContactNameResolver? = null,
) : ViewModel() {
    private val _scheduledSmsHistory = MutableStateFlow<List<ScheduledSmsHistory>>(emptyList())
    val scheduledSmsHistory: StateFlow<List<ScheduledSmsHistory>> = _scheduledSmsHistory.asStateFlow()

    private val _contactNames = MutableStateFlow<Map<String, String>>(emptyMap())
    val contactNames: StateFlow<Map<String, String>> = _contactNames.asStateFlow()

    private val _keepScheduleHistory = MutableStateFlow(true)
    val keepScheduleHistory: StateFlow<Boolean> = _keepScheduleHistory.asStateFlow()

    private val _showDisableConfirmation = MutableStateFlow(false)
    val showDisableConfirmation: StateFlow<Boolean> = _showDisableConfirmation.asStateFlow()

    init {
        getScheduledSmsHistoryUseCase(limit = 50)
            .onEach { history ->
                _scheduledSmsHistory.value = history
                resolveContactNames(history)
            }.launchIn(viewModelScope)
        getKeepScheduleHistoryUseCase()
            .onEach { _keepScheduleHistory.value = it }
            .launchIn(viewModelScope)
    }

    fun onKeepScheduleHistoryToggled(enabled: Boolean) {
        if (!enabled && _scheduledSmsHistory.value.isNotEmpty()) {
            _showDisableConfirmation.value = true
        } else {
            viewModelScope.launch {
                setKeepScheduleHistoryUseCase(enabled)
            }
        }
    }

    fun confirmDisableHistory() {
        _showDisableConfirmation.value = false
        viewModelScope.launch {
            setKeepScheduleHistoryUseCase(false)
            deleteAllScheduledSmsHistoryUseCase()
        }
    }

    fun dismissDisableConfirmation() {
        _showDisableConfirmation.value = false
    }

    private fun resolveContactNames(history: List<ScheduledSmsHistory>) {
        if (contactNameResolver == null) return
        val numbers =
            history
                .map { it.contactNumber }
                .filter { it.isNotBlank() }
                .distinct()

        viewModelScope.launch {
            val resolved = mutableMapOf<String, String>()
            for (number in numbers) {
                val name = contactNameResolver.lookupNameByNumber(number)
                if (name != null) {
                    resolved[number] = name
                }
            }
            _contactNames.value = resolved
        }
    }
}

class ScheduleHistoryViewModelFactory(
    private val getScheduledSmsHistoryUseCase: GetScheduledSmsHistoryUseCase,
    private val getKeepScheduleHistoryUseCase: GetKeepScheduleHistoryUseCase,
    private val setKeepScheduleHistoryUseCase: SetKeepScheduleHistoryUseCase,
    private val deleteAllScheduledSmsHistoryUseCase: DeleteAllScheduledSmsHistoryUseCase,
    private val contactNameResolver: ContactNameResolver? = null,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return ScheduleHistoryViewModel(
            getScheduledSmsHistoryUseCase = getScheduledSmsHistoryUseCase,
            getKeepScheduleHistoryUseCase = getKeepScheduleHistoryUseCase,
            setKeepScheduleHistoryUseCase = setKeepScheduleHistoryUseCase,
            deleteAllScheduledSmsHistoryUseCase = deleteAllScheduledSmsHistoryUseCase,
            contactNameResolver = contactNameResolver,
        ) as T
    }
}
