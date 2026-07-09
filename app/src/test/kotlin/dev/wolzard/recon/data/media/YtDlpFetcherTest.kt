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
