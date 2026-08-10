package dev.wolzard.recon.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinkPreviewTest {
    @Test fun durationIsMinutesSecondsUnderAnHour() {
        assertEquals("0:00", LinkPreview.formatDuration(0))
        assertEquals("0:07", LinkPreview.formatDuration(7))
        assertEquals("0:38", LinkPreview.formatDuration(38))
        assertEquals("1:05", LinkPreview.formatDuration(65))
        assertEquals("59:59", LinkPreview.formatDuration(3599))
    }

    @Test fun durationIsHoursMinutesSecondsFromAnHour() {
        assertEquals("1:00:00", LinkPreview.formatDuration(3600))
        assertEquals("1:02:03", LinkPreview.formatDuration(3723))
        assertEquals("12:00:09", LinkPreview.formatDuration(43209))
    }

    @Test fun missingOrNegativeDurationIsNull() {
        assertNull(LinkPreview.formatDuration(null))
        assertNull(LinkPreview.formatDuration(-1))
    }
}
