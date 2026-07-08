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
        assertTrue(stream.reads >= data.size / (8 * 1024))
        assertEquals(1f, progress.last(), 0f)
    }

    // Security: a share must not make Recon read (and upload) its own private files (confused deputy).

    private fun fetcherWithRoot(root: java.io.File, opened: MutableList<String>) = ContentUriFetcher(
        open = { opened += it; ByteArrayInputStream(ByteArray(4)) },
        describe = { "video/mp4" to 4L },
        allowedLocalRoot = root,
        openFile = { opened += it.path; it.inputStream() },
    )

    @Test fun fileUrisOutsideTheSharedRootAreRefused() = runTest {
        val app = tmp.newFolder("app")
        val shared = java.io.File(app, "shared").apply { mkdirs() }
        val secret = java.io.File(app, "shared_prefs/secrets.xml").apply { parentFile!!.mkdirs(); writeText("x") }
        val opened = mutableListOf<String>()
        val f = fetcherWithRoot(shared, opened)
        for (src in listOf(
            "file://${secret.path}",
            secret.path,
            "file://${shared.path}/../shared_prefs/secrets.xml",
            "${shared.path}/../shared_prefs/secrets.xml",
            "http://example.com/v.mp4",
            "FILE://${secret.path}",
            "file://${shared.path}/%2e%2e/shared_prefs/secrets.xml",       // decoded ".." must not escape
            "file://${shared.path}/%2E%2E/shared_prefs/secrets.xml",
            "${shared.path}/%2e%2e/shared_prefs/secrets.xml",
            "file://evil.host${shared.path}/job1.mp4",
        )) {
            try {
                f.fetch(src, tmp.newFolder()); org.junit.Assert.fail(src)
            } catch (e: dev.wolzard.recon.core.model.AnalyzerException) {
                assertEquals(dev.wolzard.recon.core.model.FailureReason.DOWNLOAD_FAILED, e.reason)
            }
        }
        assertTrue("nothing may be opened: $opened", opened.isEmpty())
    }

    @Test fun contentUrisAndFilesInsideTheSharedRootAreAllowed() = runTest {
        val shared = tmp.newFolder("shared")
        val copy = java.io.File(shared, "job1.mp4").apply { writeBytes(ByteArray(4)) }
        val opened = mutableListOf<String>()
        val f = fetcherWithRoot(shared, opened)
        f.fetch("content://com.whatsapp.provider.media/item/1", tmp.newFolder())
        f.fetch(copy.path, tmp.newFolder())
        f.fetch("file://${copy.path}", tmp.newFolder())
        assertEquals(listOf("content://com.whatsapp.provider.media/item/1", copy.canonicalPath, copy.canonicalPath), opened)
    }

    @Test fun withoutAllowedRootOnlyContentUris() = runTest {
        val opened = mutableListOf<String>()
        val f = ContentUriFetcher(open = { opened += it; ByteArrayInputStream(ByteArray(1)) }, describe = { null to null }, allowedLocalRoot = null)
        try { f.fetch("/sdcard/x.mp4", tmp.newFolder()); org.junit.Assert.fail() } catch (_: dev.wolzard.recon.core.model.AnalyzerException) { }
        assertTrue(opened.isEmpty())
    }

    @Test fun oversizedCopyIsRefusedAndRemoved() = runTest {
        val f = ContentUriFetcher(open = { ByteArrayInputStream(ByteArray(10_000)) }, describe = { null to null }, allowedLocalRoot = null, maxBytes = 4_096)
        val dir = tmp.newFolder()
        try { f.fetch("content://x/1", dir); org.junit.Assert.fail() } catch (e: dev.wolzard.recon.core.model.AnalyzerException) {
            assertEquals(dev.wolzard.recon.core.model.FailureReason.DOWNLOAD_FAILED, e.reason)
        }
        assertTrue(dir.listFiles()!!.isEmpty())
    }
}
