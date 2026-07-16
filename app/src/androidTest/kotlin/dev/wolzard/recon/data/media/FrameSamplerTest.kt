package dev.wolzard.recon.data.media

import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.wolzard.recon.core.model.AnalyzerException
import dev.wolzard.recon.core.model.FailureReason
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class FrameSamplerTest {
    private val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val dir = File(ctx.cacheDir, "jobs/sampler-test").apply { deleteRecursively(); mkdirs() }

    @After fun clean() { dir.deleteRecursively() }

    private fun asset(name: String) = File(dir, name).apply {
        InstrumentationRegistry.getInstrumentation().context.assets.open(name).use { i -> outputStream().use { i.copyTo(it) } }
    }

    @Test fun tenSecondClipGivesUpToFiveSmallFramesAndAudio() = runBlocking {
        val out = AndroidFrameSampler().sample(asset("sample_10s.mp4"), File(dir, "frames"))
        assertTrue("frames=${out.frames.size}", out.frames.size in 1..5)
        out.frames.forEach { f ->
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.file.path, o)
            assertTrue(maxOf(o.outWidth, o.outHeight) in 1..768)
        }
        assertEquals(out.frames.sortedBy { it.timestampMs }, out.frames)
        assertTrue(out.audio!!.length() > 0)
    }

    @Test fun notAVideoIsUnreadable() = runBlocking {
        val junk = File(dir, "junk.mp4").apply { writeText("not a video") }
        try {
            AndroidFrameSampler().sample(junk, File(dir, "frames")); fail()
        } catch (e: AnalyzerException) {
            assertEquals(FailureReason.VIDEO_UNREADABLE, e.reason)
        }
    }
}
