package com.xanichka.xacode.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CreationModeTest {
    @Test
    fun everyModeHasDistinctUsefulPrompt() {
        val prompts = CreationMode.entries.map { it.systemPrompt() }

        assertEquals(CreationMode.entries.size, prompts.distinct().size)
        assertTrue(prompts.all { it.contains("XaCode") && it.length > 40 })
    }
}

