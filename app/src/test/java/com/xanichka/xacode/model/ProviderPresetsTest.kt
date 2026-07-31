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
}
