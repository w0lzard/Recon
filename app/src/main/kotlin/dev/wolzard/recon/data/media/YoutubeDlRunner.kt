package dev.wolzard.recon.data.media

import android.content.Context
import android.util.Log
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import dev.wolzard.recon.core.link.LinkRouter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * youtubedl-android access. One lock covers init, update and every download, because an update replaces the
 * yt-dlp zip in place and a download running at the same time fails with "ZipImportError: bad local file header"
 * (seen on the API 26 AVD, 2026-10-08). A fresh install updates once before its first download: the bundled
 * yt-dlp ages out (the 0.18.1 library ships 2025.11.12).
 */
object YtDlp {
    private val lock = Mutex()
    @Volatile private var ready = false

    suspend fun <T> use(context: Context, block: suspend () -> T): T = lock.withLock {
        if (!ready) {
            withContext(Dispatchers.IO) {
                val app = context.applicationContext
                YoutubeDL.getInstance().init(app)
                FFmpeg.getInstance().init(app)
            }
            ready = true
        }
        block()
    }

    /** @return the update status, or null if it failed (the current version stays in use). */
    suspend fun update(context: Context): YoutubeDL.UpdateStatus? = use(context) { updateLocked(context) }

    /** Called by downloads: updates first if this install never updated successfully. */
    suspend fun <T> useUpdated(context: Context, block: suspend () -> T): T = use(context) {
        if (!prefs(context).getBoolean(UPDATED, false)) updateLocked(context)
        block()
    }

    private suspend fun updateLocked(context: Context): YoutubeDL.UpdateStatus? = withContext(Dispatchers.IO) {
        runCatching { YoutubeDL.getInstance().updateYoutubeDL(context.applicationContext, YoutubeDL.UpdateChannel._STABLE) }
            .onSuccess { prefs(context).edit().putBoolean(UPDATED, true).apply() }
            .onFailure { Log.w("YtDlpUpdate", "yt-dlp update failed", it) }
            .getOrNull()
    }

    private fun prefs(context: Context) = context.getSharedPreferences("ytdlp", Context.MODE_PRIVATE)
    private const val UPDATED = "updatedOnce"
}

class YoutubeDlRunner(private val context: Context) : YtDlpRunner {
    override suspend fun run(url: String, args: List<String>, processId: String, onProgress: (Float) -> Unit): String {
        val request = request(url, args)
        val ytdl = YoutubeDL.getInstance()
        return YtDlp.useUpdated(context) { coroutineScope {
            val call = async(Dispatchers.IO) { ytdl.execute(request, processId) { progress, _, _ -> onProgress((progress / 100f).coerceIn(0f, 1f)) } }
            try {
                call.await().out
            } catch (e: CancellationException) {
                ytdl.destroyProcessById(processId) // kills the Python child; the job's finally deletes its dir
                throw e
            } catch (e: YoutubeDLException) {
                throw YtDlpException(e.message.orEmpty())
            }
        } }
    }

    companion object {
        private val flagsWithoutValue = setOf("--no-playlist", "--no-simulate", "--dump-json", "--skip-download") // last two: D26 link preview

        /** http(s) only; the URL goes after "--" so yt-dlp can never read it as an option. */
        fun request(url: String, args: List<String>): YoutubeDLRequest {
            if (!LinkRouter.isWebUrl(url)) throw YtDlpException("Not a web link")
            val request = YoutubeDLRequest(url)
            var i = 0
            while (i < args.size) {
                val key = args[i]
                if (key in flagsWithoutValue) { request.addOption(key); i++ } else { request.addOption(key, args[i + 1]); i += 2 }
            }
            return request.addCommands(listOf("--"))
        }
    }
}
