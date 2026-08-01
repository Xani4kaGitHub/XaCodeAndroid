package com.xanichka.xacode.data

import com.xanichka.xacode.model.ChatMessage
import com.xanichka.xacode.model.MessageRole

/** Builds a bounded per-chat context while preserving the newest conversation turns. */
object ContextWindow {
    fun estimateTokens(text: String): Int = (text.length + 3) / 4

    fun prepare(messages: List<ChatMessage>, modelContextLimit: Int): List<ChatMessage> {
        if (messages.isEmpty()) return messages
        val usableTokens = (modelContextLimit.coerceAtLeast(1_024) * 0.72f).toInt().coerceAtLeast(700)
        val newest = mutableListOf<ChatMessage>()
        var used = 0
        for (message in messages.asReversed()) {
            val cost = estimateTokens(message.text) + estimateTokens(message.context) + 12
            if (newest.size >= 2 && used + cost > usableTokens) break
            newest += if (cost > usableTokens) message.copy(
                text = message.text.take(usableTokens * 3),
                context = message.context.take((usableTokens / 4).coerceAtLeast(0) * 3)
            ) else message
            used += cost
        }
        newest.reverse()
        val omittedCount = messages.size - newest.size
        if (omittedCount <= 0) return newest
        val omitted = messages.take(omittedCount)
        val summary = buildString {
            append("[Сжатый контекст предыдущей части чата — ").append(omittedCount).append(" сообщений]\n")
            omitted.takeLast(12).forEach { message ->
                append(if (message.role == MessageRole.USER) "Пользователь: " else "XaCode: ")
                append(message.text.replace(Regex("\\s+"), " ").take(240))
                if (message.context.isNotBlank()) append(" [были прикреплены файлы]")
                append('\n')
            }
        }
        return listOf(ChatMessage(role = MessageRole.USER, text = summary)) + newest
    }
}
