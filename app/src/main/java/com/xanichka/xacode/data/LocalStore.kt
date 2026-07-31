package com.xanichka.xacode.data

import android.content.Context
import com.xanichka.xacode.model.ChatMessage
import com.xanichka.xacode.model.Conversation
import com.xanichka.xacode.model.CreationMode
import com.xanichka.xacode.model.MessageRole
import com.xanichka.xacode.model.ProviderSettings
import org.json.JSONArray
import org.json.JSONObject

class LocalStore(context: Context) {
    private val preferences = context.getSharedPreferences("xacode", Context.MODE_PRIVATE)
    private val secrets = SecretStore(context)

    fun loadSettings() = ProviderSettings(
        endpoint = preferences.getString("endpoint", null)
            ?: "https://api.openai.com/v1/chat/completions",
        apiKey = secrets.readApiKey(),
        model = preferences.getString("model", "gpt-4.1-mini").orEmpty()
    )

    fun saveSettings(value: ProviderSettings) {
        secrets.writeApiKey(value.apiKey.trim())
        preferences.edit()
            .putString("endpoint", value.endpoint.trim())
            .putString("model", value.model.trim())
            .remove("apiKey")
            .apply()
    }

    fun loadConversations(): List<Conversation> = runCatching {
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
                    createdAt = message.optLong("createdAt", 0L)
                )
            }
            Conversation(
                id = item.getString("id"),
                title = item.optString("title", "Новый чат"),
                mode = runCatching { CreationMode.valueOf(item.optString("mode")) }
                    .getOrDefault(CreationMode.CHAT),
                messages = messages,
                updatedAt = item.optLong("updatedAt", 0L)
            )
        }.sortedByDescending { it.updatedAt }
    }.getOrDefault(emptyList())

    fun saveConversations(items: List<Conversation>) {
        val array = JSONArray()
        items.forEach { conversation ->
            val messages = JSONArray()
            conversation.messages.forEach { message ->
                messages.put(JSONObject().apply {
                    put("id", message.id)
                    put("role", message.role.name)
                    put("text", message.text)
                    put("createdAt", message.createdAt)
                })
            }
            array.put(JSONObject().apply {
                put("id", conversation.id)
                put("title", conversation.title)
                put("mode", conversation.mode.name)
                put("updatedAt", conversation.updatedAt)
                put("messages", messages)
            })
        }
        preferences.edit().putString("conversations", array.toString()).apply()
    }
}
