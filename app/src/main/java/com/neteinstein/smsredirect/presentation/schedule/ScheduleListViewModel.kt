package com.neteinstein.smsredirect.presentation.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.neteinstein.smsredirect.domain.contacts.ContactNameResolver
import com.neteinstein.smsredirect.domain.model.ScheduledSms
import com.neteinstein.smsredirect.domain.usecase.DeleteScheduledSmsUseCase
import com.neteinstein.smsredirect.domain.usecase.GetScheduledSmsListUseCase
import com.neteinstein.smsredirect.domain.usecase.ReorderScheduledSmsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class ScheduleListViewModel(
    getScheduledSmsListUseCase: GetScheduledSmsListUseCase,
    private val deleteScheduledSmsUseCase: DeleteScheduledSmsUseCase,
    private val reorderScheduledSmsUseCase: ReorderScheduledSmsUseCase,
    private val contactNameResolver: ContactNameResolver? = null,
) : ViewModel() {
    private val _scheduledSmsList = MutableStateFlow<List<ScheduledSms>>(emptyList())
    val scheduledSmsList: StateFlow<List<ScheduledSms>> = _scheduledSmsList.asStateFlow()

    private val _contactNames = MutableStateFlow<Map<String, String>>(emptyMap())
    val contactNames: StateFlow<Map<String, String>> = _contactNames.asStateFlow()

    init {
        getScheduledSmsListUseCase()
            .onEach { list ->
                _scheduledSmsList.value = list
                resolveContactNames(list)
            }.launchIn(viewModelScope)
    }

    private fun resolveContactNames(list: List<ScheduledSms>) {
        if (contactNameResolver == null) return
        val numbers =
            list
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

    fun deleteScheduledSms(scheduledSms: ScheduledSms) {
        viewModelScope.launch {
            deleteScheduledSmsUseCase(scheduledSms)
        }
    }

    fun moveScheduledSms(
        fromIndex: Int,
        toIndex: Int,
    ) {
        val current = _scheduledSmsList.value.toMutableList()
        if (fromIndex !in current.indices || toIndex !in current.indices) return
        val item = current.removeAt(fromIndex)
        current.add(toIndex, item)
        _scheduledSmsList.value = current
    }

    fun commitReorder() {
        viewModelScope.launch {
            reorderScheduledSmsUseCase(_scheduledSmsList.value)
        }
    }
}

class ScheduleListViewModelFactory(
    private val getScheduledSmsListUseCase: GetScheduledSmsListUseCase,
    private val deleteScheduledSmsUseCase: DeleteScheduledSmsUseCase,
    private val reorderScheduledSmsUseCase: ReorderScheduledSmsUseCase,
    private val contactNameResolver: ContactNameResolver? = null,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return ScheduleListViewModel(
            getScheduledSmsListUseCase,
            deleteScheduledSmsUseCase,
            reorderScheduledSmsUseCase,
            contactNameResolver,
        ) as T
    }
}
