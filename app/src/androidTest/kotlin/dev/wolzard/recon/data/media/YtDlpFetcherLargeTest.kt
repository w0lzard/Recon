package dev.wolzard.recon.data.media

import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Manual, needs network: downloads the S3 YouTube Short with the real yt-dlp (T7). */
@LargeTest
@RunWith(AndroidJUnit4::class)
class YtDlpFetcherLargeTest {
    private fun mimes(f: File): List<String> {
        val x = MediaExtractor()
        return try {
            x.setDataSource(f.path)
            (0 until x.trackCount).map { x.getTrackFormat(it).getString(MediaFormat.KEY_MIME)!! }
        } finally { x.release() }
    }

    @Test fun downloadsTheS3ShortAsAvc1VideoPlusM4aAudio() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dir = File(ctx.cacheDir, "jobs/large-test").apply { deleteRecursively() }
        try {
            val m = YtDlpFetcher(YoutubeDlRunner(ctx)).fetch("https://www.youtube.com/watch?v=Psy_4mEWNDI", dir)
            assertTrue(m.video.length() > 0)
            assertEquals(listOf("video/avc"), mimes(m.video))           // never AV1 on API 26
            assertEquals(listOf("audio/mp4a-latm"), mimes(m.audio!!))
            println("LARGE format=${m.formatId} video=${m.video.length()} audio=${m.audio!!.length()}")
        } finally {
            dir.deleteRecursively()
        }
    }
}
