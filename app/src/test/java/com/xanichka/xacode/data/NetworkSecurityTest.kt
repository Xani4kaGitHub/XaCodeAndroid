package com.xanichka.xacode.data

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class NetworkSecurityTest {
    @Test
    fun externalApiRequiresHttps() {
        assertThrows(IllegalArgumentException::class.java) {
            NetworkSecurity.apiUrl("http://example.com/v1")
        }
    }

    @Test
    fun localOllamaMayUseHttp() {
        assertEquals("127.0.0.1", NetworkSecurity.apiUrl("http://127.0.0.1:11434/v1").host)
    }

    @Test
    fun downloaderRejectsLocalNetwork() {
        assertThrows(IllegalArgumentException::class.java) {
            NetworkSecurity.publicDownloadUrl("https://127.0.0.1/private")
        }
    }

    @Test
    fun responseReaderEnforcesLimit() {
        assertThrows(IllegalArgumentException::class.java) {
            NetworkSecurity.readLimited(ByteArrayInputStream(ByteArray(9)), 8)
        }
        assertEquals("hello", NetworkSecurity.readLimited(ByteArrayInputStream("hello".toByteArray()), 8))
    }
}
