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
