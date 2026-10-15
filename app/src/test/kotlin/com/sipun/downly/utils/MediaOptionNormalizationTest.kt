package com.sipun.downly.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaOptionNormalizationTest {
    @Test
    fun videoResolutionUsesCommonLevelsAndSupportsPortraitVideo() {
        assertEquals(1080, commonVideoResolution(1080, 1920))
        assertEquals(1080, commonVideoResolution(1920, 1080))
        assertEquals(720, commonVideoResolution(1280, 960))
        assertEquals(480, commonVideoResolution(854, 480))
        assertNull(commonVideoResolution(320, 200))
    }

    @Test
    fun audioBitrateMapsToNearestCommonLevel() {
        assertEquals(128, commonAudioBitrate(129))
        assertEquals(128, commonAudioBitrate(122))
        assertEquals(64, commonAudioBitrate(70))
        assertEquals(48, commonAudioBitrate(52))
        assertEquals(320, commonAudioBitrate(320))
        assertNull(commonAudioBitrate(0))
    }
}
