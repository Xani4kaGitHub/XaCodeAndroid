package com.xanichka.xacode.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownMathTest {
    @Test
    fun rendersFractionWithoutRegexCrash() {
        assertEquals("(1)⁄(2)", prettyMath("\\frac{1}{2}"))
    }

    @Test
    fun rendersNestedRootsAndFractions() {
        val rendered = prettyMath("\\sqrt{\\frac{x^{2}}{4}}")
        assertTrue(rendered.startsWith("√("))
        assertTrue(rendered.contains("(x²)⁄(4)"))
    }

    @Test
    fun leavesMalformedLatexVisibleInsteadOfCrashing() {
        assertEquals("\\frac{1", prettyMath("\\frac{1"))
    }
}
