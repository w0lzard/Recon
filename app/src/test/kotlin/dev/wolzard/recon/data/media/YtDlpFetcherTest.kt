package dev.wolzard.recon.data.media

import dev.wolzard.recon.core.model.AnalyzerException
import dev.wolzard.recon.core.model.FailureReason
import dev.wolzard.recon.core.port.FetchQuality
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class YtDlpFetcherTest {
    @get:Rule val tmp = TemporaryFolder()

    /** Fake yt-dlp: records args, writes the file named by -o and prints "format|path" like --print after_move. */
    private class FakeRunner(val formatId: String = "18", val fail: String? = null) : YtDlpRunner {
        val calls = mutableListOf<List<String>>()
        override suspend fun run(url: String, args: List<String>, processId: String, onProgress: (Float) -> Unit): String {
            calls += args
            fail?.let { throw YtDlpException(it) }
            onProgress(0.5f)
            val template = args[args.indexOf("-o") + 1]
            val ext = if (args[args.indexOf("-f") + 1].startsWith("ba")) "m4a" else "mp4"
            val file = File(template.replace("%(ext)s", ext)).apply { writeBytes(ByteArray(10)) }
            return "[download] 100%\n$formatId|${file.path}\n"
        }
    }

    private fun dest() = tmp.newFolder("job")

    @Test fun buildArgsIsTheExactS3List() {
        val d = File("/cache/jobs/1")
        assertEquals(
            listOf(
                "-f", "b[height<=854][width<=854]/b[height<=1280][width<=1280]/w",
                "--no-playlist",
                "-o", "/cache/jobs/1/video.%(ext)s",
                "--print", "after_move:%(format_id)s|%(filepath)s", "--no-simulate",
            ),
            YtDlpFetcher.buildArgs(d),
        )
    }

    @Test fun youtubeFallbackIsAvc1FirstNeverAv1() {
        val video = YtDlpFetcher.youTubeVideoArgs(File("/d"), FetchQuality.DEFAULT, avoid = null)
        val f = video[video.indexOf("-f") + 1]
        assertTrue(f.contains("[vcodec^=avc1]"))
        assertTrue(f.contains("[vcodec!^=av01]"))
        assertEquals("bv*[vcodec^=avc1][height<=854][width<=854]/bv*[vcodec!^=av01][height<=854][width<=854]", f)
        val audio = YtDlpFetcher.youTubeAudioArgs(File("/d"))
        assertEquals("ba[ext=m4a]", audio[audio.indexOf("-f") + 1])
        assertTrue(audio.contains("/d/audio.%(ext)s"))
    }

    @Test fun otherFormatRetryExcludesTheFailedFormat() {
        val args = YtDlpFetcher.buildArgs(File("/d"), FetchQuality.OTHER_FORMAT, avoid = "397")
        assertEquals(
            "bv*[vcodec^=avc1][height<=854][width<=854][format_id!=397]/b[height<=854][width<=854][format_id!=397]/w[format_id!=397]",
            args[args.indexOf("-f") + 1],
        )
    }

    @Test fun sharperUsesThe720pCap() {
        val args = YtDlpFetcher.buildArgs(File("/d"), FetchQuality.SHARPER, avoid = null)
        assertEquals("b[height<=1280][width<=1280]/w", args[args.indexOf("-f") + 1])
        val yt = YtDlpFetcher.youTubeVideoArgs(File("/d"), FetchQuality.SHARPER, avoid = null)
        assertEquals("bv*[vcodec^=avc1][height<=1280][width<=1280]/bv*[vcodec!^=av01][height<=1280][width<=1280]", yt[yt.indexOf("-f") + 1])
    }

    @Test fun nonYouTubeIsOneMuxedFile() = runTest {
        val runner = FakeRunner()
        val m = YtDlpFetcher(runner).fetch("https://www.instagram.com/reel/x/", dest())
        assertEquals(1, runner.calls.size)
        assertEquals("video.mp4", m.video.name)
        assertNull(m.audio)
        assertEquals("18", m.formatId)
    }

    @Test fun youtubeIsTwoFilesVideoThenAudio() = runTest {
        val runner = FakeRunner(formatId = "135")
        val m = YtDlpFetcher(runner).fetch("https://youtu.be/Psy_4mEWNDI", dest())
        assertEquals(2, runner.calls.size)
        assertEquals("video.mp4", m.video.name)
        assertEquals("audio.m4a", m.audio!!.name)
        assertEquals("135", m.formatId)
    }

    @Test fun ytDlpErrorIsDownloadFailedWithLastStderrLine() = runTest {
        val runner = FakeRunner(fail = "WARNING: ffmpeg-location … does not exist!\nERROR: [Instagram] x: Requested content is not available, rate-limit reached or login required\n")
        try {
            YtDlpFetcher(runner).fetch("https://www.instagram.com/reel/x/", dest()); fail()
        } catch (e: AnalyzerException) {
            assertEquals(FailureReason.DOWNLOAD_FAILED, e.reason)
            assertEquals("ERROR: [Instagram] x: Requested content is not available, rate-limit reached or login required", e.message)
        }
    }

    @Test fun networkErrorsMapToNetwork() = runTest {
        val runner = FakeRunner(fail = "ERROR: [download] Got error: [Errno 7] No address associated with hostname. Giving up after 10 retries")
        try {
            YtDlpFetcher(runner).fetch("https://www.instagram.com/reel/x/", dest()); fail()
        } catch (e: AnalyzerException) {
            assertEquals(FailureReason.NETWORK, e.reason)
        }
    }
}
