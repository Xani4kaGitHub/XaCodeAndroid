package com.xanichka.xacode.data

import com.xanichka.xacode.model.AppSettings
import com.xanichka.xacode.model.ChatMessage
import com.xanichka.xacode.model.MessageRole
import com.xanichka.xacode.model.ModelProfile
import com.xanichka.xacode.model.ProviderType
import com.xanichka.xacode.model.ToolTrace
import com.xanichka.xacode.model.ToolTraceState
import com.xanichka.xacode.model.presetFor
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

data class AiResult(val text: String, val inputTokens: Int = 0, val outputTokens: Int = 0, val toolCalls: Int = 0, val elapsedMs: Long = 0, val toolTrace: List<ToolTrace> = emptyList())
data class AgentProgress(val inputTokens: Int = 0, val outputTokens: Int = 0, val toolCalls: Int = 0, val elapsedMs: Long = 0, val currentTool: String = "", val toolTrace: List<ToolTrace> = emptyList())
internal data class DsmlToolCall(val name: String, val arguments: String)

class AiClient(private val onCredentialRefreshed: (profileId: String, credential: String) -> Unit = { _, _ -> }) {
    suspend fun complete(settings: AppSettings, profile: ModelProfile, messages: List<ChatMessage>, tools: AgentToolExecutor? = null, onProgress: (AgentProgress) -> Unit = {}): AiResult {
        NetworkSecurity.apiUrl(profile.baseUrl)
        require(profile.model.isNotBlank()) { "Укажите модель в настройках" }
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
        return if (profile.provider == ProviderType.CHATGPT) {
            completeChatGpt(settings, profile, systemPrompt, messages, tools, onProgress)
        } else if (usesAnthropicFormat(profile)) {
            completeAnthropic(settings, profile, systemPrompt, messages, tools, onProgress)
        } else {
            completeOpenAi(settings, profile, systemPrompt, messages, tools, onProgress)
        }
    }

    private suspend fun completeChatGpt(
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
        val toolTrace = mutableListOf<ToolTrace>()
        var previousToolSignature = ""
        val oauthClient = ChatGptOAuthClient()
        val savedAuth = ChatGptAuth.decode(profile.apiKey)
        val auth = oauthClient.refreshIfNeeded(savedAuth)
        if (auth != savedAuth) onCredentialRefreshed(profile.id, auth.encode())
        val input = JSONArray()
        messages.forEach { message ->
            input.put(JSONObject()
                .put("role", if (message.role == MessageRole.USER) "user" else "assistant")
                .put("content", message.text + message.context.takeIf { it.isNotBlank() }?.let { "\n\nКонтекст из файлов:\n$it" }.orEmpty()))
        }
        val responseTools = tools?.definitions?.let(::responsesTools)
        val headers = mapOf(
            "Content-Type" to "application/json",
            "Accept" to "text/event-stream",
            "Authorization" to "Bearer ${auth.accessToken}",
            "ChatGPT-Account-Id" to auth.accountId,
            "Originator" to "codex_cli_rs",
            "Version" to "0.144.6",
            "Session_id" to java.util.UUID.randomUUID().toString(),
            "User-Agent" to "codex_cli_rs/0.144.6 (Android; XaCode)"
        )
        var round = 0
        while (true) {
            if (settings.agentLimitsEnabled && round >= settings.agentMaxRounds) return budgetResult(startedAt, totalInputTokens, totalOutputTokens, totalToolCalls, toolTrace, "достигнут лимит раундов")
            round++
            currentCoroutineContext().ensureActive()
            budgetStop(settings, startedAt, totalInputTokens, totalOutputTokens, totalToolCalls, toolTrace)?.let { return it }
            val payload = JSONObject()
                .put("model", profile.model)
                .put("instructions", systemPrompt)
                .put("input", input)
                .put("store", false)
                .put("stream", true)
                .put("reasoning", JSONObject().put("effort", profile.reasoningEffort).put("summary", "auto"))
            if (responseTools != null) {
                payload.put("tools", responseTools)
                payload.put("tool_choice", "auto")
                payload.put("parallel_tool_calls", true)
            }
            if (profile.serviceTier == "fast") payload.put("service_tier", "priority")
            // Never send ChatGPT OAuth credentials to a user-editable/custom host.
            val response = requestChatGptSse("https://chatgpt.com/backend-api/codex/responses", headers, payload)
            totalInputTokens += response.inputTokens
            totalOutputTokens += response.outputTokens
            budgetStop(settings, startedAt, totalInputTokens, totalOutputTokens, totalToolCalls, toolTrace)?.let { return it }
            if (tools == null || response.calls.isEmpty()) {
                return AiResult(response.text.trim(), totalInputTokens, totalOutputTokens, totalToolCalls, System.currentTimeMillis() - startedAt, toolTrace.toList())
            }
            if (settings.agentLimitsEnabled && totalToolCalls + response.calls.size > settings.agentMaxToolCalls) return budgetResult(startedAt, totalInputTokens, totalOutputTokens, totalToolCalls, toolTrace, "достигнут лимит инструментов")
            if (response.text.isNotBlank()) input.put(JSONObject().put("role", "assistant").put("content", response.text))
            totalToolCalls += response.calls.size
            response.calls.forEach { call ->
                input.put(JSONObject().put("type", "function_call").put("call_id", call.callId).put("name", call.name).put("arguments", call.arguments))
                val signature = "${call.name}:${call.arguments}"
                val traceIndex = toolTrace.size
                toolTrace += ToolTrace(name = call.name, arguments = traceValue(call.arguments))
                onProgress(AgentProgress(totalInputTokens, totalOutputTokens, totalToolCalls, System.currentTimeMillis() - startedAt, call.name, toolTrace.toList()))
                val toolStartedAt = System.currentTimeMillis()
                val result = if (signature == previousToolSignature) "ERROR [${call.name}]: identical consecutive call skipped" else tools.execute(call.name, call.arguments)
                toolTrace[traceIndex] = toolTrace[traceIndex].copy(result = traceValue(result), state = if (result.startsWith("ERROR")) ToolTraceState.ERROR else ToolTraceState.SUCCESS, elapsedMs = System.currentTimeMillis() - toolStartedAt)
                onProgress(AgentProgress(totalInputTokens, totalOutputTokens, totalToolCalls, System.currentTimeMillis() - startedAt, call.name, toolTrace.toList()))
                previousToolSignature = signature
                input.put(JSONObject().put("type", "function_call_output").put("call_id", call.callId).put("output", result))
            }
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
        val toolTrace = mutableListOf<ToolTrace>()
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
        payload.put("model", profile.model)
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
        var round = 0
        while (true) {
            if (settings.agentLimitsEnabled && round >= settings.agentMaxRounds) return budgetResult(startedAt, totalInputTokens, totalOutputTokens, totalToolCalls, toolTrace, "достигнут лимит раундов")
            round++
            currentCoroutineContext().ensureActive()
            budgetStop(settings, startedAt, totalInputTokens, totalOutputTokens, totalToolCalls, toolTrace)?.let { return it }
            val response = request(openAiEndpoint(profile.baseUrl), headers, payload)
            val root = JSONObject(response)
            val usage = root.optJSONObject("usage")
            totalInputTokens += usage?.optInt("prompt_tokens", 0) ?: 0
            totalOutputTokens += usage?.optInt("completion_tokens", 0) ?: 0
            budgetStop(settings, startedAt, totalInputTokens, totalOutputTokens, totalToolCalls, toolTrace)?.let { return it }
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
                return AiResult(sanitizeAssistantText(rawContent), totalInputTokens, totalOutputTokens, totalToolCalls, System.currentTimeMillis() - startedAt, toolTrace.toList())
            }
            if (settings.agentLimitsEnabled && totalToolCalls + calls.length() > settings.agentMaxToolCalls) return budgetResult(startedAt, totalInputTokens, totalOutputTokens, totalToolCalls, toolTrace, "достигнут лимит инструментов")
            totalToolCalls += calls.length()
            val cleanContent = sanitizeAssistantText(rawContent)
            bodyMessages.put(JSONObject().put("role", "assistant")
                .put("content", if (cleanContent.isBlank()) JSONObject.NULL else cleanContent)
                .put("tool_calls", calls))
            for (index in 0 until calls.length()) {
                val call = calls.getJSONObject(index); val function = call.getJSONObject("function")
                val name = function.getString("name")
                val arguments = function.optString("arguments", "{}")
                val signature = "$name:$arguments"
                val traceIndex = toolTrace.size
                toolTrace += ToolTrace(name = name, arguments = traceValue(arguments))
                onProgress(AgentProgress(totalInputTokens, totalOutputTokens, totalToolCalls, System.currentTimeMillis() - startedAt, name, toolTrace.toList()))
                val toolStartedAt = System.currentTimeMillis()
                val result = if (signature == previousToolSignature) {
                    "ERROR [$name]: identical consecutive call skipped; use the previous result or change the arguments"
                } else tools.execute(name, arguments)
                toolTrace[traceIndex] = toolTrace[traceIndex].copy(
                    result = traceValue(result),
                    state = if (result.startsWith("ERROR")) ToolTraceState.ERROR else ToolTraceState.SUCCESS,
                    elapsedMs = System.currentTimeMillis() - toolStartedAt
                )
                onProgress(AgentProgress(totalInputTokens, totalOutputTokens, totalToolCalls, System.currentTimeMillis() - startedAt, name, toolTrace.toList()))
                previousToolSignature = signature
                bodyMessages.put(JSONObject().put("role", "tool").put("tool_call_id", call.getString("id")).put("content", result))
            }
        }
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
        val toolTrace = mutableListOf<ToolTrace>()
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
        payload.put("model", profile.model)
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
        var round = 0
        while (true) {
            if (settings.agentLimitsEnabled && round >= settings.agentMaxRounds) return budgetResult(startedAt, totalInputTokens, totalOutputTokens, totalToolCalls, toolTrace, "достигнут лимит раундов")
            round++
            currentCoroutineContext().ensureActive()
            budgetStop(settings, startedAt, totalInputTokens, totalOutputTokens, totalToolCalls, toolTrace)?.let { return it }
            val response = request(anthropicEndpoint(profile.baseUrl), headers, payload)
            val root = JSONObject(response)
            val usage = root.optJSONObject("usage")
            totalInputTokens += usage?.optInt("input_tokens", 0) ?: 0
            totalOutputTokens += usage?.optInt("output_tokens", 0) ?: 0
            budgetStop(settings, startedAt, totalInputTokens, totalOutputTokens, totalToolCalls, toolTrace)?.let { return it }
            val content = root.optJSONArray("content") ?: JSONArray()
            val toolUses = (0 until content.length()).mapNotNull { index -> content.optJSONObject(index)?.takeIf { it.optString("type") == "tool_use" } }
            if (tools == null || toolUses.isEmpty()) return AiResult((0 until content.length()).mapNotNull { index -> content.optJSONObject(index)?.takeIf { it.optString("type") == "text" }?.optString("text") }.joinToString("").trim(), totalInputTokens, totalOutputTokens, totalToolCalls, System.currentTimeMillis() - startedAt, toolTrace.toList())
            if (settings.agentLimitsEnabled && totalToolCalls + toolUses.size > settings.agentMaxToolCalls) return budgetResult(startedAt, totalInputTokens, totalOutputTokens, totalToolCalls, toolTrace, "достигнут лимит инструментов")
            totalToolCalls += toolUses.size
            bodyMessages.put(JSONObject().put("role", "assistant").put("content", content))
            val results = JSONArray()
            toolUses.forEach { use ->
                val name = use.getString("name")
                val arguments = use.optJSONObject("input")?.toString() ?: "{}"
                val traceIndex = toolTrace.size
                toolTrace += ToolTrace(name = name, arguments = traceValue(arguments))
                onProgress(AgentProgress(totalInputTokens, totalOutputTokens, totalToolCalls, System.currentTimeMillis() - startedAt, name, toolTrace.toList()))
                val toolStartedAt = System.currentTimeMillis()
                val result = tools.execute(name, arguments)
                toolTrace[traceIndex] = toolTrace[traceIndex].copy(result = traceValue(result), state = if (result.startsWith("ERROR")) ToolTraceState.ERROR else ToolTraceState.SUCCESS, elapsedMs = System.currentTimeMillis() - toolStartedAt)
                onProgress(AgentProgress(totalInputTokens, totalOutputTokens, totalToolCalls, System.currentTimeMillis() - startedAt, name, toolTrace.toList()))
                results.put(JSONObject().put("type", "tool_result").put("tool_use_id", use.getString("id")).put("content", result))
            }
            bodyMessages.put(JSONObject().put("role", "user").put("content", results))
        }
    }

    private suspend fun request(url: String, headers: Map<String, String>, payload: JSONObject): String = retryTransient {
        currentCoroutineContext().ensureActive()
        val connection = NetworkSecurity.apiUrl(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 30_000
            connection.readTimeout = 90_000
            connection.doOutput = true
            headers.forEach(connection::setRequestProperty)
            connection.outputStream.bufferedWriter().use { it.write(payload.toString()) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = NetworkSecurity.readLimited(stream, 8 * 1024 * 1024)
            if (code !in 200..299) {
                val message = extractError(response).ifBlank { "API вернул ошибку $code" }
                if (code in RETRYABLE_STATUS_CODES) throw RetryableHttpException(message, retryAfterMs(connection))
                error(message)
            }
            currentCoroutineContext().ensureActive()
            response
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun requestChatGptSse(url: String, headers: Map<String, String>, payload: JSONObject): ChatGptResponse = retryTransient {
        currentCoroutineContext().ensureActive()
        val connection = NetworkSecurity.apiUrl(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 30_000
            connection.readTimeout = 120_000
            connection.doOutput = true
            headers.forEach(connection::setRequestProperty)
            connection.outputStream.bufferedWriter().use { it.write(payload.toString()) }
            val code = connection.responseCode
            if (code !in 200..299) {
                val errorBody = NetworkSecurity.readLimited(connection.errorStream, 2 * 1024 * 1024)
                val message = extractError(errorBody).ifBlank { "ChatGPT Codex вернул ошибку $code" }
                if (code in RETRYABLE_STATUS_CODES) throw RetryableHttpException(message, retryAfterMs(connection))
                error(message)
            }
            val text = StringBuilder()
            val calls = linkedMapOf<String, ChatGptToolCall>()
            var inputTokens = 0
            var outputTokens = 0
            val coroutineContext = currentCoroutineContext()
            connection.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    coroutineContext.ensureActive()
                    if (!line.startsWith("data:")) return@forEach
                    val data = line.removePrefix("data:").trim()
                    if (data.isBlank() || data == "[DONE]") return@forEach
                    val event = runCatching { JSONObject(data) }.getOrNull() ?: return@forEach
                    when (event.optString("type")) {
                        "response.output_text.delta" -> text.append(event.optString("delta"))
                        "response.output_item.done" -> event.optJSONObject("item")?.let { item ->
                            if (item.optString("type") == "function_call") {
                                val callId = item.optString("call_id", item.optString("id"))
                                if (callId.isNotBlank()) calls[callId] = ChatGptToolCall(callId, item.optString("name"), item.optString("arguments", "{}"))
                            }
                        }
                        "response.completed" -> event.optJSONObject("response")?.let { completed ->
                            completed.optJSONObject("usage")?.let { usage ->
                                inputTokens = usage.optInt("input_tokens", inputTokens)
                                outputTokens = usage.optInt("output_tokens", outputTokens)
                            }
                            val output = completed.optJSONArray("output") ?: JSONArray()
                            for (index in 0 until output.length()) {
                                val item = output.optJSONObject(index) ?: continue
                                if (item.optString("type") == "function_call") {
                                    val callId = item.optString("call_id", item.optString("id"))
                                    if (callId.isNotBlank()) calls[callId] = ChatGptToolCall(callId, item.optString("name"), item.optString("arguments", "{}"))
                                }
                            }
                        }
                        "error", "response.failed" -> {
                            val failure = event.optJSONObject("error")
                                ?: event.optJSONObject("response")?.optJSONObject("error")
                            error(failure?.optString("message").orEmpty().ifBlank { "Ошибка ChatGPT Codex" })
                        }
                    }
                }
            }
            ChatGptResponse(text.toString(), calls.values.toList(), inputTokens, outputTokens)
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun <T> retryTransient(block: suspend () -> T): T {
        var failure: RetryableHttpException? = null
        repeat(MAX_HTTP_ATTEMPTS) { attempt ->
            try {
                return block()
            } catch (error: RetryableHttpException) {
                failure = error
                if (attempt == MAX_HTTP_ATTEMPTS - 1) throw error
                currentCoroutineContext().ensureActive()
                delay(error.retryAfterMs ?: (500L shl attempt).coerceAtMost(4_000L))
            }
        }
        throw failure ?: IllegalStateException("API request failed")
    }

    private fun retryAfterMs(connection: HttpURLConnection): Long? {
        val value = connection.getHeaderField("Retry-After")?.trim()?.toLongOrNull() ?: return null
        return (value * 1_000L).coerceIn(0L, 30_000L)
    }

    private fun responsesTools(definitions: JSONArray): JSONArray = JSONArray().apply {
        for (index in 0 until definitions.length()) {
            val function = definitions.getJSONObject(index).getJSONObject("function")
            put(JSONObject()
                .put("type", "function")
                .put("name", function.getString("name"))
                .put("description", function.optString("description"))
                .put("parameters", function.optJSONObject("parameters") ?: JSONObject().put("type", "object"))
                .put("strict", false))
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

    private fun budgetStop(settings: AppSettings, startedAt: Long, input: Int, output: Int, calls: Int, trace: List<ToolTrace>): AiResult? = when {
        !settings.agentLimitsEnabled -> null
        input + output >= settings.agentMaxTokens -> budgetResult(startedAt, input, output, calls, trace, "достигнут лимит токенов")
        System.currentTimeMillis() - startedAt >= settings.agentMaxMinutes * 60_000L -> budgetResult(startedAt, input, output, calls, trace, "достигнут лимит времени")
        calls >= settings.agentMaxToolCalls -> budgetResult(startedAt, input, output, calls, trace, "достигнут лимит инструментов")
        else -> null
    }

    private companion object {
        const val MAX_HTTP_ATTEMPTS = 3
        val RETRYABLE_STATUS_CODES = setOf(429, 502, 503, 504)
    }

    private fun budgetResult(startedAt: Long, input: Int, output: Int, calls: Int, trace: List<ToolTrace>, reason: String) = AiResult(
        text = "XaCode безопасно остановил агента: $reason. Уже выполненные действия сохранены в журнале инструментов.",
        inputTokens = input,
        outputTokens = output,
        toolCalls = calls,
        elapsedMs = System.currentTimeMillis() - startedAt,
        toolTrace = trace.toList()
    )

}

private data class ChatGptToolCall(val callId: String, val name: String, val arguments: String)
private data class ChatGptResponse(val text: String, val calls: List<ChatGptToolCall>, val inputTokens: Int, val outputTokens: Int)
private class RetryableHttpException(message: String, val retryAfterMs: Long?) : IllegalStateException(message)

private fun traceValue(value: String): String {
    val redacted = value.replace(
        Regex("""(?i)(api[_-]?key|authorization|password|token)([\"']?\s*[:=]\s*[\"']?)[^\"'\s,}]+"""),
        "\$1\$2***"
    )
    return if (redacted.length <= 4_000) redacted else redacted.take(4_000) + "\n… обрезано XaCode"
}

internal fun parseDsmlToolCalls(content: String): List<DsmlToolCall> {
    if (!content.contains("DSML", ignoreCase = true)) return emptyList()
    val normalized = content.replace('｜', '|')
    val invoke = Regex(
        """<\s*\|+\s*DSML\s*\|+\s*invoke\s+name\s*=\s*[\"']([^\"']+)[\"']\s*>(.*?)<\s*/\s*\|+\s*DSML\s*\|+\s*invoke\s*>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    val parameter = Regex(
        """<\s*\|+\s*DSML\s*\|+\s*parameter\s+([^>]*)>(.*?)<\s*/\s*\|+\s*DSML\s*\|+\s*parameter\s*>""",
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
        Regex("""<\s*\|+\s*DSML\s*\|+\s*tool_calls\s*>.*?<\s*/\s*\|+\s*DSML\s*\|+\s*tool_calls\s*>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)),
        ""
    )
    return withoutCalls.replace(Regex("""<\s*/?\s*\|+\s*DSML\s*\|+[^>]*>""", RegexOption.IGNORE_CASE), "").trim()
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
