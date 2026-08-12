package dev.wolzard.recon.data.link

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.BufferedSource
import java.util.concurrent.TimeUnit

/**
 * D26: link thumbnails. https only (also after redirects), at most [MAX_BYTES] downloaded, decoded downsampled to
 * about [TARGET_PX] on the long side, kept in a small in-memory cache. Any failure is null, never an error.
 */
class ThumbnailLoader(client: OkHttpClient) {
    private val http = client.newBuilder().followSslRedirects(false).callTimeout(LinkPreviewFetcher.TIMEOUT_MS, TimeUnit.MILLISECONDS).build()
    private val cache = object : LruCache<String, Bitmap>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    suspend fun load(url: String?): ImageBitmap? {
        if (url == null || !LinkPreviewFetcher.isHttps(url)) return null
        cache.get(url)?.let { return it.asImageBitmap() }
        val bitmap = withContext(Dispatchers.IO) {
            try {
                http.newCall(Request.Builder().url(url).build()).execute().use { r ->
                    if (!r.isSuccessful || !LinkPreviewFetcher.isHttps(r.request.url.toString())) return@use null
                    readCapped(r.body.source(), r.body.contentLength())?.let(::decode)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        } ?: return null
        cache.put(url, bitmap)
        return bitmap.asImageBitmap()
    }

    companion object {
        const val MAX_BYTES = 1 shl 20          // 1 MiB; platform thumbnails are tens of KB
        const val MAX_SIDE = 8192               // larger declared dimensions are refused before decoding
        const val TARGET_PX = 256               // 64 dp at xxxhdpi, the largest slot the design uses
        private const val CACHE_BYTES = 4 shl 20

        /** The body, or null if it is (or claims to be) larger than [max]. */
        fun readCapped(source: BufferedSource, contentLength: Long, max: Int = MAX_BYTES): ByteArray? {
            if (contentLength > max) return null
            if (source.request(max + 1L)) return null
            return source.readByteArray()
        }

        /** Largest power of two that keeps the shorter side at or above [target]. */
        fun sampleSize(width: Int, height: Int, target: Int = TARGET_PX): Int {
            var s = 1
            while (minOf(width, height) / (s * 2) >= target) s *= 2
            return s
        }

        fun boundsOk(width: Int, height: Int) = width in 1..MAX_SIDE && height in 1..MAX_SIDE

        private fun decode(bytes: ByteArray): Bitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (!boundsOk(bounds.outWidth, bounds.outHeight)) return null
            val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight); inPreferredConfig = Bitmap.Config.RGB_565 }
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        }
    }
}
