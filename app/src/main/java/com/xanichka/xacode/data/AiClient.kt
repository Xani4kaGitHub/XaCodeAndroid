package com.xanichka.xacode.data

import com.xanichka.xacode.model.ChatMessage
import com.xanichka.xacode.model.CreationMode
import com.xanichka.xacode.model.MessageRole
import com.xanichka.xacode.model.ProviderSettings
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class AiClient {
    fun complete(
        settings: ProviderSettings,
        mode: CreationMode,
        messages: List<ChatMessage>
    ): String {
        require(settings.endpoint.startsWith("https://") || settings.endpoint.startsWith("http://")) {
            "Укажите полный адрес API в настройках"
        }
        require(settings.model.isNotBlank()) { "Укажите модель в настройках" }

        val bodyMessages = JSONArray().put(
            JSONObject().put("role", "system").put("content", mode.systemPrompt())
        )
        messages.forEach { message ->
            bodyMessages.put(JSONObject().apply {
                put("role", if (message.role == MessageRole.USER) "user" else "assistant")
                put("content", message.text)
            })
        }
        val payload = JSONObject()
            .put("model", settings.model)
            .put("messages", bodyMessages)
            .put("temperature", 0.7)

        val connection = URL(settings.endpoint).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 30_000
            connection.readTimeout = 90_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            if (settings.apiKey.isNotBlank()) {
                connection.setRequestProperty("Authorization", "Bearer ${settings.apiKey}")
            }
            connection.outputStream.bufferedWriter().use { it.write(payload.toString()) }

            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val message = runCatching {
                    JSONObject(response).getJSONObject("error").optString("message")
                }.getOrNull().orEmpty()
                error(message.ifBlank { "API вернул ошибку $code" })
            }
            val json = JSONObject(response)
            json.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
                .trim()
        } finally {
            connection.disconnect()
        }
    }
}

