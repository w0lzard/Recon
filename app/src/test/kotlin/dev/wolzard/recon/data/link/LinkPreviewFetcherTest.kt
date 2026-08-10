package dev.wolzard.recon.data.link

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkPreviewFetcherTest {
    private val info = """{"id":"x","title":"Glass Harbour","duration":38.4,"thumbnail":"https://i.ytimg.com/vi/x/hq.jpg","formats":[]}"""

    @Test fun parsesTitleLengthAndThumbnail() = runTest {
        val p = LinkPreviewFetcher({ "WARNING: something\n$info\n" }).fetch("https://youtu.be/x")!!
        assertEquals("Glass Harbour", p.title)
        assertEquals(38L, p.durationSec)
        assertEquals("https://i.ytimg.com/vi/x/hq.jpg", p.thumbnailUrl)
    }

    @Test fun timeoutGivesNullAfterFifteenSecondsAndCancelsTheCall() = runTest {
        var cancelled = false
        val f = LinkPreviewFetcher({ try { awaitCancellation() } finally { cancelled = true } })
        assertNull(f.fetch("https://youtu.be/x"))
        assertEquals(LinkPreviewFetcher.TIMEOUT_MS, currentTime)
        assertTrue(cancelled) // YoutubeDlRunner kills the yt-dlp process on cancellation
    }

    @Test fun errorsAndUnusableOutputGiveNull() = runTest {
        assertNull(LinkPreviewFetcher({ throw IllegalStateException("yt-dlp failed") }).fetch("https://youtu.be/x"))
        assertNull(LinkPreviewFetcher({ "not json" }).fetch("https://youtu.be/x"))
        assertNull(LinkPreviewFetcher({ """{"id":"x"}""" }).fetch("https://youtu.be/x"))
    }

    @Test fun nonHttpsThumbnailIsDropped() {
        listOf("http://cdn.example/x.jpg", "file:///sdcard/x.jpg", "content://media/x", "javascript:alert(1)", "//cdn.example/x.jpg").forEach { u ->
            val p = LinkPreviewFetcher.parse("""{"title":"T","thumbnail":"$u"}""")!!
            assertNull(u, p.thumbnailUrl)
            assertEquals("T", p.title)
        }
    }

    @Test fun titleIsCleanedAndCapped() {
        val p = LinkPreviewFetcher.parse("""{"title":"  Line one\nline\ttwo\u0007  "}""")!!
        assertEquals("Line one line two", p.title)
        val long = LinkPreviewFetcher.parse("""{"title":"${"x".repeat(500)}"}""")!!.title!!
        assertEquals(LinkPreviewFetcher.MAX_TITLE, long.length)
        assertTrue(long.endsWith("…"))
    }

    @Test fun badDurationIsIgnored() {
        assertNull(LinkPreviewFetcher.parse("""{"title":"T","duration":-3}""")!!.durationSec)
        assertNull(LinkPreviewFetcher.parse("""{"title":"T","duration":"38"}""")!!.durationSec)
        assertNull(LinkPreviewFetcher.parse("""{"title":"T","duration":1e9}""")!!.durationSec)
    }
}
