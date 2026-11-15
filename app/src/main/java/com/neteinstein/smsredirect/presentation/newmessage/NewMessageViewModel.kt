package com.neteinstein.smsredirect.presentation.newmessage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.neteinstein.smsredirect.domain.messaging.MessagingAddresses
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Recipient(
    val address: String,
    val name: String? = null,
)

data class NewMessageUiState(
    val recipients: List<Recipient> = emptyList(),
    val input: String = "",
    val invalidInput: Boolean = false,
) {
    /** Recipients plus whatever valid number is still in the field, so "Next" needs no extra tap. */
    val effectiveAddresses: List<String>
        get() = recipients.map { it.address } + listOfNotNull(input.trim().takeIf { isValidAddress(it) })

    val canProceed: Boolean get() = effectiveAddresses.isNotEmpty()
}

/** Picks who a new conversation is with; the conversation screen does the rest. */
class NewMessageViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(NewMessageUiState())
    val uiState: StateFlow<NewMessageUiState> = _uiState.asStateFlow()

    fun onInputChanged(text: String) {
        // A comma or semicolon commits what was typed, like in the stock app.
        if (text.endsWith(',') || text.endsWith(';')) {
            _uiState.value = _uiState.value.copy(input = text.dropLast(1))
            commitInput()
        } else {
            _uiState.value = _uiState.value.copy(input = text, invalidInput = false)
        }
    }

    /** Returns false (and flags the field) when the text is not a phone number or email. */
    fun commitInput(): Boolean {
        val text = _uiState.value.input.trim()
        if (text.isEmpty()) return true
        if (!isValidAddress(text)) {
            _uiState.value = _uiState.value.copy(invalidInput = true)
            return false
        }
        add(Recipient(text))
        _uiState.value = _uiState.value.copy(input = "", invalidInput = false)
        return true
    }

    fun onContactPicked(
        number: String,
        name: String?,
    ) {
        if (isValidAddress(number)) add(Recipient(number.trim(), name?.takeIf { it.isNotBlank() }))
    }

    fun onRecipientRemoved(recipient: Recipient) {
        _uiState.value = _uiState.value.copy(recipients = _uiState.value.recipients - recipient)
    }

    private fun add(recipient: Recipient) {
        val current = _uiState.value.recipients
        if (current.any { MessagingAddresses.same(it.address, recipient.address) }) return
        _uiState.value = _uiState.value.copy(recipients = current + recipient)
    }
}

/** A dialable number (digits, optional leading +, common separators) or an email address. */
internal fun isValidAddress(text: String): Boolean {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return false
    if ('@' in trimmed) return Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(trimmed)
    if (!Regex("^\\+?[0-9 ()\\-.]+$").matches(trimmed)) return false
    return trimmed.count { it.isDigit() } >= 3
}

class NewMessageViewModelFactory : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = NewMessageViewModel() as T
}
