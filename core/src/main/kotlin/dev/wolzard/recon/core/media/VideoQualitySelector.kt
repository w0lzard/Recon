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
    val resolutionLabel: String,
    val height: Int,
    val width: Int,
    val vcodec: String,
    val acodec: String,
    val formatId: String,
    val estimatedSizeBytes: Long,
    val isDefault: Boolean = false,
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
    }

    /**
     * Parses yt-dlp `--dump-json` output and extracts selectable video save options.
     * Offers resolutions the source really has up to 4K (2160p).
     * Prefers h264 + AAC; allows VP9 + Opus only for 2160p and 1440p; excludes AV1.
     * Defaults to 1080p, or marks best available if max < 1080p.
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

        return selectOptions(rawFormats, duration)
    }

    fun selectOptions(formats: List<RawFormat>, durationSec: Double): List<VideoSaveOption> {
        // Exclude AV1
        val validFormats = formats.filterNot { it.isAv1 }

        val audioFormats = validFormats.filter { it.isAudioOnly }
        val bestAacAudio = audioFormats.filter { it.isAac }.maxByOrNull { it.filesize.takeIf { s -> s > 0 } ?: (it.abr * 1000).toLong() }
        val bestOpusAudio = audioFormats.filter { it.isOpus }.maxByOrNull { it.filesize.takeIf { s -> s > 0 } ?: (it.abr * 1000).toLong() }
        val fallbackAudio = audioFormats.maxByOrNull { it.filesize.takeIf { s -> s > 0 } ?: (it.abr * 1000).toLong() }

        val videoFormats = validFormats.filter { it.isVideo }
        if (videoFormats.isEmpty()) return emptyList()

        // Standard target resolution tiers (capped at 4K / 2160p)
        val targetTiers = listOf(2160, 1440, 1080, 720, 480, 360, 240)

        // Group video formats by standard resolution tier
        val optionsByTier = mutableMapOf<Int, VideoSaveOption>()

        for (tier in targetTiers) {
            val tierFormats = videoFormats.filter { fmt ->
                when (tier) {
                    2160 -> fmt.effectiveHeight >= 2160
                    1440 -> fmt.effectiveHeight in 1440..2159
                    1080 -> fmt.effectiveHeight in 1080..1439
                    720 -> fmt.effectiveHeight in 720..1079
                    480 -> fmt.effectiveHeight in 480..719
                    360 -> fmt.effectiveHeight in 360..479
                    240 -> fmt.effectiveHeight in 240..359
                    else -> false
                }
            }
            if (tierFormats.isEmpty()) continue

            // Codec preference:
            // 1. Prefer h264 + AAC for all resolutions
            // 2. Allow VP9 + Opus ONLY for 2160p and 1440p
            val h264Video = tierFormats.filter { it.isH264 }.maxByOrNull { it.bitrateOrSize }
            val vp9Video = if (tier >= 1440) tierFormats.filter { it.isVp9 }.maxByOrNull { it.bitrateOrSize } else null

            val chosenVideo = h264Video ?: vp9Video ?: continue
            val chosenAudio = when {
                chosenVideo.isMuxed -> null
                chosenVideo.isH264 -> bestAacAudio ?: fallbackAudio
                chosenVideo.isVp9 -> bestOpusAudio ?: fallbackAudio
                else -> fallbackAudio
            }

            val formatId = if (chosenAudio != null) "${chosenVideo.formatId}+${chosenAudio.formatId}" else chosenVideo.formatId
            val vcodecLabel = if (chosenVideo.isH264) "h264" else if (chosenVideo.isVp9) "vp9" else chosenVideo.vcodec
            val acodecLabel = if (chosenAudio != null) {
                if (chosenAudio.isAac) "aac" else if (chosenAudio.isOpus) "opus" else chosenAudio.acodec
            } else if (chosenVideo.isAac) "aac" else chosenVideo.acodec

            val codecStr = "$vcodecLabel + $acodecLabel"
            val estimatedSize = estimateSize(chosenVideo, chosenAudio, durationSec)

            optionsByTier[tier] = VideoSaveOption(
                resolutionLabel = resolutionLabelForTier(tier),
                height = chosenVideo.effectiveHeight.takeIf { it > 0 } ?: tier,
                width = chosenVideo.width,
                vcodec = vcodecLabel,
                acodec = acodecLabel,
                formatId = formatId,
                estimatedSizeBytes = estimatedSize,
                isDefault = false,
            )
        }

        if (optionsByTier.isEmpty()) {
            // Handle edge-case non-standard resolution muxed format (e.g. single 540p file)
            val bestMuxed = videoFormats.filter { it.isMuxed && (it.isH264 || it.effectiveHeight >= 1440) }.maxByOrNull { it.effectiveHeight }
            if (bestMuxed != null) {
                val tier = bestMuxed.effectiveHeight
                val estSize = estimateSize(bestMuxed, null, durationSec)
                optionsByTier[tier] = VideoSaveOption(
                    resolutionLabel = "${tier}p",
                    height = bestMuxed.height,
                    width = bestMuxed.width,
                    vcodec = if (bestMuxed.isH264) "h264" else bestMuxed.vcodec,
                    acodec = if (bestMuxed.isAac) "aac" else bestMuxed.acodec,
                    formatId = bestMuxed.formatId,
                    estimatedSizeBytes = estSize,
                    isDefault = true,
                )
            }
        }

        val sorted = optionsByTier.values.sortedByDescending { it.height }
        if (sorted.isEmpty()) return emptyList()

        val maxAvailableHeight = sorted.first().height
        val isLowerThan1080 = maxAvailableHeight < 1080

        return sorted.mapIndexed { index, opt ->
            val isDefault = if (isLowerThan1080) {
                index == 0 // Highest available is default when max < 1080p
            } else {
                opt.height == 1080 || (opt.height in 1080..1439)
            }
            val label = if (isLowerThan1080 && index == 0) {
                "${opt.height}p (best available)"
            } else {
                opt.resolutionLabel
            }
            opt.copy(resolutionLabel = label, isDefault = isDefault)
        }
    }

    private val RawFormat.bitrateOrSize: Long
        get() = filesize.takeIf { it > 0 } ?: filesizeApprox.takeIf { it > 0 } ?: (tbr * 1000).toLong()

    private fun resolutionLabelForTier(tier: Int): String = when (tier) {
        2160 -> "2160p (4K)"
        1440 -> "1440p (2K)"
        1080 -> "1080p"
        720 -> "720p"
        480 -> "480p"
        360 -> "360p"
        240 -> "240p"
        else -> "${tier}p"
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
