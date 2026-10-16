package com.sipun.downly.core.notification

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadNotificationFormatterTest {
    @Test
    fun progressTextIncludesPercentAndDetail() {
        assertEquals(
            "68% • 17.42 MiB / 25.61 MiB • 7.39 MiB/s • ETA 00:01",
            NotificationProgressFormatter.progressText(
                percent = 68,
                detail = "17.42 MiB / 25.61 MiB",
                speed = "7.39 MiB/s",
                eta = "00:01"
            )
        )
    }
}
