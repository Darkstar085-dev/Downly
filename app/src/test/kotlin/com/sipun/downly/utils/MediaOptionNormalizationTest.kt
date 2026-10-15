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
    fun bitrateEstimateScalesWithBitrateAndDuration() {
        assertEquals(10_688_000L, estimateBytesFromBitrate(128.0, 668))
        assertEquals(21_376_000L, estimateBytesFromBitrate(256.0, 668))
        assertEquals(21_376_000L, estimateBytesFromBitrate(128.0, 1336))
        assertNull(estimateBytesFromBitrate(0.0, 668))
        assertNull(estimateBytesFromBitrate(128.0, 0))
    }

    @Test
    fun sizeResolutionPrefersExactThenRemoteThenApproximateThenEstimate() {
        assertEquals(1000L, resolveSizeValue(1000L, 2000L, 3000L, 128.0, 60).bytes)
        assertEquals(false, resolveSizeValue(1000L, 2000L, 3000L, 128.0, 60).approximate)
        assertEquals(2000L, resolveSizeValue(0L, 2000L, 3000L, 128.0, 60).bytes)
        assertEquals(false, resolveSizeValue(0L, 2000L, 3000L, 128.0, 60).approximate)
        assertEquals(3000L, resolveSizeValue(0L, null, 3000L, 128.0, 60).bytes)
        assertEquals(true, resolveSizeValue(0L, null, 3000L, 128.0, 60).approximate)
        assertEquals(960000L, resolveSizeValue(0L, null, 0L, 128.0, 60).bytes)
        assertEquals(true, resolveSizeValue(0L, null, 0L, 128.0, 60).approximate)
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
