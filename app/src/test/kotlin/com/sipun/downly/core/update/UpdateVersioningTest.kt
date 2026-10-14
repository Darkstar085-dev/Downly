package com.sipun.downly.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateVersioningTest {

    @Test
    fun newerVersion_handlesDifferentSegmentCounts() {
        assertTrue(UpdateVersioning.isNewerVersion("1.2", "1.2.1"))
        assertFalse(UpdateVersioning.isNewerVersion("1.2.1", "1.2"))
        assertFalse(UpdateVersioning.isNewerVersion("1.2.0", "1.2"))
    }

    @Test
    fun newerVersion_ignoresLeadingV() {
        assertTrue(UpdateVersioning.isNewerVersion("v0.1", "v0.2"))
        assertFalse(UpdateVersioning.isNewerVersion("v0.2", "0.2"))
    }

    @Test
    fun releaseNotes_extractsBulletTextAndMarkdownLinks() {
        val notes = """
            ## Changes
            - Added [queue controls](https://example.com/queue)
            - Fixed playback restoration
            Not a bullet
        """.trimIndent()

        assertEquals(
            "Added queue controls\nFixed playback restoration",
            notes.toReleaseNotes(),
        )
    }

    @Test
    fun formatReleaseDate_returnsNullForInvalidInput() {
        assertEquals("Jan 2, 2026", UpdateVersioning.formatReleaseDate("2026-01-02T12:30:00Z"))
        assertEquals(null, UpdateVersioning.formatReleaseDate("not-a-date"))
    }
}
