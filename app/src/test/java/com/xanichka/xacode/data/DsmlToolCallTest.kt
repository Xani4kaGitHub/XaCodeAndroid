package com.xanichka.xacode.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DsmlToolCallTest {
    @Test
    fun parsesAsciiDoublePipeDsmlAndRemovesItFromVisibleText() {
        val sample = """Installing in background.
            <||DSML||tool_calls>
            <||DSML||invoke name="run_command">
            <||DSML||parameter name="command" string="true">python --version</||DSML||parameter>
            <||DSML||parameter name="timeoutSeconds" string="false">30</||DSML||parameter>
            </||DSML||invoke>
            </||DSML||tool_calls>""".trimIndent()

        val calls = parseDsmlToolCalls(sample)

        assertEquals(1, calls.size)
        assertEquals("run_command", calls.single().name)
        assertEquals("{\"command\":\"python --version\", \"timeoutSeconds\":30}", calls.single().arguments)
        assertEquals("Installing in background.", sanitizeAssistantText(sample))
    }

    private val sample = """
        <｜DSML｜tool_calls>
        <｜DSML｜invoke name="run_python">
        <｜DSML｜parameter name="path" string="true">audit/src/locate2.py</｜DSML｜parameter>
        <｜DSML｜parameter name="arguments">["--quick"]</｜DSML｜parameter>
        </｜DSML｜invoke>
        </｜DSML｜tool_calls>
    """.trimIndent()

    @Test
    fun parsesDeepSeekDsmlToolCall() {
        val calls = parseDsmlToolCalls(sample)
        assertEquals(1, calls.size)
        assertEquals("run_python", calls.single().name)
        assertEquals("{\"path\":\"audit/src/locate2.py\", \"arguments\":[\"--quick\"]}", calls.single().arguments)
    }

    @Test
    fun removesProtocolFromVisibleAnswer() {
        val cleaned = sanitizeAssistantText("Запускаю.\n$sample\nГотово")
        assertTrue(cleaned.contains("Запускаю."))
        assertTrue(cleaned.contains("Готово"))
        assertFalse(cleaned.contains("DSML"))
        assertFalse(cleaned.contains("run_python"))
    }
}
