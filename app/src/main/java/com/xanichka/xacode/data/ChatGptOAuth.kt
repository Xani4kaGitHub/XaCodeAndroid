package com.xanichka.xacode.data

import android.util.Base64
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

data class ChatGptDeviceCode internal constructor(
    val verificationUrl: String,
    val userCode: String,
    internal val deviceAuthId: String,
    internal val intervalSeconds: Long
)

data class ChatGptAuth(
    val accessToken: String,
    val refreshToken: String,
    val idToken: String,
    val accountId: String,
    val email: String = "",
    val planType: String = ""
) {
    fun encode(): String = JSONObject()
        .put("type", "chatgpt_oauth")
        .put("accessToken", accessToken)
        .put("refreshToken", refreshToken)
        .put("idToken", idToken)
        .put("accountId", accountId)
        .put("email", email)
        .put("planType", planType)
        .toString()

    companion object {
        fun decode(value: String): ChatGptAuth {
            val json = JSONObject(value)
            require(json.optString("type") == "chatgpt_oauth") { "Войдите через ChatGPT OAuth в настройках модели" }
            return ChatGptAuth(
                accessToken = json.getString("accessToken"),
                refreshToken = json.getString("refreshToken"),
                idToken = json.optString("idToken"),
                accountId = json.getString("accountId"),
                email = json.optString("email"),
                planType = json.optString("planType")
            )
        }
    }
}

/** Device-code OAuth used by the open-source Codex client, adapted for Android. */
class ChatGptOAuthClient {
    suspend fun requestDeviceCode(): ChatGptDeviceCode {
        val response = request(
            "$ISSUER/api/accounts/deviceauth/usercode",
            "application/json",
            JSONObject().put("client_id", CLIENT_ID).toString()
        )
        require(response.code in 200..299) {
            if (response.code == 404) "Включите Device Code authorization в настройках безопасности ChatGPT" else "ChatGPT OAuth вернул ошибку ${response.code}"
        }
        val json = JSONObject(response.body)
        val interval = json.opt("interval")?.toString()?.trim()?.toLongOrNull()?.coerceIn(1, 30) ?: 5L
        return ChatGptDeviceCode(
            verificationUrl = "$ISSUER/codex/device",
            userCode = json.optString("user_code", json.optString("usercode")),
            deviceAuthId = json.getString("device_auth_id"),
            intervalSeconds = interval
        )
    }

    suspend fun completeDeviceLogin(device: ChatGptDeviceCode): ChatGptAuth {
        val deadline = System.currentTimeMillis() + 15 * 60_000L
        var authorization: JSONObject? = null
        while (System.currentTimeMillis() < deadline) {
            currentCoroutineContext().ensureActive()
            val response = request(
                "$ISSUER/api/accounts/deviceauth/token",
                "application/json",
                JSONObject().put("device_auth_id", device.deviceAuthId).put("user_code", device.userCode).toString()
            )
            when {
                response.code in 200..299 -> { authorization = JSONObject(response.body); break }
                response.code == 403 || response.code == 404 -> delay(device.intervalSeconds * 1_000L)
                else -> error("Не удалось завершить вход ChatGPT (${response.code})")
            }
        }
        val code = authorization ?: error("Время входа истекло. Запустите ChatGPT OAuth ещё раз")
        val tokenResponse = request(
            "$ISSUER/oauth/token",
            "application/x-www-form-urlencoded",
            form(
                "grant_type" to "authorization_code",
                "code" to code.getString("authorization_code"),
                "redirect_uri" to "$ISSUER/deviceauth/callback",
                "client_id" to CLIENT_ID,
                "code_verifier" to code.getString("code_verifier")
            )
        )
        require(tokenResponse.code in 200..299) { "ChatGPT не выдал OAuth-токен (${tokenResponse.code})" }
        return parseTokens(JSONObject(tokenResponse.body)).also { authCache[it.accountId] = it }
    }

    suspend fun refreshIfNeeded(auth: ChatGptAuth): ChatGptAuth {
        authCache.putIfAbsent(auth.accountId, auth)
        return refreshMutex.withLock {
            val current = authCache[auth.accountId] ?: auth
            if (!jwtExpiresSoon(current.accessToken)) return@withLock current
            val response = request(
                "$ISSUER/oauth/token",
                "application/x-www-form-urlencoded",
                form("client_id" to CLIENT_ID, "grant_type" to "refresh_token", "refresh_token" to current.refreshToken)
            )
            require(response.code in 200..299) { "Сессия ChatGPT истекла. Войдите снова" }
            parseTokens(JSONObject(response.body), current).also { authCache[it.accountId] = it }
        }
    }

    private fun parseTokens(json: JSONObject, previous: ChatGptAuth? = null): ChatGptAuth {
        val accessToken = json.getString("access_token")
        val idToken = json.optString("id_token", previous?.idToken.orEmpty())
        val claims = jwtClaims(idToken.ifBlank { accessToken })
        val authClaims = claims.optJSONObject("https://api.openai.com/auth") ?: JSONObject()
        val accountId = authClaims.optString("chatgpt_account_id", previous?.accountId.orEmpty())
        require(accountId.isNotBlank()) { "ChatGPT не вернул идентификатор аккаунта Codex" }
        return ChatGptAuth(
            accessToken = accessToken,
            refreshToken = json.optString("refresh_token", previous?.refreshToken.orEmpty()),
            idToken = idToken,
            accountId = accountId,
            email = claims.optString("email", previous?.email.orEmpty()),
            planType = authClaims.optString("chatgpt_plan_type", previous?.planType.orEmpty())
        )
    }

    private fun jwtExpiresSoon(token: String): Boolean = runCatching {
        jwtClaims(token).optLong("exp", 0L) <= System.currentTimeMillis() / 1_000L + 120L
    }.getOrDefault(true)

    private fun jwtClaims(token: String): JSONObject {
        val payload = token.split('.').getOrNull(1) ?: error("Некорректный OAuth-токен ChatGPT")
        val decoded = Base64.decode(payload, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        return JSONObject(String(decoded, StandardCharsets.UTF_8))
    }

    private suspend fun request(url: String, contentType: String, body: String): HttpResult {
        currentCoroutineContext().ensureActive()
        val connection = NetworkSecurity.apiUrl(url).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 30_000
            connection.readTimeout = 30_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", contentType)
            connection.setRequestProperty("User-Agent", "XaCode Android")
            connection.outputStream.bufferedWriter().use { it.write(body) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            HttpResult(code, NetworkSecurity.readLimited(stream, 512 * 1024))
        } finally {
            connection.disconnect()
        }
    }

    private fun form(vararg values: Pair<String, String>): String = values.joinToString("&") { (key, value) ->
        "${URLEncoder.encode(key, "UTF-8")}=${URLEncoder.encode(value, "UTF-8")}"
    }

    private data class HttpResult(val code: Int, val body: String)

    companion object {
        private const val ISSUER = "https://auth.openai.com"
        // Public OAuth client id from the Apache-licensed OpenAI Codex client.
        private const val CLIENT_ID = "app_EMoamEEZ73f0CkXaXp7hrann"
        private val refreshMutex = Mutex()
        private val authCache = ConcurrentHashMap<String, ChatGptAuth>()
    }
}
