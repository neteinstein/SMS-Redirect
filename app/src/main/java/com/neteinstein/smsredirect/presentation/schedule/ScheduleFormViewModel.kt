package com.neteinstein.smsredirect.presentation.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.neteinstein.smsredirect.domain.contacts.ContactNameResolver
import com.neteinstein.smsredirect.domain.model.RepeatType
import com.neteinstein.smsredirect.domain.model.ScheduledSms
import com.neteinstein.smsredirect.domain.repository.ScheduledSmsRepository
import com.neteinstein.smsredirect.domain.usecase.AddScheduledSmsUseCase
import com.neteinstein.smsredirect.domain.usecase.GetDefaultPrefixNowUseCase
import com.neteinstein.smsredirect.domain.usecase.GetScheduledSmsCategoriesUseCase
import com.neteinstein.smsredirect.domain.usecase.GetScheduledSmsUseCase
import com.neteinstein.smsredirect.domain.usecase.UpdateScheduledSmsUseCase
import com.neteinstein.smsredirect.presentation.rule.RuleViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ScheduleFormUiState(
    val id: Long = 0,
    val name: String = "",
    val category: String = "",
    val existingCategories: List<String> = emptyList(),
    val contactNumber: String = "",
    val contactName: String? = null,
    val message: String = "",
    val dateEpochMs: Long? = null,
    val repeatType: RepeatType = RepeatType.NONE,
    val stopAtEpochMs: Long? = null,
    val error: String? = null,
    val saved: Boolean = false,
)

class ScheduleFormViewModel(
    private val getScheduledSmsUseCase: GetScheduledSmsUseCase,
    private val addScheduledSmsUseCase: AddScheduledSmsUseCase,
    private val updateScheduledSmsUseCase: UpdateScheduledSmsUseCase,
    private val getScheduledSmsCategoriesUseCase: GetScheduledSmsCategoriesUseCase,
    private val scheduledSmsRepository: ScheduledSmsRepository,
    private val getDefaultPrefixNowUseCase: GetDefaultPrefixNowUseCase,
    private val contactNameResolver: ContactNameResolver? = null,
    private val defaultScheduledSmsNameProvider: (Int) -> String = { "Scheduled SMS #$it" },
) : ViewModel() {
    private val _uiState = MutableStateFlow(ScheduleFormUiState())
    val uiState: StateFlow<ScheduleFormUiState> = _uiState.asStateFlow()

    private var contactLookupJob: Job? = null

    init {
        getScheduledSmsCategoriesUseCase()
            .onEach { categories ->
                _uiState.update { it.copy(existingCategories = categories) }
            }.launchIn(viewModelScope)
    }

    fun loadScheduledSms(id: Long) {
        if (id <= 0) return
        viewModelScope.launch {
            val scheduledSms = getScheduledSmsUseCase(id) ?: return@launch
            _uiState.update {
                it.copy(
                    id = scheduledSms.id,
                    name = scheduledSms.name,
                    category = scheduledSms.category,
                    contactNumber = scheduledSms.contactNumber,
                    message = scheduledSms.message,
                    dateEpochMs = scheduledSms.dateEpochMs,
                    repeatType = scheduledSms.repeatType,
                    stopAtEpochMs = scheduledSms.stopAtEpochMs,
                )
            }
            lookupContactName(scheduledSms.contactNumber) { name ->
                _uiState.update { it.copy(contactName = name) }
            }
        }
    }

    fun onNameChanged(value: String) {
        _uiState.update { it.copy(name = value, error = null) }
    }

    fun onCategoryChanged(value: String) {
        _uiState.update { it.copy(category = value, error = null) }
    }

    fun onContactChanged(value: String) {
        val filtered = value.filter { it.isDigit() || it in "+- ()" }
        _uiState.update { it.copy(contactNumber = filtered, contactName = null, error = null) }
        scheduleContactLookup(filtered)
    }

    fun onContactPicked(
        number: String,
        contactName: String,
    ) {
        contactLookupJob?.cancel()
        val filtered = number.filter { it.isDigit() || it in "+- ()" }
        _uiState.update {
            it.copy(contactNumber = filtered, contactName = contactName, error = null)
        }
    }

    fun onMessageChanged(value: String) {
        _uiState.update { it.copy(message = value, error = null) }
    }

    fun onDateSelected(epochMs: Long) {
        _uiState.update { it.copy(dateEpochMs = epochMs, error = null) }
    }

    fun onRepeatTypeChanged(repeatType: RepeatType) {
        _uiState.update {
            it.copy(
                repeatType = repeatType,
                // A one-time (NONE) send has no stop condition; drop any previously picked stop date.
                stopAtEpochMs = if (repeatType == RepeatType.NONE) null else it.stopAtEpochMs,
            )
        }
    }

    fun onStopAtSelected(epochMs: Long?) {
        _uiState.update { it.copy(stopAtEpochMs = epochMs) }
    }

    private fun scheduleContactLookup(phoneNumber: String) {
        if (contactNameResolver == null) return
        contactLookupJob?.cancel()
        contactLookupJob =
            viewModelScope.launch {
                delay(RuleViewModel.LOOKUP_DEBOUNCE_MS)
                val name = contactNameResolver.lookupNameByNumber(phoneNumber)
                _uiState.update { it.copy(contactName = name) }
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

    fun saveScheduledSms(
        invalidContactError: String = "Contact must be a valid phone number",
        missingMessageError: String = "Message is required",
        missingDateError: String = "Date is required",
    ) {
        viewModelScope.launch {
            val current = uiState.value
            if (!RuleViewModel.isValidPhoneNumber(current.contactNumber.trim())) {
                _uiState.update { it.copy(error = invalidContactError) }
                return@launch
            }
            if (current.message.isBlank()) {
                _uiState.update { it.copy(error = missingMessageError) }
                return@launch
            }
            val date = current.dateEpochMs
            if (date == null || date <= 0) {
                _uiState.update { it.copy(error = missingDateError) }
                return@launch
            }

            val prefix = getDefaultPrefixNowUseCase()
            val contactValue = RuleViewModel.normalizeTargetNumber(current.contactNumber.trim(), prefix)

            val name =
                current.name.trim().ifBlank {
                    val count = scheduledSmsRepository.getScheduledSmsCount()
                    defaultScheduledSmsNameProvider(count + 1)
                }

            val scheduledSms =
                ScheduledSms(
                    id = current.id,
                    name = name,
                    category = current.category.trim(),
                    contactNumber = contactValue,
                    message = current.message,
                    dateEpochMs = date,
                    repeatType = current.repeatType,
                    stopAtEpochMs = if (current.repeatType == RepeatType.NONE) null else current.stopAtEpochMs,
                )
            val result =
                if (scheduledSms.id == 0L) {
                    addScheduledSmsUseCase(scheduledSms)
                } else {
                    updateScheduledSmsUseCase(scheduledSms)
                }
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

class ScheduleFormViewModelFactory(
    private val getScheduledSmsUseCase: GetScheduledSmsUseCase,
    private val addScheduledSmsUseCase: AddScheduledSmsUseCase,
    private val updateScheduledSmsUseCase: UpdateScheduledSmsUseCase,
    private val getScheduledSmsCategoriesUseCase: GetScheduledSmsCategoriesUseCase,
    private val scheduledSmsRepository: ScheduledSmsRepository,
    private val getDefaultPrefixNowUseCase: GetDefaultPrefixNowUseCase,
    private val contactNameResolver: ContactNameResolver? = null,
    private val defaultScheduledSmsNameProvider: (Int) -> String = { "Scheduled SMS #$it" },
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return ScheduleFormViewModel(
            getScheduledSmsUseCase = getScheduledSmsUseCase,
            addScheduledSmsUseCase = addScheduledSmsUseCase,
            updateScheduledSmsUseCase = updateScheduledSmsUseCase,
            getScheduledSmsCategoriesUseCase = getScheduledSmsCategoriesUseCase,
            scheduledSmsRepository = scheduledSmsRepository,
            getDefaultPrefixNowUseCase = getDefaultPrefixNowUseCase,
            contactNameResolver = contactNameResolver,
            defaultScheduledSmsNameProvider = defaultScheduledSmsNameProvider,
        ) as T
    }
}
