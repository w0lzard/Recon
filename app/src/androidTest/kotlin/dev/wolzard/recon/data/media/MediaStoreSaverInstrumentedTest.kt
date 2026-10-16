package dev.wolzard.recon.data.media

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class MediaStoreSaverInstrumentedTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private var createdUri: Uri? = null

    @After
    fun tearDown() {
        createdUri?.let { ctx.contentResolver.delete(it, null, null) }
    }

    @Test
    fun savesVideoIntoMoviesReconOnApi29Plus() {
        val testFile = File(ctx.cacheDir, "sample_test.mp4").apply {
            writeBytes("DUMMY_MP4_BYTES_STREAM_COPY".toByteArray())
        }

        val uri = MediaStoreSaver.saveToMediaStore(ctx, testFile, "Recon_MediaStore_Test")
        assertNotNull("MediaStore uri must not be null", uri)
        createdUri = uri

        // Verify content can be read back and matches stream-copied bytes
        val readBytes = ctx.contentResolver.openInputStream(uri!!)!!.use { it.readBytes() }
        assertEquals("DUMMY_MP4_BYTES_STREAM_COPY", String(readBytes))

        // On API 29+, verify RELATIVE_PATH
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val projection = arrayOf(
                MediaStore.Video.Media.RELATIVE_PATH,
                MediaStore.Video.Media.DISPLAY_NAME,
            )
            ctx.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                assertTrue(cursor.moveToFirst())
                val path = cursor.getString(0)
                val name = cursor.getString(1)
                assertTrue("Expected Movies/Recon, got $path", path.contains("Movies/Recon"))
                assertTrue("Expected name to start with Recon_MediaStore_Test, got $name", name.startsWith("Recon_MediaStore_Test"))
            }
        }

        testFile.delete()
    }
}
