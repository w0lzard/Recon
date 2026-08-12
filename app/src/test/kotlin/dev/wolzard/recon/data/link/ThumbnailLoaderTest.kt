package dev.wolzard.recon.data.link

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThumbnailLoaderTest {
    private val server = MockWebServer()
    @Before fun start() = server.start()
    @After fun stop() = server.close()

    @Test fun nonHttpsThumbnailIsNeverRequested() = runTest {
        server.enqueue(MockResponse.Builder().body("image").build())
        val loader = ThumbnailLoader(OkHttpClient())
        assertNull(loader.load(server.url("/x.jpg").toString()))   // MockWebServer is plain http
        assertNull(loader.load("file:///sdcard/x.jpg"))
        assertNull(loader.load(null))
        assertEquals(0, server.requestCount)
    }

    @Test fun oversizedBodyIsRefused() {
        val max = ThumbnailLoader.MAX_BYTES
        assertNull(ThumbnailLoader.readCapped(Buffer().write(ByteArray(max + 1)), contentLength = -1))      // no length header, too long
        assertNull(ThumbnailLoader.readCapped(Buffer().write(ByteArray(10)), contentLength = max + 1L))     // declared too long: not read
        assertEquals(max, ThumbnailLoader.readCapped(Buffer().write(ByteArray(max)), contentLength = max.toLong())!!.size)
        assertNotNull(ThumbnailLoader.readCapped(Buffer().write(ByteArray(100)), contentLength = -1))
    }

    @Test fun oversizedDimensionsAreRefusedAndLargeImagesDownsampled() {
        assertFalse(ThumbnailLoader.boundsOk(ThumbnailLoader.MAX_SIDE + 1, 100))
        assertFalse(ThumbnailLoader.boundsOk(0, 100))
        assertTrue(ThumbnailLoader.boundsOk(1280, 720))
        assertEquals(1, ThumbnailLoader.sampleSize(480, 360))
        assertEquals(2, ThumbnailLoader.sampleSize(1280, 720))   // 720/2 = 360 ≥ 256, 720/4 = 180 < 256
        assertEquals(8, ThumbnailLoader.sampleSize(4000, 3000))
    }
}
