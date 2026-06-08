package dev.wolzard.recon.core.link

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkRouterTest {
    @Test fun trailingPunctuationIsTrimmed() {
        assertEquals("https://youtu.be/abc?si=x", LinkRouter.extractUrl("check this https://youtu.be/abc?si=x)."))
        assertEquals("https://www.instagram.com/reel/DeJ_pr9CNuO/", LinkRouter.extractUrl("wow https://www.instagram.com/reel/DeJ_pr9CNuO/…"))
        assertEquals("https://x.com/a/status/1", LinkRouter.extractUrl("(see https://x.com/a/status/1)"))
    }

    @Test fun balancedParenthesesAreKept() {
        assertEquals("https://en.wikipedia.org/wiki/Mercury_(planet)", LinkRouter.extractUrl("https://en.wikipedia.org/wiki/Mercury_(planet)."))
    }

    @Test fun firstOfSeveralUrls() {
        assertEquals("https://a.com/1", LinkRouter.extractUrl("https://a.com/1 and http://b.com/2"))
    }

    @Test fun noUrlOrSchemelessGivesNull() {
        assertNull(LinkRouter.extractUrl("just some text"))
        assertNull(LinkRouter.extractUrl("youtube.com/watch?v=abc"))
        assertNull(LinkRouter.extractUrl("ftp://files.example.com/x"))
    }

    @Test fun youtubeHostsClassify() {
        listOf(
            "https://m.youtube.com/shorts/x",
            "https://www.youtube.com/watch?v=Psy_4mEWNDI",
            "https://youtube.com/shorts/abc",
