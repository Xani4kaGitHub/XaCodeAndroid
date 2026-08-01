package com.xanichka.xacode.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderPresetsTest {
    @Test
    fun presetsMatchEveryProviderAndHaveUniqueTypes() {
        assertEquals(ProviderType.entries.size, providerPresets.map { it.type }.distinct().size)
        assertTrue(providerPresets.filter { it.type != ProviderType.CUSTOM }.all { it.baseUrl.isNotBlank() && it.defaultModel.isNotBlank() })
    }

    @Test
    fun deepSeekUsesCurrentV4ModelsAndMigratesRetiredAliases() {
        val preset = presetFor(ProviderType.DEEPSEEK)
        assertEquals("deepseek-v4-flash", preset.defaultModel)
        assertEquals(listOf("deepseek-v4-flash", "deepseek-v4-pro"), preset.models)
        assertEquals(1_000_000, preset.defaultContextTokens)
        assertEquals("deepseek-v4-flash", currentModelId(ProviderType.DEEPSEEK, "deepseek-chat"))
        assertEquals("deepseek-v4-flash", currentModelId(ProviderType.DEEPSEEK, "deepseek-reasoner"))
        assertEquals(1_000_000, currentContextTokens(ProviderType.DEEPSEEK, "deepseek-v4-flash", 128_000))
    }

    @Test
    fun chatGptOAuthIsASeparateProviderWithCodexModels() {
        val preset = presetFor(ProviderType.CHATGPT)
        assertTrue(preset.oauth)
        assertTrue(preset.apiKeyOptional)
        assertEquals("gpt-5.6-sol", preset.defaultModel)
        assertEquals(listOf("gpt-5.6-sol", "gpt-5.6-terra", "gpt-5.6-luna", "gpt-5.5", "gpt-5.4", "gpt-5.4-mini"), preset.models)
    }
}
