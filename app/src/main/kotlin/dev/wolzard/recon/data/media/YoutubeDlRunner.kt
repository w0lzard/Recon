package dev.wolzard.recon.data.media

import android.content.Context
import android.util.Log
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
            withContext(Dispatchers.IO) { YoutubeDL.getInstance().init(context.applicationContext) }
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
