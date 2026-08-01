package com.xanichka.xacode.data

import com.xanichka.xacode.model.ChatMessage
import com.xanichka.xacode.model.MessageRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextWindowTest {
    @Test fun keepsAllShortMessages() {
        val messages = listOf(ChatMessage(role = MessageRole.USER, text = "hello"), ChatMessage(role = MessageRole.ASSISTANT, text = "world"))
        assertEquals(messages, ContextWindow.prepare(messages, 4_096))
    }

    @Test fun summarizesOldMessagesAndKeepsNewest() {
        val messages = (1..20).map { ChatMessage(role = if (it % 2 == 0) MessageRole.ASSISTANT else MessageRole.USER, text = "$it ${"x".repeat(500)}") }
        val result = ContextWindow.prepare(messages, 1_024)
        assertTrue(result.first().text.startsWith("[Сжатый контекст"))
        assertEquals(messages.last().text, result.last().text)
        assertTrue(result.size < messages.size)
    }
}
