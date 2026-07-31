package com.xanichka.xacode.model

import java.util.UUID

enum class CreationMode(val title: String, val subtitle: String) {
    CHAT("Спросить", "Идеи, ответы и помощь"),
    APP("Приложение", "Спроектировать продукт"),
    CODE("Код", "Написать или исправить код"),
    BOT("Бот", "Создать умного помощника");

    fun systemPrompt(): String = when (this) {
        CHAT -> "Ты XaCode — дружелюбный и точный AI-помощник. Отвечай на языке пользователя."
        APP -> "Ты XaCode — продуктовый инженер. Помогай превратить идею в рабочее приложение: уточняй требования, предлагай архитектуру и пиши готовый код."
        CODE -> "Ты XaCode — сильный разработчик. Давай практичные решения, полный код и коротко объясняй важные решения."
        BOT -> "Ты XaCode — архитектор AI-ботов. Проектируй сценарии, инструменты, память, безопасность и готовую реализацию."
    }
}

enum class MessageRole { USER, ASSISTANT }

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: MessageRole,
    val text: String,
    val createdAt: Long = System.currentTimeMillis()
)

data class Conversation(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "Новый чат",
    val mode: CreationMode = CreationMode.CHAT,
    val messages: List<ChatMessage> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class ProviderSettings(
    val endpoint: String = "https://api.openai.com/v1/chat/completions",
    val apiKey: String = "",
    val model: String = "gpt-4.1-mini"
)

