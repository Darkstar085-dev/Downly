package com.sipun.downly.core.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadWorkManagerTest {
    @Test
    fun pauseRequestIsConsumedByTheDownloadWorkerOnce() {
        val id = "pause-test-id"

        DownloadWorkManager.requestPause(id)

        assertTrue(DownloadWorkManager.consumePauseRequest(id))
        assertFalse(DownloadWorkManager.consumePauseRequest(id))
    }
}
