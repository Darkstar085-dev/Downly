package com.sipun.downly.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicateReplacementTest {

    @Test
    fun successfulReplacement_backsUpBeforeWritingAndKeepsExistingUri() {
        val events = mutableListOf<String>()

        replaceWithRollback(
            backup = { events += "backup" },
            replace = { events += "replace" },
            restore = { events += "restore" },
        )

        assertEquals(listOf("backup", "replace"), events)
    }

    @Test
    fun replacementFailure_restoresOriginalContentAndRethrows() {
        val events = mutableListOf<String>()
        val error = runCatching {
            replaceWithRollback(
                backup = { events += "backup" },
                replace = {
                    events += "replace"
                    throw IllegalStateException("write failed")
                },
                restore = { events += "restore" },
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertEquals(listOf("backup", "replace", "restore"), events)
    }

    @Test
    fun backupFailure_doesNotAttemptReplacement() {
        var replacementStarted = false

        val error = runCatching {
            replaceWithRollback(
                backup = { throw IllegalStateException("backup failed") },
                replace = { replacementStarted = true },
                restore = {},
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertFalse(replacementStarted)
    }

    @Test
    fun restoreFailure_isAttachedToOriginalWriteFailure() {
        val result = runCatching {
            replaceWithRollback(
                backup = {},
                replace = { throw IllegalStateException("write failed") },
                restore = { throw IllegalArgumentException("restore failed") },
            )
        }.exceptionOrNull()

        assertTrue(result is IllegalStateException)
        assertEquals(1, result?.suppressed?.size)
        assertTrue(result?.suppressed?.first() is IllegalArgumentException)
    }
}
