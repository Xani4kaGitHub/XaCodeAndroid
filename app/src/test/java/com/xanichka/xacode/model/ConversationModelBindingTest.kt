package com.xanichka.xacode.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ConversationModelBindingTest {
    @Test
    fun bindingKeepsRoutingAfterProfileModelIsEdited() {
        val original = ModelProfile(
            id = "profile",
            provider = ProviderType.OPENAI,
            apiKey = "secret",
            baseUrl = "https://api.openai.com/v1",
            model = "gpt-5.6-sol",
            reasoningEffort = "medium",
            serviceTier = "standard"
        )
        val binding = original.toConversationBinding()
        val edited = original.copy(model = "gpt-5.6-luna", reasoningEffort = "low", serviceTier = "fast")

        val resolved = binding.resolveCredential(listOf(edited))

        assertEquals("gpt-5.6-sol", resolved.model)
        assertEquals("medium", resolved.reasoningEffort)
        assertEquals("standard", resolved.serviceTier)
        assertEquals("secret", resolved.apiKey)
    }

    @Test
    fun bindingRejectsCredentialProfileChangedToAnotherProvider() {
        val binding = ModelProfile(id = "profile", provider = ProviderType.OPENAI).toConversationBinding()

        assertThrows(IllegalArgumentException::class.java) {
            binding.resolveCredential(listOf(ModelProfile(id = "profile", provider = ProviderType.ANTHROPIC)))
        }
    }
}
