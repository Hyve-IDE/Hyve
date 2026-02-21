// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.decompile

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class DecompilationFixesTest {

    @Test
    fun `removes empty assertion-disabled if blocks`() {
        val input = """
            public void foo() {
                if (!${'$'}assertionsDisabled && x == null) { }
                doSomething();
            }
        """.trimIndent()
        val result = DecompilationFixes.applyAll(input)
        assertFalse(result.contains("\$assertionsDisabled"))
        assert(result.contains("doSomething()"))
    }

    @Test
    fun `removes empty static blocks`() {
        val input = """
            public class Foo {
                static { }
                int x;
            }
        """.trimIndent()
        val result = DecompilationFixes.applyAll(input)
        assertFalse(result.contains("static { }"))
        assert(result.contains("int x"))
    }

    @Test
    fun `replaces unrepresentable assertionsDisabled with false`() {
        val input = "if (<unrepresentable>.\$assertionsDisabled) { throw new Error(); }"
        val result = DecompilationFixes.applyAll(input)
        assert(result.contains("false"))
        assertFalse(result.contains("<unrepresentable>"))
    }

    @Test
    fun `replaces remaining unrepresentable with null comment`() {
        val input = "Object x = <unrepresentable>;"
        val result = DecompilationFixes.applyAll(input)
        assertEquals("Object x = null /* <unrepresentable> */;", result)
    }

    @Test
    fun `converts empty enum to class`() {
        val input = """
            enum Color {
                private int r;
        """.trimIndent()
        val result = DecompilationFixes.applyAll(input)
        assert(result.contains("class Color {"))
        assertFalse(result.contains("enum Color"))
    }

    @Test
    fun `fixes generic inner class instantiation`() {
        val input = "new Foo<Bar>.Baz()"
        val result = DecompilationFixes.applyAll(input)
        assertEquals("new Foo.Baz()", result)
    }

    @Test
    fun `is idempotent`() {
        val input = """
            public class Test {
                static { }
                if (!${'$'}assertionsDisabled) { }
                Object x = <unrepresentable>;
            }
        """.trimIndent()
        val first = DecompilationFixes.applyAll(input)
        val second = DecompilationFixes.applyAll(first)
        assertEquals(first, second)
    }
}
