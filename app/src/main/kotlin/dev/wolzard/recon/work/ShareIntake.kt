package dev.wolzard.recon.work

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import dev.wolzard.recon.core.port.JobStore
import dev.wolzard.recon.data.media.ContentUriFetcher
import dev.wolzard.recon.data.notice.Notice
import java.io.File
import java.util.UUID

/**
 * Share intake (T12). A shared video is copied **immediately** (the URI grant is temporary, R14) into
 * filesDir/shared/<uuid>/; text keeps its links for RunJob. Then the job is enqueued.
 */
class ShareIntake(private val context: Context, private val store: JobStore, private val copier: ContentUriFetcher) {

    /** @return the new job id, or null if the intent isn't a usable share. */
    suspend fun accept(intent: Intent): String? {
        if (intent.action != Intent.ACTION_SEND) return null
        val type = intent.type.orEmpty()
        val id = if (type.startsWith("video/")) {
            val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java) ?: return null
            if (uri.scheme != "content") return null                      // never read file:// from another app
            val dir = File(context.filesDir, "${ContentUriFetcher.SHARED_DIR}/${UUID.randomUUID()}")
            val copy = copier.fetch(uri.toString(), dir).video
            store.create(intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty(), copy.path)
        } else {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() } ?: return null
            store.create(text, null)
        }
        JobQueue.enqueue(context, id)
        return id
    }
}

/**
 * T16: until the personal-use notice is accepted, a share is held (raw intent, unvalidated) and creates no job.
 * [accepted] releases it through the same [intake] as any fresh share, so it is validated then, not when held.
 * ponytail: held in memory (+ the activity's saved state); a process death before accept drops it.
 */
class ShareGate(private val notice: Notice, private val intake: suspend (Intent) -> String?) {
    var held: Intent? = null

    /** @return the job id, or null if held or not a usable share. */
    suspend fun share(intent: Intent): String? {
        if (notice.needed) { held = intent; return null }
        return intake(intent)
    }

    suspend fun accepted(): String? {
        notice.accept()
        return held?.also { held = null }?.let { intake(it) }
    }
}
