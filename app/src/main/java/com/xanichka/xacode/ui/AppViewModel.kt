package com.xanichka.xacode.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xanichka.xacode.data.AiClient
import com.xanichka.xacode.data.LocalStore
import com.xanichka.xacode.model.AppSettings
import com.xanichka.xacode.model.ChatMessage
import com.xanichka.xacode.model.Conversation
import com.xanichka.xacode.model.MessageRole
import com.xanichka.xacode.model.ModelProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppUiState(
    val conversations: List<Conversation> = emptyList(),
    val activeId: String? = null,
    val settings: AppSettings = AppSettings(),
    val isSending: Boolean = false,
    val testingProfileId: String? = null,
    val connectionResult: String? = null,
    val error: String? = null
) {
    val activeConversation: Conversation? get() = conversations.firstOrNull { it.id == activeId }
    val currentProfile: ModelProfile
        get() = settings.profiles.firstOrNull { it.id == activeConversation?.modelProfileId }
            ?: settings.activeProfile
}

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val store = LocalStore(application)
    private val client = AiClient()
    private val persistenceDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val initialSettings = store.loadSettings()
    private val _state = MutableStateFlow(
        AppUiState(
            conversations = store.loadConversations(initialSettings.activeProfileId),
            settings = initialSettings
        )
    )
    val state = _state.asStateFlow()

    fun selectConversation(id: String) = _state.update { it.copy(activeId = id) }

    fun newChat() = _state.update { it.copy(activeId = null, error = null) }

    fun selectProfile(id: String) {
        var settingsToSave: AppSettings? = null
        var conversationsToSave: List<Conversation>? = null
        _state.update { current ->
            if (current.settings.profiles.none { it.id == id } || current.isSending) return@update current
            val settings = current.settings.copy(activeProfileId = id)
            val conversations = current.conversations.map { conversation ->
                if (conversation.id == current.activeId) conversation.copy(modelProfileId = id) else conversation
            }
            settingsToSave = settings
            conversationsToSave = conversations
            current.copy(settings = settings, conversations = conversations)
        }
        viewModelScope.launch(persistenceDispatcher) {
            settingsToSave?.let(store::saveSettings)
            conversationsToSave?.let(store::saveConversations)
        }
    }

    fun saveSettings(settings: AppSettings) {
        if (settings.profiles.isEmpty()) return
        val normalizedActive = settings.activeProfileId.takeIf { id -> settings.profiles.any { it.id == id } }
            ?: settings.profiles.first().id
        val normalized = settings.copy(activeProfileId = normalizedActive)
        val removed = _state.value.settings.profiles.map { it.id }.toSet() - normalized.profiles.map { it.id }.toSet()
        var conversationsToSave: List<Conversation>? = null
        _state.update { current ->
            val conversations = current.conversations.map { conversation ->
                if (normalized.profiles.none { it.id == conversation.modelProfileId }) {
                    conversation.copy(modelProfileId = normalized.activeProfileId)
                } else conversation
            }
            conversationsToSave = conversations
            current.copy(settings = normalized, conversations = conversations, connectionResult = null, error = null)
        }
        viewModelScope.launch(persistenceDispatcher) {
            removed.forEach(store::deleteProfileSecret)
            store.saveSettings(normalized)
            conversationsToSave?.let(store::saveConversations)
        }
    }

    fun setWorkspaceUri(uri: String) = saveSettings(_state.value.settings.copy(workspaceUri = uri))

    fun testProfile(settings: AppSettings, profile: ModelProfile) {
        if (_state.value.testingProfileId != null) return
        _state.update { it.copy(testingProfileId = profile.id, connectionResult = null) }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { client.testConnection(settings, profile) }
            }.onSuccess {
                _state.update { state -> state.copy(testingProfileId = null, connectionResult = "Подключение работает") }
            }.onFailure { throwable ->
                _state.update { state -> state.copy(testingProfileId = null, connectionResult = throwable.message ?: "Ошибка подключения") }
            }
        }
    }

    fun clearConnectionResult() = _state.update { it.copy(connectionResult = null) }

    fun deleteConversation(id: String) {
        var conversationsToSave: List<Conversation>? = null
        _state.update { current ->
            val updated = current.conversations.filterNot { it.id == id }
            conversationsToSave = updated
            current.copy(conversations = updated, activeId = if (current.activeId == id) null else current.activeId)
        }
        viewModelScope.launch(persistenceDispatcher) { conversationsToSave?.let(store::saveConversations) }
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    fun send(text: String, context: String = "") {
        val prompt = text.trim()
        if (prompt.isEmpty() || _state.value.isSending) return
        val snapshot = _state.value
        val existing = snapshot.activeConversation
        val userMessage = ChatMessage(role = MessageRole.USER, text = prompt, context = context)
        val conversation = if (existing == null) {
            Conversation(
                title = prompt.replace('\n', ' ').take(42),
                modelProfileId = snapshot.settings.activeProfileId,
                messages = listOf(userMessage)
            )
        } else {
            existing.copy(messages = existing.messages + userMessage, updatedAt = System.currentTimeMillis())
        }
        val updated = listOf(conversation) + snapshot.conversations.filterNot { it.id == conversation.id }
        _state.update { it.copy(conversations = updated, activeId = conversation.id, isSending = true, error = null) }
        viewModelScope.launch(persistenceDispatcher) { store.saveConversations(updated) }

        viewModelScope.launch {
            val currentSettings = _state.value.settings
            val profile = currentSettings.profiles.firstOrNull { it.id == conversation.modelProfileId }
                ?: currentSettings.activeProfile
            runCatching {
                withContext(Dispatchers.IO) { client.complete(currentSettings, profile, conversation.messages) }
            }.onSuccess { answer -> appendAssistant(conversation.id, answer) }
                .onFailure { throwable ->
                    _state.update { it.copy(isSending = false, error = throwable.message ?: "Не удалось получить ответ") }
                }
        }
    }

    private fun appendAssistant(conversationId: String, answer: String) {
        var conversationsToSave: List<Conversation>? = null
        _state.update { current ->
            val updated = current.conversations.map { conversation ->
                if (conversation.id == conversationId) {
                    conversation.copy(
                        messages = conversation.messages + ChatMessage(role = MessageRole.ASSISTANT, text = answer),
                        updatedAt = System.currentTimeMillis()
                    )
                } else conversation
            }.sortedByDescending { it.updatedAt }
            conversationsToSave = updated
            current.copy(conversations = updated, isSending = false)
        }
        viewModelScope.launch(persistenceDispatcher) { conversationsToSave?.let(store::saveConversations) }
    }
}
