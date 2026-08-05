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
            "https://music.youtube.com/watch?v=1",
            "https://youtu.be/abc",
        ).forEach { assertEquals(it, Route.YouTube(it), LinkRouter.classify(it)) }
        assertEquals(Route.Other("https://notyoutube.com/x"), LinkRouter.classify("https://notyoutube.com/x"))
        assertEquals(Route.Other("https://www.instagram.com/reel/1/"), LinkRouter.classify("https://www.instagram.com/reel/1/"))
    }

    @Test fun unknownHostsNeedResolvingKnownOnesDoNot() {
        assertTrue(LinkRouter.needsResolving("https://bit.ly/3abc"))
        assertTrue(LinkRouter.needsResolving("https://t.co/xyz"))
        listOf(
            "https://youtu.be/a", "https://www.instagram.com/reel/1/", "https://vm.tiktok.com/Z/", "https://www.tiktok.com/@a/video/1",
            "https://x.com/a/status/1", "https://twitter.com/a", "https://m.facebook.com/watch/?v=1", "https://fb.watch/x",
            "https://www.reddit.com/r/a/comments/1/", "https://music.youtube.com/watch?v=1",
        ).forEach { assertFalse(it, LinkRouter.needsResolving(it)) }
    }

    private val nonWeb = listOf(
        "file:///data/data/dev.wolzard.recon/databases/recon.db", "file://host/x", "intent://scan/#Intent;scheme=zxing;end",
        "javascript:alert(1)", "content://media/external/video/1", "ftp://example.com/a.mp4", "-o/etc/x", "//example.com/a",
    )

    @Test fun onlyHttpAndHttpsAreWebUrls() {
        assertTrue(LinkRouter.isWebUrl("https://youtu.be/abc"))
        assertTrue(LinkRouter.isWebUrl("HTTP://www.instagram.com/reel/x/"))
        nonWeb.forEach { assertFalse(it, LinkRouter.isWebUrl(it)) }
        assertFalse(LinkRouter.isWebUrl("https://localhost/x"))   // no dotted host
    }

    @Test fun nonWebSchemesAreNeverExtracted() {
        nonWeb.forEach { assertNull(it, LinkRouter.extractUrl("look $it")) }
        assertEquals("https://youtu.be/a", LinkRouter.extractUrl("javascript:void(0) then https://youtu.be/a"))
    }

    @Test fun validLinkIsTheFirstWebLinkOrNull() {
        assertEquals("https://youtu.be/a", LinkRouter.validLink("watch this https://youtu.be/a!"))
        assertNull(LinkRouter.validLink("no link here"))
        assertNull(LinkRouter.validLink("javascript:alert(1) intent://x#Intent;end file:///data/x"))
        assertNull(LinkRouter.validLink("https://localhost/x")) // no dot in the host: not a real host
    }

    @Test fun validLinkIsStableWhenRunTwice() { // share-time check, then the Start-time check on its result
        val first = LinkRouter.validLink("see https://www.instagram.com/reel/abc/).")!!
        assertEquals(first, LinkRouter.validLink(first))
    }
}
