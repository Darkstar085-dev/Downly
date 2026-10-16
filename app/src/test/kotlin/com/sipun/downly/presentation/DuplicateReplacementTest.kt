package com.sipun.downly.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.sipun.downly.data.DownloadRecord
import com.sipun.downly.data.DownloadStatus
import com.sipun.downly.data.DuplicateDownloadPrompt
import com.sipun.downly.data.MediaMode

class DuplicateReplacementTest {
    @Test
    fun duplicateChoiceIsAvailableOnlyForTheWaitingRecord() {
        val prompt = DuplicateDownloadPrompt("waiting", "/tmp/source", "content://existing", "Download/Downly/Videos/item.mp4", MediaMode.VIDEO, "item.mp4")
        val waiting = DownloadRecord(id = "waiting", url = "url", title = "Waiting", status = DownloadStatus.FAILED)
        val other = DownloadRecord(id = "other", url = "url", title = "Other", status = DownloadStatus.FAILED)

        assertEquals(prompt, duplicateChoiceFor(waiting, prompt))
        assertEquals(null, duplicateChoiceFor(other, prompt))
    }

    @Test
    fun workerRefreshRestoresPromptOnlyWhenItsRecordIsStillInHistory() {
        val prompt = DuplicateDownloadPrompt("waiting", "/tmp/source", "content://existing", "Download/Downly/Videos/item.mp4", MediaMode.VIDEO, "item.mp4")
        val waiting = DownloadRecord(id = "waiting", url = "url", title = "Waiting", status = DownloadStatus.FAILED)

        assertEquals(prompt, duplicatePromptForHistory(listOf(waiting), prompt))
        assertEquals(null, duplicatePromptForHistory(emptyList(), prompt))
    }

    @Test
    fun replacementRemovesOlderHistoryEntryForTheSamePublishedPath() {
        val old = DownloadRecord(id = "old", url = "old", title = "Old", status = DownloadStatus.COMPLETE, filePath = "Download/Downly/Videos/item.mp4")
        val current = DownloadRecord(id = "current", url = "new", title = "New", status = DownloadStatus.COMPLETE, filePath = "Download/Downly/Videos/item.mp4")

        val result = reconcileReplacedHistory(listOf(current, old), current, "Download/Downly/Videos/item.mp4")

        assertEquals(listOf(current), result)
    }


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
