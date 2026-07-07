package dev.wolzard.recon.data.media

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.InputStream

class ContentUriFetcherTest {
    @get:Rule val tmp = TemporaryFolder()

    private class CountingStream(private val inner: InputStream) : InputStream() {
        var maxRead = 0
        var reads = 0
        override fun read(): Int = inner.read()
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            reads++; maxRead = maxOf(maxRead, len)
            return inner.read(b, off, len)
        }
    }

    @Test fun copiesFiveMegabytesInEightKilobyteChunks() = runTest {
        val data = ByteArray(5 * 1024 * 1024) { (it % 251).toByte() }
        val stream = CountingStream(ByteArrayInputStream(data))
        val progress = mutableListOf<Float>()
        val fetcher = ContentUriFetcher(open = { stream }, describe = { "video/mp4" to data.size.toLong() }, allowedLocalRoot = null)
        val media = fetcher.fetch("content://media/1", tmp.newFolder("job"), onProgress = { progress += it })
        assertArrayEquals(data, media.video.readBytes())
        assertEquals("video.mp4", media.video.name)
        assertEquals(8 * 1024, stream.maxRead)
