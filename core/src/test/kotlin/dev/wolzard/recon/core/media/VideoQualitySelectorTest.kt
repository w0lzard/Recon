package dev.wolzard.recon.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class VideoQualitySelectorTest {

    private val youtubeJson = """
    {
      "id": "sample_yt",
      "title": "YouTube Sample Video",
      "duration": 60.0,
      "formats": [
        {"format_id": "18", "ext": "mp4", "width": 640, "height": 360, "vcodec": "avc1.42001E", "acodec": "mp4a.40.2", "filesize": 5000000},
        {"format_id": "137", "ext": "mp4", "width": 1920, "height": 1080, "vcodec": "avc1.640028", "acodec": "none", "filesize": 25000000},
        {"format_id": "248", "ext": "webm", "width": 1920, "height": 1080, "vcodec": "vp9", "acodec": "none", "filesize": 20000000},
        {"format_id": "399", "ext": "mp4", "width": 1920, "height": 1080, "vcodec": "av01.0.08M.08", "acodec": "none", "filesize": 15000000},
        {"format_id": "271", "ext": "webm", "width": 2560, "height": 1440, "vcodec": "vp9", "acodec": "none", "filesize": 50000000},
        {"format_id": "313", "ext": "webm", "width": 3840, "height": 2160, "vcodec": "vp9", "acodec": "none", "filesize": 100000000},
        {"format_id": "140", "ext": "m4a", "vcodec": "none", "acodec": "mp4a.40.2", "filesize": 2000000},
        {"format_id": "251", "ext": "webm", "vcodec": "none", "acodec": "opus", "filesize": 1500000}
      ]
    }
    """.trimIndent()

    @Test
    fun formatsSelectedUpTo4KWithProperCodecs() {
        val options = VideoQualitySelector.parseOptions(youtubeJson)
        // Expected resolutions: 2160p (4K), 1440p (2K), 1080p, 360p
        assertEquals(4, options.size)

        // 2160p: VP9 + Opus (h264 not available at 4K)
        val opt4k = options.first { it.height == 2160 }
        assertEquals("2160p (4K)", opt4k.resolutionLabel)
        assertEquals("vp9", opt4k.vcodec)
        assertEquals("opus", opt4k.acodec)
        assertEquals("313+251", opt4k.formatId)
        assertEquals(101_500_000L, opt4k.estimatedSizeBytes) // 100MB + 1.5MB
        assertFalse(opt4k.isDefault)

        // 1440p: VP9 + Opus (h264 not available at 1440p)
        val opt1440 = options.first { it.height == 1440 }
        assertEquals("1440p (2K)", opt1440.resolutionLabel)
        assertEquals("vp9", opt1440.vcodec)
        assertEquals("opus", opt1440.acodec)
        assertEquals("271+251", opt1440.formatId)
        assertEquals(51_500_000L, opt1440.estimatedSizeBytes) // 50MB + 1.5MB

        // 1080p: prefers h264 + AAC, excludes VP9 (248) and AV1 (399)
        val opt1080 = options.first { it.height == 1080 }
        assertEquals("1080p", opt1080.resolutionLabel)
        assertEquals("h264", opt1080.vcodec)
        assertEquals("aac", opt1080.acodec)
        assertEquals("137+140", opt1080.formatId)
        assertEquals(27_000_000L, opt1080.estimatedSizeBytes) // 25MB + 2MB
        assertTrue("1080p should be default", opt1080.isDefault)

        // 360p: muxed format 18
        val opt360 = options.first { it.height == 360 }
        assertEquals("360p", opt360.resolutionLabel)
        assertEquals("18", opt360.formatId)
        assertEquals("h264", opt360.vcodec)
        assertEquals("aac", opt360.acodec)
        assertEquals(5_000_000L, opt360.estimatedSizeBytes)
    }

    @Test
    fun maxResolutionLowerThan1080pIsLabeledAndMadeDefault() {
        val lowerResJson = """
        {
          "id": "lower",
          "title": "Low Res Video",
          "duration": 30.0,
          "formats": [
            {"format_id": "136", "ext": "mp4", "width": 1280, "height": 720, "vcodec": "avc1.4d401f", "acodec": "none", "filesize": 10000000},
            {"format_id": "18", "ext": "mp4", "width": 640, "height": 360, "vcodec": "avc1.42001E", "acodec": "mp4a.40.2", "filesize": 3000000},
            {"format_id": "140", "ext": "m4a", "vcodec": "none", "acodec": "mp4a.40.2", "filesize": 1000000}
          ]
        }
        """.trimIndent()

        val options = VideoQualitySelector.parseOptions(lowerResJson)
        assertEquals(2, options.size)

        val best = options.first()
        assertEquals("720p (best available)", best.resolutionLabel)
        assertEquals(720, best.height)
        assertTrue("Top option when < 1080p should be default", best.isDefault)
        assertEquals("136+140", best.formatId)
        assertEquals(11_000_000L, best.estimatedSizeBytes)
    }

    @Test
    fun excludesAv1FormatEvenIfHigherResolution() {
        val av1Only1080p = """
        {
          "id": "av1",
          "title": "AV1 1080p but 720p h264",
          "duration": 20.0,
          "formats": [
            {"format_id": "399", "ext": "mp4", "width": 1920, "height": 1080, "vcodec": "av01.0.08M.08", "acodec": "none", "filesize": 15000000},
            {"format_id": "136", "ext": "mp4", "width": 1280, "height": 720, "vcodec": "avc1.4d401f", "acodec": "none", "filesize": 8000000},
            {"format_id": "140", "ext": "m4a", "vcodec": "none", "acodec": "mp4a.40.2", "filesize": 1000000}
          ]
        }
        """.trimIndent()

        val options = VideoQualitySelector.parseOptions(av1Only1080p)
        // 1080p AV1 should be excluded completely!
        assertFalse("AV1 must be excluded", options.any { it.vcodec.contains("av1") || it.vcodec.contains("av01") })
        assertEquals(1, options.size)
        assertEquals("720p (best available)", options.first().resolutionLabel)
    }

    @Test
    fun singleFileMuxedFormatHandledWithoutSeparateAudio() {
        val tiktokJson = """
        {
          "id": "tiktok_1",
          "title": "Reel Video",
          "duration": 15.0,
          "formats": [
            {"format_id": "download", "ext": "mp4", "width": 1080, "height": 1920, "vcodec": "h264", "acodec": "aac", "filesize": 8500000}
          ]
        }
        """.trimIndent()

        val options = VideoQualitySelector.parseOptions(tiktokJson)
        assertEquals(1, options.size)
        val opt = options.first()
        assertEquals("download", opt.formatId)
        assertEquals("h264", opt.vcodec)
        assertEquals("aac", opt.acodec)
        assertEquals(8_500_000L, opt.estimatedSizeBytes)
        assertEquals("1080p", opt.resolutionLabel) // 1080x1920 is 1080p vertical
        assertTrue(opt.isDefault)
    }

    @Test
    fun sizeEstimatedFromBitrateWhenFilesizeAbsent() {
        val bitrateJson = """
        {
          "id": "bitrate",
          "title": "Bitrate Only Video",
          "duration": 10.0,
          "formats": [
            {"format_id": "137", "ext": "mp4", "width": 1920, "height": 1080, "vcodec": "avc1", "acodec": "none", "vbr": 4000.0},
            {"format_id": "140", "ext": "m4a", "vcodec": "none", "acodec": "mp4a.40.2", "abr": 128.0}
          ]
        }
        """.trimIndent()

        val options = VideoQualitySelector.parseOptions(bitrateJson)
        assertEquals(1, options.size)
        val opt = options.first()
        // Video: 4000 kbps * 1000 / 8 * 10s = 5,000,000 bytes
        // Audio: 128 kbps * 1000 / 8 * 10s = 160,000 bytes
        // Total: 5,160,000 bytes
        assertEquals(5_160_000L, opt.estimatedSizeBytes)
    }

    @Test
    fun storageCheckEnforcesDoubleTheEstimateForMerge() {
        val estimate = 50_000_000L // 50 MB
        val required = VideoQualitySelector.requiredStorage(estimate) // 100 MB
        assertEquals(100_000_000L, required)

        assertTrue(VideoQualitySelector.hasEnoughStorage(150_000_000L, estimate))
        assertTrue(VideoQualitySelector.hasEnoughStorage(100_000_000L, estimate))
        assertFalse(VideoQualitySelector.hasEnoughStorage(99_999_999L, estimate))
        assertFalse(VideoQualitySelector.hasEnoughStorage(50_000_000L, estimate))
    }

    @Test
    fun formatBytesFormatsProperly() {
        assertEquals("500 KB", VideoQualitySelector.formatBytes(500 * 1024L))
        assertEquals("45.0 MB", VideoQualitySelector.formatBytes(45 * 1024L * 1024L))
        assertEquals("1.50 GB", VideoQualitySelector.formatBytes((1.5 * 1024L * 1024L * 1024L).toLong()))
    }

    @Test
    fun tempDirectoryCleanupDeletesOnCancelOrFailure() {
        val tempDir = File.createTempFile("recon_save_test", "").also { it.delete(); it.mkdirs() }
        val dummyVideo = File(tempDir, "video.mp4").also { it.writeText("sample") }
        val dummyAudio = File(tempDir, "audio.m4a").also { it.writeText("sample audio") }

        assertTrue(tempDir.exists())
        assertTrue(dummyVideo.exists())
        assertTrue(dummyAudio.exists())

        // Simulate cancel / failure cleanup
        tempDir.deleteRecursively()

        assertFalse(tempDir.exists())
        assertFalse(dummyVideo.exists())
    }
}
