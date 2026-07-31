package com.xanichka.xacode.data

import android.content.Context
import com.xanichka.xacode.model.AppSettings
import com.xanichka.xacode.model.ChatMessage
import com.xanichka.xacode.model.Conversation
import com.xanichka.xacode.model.MessageRole
import com.xanichka.xacode.model.ModelProfile
import com.xanichka.xacode.model.ProviderType
import com.xanichka.xacode.model.ProjectWorkspace
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
                ModelProfile(
                    id = id,
                    name = item.optString("name", "Модель"),
                    provider = runCatching { ProviderType.valueOf(item.optString("provider")) }
                        .getOrDefault(ProviderType.CUSTOM),
                    apiKey = secrets.readApiKey(id),
                    baseUrl = item.optString("baseUrl"),
                    model = item.optString("model"),
                    maxContextTokens = item.optInt("maxContextTokens", 32_000).coerceAtLeast(1_024),
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
                    model = model ?: "deepseek-chat"
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
            agentFileToolsEnabled = preferences.getBoolean("agentFileToolsEnabled", true),
            autoVerifyChanges = preferences.getBoolean("autoVerifyChanges", true),
            animationsEnabled = preferences.getBoolean("animationsEnabled", true)
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
            .putBoolean("agentFileToolsEnabled", value.agentFileToolsEnabled)
            .putBoolean("autoVerifyChanges", value.autoVerifyChanges)
            .putBoolean("animationsEnabled", value.animationsEnabled)
            .remove("endpoint")
            .remove("model")
            .remove("apiKey")
            .apply()
    }

    fun deleteProfileSecret(profileId: String) = secrets.deleteApiKey(profileId)

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
