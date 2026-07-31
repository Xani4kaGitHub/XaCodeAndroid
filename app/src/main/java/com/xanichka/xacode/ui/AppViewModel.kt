package com.xanichka.xacode.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xanichka.xacode.data.AiClient
import com.xanichka.xacode.data.LocalStore
import com.xanichka.xacode.model.ChatMessage
import com.xanichka.xacode.model.Conversation
import com.xanichka.xacode.model.CreationMode
import com.xanichka.xacode.model.MessageRole
import com.xanichka.xacode.model.ProviderSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppUiState(
    val conversations: List<Conversation> = emptyList(),
    val activeId: String? = null,
    val selectedMode: CreationMode = CreationMode.CHAT,
    val settings: ProviderSettings = ProviderSettings(),
    val isSending: Boolean = false,
    val error: String? = null
) {
    val activeConversation: Conversation?
        get() = conversations.firstOrNull { it.id == activeId }
}

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val store = LocalStore(application)
    private val client = AiClient()
    private val _state = MutableStateFlow(
        AppUiState(
            conversations = store.loadConversations(),
            settings = store.loadSettings()
        )
    )
    val state = _state.asStateFlow()

    fun selectConversation(id: String) = _state.update { current ->
        val conversation = current.conversations.firstOrNull { it.id == id }
        current.copy(activeId = id, selectedMode = conversation?.mode ?: current.selectedMode)
    }

    fun newChat(mode: CreationMode = _state.value.selectedMode) {
        _state.update { it.copy(activeId = null, selectedMode = mode, error = null) }
    }

    fun selectMode(mode: CreationMode) = _state.update {
        if (it.activeConversation == null) it.copy(selectedMode = mode) else it
    }

    fun saveSettings(settings: ProviderSettings) {
        store.saveSettings(settings)
        _state.update { it.copy(settings = settings, error = null) }
    }

    fun deleteConversation(id: String) {
        _state.update { current ->
            val updated = current.conversations.filterNot { it.id == id }
            store.saveConversations(updated)
            current.copy(
                conversations = updated,
                activeId = if (current.activeId == id) null else current.activeId
            )
        }
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    fun send(text: String) {
        val prompt = text.trim()
        if (prompt.isEmpty() || _state.value.isSending) return

        val snapshot = _state.value
        val existing = snapshot.activeConversation
        val userMessage = ChatMessage(role = MessageRole.USER, text = prompt)
        val conversation = if (existing == null) {
            Conversation(
                title = prompt.replace('\n', ' ').take(42),
                mode = snapshot.selectedMode,
                messages = listOf(userMessage)
            )
        } else {
            existing.copy(
                messages = existing.messages + userMessage,
                updatedAt = System.currentTimeMillis()
            )
        }
        val updated = listOf(conversation) + snapshot.conversations.filterNot { it.id == conversation.id }
        store.saveConversations(updated)
        _state.update {
            it.copy(conversations = updated, activeId = conversation.id, isSending = true, error = null)
        }

        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    client.complete(_state.value.settings, conversation.mode, conversation.messages)
                }
            }.onSuccess { answer ->
                appendAssistant(conversation.id, answer)
            }.onFailure { throwable ->
                _state.update { it.copy(isSending = false, error = throwable.message ?: "Не удалось получить ответ") }
            }
        }
    }

    private fun appendAssistant(conversationId: String, answer: String) {
        _state.update { current ->
            val updated = current.conversations.map { conversation ->
                if (conversation.id == conversationId) {
                    conversation.copy(
                        messages = conversation.messages + ChatMessage(
                            role = MessageRole.ASSISTANT,
                            text = answer
                        ),
                        updatedAt = System.currentTimeMillis()
                    )
                } else conversation
            }.sortedByDescending { it.updatedAt }
            store.saveConversations(updated)
            current.copy(conversations = updated, isSending = false)
        }
    }
}

