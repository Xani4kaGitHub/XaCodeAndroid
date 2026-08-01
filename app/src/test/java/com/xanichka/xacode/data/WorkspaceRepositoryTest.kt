package com.xanichka.xacode.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WorkspaceRepositoryTest {
    @Test fun normalizesRelativePaths() {
        assertEquals(listOf("src", "main", "App.kt"), WorkspacePathPolicy.segments("src\\main/App.kt"))
    }

    @Test fun allowsEmptyDirectoryOnlyWhenRequested() {
        assertEquals(emptyList<String>(), WorkspacePathPolicy.segments("", allowEmpty = true))
        assertThrows(IllegalArgumentException::class.java) { WorkspacePathPolicy.segments("") }
    }

    @Test fun rejectsAbsoluteAndTraversalPaths() {
        listOf("/system/build.prop", "C:/secret.txt", "../secret.txt", "src/../secret.txt").forEach { path ->
            assertThrows(path, IllegalArgumentException::class.java) { WorkspacePathPolicy.segments(path) }
        }
    }

    @Test fun rejectsControlCharactersAndOversizedNames() {
        assertThrows(IllegalArgumentException::class.java) { WorkspacePathPolicy.segments("src/bad\u0000name") }
        assertThrows(IllegalArgumentException::class.java) { WorkspacePathPolicy.segments("a".repeat(256)) }
    }
}
