package com.xanichka.xacode.model

import java.util.UUID

enum class ProviderType { DEEPSEEK, OPENAI, ANTHROPIC, GOOGLE, OPENROUTER, AGENTROUTER, OLLAMA, CUSTOM }

data class ProviderPreset(
    val type: ProviderType,
    val label: String,
    val baseUrl: String,
    val defaultModel: String,
    val models: List<String>,
    val anthropicFormat: Boolean = false,
    val apiKeyOptional: Boolean = false
)

val providerPresets = listOf(
    ProviderPreset(ProviderType.DEEPSEEK, "DeepSeek", "https://api.deepseek.com", "deepseek-chat", listOf("deepseek-chat", "deepseek-reasoner")),
    ProviderPreset(ProviderType.OPENAI, "OpenAI", "https://api.openai.com/v1", "gpt-4.1", listOf("gpt-4.1", "gpt-4.1-mini", "o3")),
    ProviderPreset(ProviderType.ANTHROPIC, "Anthropic", "https://api.anthropic.com/v1/messages", "claude-sonnet-4-5", listOf("claude-sonnet-4-5", "claude-opus-4-1"), anthropicFormat = true),
    ProviderPreset(ProviderType.GOOGLE, "Google Gemini", "https://generativelanguage.googleapis.com/v1beta/openai/", "gemini-2.5-pro", listOf("gemini-2.5-pro", "gemini-2.5-flash")),
    ProviderPreset(ProviderType.OPENROUTER, "OpenRouter", "https://openrouter.ai/api/v1", "openai/gpt-4.1", listOf("openai/gpt-4.1", "anthropic/claude-sonnet-4", "google/gemini-2.5-pro")),
    ProviderPreset(ProviderType.AGENTROUTER, "AgentRouter", "https://agentrouter.org/v1", "claude-3-5-sonnet-20241022", listOf("claude-3-5-sonnet-20241022", "gpt-4o", "deepseek-v3.2")),
    ProviderPreset(ProviderType.OLLAMA, "Ollama", "http://127.0.0.1:11434/v1", "qwen3-coder", listOf("qwen3-coder", "llama3.3", "gemma3"), apiKeyOptional = true),
    ProviderPreset(ProviderType.CUSTOM, "Свой API", "", "", emptyList(), apiKeyOptional = true)
)

fun presetFor(type: ProviderType) = providerPresets.first { it.type == type }

data class ModelProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "DeepSeek",
    val provider: ProviderType = ProviderType.DEEPSEEK,
    val apiKey: String = "",
    val baseUrl: String = "https://api.deepseek.com",
    val model: String = "deepseek-chat",
    val maxContextTokens: Int = 32_000,
    val showReasoning: Boolean = false,
    val reasoningEffort: String = "high"
)

data class AppSettings(
    val activeProfileId: String = "deepseek-default",
    val profiles: List<ModelProfile> = listOf(ModelProfile(id = "deepseek-default")),
    val customInstructionsEnabled: Boolean = false,
    val customInstructions: String = "",
    val temperatureEnabled: Boolean = false,
    val temperature: Float = 0.7f
) {
    val activeProfile: ModelProfile
        get() = profiles.firstOrNull { it.id == activeProfileId } ?: profiles.first()
}

enum class MessageRole { USER, ASSISTANT }

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: MessageRole,
    val text: String,
    val createdAt: Long = System.currentTimeMillis()
)

data class Conversation(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "Новый чат",
    val modelProfileId: String = "deepseek-default",
    val messages: List<ChatMessage> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis()
)

