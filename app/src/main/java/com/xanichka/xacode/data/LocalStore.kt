package com.xanichka.xacode.data

import android.content.Context
import com.xanichka.xacode.model.AppSettings
import com.xanichka.xacode.model.ChatMessage
import com.xanichka.xacode.model.Conversation
import com.xanichka.xacode.model.MessageRole
import com.xanichka.xacode.model.ModelProfile
import com.xanichka.xacode.model.ProviderType
import com.xanichka.xacode.model.ProjectWorkspace
import com.xanichka.xacode.model.UiLanguage
import com.xanichka.xacode.model.ToolTrace
import com.xanichka.xacode.model.ToolTraceState
import com.xanichka.xacode.model.currentContextTokens
import com.xanichka.xacode.model.currentModelId
import com.xanichka.xacode.model.presetFor
import org.json.JSONArray
import org.json.JSONObject

class LocalStore(context: Context) {
    private val preferences = context.getSharedPreferences("xacode", Context.MODE_PRIVATE)
    private val secrets = SecretStore(context)

    fun loadSettings(): AppSettings {
        val profiles = runCatching {
            val raw = preferences.getString("modelProfiles", null)
            if (raw.isNullOrBlank()) return@runCatching emptyList()
            val array = JSONArray(raw)
            (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                val id = item.getString("id")
                val provider = runCatching { ProviderType.valueOf(item.optString("provider")) }
                    .getOrDefault(ProviderType.CUSTOM)
                val savedModel = item.optString("model")
                val savedContext = item.optInt("maxContextTokens", presetFor(provider).defaultContextTokens)
                ModelProfile(
                    id = id,
                    name = item.optString("name", "Модель"),
                    provider = provider,
                    apiKey = secrets.readApiKey(id),
                    baseUrl = item.optString("baseUrl"),
                    model = currentModelId(provider, savedModel),
                    maxContextTokens = currentContextTokens(provider, savedModel, savedContext),
                    showReasoning = item.optBoolean("showReasoning", false),
                    reasoningEffort = item.optString("reasoningEffort", "high")
                )
            }
        }.getOrDefault(emptyList()).ifEmpty {
            val endpoint = preferences.getString("endpoint", null)
            val model = preferences.getString("model", null)
            listOf(
                ModelProfile(
                    id = "deepseek-default",
                    name = if (endpoint != null) "Основная модель" else "DeepSeek",
                    provider = if (endpoint != null) ProviderType.CUSTOM else ProviderType.DEEPSEEK,
                    apiKey = secrets.readApiKey("deepseek-default"),
                    baseUrl = endpoint ?: "https://api.deepseek.com",
                    model = currentModelId(ProviderType.DEEPSEEK, model ?: "deepseek-v4-flash"),
                    maxContextTokens = 1_000_000
                )
            )
        }
        val requestedActive = preferences.getString("activeProfileId", profiles.first().id).orEmpty()
        val activeId = requestedActive.takeIf { id -> profiles.any { it.id == id } } ?: profiles.first().id
        val legacyWorkspace = preferences.getString("workspaceUri", "").orEmpty()
        val projects = runCatching {
            val array = JSONArray(preferences.getString("projects", "[]"))
            (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                ProjectWorkspace(
                    id = item.getString("id"),
                    name = item.optString("name", "Проект"),
                    treeUri = item.getString("treeUri"),
                    managed = item.optBoolean("managed", false),
                    createdAt = item.optLong("createdAt", System.currentTimeMillis())
                )
            }
        }.getOrDefault(emptyList()).ifEmpty {
            if (legacyWorkspace.isBlank()) emptyList()
            else listOf(ProjectWorkspace(name = "Рабочая папка", treeUri = legacyWorkspace))
        }
        return AppSettings(
            activeProfileId = activeId,
            profiles = profiles,
            customInstructionsEnabled = preferences.getBoolean("customInstructionsEnabled", false),
            customInstructions = preferences.getString("customInstructions", "").orEmpty(),
            temperatureEnabled = preferences.getBoolean("temperatureEnabled", false),
            temperature = preferences.getFloat("temperature", 0.7f).coerceIn(0f, 2f),
            workspaceUri = legacyWorkspace,
            projectsRootUri = preferences.getString("projectsRootUri", "").orEmpty().ifBlank {
                projects.firstOrNull()?.treeUri.orEmpty()
            },
            projects = projects,
            permissionOnboardingDone = preferences.getBoolean("permissionOnboardingDone", false),
            backgroundOnboardingDone = preferences.getBoolean("backgroundOnboardingDone", false),
            agentFileToolsEnabled = preferences.getBoolean("agentFileToolsEnabled", true),
            destructiveToolsEnabled = preferences.getBoolean("destructiveToolsEnabled", false),
            networkDownloadsEnabled = preferences.getBoolean("networkDownloadsEnabled", false),
            pythonExecutionEnabled = preferences.getBoolean("pythonExecutionEnabled", false),
            termuxExecutionEnabled = preferences.getBoolean("termuxExecutionEnabled", false),
            autoVerifyChanges = preferences.getBoolean("autoVerifyChanges", true),
            showToolActivity = preferences.getBoolean("showToolActivity", true),
            agentLimitsEnabled = preferences.getBoolean("agentLimitsEnabled", false),
            agentMaxTokens = preferences.getInt("agentMaxTokens", 100_000).coerceIn(1_000, 2_000_000),
            agentMaxToolCalls = preferences.getInt("agentMaxToolCalls", 20).coerceIn(1, 1_000),
            agentMaxRounds = preferences.getInt("agentMaxRounds", 10).coerceIn(1, 1_000),
            agentMaxMinutes = preferences.getInt("agentMaxMinutes", 8).coerceIn(1, 1_440),
            animationsEnabled = preferences.getBoolean("animationsEnabled", true),
            language = runCatching {
                UiLanguage.valueOf(preferences.getString("language", UiLanguage.RUSSIAN.name).orEmpty())
            }.getOrDefault(UiLanguage.RUSSIAN)
        )
    }

    fun saveSettings(value: AppSettings) {
        val profilesJson = JSONArray()
        value.profiles.forEach { profile ->
            secrets.writeApiKey(profile.id, profile.apiKey.trim())
            profilesJson.put(JSONObject().apply {
                put("id", profile.id)
                put("name", profile.name.trim())
                put("provider", profile.provider.name)
                put("baseUrl", profile.baseUrl.trim())
                put("model", profile.model.trim())
                put("maxContextTokens", profile.maxContextTokens)
                put("showReasoning", profile.showReasoning)
                put("reasoningEffort", profile.reasoningEffort)
            })
        }
        val projectsJson = JSONArray()
        value.projects.forEach { project ->
            projectsJson.put(JSONObject().apply {
                put("id", project.id)
                put("name", project.name)
                put("treeUri", project.treeUri)
                put("managed", project.managed)
                put("createdAt", project.createdAt)
            })
        }
        preferences.edit()
            .putString("activeProfileId", value.activeProfileId)
            .putString("modelProfiles", profilesJson.toString())
            .putBoolean("customInstructionsEnabled", value.customInstructionsEnabled)
            .putString("customInstructions", value.customInstructions.trim())
            .putBoolean("temperatureEnabled", value.temperatureEnabled)
            .putFloat("temperature", value.temperature.coerceIn(0f, 2f))
            .putString("workspaceUri", value.workspaceUri)
            .putString("projectsRootUri", value.projectsRootUri)
            .putString("projects", projectsJson.toString())
            .putBoolean("permissionOnboardingDone", value.permissionOnboardingDone)
            .putBoolean("backgroundOnboardingDone", value.backgroundOnboardingDone)
            .putBoolean("agentFileToolsEnabled", value.agentFileToolsEnabled)
            .putBoolean("destructiveToolsEnabled", value.destructiveToolsEnabled)
            .putBoolean("networkDownloadsEnabled", value.networkDownloadsEnabled)
            .putBoolean("pythonExecutionEnabled", value.pythonExecutionEnabled)
            .putBoolean("termuxExecutionEnabled", value.termuxExecutionEnabled)
            .putBoolean("autoVerifyChanges", value.autoVerifyChanges)
            .putBoolean("showToolActivity", value.showToolActivity)
            .putBoolean("agentLimitsEnabled", value.agentLimitsEnabled)
            .putInt("agentMaxTokens", value.agentMaxTokens.coerceIn(1_000, 2_000_000))
            .putInt("agentMaxToolCalls", value.agentMaxToolCalls.coerceIn(1, 1_000))
            .putInt("agentMaxRounds", value.agentMaxRounds.coerceIn(1, 1_000))
            .putInt("agentMaxMinutes", value.agentMaxMinutes.coerceIn(1, 1_440))
            .putBoolean("animationsEnabled", value.animationsEnabled)
            .putString("language", value.language.name)
            .remove("endpoint")
            .remove("model")
            .remove("apiKey")
            .apply()
    }

    fun deleteProfileSecret(profileId: String) = secrets.deleteApiKey(profileId)

    fun loadActiveConversationId(): String? = preferences.getString("lastConversationId", null)
    fun loadActiveProjectId(): String? = preferences.getString("lastProjectId", null)
    fun saveActiveSelection(conversationId: String?, projectId: String?) {
        preferences.edit().putString("lastConversationId", conversationId).putString("lastProjectId", projectId).apply()
    }

    fun loadDrafts(): Map<String, String> = runCatching {
        val json = JSONObject(preferences.getString("chatDrafts", "{}"))
        json.keys().asSequence().associateWith { key -> json.optString(key) }.filterValues { it.isNotBlank() }
    }.getOrDefault(emptyMap())

    fun saveDraft(key: String, text: String) {
        val json = runCatching { JSONObject(preferences.getString("chatDrafts", "{}")) }.getOrElse { JSONObject() }
        if (text.isBlank()) json.remove(key) else json.put(key, text)
        preferences.edit().putString("chatDrafts", json.toString()).apply()
    }

    fun loadConversations(defaultProfileId: String): List<Conversation> = runCatching {
        val array = JSONArray(preferences.getString("conversations", "[]"))
        (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            val messagesJson = item.optJSONArray("messages") ?: JSONArray()
            val messages = (0 until messagesJson.length()).map { messageIndex ->
                val message = messagesJson.getJSONObject(messageIndex)
                ChatMessage(
                    id = message.getString("id"),
                    role = MessageRole.valueOf(message.getString("role")),
                    text = message.getString("text"),
                    context = message.optString("context", ""),
                    inputTokens = message.optInt("inputTokens", 0),
                    outputTokens = message.optInt("outputTokens", 0),
                    toolCalls = message.optInt("toolCalls", 0),
                    elapsedMs = message.optLong("elapsedMs", 0L),
                    toolTrace = (message.optJSONArray("toolTrace") ?: JSONArray()).let { traces ->
                        (0 until traces.length()).map { traceIndex ->
                            val trace = traces.getJSONObject(traceIndex)
                            ToolTrace(
                                id = trace.optString("id"),
                                name = trace.optString("name"),
                                arguments = trace.optString("arguments"),
                                result = trace.optString("result"),
                                state = runCatching { ToolTraceState.valueOf(trace.optString("state")) }.getOrDefault(ToolTraceState.ERROR),
                                elapsedMs = trace.optLong("elapsedMs")
                            )
                        }
                    },
                    createdAt = message.optLong("createdAt", 0L)
                )
            }
            Conversation(
                id = item.getString("id"),
                title = item.optString("title", "Новый чат"),
                modelProfileId = item.optString("modelProfileId", defaultProfileId),
                projectId = item.optString("projectId", "").takeIf { it.isNotBlank() },
                messages = messages,
                updatedAt = item.optLong("updatedAt", 0L)
            )
        }.sortedByDescending { it.updatedAt }
    }.getOrDefault(emptyList())

    fun saveConversations(items: List<Conversation>) {
        val array = JSONArray()
        items.filter { it.messages.isNotEmpty() }.take(100).forEach { conversation ->
            val messages = JSONArray()
            conversation.messages.forEach { message ->
                messages.put(JSONObject().apply {
                    put("id", message.id)
                    put("role", message.role.name)
                    put("text", message.text)
                    put("context", message.context)
                    put("inputTokens", message.inputTokens)
                    put("outputTokens", message.outputTokens)
                    put("toolCalls", message.toolCalls)
                    put("elapsedMs", message.elapsedMs)
                    put("toolTrace", JSONArray().apply {
                        message.toolTrace.forEach { trace -> put(JSONObject().apply {
                            put("id", trace.id)
                            put("name", trace.name)
                            put("arguments", trace.arguments)
                            put("result", trace.result)
                            put("state", trace.state.name)
                            put("elapsedMs", trace.elapsedMs)
                        }) }
                    })
                    put("createdAt", message.createdAt)
                })
            }
            array.put(JSONObject().apply {
                put("id", conversation.id)
                put("title", conversation.title)
                put("modelProfileId", conversation.modelProfileId)
                put("projectId", conversation.projectId ?: "")
                put("updatedAt", conversation.updatedAt)
                put("messages", messages)
            })
        }
        preferences.edit().putString("conversations", array.toString()).apply()
    }
}
