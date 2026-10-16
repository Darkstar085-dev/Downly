package com.sipun.downly.core.notification

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadNotificationNavigationTest {
    @Test
    fun pausedResumeUsesHistoryDestinationExtra() {
        assertEquals("open_history", DownloadNotificationHelper.EXTRA_OPEN_HISTORY)
    }
}
