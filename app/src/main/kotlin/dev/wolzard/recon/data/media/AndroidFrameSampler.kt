package dev.wolzard.recon.data.media

import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.os.Build
import android.util.Log
import dev.wolzard.recon.core.model.AnalysisInput
import dev.wolzard.recon.core.model.AnalyzerException
import dev.wolzard.recon.core.model.FailureReason
import dev.wolzard.recon.core.port.FrameSampler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import kotlin.coroutines.coroutineContext

/**
 * Path B (T8). One Bitmap alive at a time; each is recycled right after its dHash and JPEG are written.
 * Returns an empty frame list when nothing decodes (e.g. a codec the device lacks): RunJob treats that as a
 * failure and retries once in another format, never DONE.
 */
class AndroidFrameSampler : FrameSampler {

    override suspend fun sample(video: File, outDir: File): AnalysisInput.FramesAndAudio = withContext(Dispatchers.IO) {
        outDir.mkdirs()
        val retriever = MediaMetadataRetriever()
        val frames = mutableListOf<AnalysisInput.Frame>()
        try {
            val duration = try {
                retriever.setDataSource(video.path)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                    ?: throw AnalyzerException(FailureReason.VIDEO_UNREADABLE, message = "no duration")
            } catch (e: RuntimeException) {
                throw AnalyzerException(FailureReason.VIDEO_UNREADABLE, message = "can't open video", cause = e)
            }
            val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
            val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
            if (SamplerLimits.frameTooLarge(w, h)) throw AnalyzerException(FailureReason.VIDEO_UNREADABLE, message = "frame size ${w}x$h too large")
            var lastHash: Long? = null
            for (t in FrameTimestamps.of(duration)) {
                coroutineContext.ensureActive()
                val bitmap = frameAt(retriever, t) ?: continue
                val hash = dHash(bitmap)
                if (lastHash != null && DHash.distance(hash, lastHash) <= DUPLICATE_DISTANCE) {
                    bitmap.recycle()
                    continue
                }
                val file = File(outDir, "f_%02d.jpg".format(frames.size))
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it) }
                bitmap.recycle()
                lastHash = hash
                frames += AnalysisInput.Frame(file, t)
            }
        } finally {
            retriever.release()
        }
        AnalysisInput.FramesAndAudio(frames, copyAudio(video, File(outDir, "audio.m4a")))
    }

    private fun frameAt(r: MediaMetadataRetriever, ms: Long): Bitmap? {
        val us = ms * 1000
        if (Build.VERSION.SDK_INT >= 27) return r.getScaledFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, MAX_EDGE, MAX_EDGE)
        // API 26 has no getScaledFrameAtTime: scale the full frame, then recycle the source before the next frame.
        val full = r.getFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: return null
