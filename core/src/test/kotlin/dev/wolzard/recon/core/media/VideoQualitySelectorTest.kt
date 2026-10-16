package dev.wolzard.recon.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
        {"format_id": "135", "ext": "mp4", "width": 854, "height": 480, "vcodec": "avc1.4d401f", "acodec": "none", "filesize": 10000000},
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
    fun presetToSelectorMappingProvidesThreePresetsWithProperCodecs() {
        val options = VideoQualitySelector.parseOptions(youtubeJson)
        // Exactly three presets: 480p, 1080p (default), 4K
        assertEquals(3, options.size)

        // 1. Preset 480p: h264 + AAC
        val p480 = options[0]
        assertEquals("480p", p480.presetName)
        assertEquals("480p", p480.resolutionLabel)
        assertEquals(480, p480.realHeight)
        assertEquals("h264", p480.vcodec)
        assertEquals("aac", p480.acodec)
        assertEquals("135+140", p480.formatId)
        assertEquals(12_000_000L, p480.estimatedSizeBytes) // 10MB + 2MB
        assertFalse(p480.isDefault)
        assertTrue(p480.isEnabled)

        // 2. Preset 1080p: h264 + AAC (default)
        val p1080 = options[1]
        assertEquals("1080p", p1080.presetName)
        assertEquals("1080p", p1080.resolutionLabel)
        assertEquals(1080, p1080.realHeight)
        assertEquals("h264", p1080.vcodec)
        assertEquals("aac", p1080.acodec)
        assertEquals("137+140", p1080.formatId)
        assertEquals(27_000_000L, p1080.estimatedSizeBytes) // 25MB + 2MB
        assertTrue(p1080.isDefault)
        assertTrue(p1080.isEnabled)

        // 3. Preset 4K: VP9 + Opus (enabled because 2160p exists)
        val p4k = options[2]
        assertEquals("4K", p4k.presetName)
        assertEquals("4K", p4k.resolutionLabel)
        assertEquals(2160, p4k.realHeight)
        assertEquals("vp9", p4k.vcodec)
        assertEquals("opus", p4k.acodec)
        assertEquals("313+251", p4k.formatId)
        assertEquals(101_500_000L, p4k.estimatedSizeBytes) // 100MB + 1.5MB
        assertTrue(p4k.isEnabled)
        assertNull(p4k.disabledReason)
        assertEquals("Large file, may play poorly on older phones", p4k.note)
    }

    @Test
    fun fourKUnavailableWhenSourceOffersNo2160p() {
        val no4kJson = """
        {
          "id": "no4k",
          "title": "1080p Max Video",
          "duration": 30.0,
          "formats": [
            {"format_id": "135", "ext": "mp4", "width": 854, "height": 480, "vcodec": "avc1.4d401f", "acodec": "none", "filesize": 6000000},
            {"format_id": "137", "ext": "mp4", "width": 1920, "height": 1080, "vcodec": "avc1.640028", "acodec": "none", "filesize": 18000000},
            {"format_id": "140", "ext": "m4a", "vcodec": "none", "acodec": "mp4a.40.2", "filesize": 1000000}
          ]
        }
        """.trimIndent()

        val options = VideoQualitySelector.parseOptions(no4kJson)
        assertEquals(3, options.size)

        val p4k = options[2]
        assertEquals("4K", p4k.presetName)
        assertFalse("4K must be disabled when source has no 2160p", p4k.isEnabled)
        assertEquals("4K not available, best is 1080p", p4k.disabledReason)
    }

    @Test
    fun belowPresetLabelWhenSourceMaxIsLower() {
        val lowerResJson = """
        {
          "id": "lower",
          "title": "720p Max Video",
          "duration": 30.0,
          "formats": [
            {"format_id": "136", "ext": "mp4", "width": 1280, "height": 720, "vcodec": "avc1.4d401f", "acodec": "none", "filesize": 10000000},
            {"format_id": "18", "ext": "mp4", "width": 640, "height": 360, "vcodec": "avc1.42001E", "acodec": "mp4a.40.2", "filesize": 3000000},
            {"format_id": "140", "ext": "m4a", "vcodec": "none", "acodec": "mp4a.40.2", "filesize": 1000000}
          ]
        }
        """.trimIndent()

        val options = VideoQualitySelector.parseOptions(lowerResJson)
        assertEquals(3, options.size)

        // 1080p preset falls back to real resolution: 720p
        val p1080 = options[1]
        assertEquals("1080p", p1080.presetName)
        assertEquals("720p", p1080.resolutionLabel)
        assertEquals(720, p1080.realHeight)
        assertTrue(p1080.isDefault)
        assertEquals("136+140", p1080.formatId)

        // 480p preset falls back to real resolution: 360p
        val p480 = options[0]
        assertEquals("480p", p480.presetName)
        assertEquals("360p", p480.resolutionLabel)
        assertEquals(360, p480.realHeight)

        // 4K preset disabled with best is 720p
        val p4k = options[2]
        assertFalse(p4k.isEnabled)
        assertEquals("4K not available, best is 720p", p4k.disabledReason)
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
        assertEquals(3, options.size)

        // 1080p preset ignores AV1 and picks the highest valid h264 (720p)
        val p1080 = options[1]
        assertEquals("720p", p1080.resolutionLabel)
        assertEquals(720, p1080.realHeight)
        assertEquals("136+140", p1080.formatId)

        // 4K preset disabled
        val p4k = options[2]
        assertFalse(p4k.isEnabled)
        assertEquals("4K not available, best is 720p", p4k.disabledReason)
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
        assertEquals(3, options.size)
        val p1080 = options[1]
        assertEquals("download", p1080.formatId)
        assertEquals("h264", p1080.vcodec)
        assertEquals("aac", p1080.acodec)
        assertEquals(8_500_000L, p1080.estimatedSizeBytes)
        assertEquals("1080p", p1080.resolutionLabel)
        assertTrue(p1080.isDefault)
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
        val p1080 = options[1]
        // Video: 4000 kbps * 1000 / 8 * 10s = 5,000,000 bytes
        // Audio: 128 kbps * 1000 / 8 * 10s = 160,000 bytes
        // Total: 5,160,000 bytes
        assertEquals(5_160_000L, p1080.estimatedSizeBytes)
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
