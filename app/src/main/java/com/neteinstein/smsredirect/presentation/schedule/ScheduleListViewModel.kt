package com.neteinstein.smsredirect.presentation.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.neteinstein.smsredirect.domain.contacts.ContactNameResolver
import com.neteinstein.smsredirect.domain.model.ScheduledSms
import com.neteinstein.smsredirect.domain.usecase.DeleteScheduledSmsUseCase
import com.neteinstein.smsredirect.domain.usecase.GetNextScheduledSendUseCase
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
    private val getNextScheduledSendUseCase: GetNextScheduledSendUseCase = GetNextScheduledSendUseCase(),
) : ViewModel() {
    private val _scheduledSmsList = MutableStateFlow<List<ScheduledSms>>(emptyList())
    val scheduledSmsList: StateFlow<List<ScheduledSms>> = _scheduledSmsList.asStateFlow()

    private val _contactNames = MutableStateFlow<Map<String, String>>(emptyMap())
    val contactNames: StateFlow<Map<String, String>> = _contactNames.asStateFlow()

    /** Scheduled SMS id → when it will next be sent. Absent when nothing is left to send. */
    private val _nextSendTimes = MutableStateFlow<Map<Long, Long>>(emptyMap())
    val nextSendTimes: StateFlow<Map<Long, Long>> = _nextSendTimes.asStateFlow()

    init {
        getScheduledSmsListUseCase()
            .onEach { list ->
                _scheduledSmsList.value = list
                _nextSendTimes.value = computeNextSendTimes(list)
                resolveContactNames(list)
            }.launchIn(viewModelScope)
    }

    /**
     * Recomputes [nextSendTimes] against the current time. A send does not change the stored
     * schedule, so the list does not re-emit when one happens: call this when the screen is shown
     * again and once a shown send time has passed.
     */
    fun refreshNextSendTimes() {
        _nextSendTimes.value = computeNextSendTimes(_scheduledSmsList.value)
    }

    private fun computeNextSendTimes(list: List<ScheduledSms>): Map<Long, Long> =
        list
            .mapNotNull { scheduledSms ->
                getNextScheduledSendUseCase(scheduledSms)?.let { scheduledSms.id to it }
            }.toMap()

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
    private val getNextScheduledSendUseCase: GetNextScheduledSendUseCase = GetNextScheduledSendUseCase(),
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return ScheduleListViewModel(
            getScheduledSmsListUseCase,
            deleteScheduledSmsUseCase,
            reorderScheduledSmsUseCase,
            contactNameResolver,
            getNextScheduledSendUseCase,
        ) as T
    }
}
