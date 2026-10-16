package dev.wolzard.recon.data.media

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@LargeTest
@RunWith(AndroidJUnit4::class)
class RealSaveTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val dir = File(ctx.cacheDir, "real_save_test").apply { deleteRecursively(); mkdirs() }
    private val createdUris = mutableListOf<Uri>()

    @After
    fun tearDown() {
        dir.deleteRecursively()
        createdUris.forEach { ctx.contentResolver.delete(it, null, null) }
    }

    private fun verifyPlayback(file: File) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.path)
            val hasVideo = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)
            val hasAudio = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L

            assertEquals("yes", hasVideo)
            assertEquals("yes", hasAudio)
            assertTrue("Duration should be > 0: $duration", duration > 0)
        } finally {
            retriever.release()
        }
    }

    private fun verifyPlaybackUri(uri: Uri) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(ctx, uri)
            val hasVideo = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)
            val hasAudio = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L

            assertEquals("yes", hasVideo)
            assertEquals("yes", hasAudio)
            assertTrue("Duration should be > 0: $duration", duration > 0)
        } finally {
            retriever.release()
        }
    }

    @Test
    fun save1080pYouTubeVideoAndMergeWithFfmpeg() = runBlocking {
        val url = "https://www.youtube.com/watch?v=Psy_4mEWNDI"
        val runner = YoutubeDlRunner(ctx)
        val fetcher = VideoFormatFetcher(runner)

        val options = fetcher.fetchOptions(url)
        assertTrue("Options should not be empty", options.isNotEmpty())

        val chosen = options.firstOrNull { it.height == 1080 } ?: options.first()
        val destDir = File(dir, "yt_1080").apply { mkdirs() }
        val args = listOf(
            "-f", chosen.formatId,
            "--no-playlist",
            "-o", "${destDir.path}/output.%(ext)s",
            "--print", "after_move:%(filepath)s",
            "--no-simulate",
        )
        val stdout = runner.run(url, args, "save-1080") {}
        val line = stdout.lines().lastOrNull { it.isNotBlank() && !it.startsWith("ERROR") && !it.startsWith("WARNING") }
        val file = line?.trim()?.let(::File)?.takeIf { it.exists() }
            ?: destDir.listFiles()?.firstOrNull { it.isFile && it.length() > 0 }!!

        assertTrue("Merged file exists and has size: ${file.length()}", file.length() > 0)
        verifyPlayback(file)

        // Save to MediaStore
        val uri = MediaStoreSaver.saveToMediaStore(ctx, file, "YT_1080_Merged")
        assertNotNull(uri)
        createdUris += uri!!
        verifyPlaybackUri(uri)
    }

    @Test
    fun probe4kVideoReturnsVp9AndOpusCodec() = runBlocking {
        // Big Buck Bunny 4K
        val url = "https://www.youtube.com/watch?v=aqz-KE-bpKQ"
        val runner = YoutubeDlRunner(ctx)
        val fetcher = VideoFormatFetcher(runner)

        val options = fetcher.fetchOptions(url)
        assertTrue("Options should not be empty", options.isNotEmpty())

        val opt4k = options.firstOrNull { it.height >= 2160 }
        assertNotNull("4K option must exist for Big Buck Bunny", opt4k)
        assertEquals("vp9", opt4k!!.vcodec)
        assertEquals("opus", opt4k.acodec)
        assertTrue("Estimated size must be > 0", opt4k.estimatedSizeBytes > 0)
    }

    @Test
    fun saveSingleFileVideoAndPlayBack() = runBlocking {
        val url = "https://www.youtube.com/watch?v=Psy_4mEWNDI"
        val runner = YoutubeDlRunner(ctx)
        val fetcher = VideoFormatFetcher(runner)
        val options = fetcher.fetchOptions(url)
        assertTrue("Options should not be empty", options.isNotEmpty())
        val chosen = options.last()
        val destDir = File(dir, "single_save").apply { mkdirs() }
        val args = listOf(
            "-f", chosen.formatId,
            "--no-playlist",
            "-o", "${destDir.path}/output.%(ext)s",
            "--print", "after_move:%(filepath)s",
            "--no-simulate",
        )
        val stdout = runner.run(url, args, "save-single") {}
        val line = stdout.lines().lastOrNull { it.isNotBlank() && !it.startsWith("ERROR") && !it.startsWith("WARNING") }
        val file = line?.trim()?.let(::File)?.takeIf { it.exists() }
            ?: destDir.listFiles()?.firstOrNull { it.isFile && it.length() > 0 }!!

        assertTrue("Single file exists and has size: ${file.length()}", file.length() > 0)
        verifyPlayback(file)

        val uri = MediaStoreSaver.saveToMediaStore(ctx, file, "Single_File_Saved")
        assertNotNull(uri)
        createdUris += uri!!
        verifyPlaybackUri(uri)
    }
}
