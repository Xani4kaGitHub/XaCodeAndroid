package com.xanichka.xacode.data

import com.xanichka.xacode.model.AppSettings
import com.xanichka.xacode.model.ChatMessage
import com.xanichka.xacode.model.MessageRole
import com.xanichka.xacode.model.ModelProfile
import com.xanichka.xacode.model.ProviderType
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class AiClient {
    fun complete(settings: AppSettings, profile: ModelProfile, messages: List<ChatMessage>, tools: AgentToolExecutor? = null): String {
        require(profile.baseUrl.startsWith("https://") || profile.baseUrl.startsWith("http://")) {
            "Укажите полный адрес API в настройках модели"
        }
        require(profile.model.isNotBlank() || profile.model == "-") { "Укажите модель в настройках" }
        val systemPrompt = buildString {
            append("Ты XaCode — самостоятельный AI-агент для разработки и обычных вопросов. ")
            append("Сам определи намерение пользователя: ответить, написать код, спроектировать приложение или создать бота. ")
            append("Не проси выбирать режим. Давай практичный результат на языке пользователя.")
            if (tools != null) {
                append("\nУ тебя есть инструменты файлов проекта. Сначала изучи структуру и нужные файлы, затем вноси изменения небольшими шагами. Не выдумывай содержимое файлов.")
                if (settings.autoVerifyChanges) append(" После записи перечитай важные изменённые файлы и проверь результат перед финальным ответом.")
            }
            if (settings.customInstructionsEnabled && settings.customInstructions.isNotBlank()) {
                append("\n\nПользовательские инструкции:\n")
                append(settings.customInstructions)
            }
        }
        return if (usesAnthropicFormat(profile)) {
            completeAnthropic(settings, profile, systemPrompt, messages, tools)
        } else {
            completeOpenAi(settings, profile, systemPrompt, messages, tools)
        }
    }

    fun testConnection(settings: AppSettings, profile: ModelProfile): String = complete(
        settings,
        profile,
        listOf(ChatMessage(role = MessageRole.USER, text = "Ответь одним словом: OK"))
    )

    private fun completeOpenAi(
        settings: AppSettings,
        profile: ModelProfile,
        systemPrompt: String,
        messages: List<ChatMessage>,
        tools: AgentToolExecutor?
    ): String {
        val bodyMessages = JSONArray().put(JSONObject().put("role", "system").put("content", systemPrompt))
        messages.forEach { message ->
            bodyMessages.put(JSONObject().apply {
                put("role", if (message.role == MessageRole.USER) "user" else "assistant")
                put("content", message.text + message.context.takeIf { it.isNotBlank() }?.let { "\n\nКонтекст из файлов:\n$it" }.orEmpty())
            })
        }
        val payload = JSONObject().put("messages", bodyMessages)
        if (tools != null) payload.put("tools", tools.definitions).put("tool_choice", "auto")
        if (profile.model != "-") payload.put("model", profile.model)
        if (settings.temperatureEnabled) payload.put("temperature", settings.temperature.toDouble())
        if (profile.provider == ProviderType.DEEPSEEK && profile.reasoningEffort != "disabled") {
            payload.put("thinking", JSONObject().put("type", "enabled").put("reasoning_effort", profile.reasoningEffort))
            payload.put("reasoning_effort", profile.reasoningEffort)
        }

        val headers = buildMap {
                put("Content-Type", "application/json")
                if (profile.apiKey.isNotBlank() && profile.apiKey != "-") put("Authorization", "Bearer ${profile.apiKey}")
                when (profile.provider) {
                    ProviderType.OPENROUTER -> {
                        put("HTTP-Referer", "https://github.com/Xani4kaGitHub/XaCodeAndroid")
                        put("X-Title", "XaCode Android")
                    }
                    ProviderType.AGENTROUTER -> {
                        put("User-Agent", "codex_cli_rs/0.101.0 (Android; XaCode)")
                        put("Originator", "codex_cli_rs")
                        put("Version", "0.101.0")
                    }
                    else -> Unit
                }
            }
        repeat(8) {
            val response = request(openAiEndpoint(profile.baseUrl), headers, payload)
            val message = JSONObject(response).getJSONArray("choices").getJSONObject(0).getJSONObject("message")
            val calls = message.optJSONArray("tool_calls")
            if (tools == null || calls == null || calls.length() == 0) return message.optString("content").trim()
            bodyMessages.put(message)
            for (index in 0 until calls.length()) {
                val call = calls.getJSONObject(index); val function = call.getJSONObject("function")
                bodyMessages.put(JSONObject().put("role", "tool").put("tool_call_id", call.getString("id")).put("content", tools.execute(function.getString("name"), function.optString("arguments", "{}"))))
            }
        }
        return "Остановлено после 8 шагов инструментов. Проверьте результат в файлах проекта."
    }

    private fun completeAnthropic(
        settings: AppSettings,
        profile: ModelProfile,
        systemPrompt: String,
        messages: List<ChatMessage>,
        tools: AgentToolExecutor?
    ): String {
        val bodyMessages = JSONArray()
        messages.forEach { message ->
            bodyMessages.put(JSONObject().apply {
                put("role", if (message.role == MessageRole.USER) "user" else "assistant")
                put("content", message.text + message.context.takeIf { it.isNotBlank() }?.let { "\n\nКонтекст из файлов:\n$it" }.orEmpty())
            })
        }
        val payload = JSONObject()
            .put("system", systemPrompt)
            .put("messages", bodyMessages)
            .put("max_tokens", profile.maxContextTokens.coerceIn(1_024, 16_384))
        if (tools != null) payload.put("tools", tools.anthropicDefinitions)
        if (profile.model != "-") payload.put("model", profile.model)
        if (settings.temperatureEnabled) payload.put("temperature", settings.temperature.toDouble())
        val headers = buildMap {
                put("Content-Type", "application/json")
                put("anthropic-version", "2023-06-01")
                if (profile.apiKey.isNotBlank() && profile.apiKey != "-") {
                    put("x-api-key", profile.apiKey)
                    put("Authorization", "Bearer ${profile.apiKey}")
                }
                if (profile.provider == ProviderType.AGENTROUTER) {
                    put("Originator", "codex_cli_rs")
                    put("Version", "0.101.0")
                }
            }
        repeat(8) {
            val response = request(anthropicEndpoint(profile.baseUrl), headers, payload)
            val content = JSONObject(response).optJSONArray("content") ?: JSONArray()
            val toolUses = (0 until content.length()).mapNotNull { index -> content.optJSONObject(index)?.takeIf { it.optString("type") == "tool_use" } }
            if (tools == null || toolUses.isEmpty()) return (0 until content.length()).mapNotNull { index -> content.optJSONObject(index)?.takeIf { it.optString("type") == "text" }?.optString("text") }.joinToString("").trim()
            bodyMessages.put(JSONObject().put("role", "assistant").put("content", content))
            val results = JSONArray()
            toolUses.forEach { use -> results.put(JSONObject().put("type", "tool_result").put("tool_use_id", use.getString("id")).put("content", tools.execute(use.getString("name"), use.optJSONObject("input")?.toString() ?: "{}"))) }
            bodyMessages.put(JSONObject().put("role", "user").put("content", results))
        }
        return "Остановлено после 8 шагов инструментов. Проверьте результат в файлах проекта."
    }

    private fun request(url: String, headers: Map<String, String>, payload: JSONObject): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 30_000
            connection.readTimeout = 90_000
            connection.doOutput = true
            headers.forEach(connection::setRequestProperty)
            connection.outputStream.bufferedWriter().use { it.write(payload.toString()) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error(extractError(response).ifBlank { "API вернул ошибку $code" })
            response
        } finally {
            connection.disconnect()
        }
    }

    private fun extractError(response: String): String = runCatching {
        val error = JSONObject(response).opt("error")
        when (error) {
            is JSONObject -> error.optString("message")
            is String -> error
            else -> ""
        }
    }.getOrDefault("")

    internal fun openAiEndpoint(baseUrl: String): String {
        val clean = baseUrl.trimEnd('/')
        return if (clean.endsWith("/chat/completions")) clean else "$clean/chat/completions"
    }

    private fun anthropicEndpoint(baseUrl: String): String {
        val clean = baseUrl.trimEnd('/')
        return if (clean.endsWith("/messages")) clean else "$clean/messages"
    }

    private fun usesAnthropicFormat(profile: ModelProfile): Boolean =
        profile.provider == ProviderType.ANTHROPIC ||
            (profile.provider == ProviderType.AGENTROUTER && profile.model.contains("claude", ignoreCase = true))
}
