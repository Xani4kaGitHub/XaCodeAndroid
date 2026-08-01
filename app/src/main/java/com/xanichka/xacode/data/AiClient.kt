package com.xanichka.xacode.data

import com.xanichka.xacode.model.AppSettings
import com.xanichka.xacode.model.ChatMessage
import com.xanichka.xacode.model.MessageRole
import com.xanichka.xacode.model.ModelProfile
import com.xanichka.xacode.model.ProviderType
import com.xanichka.xacode.model.presetFor
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

data class AiResult(val text: String, val inputTokens: Int = 0, val outputTokens: Int = 0, val toolCalls: Int = 0, val elapsedMs: Long = 0)
data class AgentProgress(val inputTokens: Int = 0, val outputTokens: Int = 0, val toolCalls: Int = 0, val elapsedMs: Long = 0, val currentTool: String = "")
internal data class DsmlToolCall(val name: String, val arguments: String)

class AiClient {
    suspend fun complete(settings: AppSettings, profile: ModelProfile, messages: List<ChatMessage>, tools: AgentToolExecutor? = null, onProgress: (AgentProgress) -> Unit = {}): AiResult {
        NetworkSecurity.apiUrl(profile.baseUrl)
        require(profile.model.isNotBlank() || profile.model == "-") { "Укажите модель в настройках" }
        require(presetFor(profile.provider).apiKeyOptional || profile.apiKey.isNotBlank()) {
            "API-ключ не найден. Откройте настройки модели, добавьте ключ и нажмите «Проверить»."
        }
        val systemPrompt = buildString {
            append("Ты XaCode — самостоятельный AI-агент для разработки и обычных вопросов. ")
            append("Сам определи намерение пользователя: ответить, написать код, спроектировать приложение или создать бота. ")
            append("Ты работаешь внутри нативного Android-приложения XaCode, а не на Windows или обычном ПК. Не упоминай PowerShell и пути C:\\, если пользователь сам их не дал. ")
            append("Не проси выбирать режим. Давай практичный результат на языке пользователя.")
            if (tools != null) {
                append("\nТекущий чат привязан к папке проекта. У тебя реально есть Android-инструменты: ${tools.toolNames}. ")
                append("Если пользователь просит создать или изменить проект, ОБЯЗАТЕЛЬНО вызывай инструменты, а не просто печатай код в ответе. Сначала изучи структуру и нужные файлы, затем вноси изменения небольшими шагами. Не выдумывай содержимое файлов.")
                if (settings.termuxExecutionEnabled) append(" Для запуска Node.js, npm, git, компиляторов и тестов используй Termux-инструменты. pkg и apt не заблокированы XaCode. Сначала используй inspect_runtime; при CANNOT LINK EXECUTABLE или ошибке символа OpenSSL используй repair_node_runtime, затем повтори проверку.")
                if (settings.termuxExecutionEnabled) append(" Если git_status вернул NOT_A_GIT_REPOSITORY и контроль версий нужен для задачи, используй git_init. Если run_project_checks сообщил NO_PROJECT_MANIFEST, сначала создай подходящий manifest файлами проекта, а не объявляй инструменты сломанными.")
                append(" Не печатай служебную разметку DSML пользователю. Не повторяй подряд инструмент с теми же аргументами; учитывай уже полученный результат и используй следующий нужный шаг.")
                if (settings.autoVerifyChanges) append(" После записи перечитай важные изменённые файлы и проверь результат перед финальным ответом.")
            } else {
                append("\nЭтот чат не привязан к проекту, поэтому файловых инструментов здесь нет. Чтобы работать с файлами, предложи пользователю открыть или создать проект в боковом меню.")
            }
            if (settings.customInstructionsEnabled && settings.customInstructions.isNotBlank()) {
                append("\n\nПользовательские инструкции:\n")
                append(settings.customInstructions)
            }
        }
        return if (usesAnthropicFormat(profile)) {
            completeAnthropic(settings, profile, systemPrompt, messages, tools, onProgress)
        } else {
            completeOpenAi(settings, profile, systemPrompt, messages, tools, onProgress)
        }
    }

    suspend fun testConnection(settings: AppSettings, profile: ModelProfile): String = complete(
        settings,
        profile,
        listOf(ChatMessage(role = MessageRole.USER, text = "Ответь одним словом: OK"))
    ).text

    private suspend fun completeOpenAi(
        settings: AppSettings,
        profile: ModelProfile,
        systemPrompt: String,
        messages: List<ChatMessage>,
        tools: AgentToolExecutor?,
        onProgress: (AgentProgress) -> Unit
    ): AiResult {
        val startedAt = System.currentTimeMillis()
        var totalInputTokens = 0
        var totalOutputTokens = 0
        var totalToolCalls = 0
        var previousToolSignature = ""
        val bodyMessages = JSONArray().put(JSONObject().put("role", "system").put("content", systemPrompt))
        messages.forEach { message ->
            bodyMessages.put(JSONObject().apply {
                put("role", if (message.role == MessageRole.USER) "user" else "assistant")
                put("content", message.text + message.context.takeIf { it.isNotBlank() }?.let { "\n\nКонтекст из файлов:\n$it" }.orEmpty())
            })
        }
        val payload = JSONObject().put("messages", bodyMessages)
        if (tools != null) {
            payload.put("tools", tools.definitions)
            // DeepSeek V4 thinking mode supports tools but rejects tool_choice.
            if (profile.provider != ProviderType.DEEPSEEK) payload.put("tool_choice", "auto")
        }
        if (profile.model != "-") payload.put("model", profile.model)
        if (settings.temperatureEnabled) payload.put("temperature", settings.temperature.toDouble())
        if (profile.provider == ProviderType.DEEPSEEK) {
            val thinkingEnabled = profile.reasoningEffort != "disabled"
            payload.put("thinking", JSONObject().put("type", if (thinkingEnabled) "enabled" else "disabled"))
            if (thinkingEnabled) {
                payload.put("reasoning_effort", if (profile.reasoningEffort in setOf("max", "xhigh")) "max" else "high")
            }
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
        repeat(MAX_TOOL_ROUNDS) {
            currentCoroutineContext().ensureActive()
            val response = request(openAiEndpoint(profile.baseUrl), headers, payload)
            val root = JSONObject(response)
            val usage = root.optJSONObject("usage")
            totalInputTokens += usage?.optInt("prompt_tokens", 0) ?: 0
            totalOutputTokens += usage?.optInt("completion_tokens", 0) ?: 0
            val message = root.getJSONArray("choices").getJSONObject(0).getJSONObject("message")
            val rawContent = message.optString("content")
            val nativeCalls = message.optJSONArray("tool_calls")
            val dsmlCalls = if (nativeCalls == null || nativeCalls.length() == 0) parseDsmlToolCalls(rawContent) else emptyList()
            val calls = if (nativeCalls != null && nativeCalls.length() > 0) nativeCalls else JSONArray().apply {
                dsmlCalls.forEachIndexed { index, call -> put(JSONObject()
                    .put("id", "dsml_${totalToolCalls}_${index}_${System.currentTimeMillis()}")
                    .put("type", "function")
                    .put("function", JSONObject().put("name", call.name).put("arguments", call.arguments))) }
            }
            if (tools == null || calls.length() == 0) {
                return AiResult(sanitizeAssistantText(rawContent), totalInputTokens, totalOutputTokens, totalToolCalls, System.currentTimeMillis() - startedAt)
            }
            totalToolCalls += calls.length()
            onProgress(AgentProgress(totalInputTokens, totalOutputTokens, totalToolCalls, System.currentTimeMillis() - startedAt, calls.getJSONObject(0).getJSONObject("function").optString("name")))
            val cleanContent = sanitizeAssistantText(rawContent)
            bodyMessages.put(JSONObject().put("role", "assistant")
                .put("content", if (cleanContent.isBlank()) JSONObject.NULL else cleanContent)
                .put("tool_calls", calls))
            for (index in 0 until calls.length()) {
                val call = calls.getJSONObject(index); val function = call.getJSONObject("function")
                val name = function.getString("name")
                val arguments = function.optString("arguments", "{}")
                val signature = "$name:$arguments"
                val result = if (signature == previousToolSignature) {
                    "ERROR [$name]: identical consecutive call skipped; use the previous result or change the arguments"
                } else tools.execute(name, arguments)
                previousToolSignature = signature
                bodyMessages.put(JSONObject().put("role", "tool").put("tool_call_id", call.getString("id")).put("content", result))
            }
        }
        payload.remove("tools"); payload.remove("tool_choice")
        bodyMessages.put(JSONObject().put("role", "system").put("content", "Лимит вызовов инструментов исчерпан. Не вызывай инструменты снова: кратко подведи итог выполненного, укажи реальные результаты и что осталось."))
        val finalRoot = JSONObject(request(openAiEndpoint(profile.baseUrl), headers, payload))
        val finalUsage = finalRoot.optJSONObject("usage")
        totalInputTokens += finalUsage?.optInt("prompt_tokens", 0) ?: 0
        totalOutputTokens += finalUsage?.optInt("completion_tokens", 0) ?: 0
        val finalText = sanitizeAssistantText(finalRoot.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content").orEmpty())
        return AiResult(finalText.ifBlank { "Работа завершена. Откройте файлы проекта, чтобы проверить изменения." }, totalInputTokens, totalOutputTokens, totalToolCalls, System.currentTimeMillis() - startedAt)
    }

    private suspend fun completeAnthropic(
        settings: AppSettings,
        profile: ModelProfile,
        systemPrompt: String,
        messages: List<ChatMessage>,
        tools: AgentToolExecutor?,
        onProgress: (AgentProgress) -> Unit
    ): AiResult {
        val startedAt = System.currentTimeMillis()
        var totalInputTokens = 0
        var totalOutputTokens = 0
        var totalToolCalls = 0
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
            .put("max_tokens", (profile.maxContextTokens / 4).coerceIn(1_024, 4_096))
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
        repeat(MAX_TOOL_ROUNDS) {
            currentCoroutineContext().ensureActive()
            val response = request(anthropicEndpoint(profile.baseUrl), headers, payload)
            val root = JSONObject(response)
            val usage = root.optJSONObject("usage")
            totalInputTokens += usage?.optInt("input_tokens", 0) ?: 0
            totalOutputTokens += usage?.optInt("output_tokens", 0) ?: 0
            val content = root.optJSONArray("content") ?: JSONArray()
            val toolUses = (0 until content.length()).mapNotNull { index -> content.optJSONObject(index)?.takeIf { it.optString("type") == "tool_use" } }
            if (tools == null || toolUses.isEmpty()) return AiResult((0 until content.length()).mapNotNull { index -> content.optJSONObject(index)?.takeIf { it.optString("type") == "text" }?.optString("text") }.joinToString("").trim(), totalInputTokens, totalOutputTokens, totalToolCalls, System.currentTimeMillis() - startedAt)
            totalToolCalls += toolUses.size
            onProgress(AgentProgress(totalInputTokens, totalOutputTokens, totalToolCalls, System.currentTimeMillis() - startedAt, toolUses.first().optString("name")))
            bodyMessages.put(JSONObject().put("role", "assistant").put("content", content))
            val results = JSONArray()
            toolUses.forEach { use -> results.put(JSONObject().put("type", "tool_result").put("tool_use_id", use.getString("id")).put("content", tools.execute(use.getString("name"), use.optJSONObject("input")?.toString() ?: "{}"))) }
            bodyMessages.put(JSONObject().put("role", "user").put("content", results))
        }
        payload.remove("tools")
        bodyMessages.put(JSONObject().put("role", "user").put("content", "Лимит вызовов инструментов исчерпан. Не вызывай инструменты снова: кратко подведи итог выполненного, укажи реальные результаты и что осталось."))
        val finalRoot = JSONObject(request(anthropicEndpoint(profile.baseUrl), headers, payload))
        val finalUsage = finalRoot.optJSONObject("usage")
        totalInputTokens += finalUsage?.optInt("input_tokens", 0) ?: 0
        totalOutputTokens += finalUsage?.optInt("output_tokens", 0) ?: 0
        val finalContent = finalRoot.optJSONArray("content") ?: JSONArray()
        val finalText = (0 until finalContent.length()).mapNotNull { index -> finalContent.optJSONObject(index)?.takeIf { it.optString("type") == "text" }?.optString("text") }.joinToString("").trim()
        return AiResult(finalText.ifBlank { "Работа завершена. Откройте файлы проекта, чтобы проверить изменения." }, totalInputTokens, totalOutputTokens, totalToolCalls, System.currentTimeMillis() - startedAt)
    }

    private suspend fun request(url: String, headers: Map<String, String>, payload: JSONObject): String {
        currentCoroutineContext().ensureActive()
        val connection = NetworkSecurity.apiUrl(url).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 30_000
            connection.readTimeout = 90_000
            connection.doOutput = true
            headers.forEach(connection::setRequestProperty)
            connection.outputStream.bufferedWriter().use { it.write(payload.toString()) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = NetworkSecurity.readLimited(stream, 8 * 1024 * 1024)
            if (code !in 200..299) error(extractError(response).ifBlank { "API вернул ошибку $code" })
            currentCoroutineContext().ensureActive()
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

    private companion object { const val MAX_TOOL_ROUNDS = 16 }
}

internal fun parseDsmlToolCalls(content: String): List<DsmlToolCall> {
    if (!content.contains("DSML", ignoreCase = true)) return emptyList()
    val normalized = content.replace('｜', '|')
    val invoke = Regex(
        """<\s*\|\s*DSML\s*\|\s*invoke\s+name\s*=\s*[\"']([^\"']+)[\"']\s*>(.*?)<\s*/\s*\|\s*DSML\s*\|\s*invoke\s*>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    val parameter = Regex(
        """<\s*\|\s*DSML\s*\|\s*parameter\s+([^>]*)>(.*?)<\s*/\s*\|\s*DSML\s*\|\s*parameter\s*>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    val nameAttribute = Regex("""name\s*=\s*[\"']([^\"']+)[\"']""", RegexOption.IGNORE_CASE)
    val stringAttribute = Regex("""string\s*=\s*[\"']true[\"']""", RegexOption.IGNORE_CASE)
    return invoke.findAll(normalized).mapNotNull { invocation ->
        val name = invocation.groupValues[1].trim().takeIf { it.matches(Regex("[A-Za-z0-9_]{1,64}")) } ?: return@mapNotNull null
        val arguments = mutableListOf<Pair<String, String>>()
        parameter.findAll(invocation.groupValues[2]).forEach { entry ->
            val attributes = entry.groupValues[1]
            val key = nameAttribute.find(attributes)?.groupValues?.get(1) ?: return@forEach
            val value = decodeDsml(entry.groupValues[2].trim())
            val encoded = if (stringAttribute.containsMatchIn(attributes)) jsonString(value) else value.takeIf(::looksLikeJsonValue) ?: jsonString(value)
            arguments += key to encoded
        }
        DsmlToolCall(name, arguments.joinToString(prefix = "{", postfix = "}") { (key, value) -> "${jsonString(key)}:$value" })
    }.toList()
}

internal fun sanitizeAssistantText(content: String): String {
    if (!content.contains("DSML", ignoreCase = true)) return content.trim()
    val normalized = content.replace('｜', '|')
    val withoutCalls = normalized.replace(
        Regex("""<\s*\|\s*DSML\s*\|\s*tool_calls\s*>.*?<\s*/\s*\|\s*DSML\s*\|\s*tool_calls\s*>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)),
        ""
    )
    return withoutCalls.replace(Regex("""<\s*/?\s*\|\s*DSML\s*\|[^>]*>""", RegexOption.IGNORE_CASE), "").trim()
}

private fun decodeDsml(value: String): String = value
    .replace("&quot;", "\"")
    .replace("&apos;", "'")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&amp;", "&")

private fun looksLikeJsonValue(value: String): Boolean {
    val trimmed = value.trim()
    return (trimmed.startsWith('{') && trimmed.endsWith('}')) ||
        (trimmed.startsWith('[') && trimmed.endsWith(']')) ||
        trimmed in setOf("true", "false", "null") ||
        trimmed.matches(Regex("-?(?:0|[1-9]\\d*)(?:\\.\\d+)?(?:[eE][+-]?\\d+)?"))
}

private fun jsonString(value: String): String = buildString {
    append('"')
    value.forEach { character ->
        when (character) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\b' -> append("\\b")
            '\u000C' -> append("\\f")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (character.code < 0x20) append("\\u%04x".format(character.code)) else append(character)
        }
    }
    append('"')
}
