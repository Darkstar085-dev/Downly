package com.sipun.downly.presentation

import com.sipun.downly.data.DownloadRecord
import com.sipun.downly.data.DownloadStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadQueueTest {
    @Test
    fun enqueueDownloadRecordKeepsPreviouslyQueuedDownloads() {
        val first = DownloadRecord(id = "first", url = "https://example.com/first", title = "First", status = DownloadStatus.QUEUED)
        val second = DownloadRecord(id = "second", url = "https://example.com/second", title = "Second", status = DownloadStatus.QUEUED)

        val queue = enqueueDownloadRecord(listOf(first), second)

        assertEquals(listOf(second, first), queue)
    }

    @Test
    fun mergeWorkerRecordDoesNotRestoreACancelledDownload() {
        val cancelled = DownloadRecord(
            id = "cancelled",
            url = "https://example.com/cancelled",
            title = "Cancelled",
            status = DownloadStatus.DOWNLOADING
        )
        val other = DownloadRecord(
            id = "other",
            url = "https://example.com/other",
            title = "Other",
            status = DownloadStatus.QUEUED
        )

        val history = mergeWorkerRecordIntoHistory(listOf(other), cancelled)

        assertEquals(listOf(other), history)
    }
}
