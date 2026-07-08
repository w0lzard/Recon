package dev.wolzard.recon.data.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File

class YoutubeDlRunnerTest {
    private val args = YtDlpFetcher.buildArgs(File("/tmp/job"))

    @Test fun urlComesLastAfterDoubleDash() {
        val cmd = YoutubeDlRunner.request("https://www.instagram.com/reel/x/", args).buildCommand()
        assertEquals(listOf("--", "https://www.instagram.com/reel/x/"), cmd.takeLast(2))
        assertEquals(1, cmd.count { it == "--" })
        assertEquals(1, cmd.count { it == "https://www.instagram.com/reel/x/" })
    }

    @Test fun nonWebLinksNeverReachYtDlp() {
        listOf("file:///data/data/dev.wolzard.recon/shared_prefs/x.xml", "intent://x#Intent;end", "javascript:alert(1)",
            "--exec=rm -rf /", "-o/sdcard/x", "content://x/1").forEach {
            assertThrows(it, YtDlpException::class.java) { YoutubeDlRunner.request(it, args) }
        }
    }
}
