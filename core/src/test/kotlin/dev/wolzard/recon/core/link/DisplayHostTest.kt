package dev.wolzard.recon.core.link

import dev.wolzard.recon.core.model.Job
import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayHostTest {
    private fun job(text: String = "", url: String? = null, localUri: String? = null) =
        Job("j", text, url = url, localUri = localUri, createdAt = 0, updatedAt = 0)

    @Test fun hostComesFromTheSharedTextWithWwwAndMStripped() {
        assertEquals("instagram.com", job("look https://www.instagram.com/reel/abc/").displayHost())
        assertEquals("youtube.com", job("https://m.youtube.com/watch?v=1").displayHost())
    }

    @Test fun anExplicitUrlWinsOverTheText() = assertEquals("x.com", job("https://tiktok.com/a", url = "https://x.com/a/status/1").displayHost())

    @Test fun aSharedFileIsCalledSharedVideo() = assertEquals("Shared video", job(localUri = "/files/shared/x/video.mp4").displayHost())

    @Test fun noLinkFallsBackToLink() = assertEquals("Link", job("no url here").displayHost())
}
