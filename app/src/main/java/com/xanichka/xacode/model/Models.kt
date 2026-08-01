package com.xanichka.xacode.model

import androidx.compose.runtime.Immutable
import java.util.UUID

enum class ProviderType { DEEPSEEK, OPENAI, ANTHROPIC, GOOGLE, OPENROUTER, AGENTROUTER, OLLAMA, CUSTOM }

enum class UiLanguage(val label: String) {
    RUSSIAN("Русский"),
    UKRAINIAN("Українська"),
    ENGLISH("English")
}

@Immutable
data class ProviderPreset(
    val type: ProviderType,
    val label: String,
    val baseUrl: String,
    val defaultModel: String,
    val models: List<String>,
    val defaultContextTokens: Int,
    val anthropicFormat: Boolean = false,
    val apiKeyOptional: Boolean = false
)

val providerPresets = listOf(
    ProviderPreset(ProviderType.DEEPSEEK, "DeepSeek", "https://api.deepseek.com", "deepseek-v4-flash", listOf("deepseek-v4-flash", "deepseek-v4-pro"), 1_000_000),
    ProviderPreset(ProviderType.OPENAI, "OpenAI", "https://api.openai.com/v1", "gpt-5.1", listOf("gpt-5.2", "gpt-5.1", "gpt-5", "gpt-5-mini", "gpt-5-nano", "gpt-4.1", "gpt-4.1-mini", "o3", "o4-mini"), 400_000),
    ProviderPreset(ProviderType.ANTHROPIC, "Anthropic", "https://api.anthropic.com/v1/messages", "claude-sonnet-5", listOf("claude-fable-5", "claude-opus-5", "claude-sonnet-5", "claude-haiku-4-5"), 1_000_000, anthropicFormat = true),
    ProviderPreset(ProviderType.GOOGLE, "Google Gemini", "https://generativelanguage.googleapis.com/v1beta/openai/", "gemini-3.6-flash", listOf("gemini-3.6-flash", "gemini-3.5-flash", "gemini-3.5-flash-lite", "gemini-3.1-pro-preview", "gemini-2.5-pro", "gemini-2.5-flash"), 1_000_000),
    ProviderPreset(ProviderType.OPENROUTER, "OpenRouter", "https://openrouter.ai/api/v1", "deepseek/deepseek-v4-flash", listOf("deepseek/deepseek-v4-flash", "deepseek/deepseek-v4-pro", "openai/gpt-5.1", "anthropic/claude-sonnet-5", "google/gemini-3.6-flash"), 1_000_000),
    ProviderPreset(ProviderType.AGENTROUTER, "AgentRouter", "https://agentrouter.org/v1", "claude-3-5-sonnet-20241022", listOf("claude-3-5-sonnet-20241022", "gpt-4o", "deepseek-v3.2"), 200_000),
    ProviderPreset(ProviderType.OLLAMA, "Ollama", "http://127.0.0.1:11434/v1", "qwen3-coder", listOf("qwen3-coder", "llama3.3", "gemma3"), 32_000, apiKeyOptional = true),
    ProviderPreset(ProviderType.CUSTOM, "Свой API", "", "", emptyList(), 32_000, apiKeyOptional = true)
)

fun presetFor(type: ProviderType) = providerPresets.first { it.type == type }

/** Keeps saved profiles working when a provider retires an old public model id. */
fun currentModelId(provider: ProviderType, savedModel: String): String = when {
    provider == ProviderType.DEEPSEEK && savedModel in setOf("deepseek-chat", "deepseek-reasoner") -> "deepseek-v4-flash"
    savedModel.isBlank() && provider != ProviderType.CUSTOM -> presetFor(provider).defaultModel
    else -> savedModel
}

fun currentContextTokens(provider: ProviderType, savedModel: String, savedTokens: Int): Int =
    if (
        provider == ProviderType.DEEPSEEK &&
        savedModel in setOf("deepseek-chat", "deepseek-reasoner", "deepseek-v4-flash", "deepseek-v4-pro") &&
        savedTokens == 128_000
    ) 1_000_000 else savedTokens.coerceAtLeast(1_024)

@Immutable
data class ModelProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "DeepSeek",
    val provider: ProviderType = ProviderType.DEEPSEEK,
    val apiKey: String = "",
    val baseUrl: String = "https://api.deepseek.com",
    val model: String = "deepseek-v4-flash",
    val maxContextTokens: Int = 1_000_000,
    val showReasoning: Boolean = false,
    val reasoningEffort: String = "high"
)

@Immutable
data class ProjectWorkspace(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val treeUri: String,
    val managed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Immutable
data class AppSettings(
    val activeProfileId: String = "deepseek-default",
    val profiles: List<ModelProfile> = listOf(ModelProfile(id = "deepseek-default")),
    val customInstructionsEnabled: Boolean = false,
    val customInstructions: String = "",
    val temperatureEnabled: Boolean = false,
    val temperature: Float = 0.7f,
    val workspaceUri: String = "",
    val projectsRootUri: String = "",
    val projects: List<ProjectWorkspace> = emptyList(),
    val permissionOnboardingDone: Boolean = false,
    val backgroundOnboardingDone: Boolean = false,
    val agentFileToolsEnabled: Boolean = true,
    val destructiveToolsEnabled: Boolean = false,
    val networkDownloadsEnabled: Boolean = false,
    val pythonExecutionEnabled: Boolean = false,
    val termuxExecutionEnabled: Boolean = false,
    val autoVerifyChanges: Boolean = true,
    val showToolActivity: Boolean = true,
    val agentLimitsEnabled: Boolean = false,
    val agentMaxTokens: Int = 100_000,
    val agentMaxToolCalls: Int = 20,
    val agentMaxRounds: Int = 10,
    val agentMaxMinutes: Int = 8,
    val animationsEnabled: Boolean = true,
    val language: UiLanguage = UiLanguage.RUSSIAN
) {
    val activeProfile: ModelProfile
        get() = profiles.firstOrNull { it.id == activeProfileId } ?: profiles.first()
}

enum class MessageRole { USER, ASSISTANT }

enum class ToolTraceState { RUNNING, SUCCESS, ERROR }

@Immutable
data class ToolTrace(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val arguments: String = "",
    val result: String = "",
    val state: ToolTraceState = ToolTraceState.RUNNING,
    val elapsedMs: Long = 0L
)

@Immutable
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: MessageRole,
    val text: String,
    val context: String = "",
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val toolCalls: Int = 0,
    val elapsedMs: Long = 0,
    val toolTrace: List<ToolTrace> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)

@Immutable
data class Conversation(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "Новый чат",
    val modelProfileId: String = "deepseek-default",
    val projectId: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis()
)
