package dev.wolzard.recon.core.media

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

@Serializable
data class VideoSaveOption(
    val presetName: String,               // "480p", "1080p", "4K"
    val resolutionLabel: String,          // e.g. "480p", "1080p", "4K", or real resolution e.g. "720p"
    val realHeight: Int,                  // e.g. 480, 1080, 720, 2160
    val height: Int,                      // target tier height: 480, 1080, 2160
    val width: Int,
    val vcodec: String,
    val acodec: String,
    val formatId: String,
    val estimatedSizeBytes: Long,
    val isDefault: Boolean = false,
    val isEnabled: Boolean = true,
    val disabledReason: String? = null,   // "4K not available, best is <res>"
    val note: String? = null,             // "Large file, may play poorly on older phones"
) {
    val formattedSize: String get() = VideoQualitySelector.formatBytes(estimatedSizeBytes)
}

object VideoQualitySelector {
    private val json = Json { ignoreUnknownKeys = true }

    data class RawFormat(
        val formatId: String,
        val ext: String,
        val width: Int,
        val height: Int,
        val vcodec: String,
        val acodec: String,
        val filesize: Long,
        val filesizeApprox: Long,
        val tbr: Double,
        val vbr: Double,
        val abr: Double,
    ) {
        val isVideo: Boolean get() = vcodec.isNotEmpty() && vcodec != "none"
        val isAudio: Boolean get() = acodec.isNotEmpty() && acodec != "none"
        val isMuxed: Boolean get() = isVideo && isAudio
        val isVideoOnly: Boolean get() = isVideo && !isAudio
        val isAudioOnly: Boolean get() = isAudio && !isVideo

        val isAv1: Boolean get() = vcodec.startsWith("av01", ignoreCase = true) || vcodec.contains("av1", ignoreCase = true)
        val isH264: Boolean get() = vcodec.startsWith("avc1", ignoreCase = true) || vcodec.contains("h264", ignoreCase = true)
        val isVp9: Boolean get() = vcodec.startsWith("vp9", ignoreCase = true) || vcodec.startsWith("vp09", ignoreCase = true)

        val isAac: Boolean get() = acodec.startsWith("mp4a", ignoreCase = true) || acodec.contains("aac", ignoreCase = true)
        val isOpus: Boolean get() = acodec.startsWith("opus", ignoreCase = true)

        val effectiveHeight: Int get() = if (height > 0 && width > 0) minOf(height, width) else height
        val bitrateOrSize: Long get() = filesize.takeIf { it > 0 } ?: filesizeApprox.takeIf { it > 0 } ?: (tbr * 1000).toLong()
    }

    /**
     * Parses yt-dlp `--dump-json` output and extracts three presets: 480p, 1080p (default), 4K.
     * 480p and 1080p use h264 + AAC; 4K uses VP9 + Opus and is enabled only when source offers 2160p.
     * AV1 is excluded.
     * When source's best is below a preset, shows the real resolution in the label.
     * Disables 4K with "4K not available, best is <res>".
     */
    fun parseOptions(jsonOutput: String): List<VideoSaveOption> {
        val root = runCatching {
            val trimmed = jsonOutput.trim()
            val start = trimmed.indexOf('{').takeIf { it >= 0 } ?: return emptyList()
            val end = trimmed.lastIndexOf('}').takeIf { it >= start } ?: return emptyList()
            val candidate = trimmed.substring(start, end + 1)
            json.parseToJsonElement(candidate).jsonObject
        }.getOrNull() ?: return emptyList()

        val duration = root["duration"]?.jsonPrimitive?.doubleOrNull ?: 0.0
        val formatsArray = root["formats"]?.jsonArray ?: JsonArray(emptyList())

        val rawFormats = formatsArray.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val fid = obj["format_id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val ext = obj["ext"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val w = obj["width"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0
            val h = obj["height"]?.jsonPrimitive?.longOrNull?.toInt() ?: 0
            val vc = obj["vcodec"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val ac = obj["acodec"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val fs = obj["filesize"]?.jsonPrimitive?.longOrNull ?: 0L
            val fsa = obj["filesize_approx"]?.jsonPrimitive?.longOrNull ?: 0L
            val tbr = obj["tbr"]?.jsonPrimitive?.doubleOrNull ?: 0.0
            val vbr = obj["vbr"]?.jsonPrimitive?.doubleOrNull ?: 0.0
            val abr = obj["abr"]?.jsonPrimitive?.doubleOrNull ?: 0.0

            RawFormat(
                formatId = fid,
                ext = ext,
                width = w,
                height = h,
                vcodec = vc,
                acodec = ac,
                filesize = fs,
                filesizeApprox = fsa,
                tbr = tbr,
                vbr = vbr,
                abr = abr,
            )
        }

        return selectPresets(rawFormats, duration)
    }

    fun selectPresets(formats: List<RawFormat>, durationSec: Double): List<VideoSaveOption> {
        val validFormats = formats.filterNot { it.isAv1 }
        val audioFormats = validFormats.filter { it.isAudioOnly }
        val bestAacAudio = audioFormats.filter { it.isAac }.maxByOrNull { it.bitrateOrSize } ?: audioFormats.maxByOrNull { it.bitrateOrSize }
        val bestOpusAudio = audioFormats.filter { it.isOpus }.maxByOrNull { it.bitrateOrSize } ?: audioFormats.maxByOrNull { it.bitrateOrSize }

        val videoFormats = validFormats.filter { it.isVideo }
        if (videoFormats.isEmpty()) return emptyList()

        val maxAvailableHeight = videoFormats.maxOfOrNull { it.effectiveHeight } ?: 0
        val h264Formats = videoFormats.filter { it.isH264 }

        // --- 1. Preset 480p (h264 + AAC) ---
        val chosen480Video = if (maxAvailableHeight >= 480) {
            h264Formats.filter { it.effectiveHeight in 480..719 }.maxByOrNull { it.bitrateOrSize }
                ?: h264Formats.filter { it.effectiveHeight <= 480 }.maxByOrNull { it.bitrateOrSize }
                ?: h264Formats.minByOrNull { it.effectiveHeight }
                ?: videoFormats.filter { it.effectiveHeight <= 480 }.maxByOrNull { it.bitrateOrSize }
                ?: videoFormats.minByOrNull { it.effectiveHeight }
        } else {
            h264Formats.maxByOrNull { it.bitrateOrSize } ?: videoFormats.maxByOrNull { it.bitrateOrSize }
        }

        val chosen480Audio = if (chosen480Video?.isMuxed == true) null else bestAacAudio
        val real480Height = chosen480Video?.effectiveHeight ?: 480
        val label480 = if (real480Height < 480) "${real480Height}p" else "480p"
        val formatId480 = if (chosen480Audio != null && chosen480Video != null) "${chosen480Video.formatId}+${chosen480Audio.formatId}"
            else chosen480Video?.formatId.orEmpty()
        val size480 = if (chosen480Video != null) estimateSize(chosen480Video, chosen480Audio, durationSec) else 0L

        val preset480 = VideoSaveOption(
            presetName = "480p",
            resolutionLabel = label480,
            realHeight = real480Height,
            height = 480,
            width = chosen480Video?.width ?: 640,
            vcodec = "h264",
            acodec = "aac",
            formatId = formatId480,
            estimatedSizeBytes = size480,
            isDefault = false,
            isEnabled = chosen480Video != null,
        )

        // --- 2. Preset 1080p (default, h264 + AAC) ---
        val chosen1080Video = if (maxAvailableHeight >= 1080) {
            h264Formats.filter { it.effectiveHeight in 1080..1439 }.maxByOrNull { it.bitrateOrSize }
                ?: h264Formats.maxByOrNull { it.bitrateOrSize }
                ?: videoFormats.filter { it.effectiveHeight in 1080..1439 }.maxByOrNull { it.bitrateOrSize }
                ?: videoFormats.maxByOrNull { it.bitrateOrSize }
        } else {
            h264Formats.maxByOrNull { it.bitrateOrSize } ?: videoFormats.maxByOrNull { it.bitrateOrSize }
        }

        val chosen1080Audio = if (chosen1080Video?.isMuxed == true) null else bestAacAudio
        val real1080Height = chosen1080Video?.effectiveHeight ?: 1080
        val label1080 = if (real1080Height < 1080) "${real1080Height}p" else "1080p"
        val formatId1080 = if (chosen1080Audio != null && chosen1080Video != null) "${chosen1080Video.formatId}+${chosen1080Audio.formatId}"
            else chosen1080Video?.formatId.orEmpty()
        val size1080 = if (chosen1080Video != null) estimateSize(chosen1080Video, chosen1080Audio, durationSec) else 0L

        val preset1080 = VideoSaveOption(
            presetName = "1080p",
            resolutionLabel = label1080,
            realHeight = real1080Height,
            height = 1080,
            width = chosen1080Video?.width ?: 1920,
            vcodec = "h264",
            acodec = "aac",
            formatId = formatId1080,
            estimatedSizeBytes = size1080,
            isDefault = true,
            isEnabled = chosen1080Video != null,
        )

        // --- 3. Preset 4K (VP9 + Opus, enabled only when source offers 2160p) ---
        val has2160p = maxAvailableHeight >= 2160
        val chosen4kVideo = if (has2160p) {
            val formats2160 = videoFormats.filter { it.effectiveHeight >= 2160 }
            formats2160.filter { it.isVp9 }.maxByOrNull { it.bitrateOrSize }
                ?: formats2160.maxByOrNull { it.bitrateOrSize }
        } else null

        val chosen4kAudio = if (chosen4kVideo?.isMuxed == true) null else bestOpusAudio
        val formatId4k = if (chosen4kAudio != null && chosen4kVideo != null) "${chosen4kVideo.formatId}+${chosen4kAudio.formatId}"
            else chosen4kVideo?.formatId.orEmpty()
        val size4k = if (chosen4kVideo != null) estimateSize(chosen4kVideo, chosen4kAudio, durationSec) else 0L

        val preset4k = VideoSaveOption(
            presetName = "4K",
            resolutionLabel = "4K",
            realHeight = chosen4kVideo?.effectiveHeight ?: 2160,
            height = 2160,
            width = chosen4kVideo?.width ?: 3840,
            vcodec = "vp9",
            acodec = "opus",
            formatId = formatId4k,
            estimatedSizeBytes = size4k,
            isDefault = false,
            isEnabled = has2160p && chosen4kVideo != null,
            disabledReason = if (!has2160p || chosen4kVideo == null) "4K not available, best is ${maxAvailableHeight}p" else null,
            note = "Large file, may play poorly on older phones",
        )

        return listOf(preset480, preset1080, preset4k)
    }

    private fun estimateSize(video: RawFormat, audio: RawFormat?, durationSec: Double): Long {
        val vSize = video.filesize.takeIf { it > 0 }
            ?: video.filesizeApprox.takeIf { it > 0 }
            ?: (if (video.vbr > 0) (video.vbr * 1000 / 8 * durationSec).toLong()
            else if (video.tbr > 0) (video.tbr * 1000 / 8 * durationSec).toLong()
            else defaultBitrateForHeight(video.effectiveHeight) * durationSec / 8).toLong()

        val aSize = audio?.let {
            it.filesize.takeIf { s -> s > 0 }
                ?: it.filesizeApprox.takeIf { s -> s > 0 }
                ?: (if (it.abr > 0) (it.abr * 1000 / 8 * durationSec).toLong()
                else if (it.tbr > 0) (it.tbr * 1000 / 8 * durationSec).toLong()
                else 128_000L * durationSec / 8).toLong()
        } ?: 0L

        return (vSize + aSize).coerceAtLeast(0L)
    }

    private fun defaultBitrateForHeight(height: Int): Long = when {
        height >= 2160 -> 20_000_000L // 20 Mbps
        height >= 1440 -> 10_000_000L // 10 Mbps
        height >= 1080 -> 4_500_000L  // 4.5 Mbps
        height >= 720 -> 2_500_000L   // 2.5 Mbps
        height >= 480 -> 1_200_000L   // 1.2 Mbps
        else -> 600_000L
    }

    /** Need roughly twice the estimated size for merging (video + audio + merged file). */
    fun hasEnoughStorage(freeBytes: Long, estimatedSizeBytes: Long): Boolean =
        freeBytes >= requiredStorage(estimatedSizeBytes)

    fun requiredStorage(estimatedSizeBytes: Long): Long = estimatedSizeBytes * 2

    fun formatBytes(bytes: Long): String = when {
        bytes <= 0L -> "0 B"
        bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
        bytes < 1024L * 1024L * 1024L -> {
            val mb = bytes.toDouble() / (1024.0 * 1024.0)
            "${"%.1f".format(java.util.Locale.US, mb)} MB"
        }
        else -> {
            val gb = bytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
            "${"%.2f".format(java.util.Locale.US, gb)} GB"
        }
    }
}
