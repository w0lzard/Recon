package dev.wolzard.recon.work

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import dev.wolzard.recon.core.link.LinkRouter
import dev.wolzard.recon.core.port.JobStore
import dev.wolzard.recon.data.media.ContentUriFetcher
import dev.wolzard.recon.data.notice.Notice
import java.io.File
import java.util.UUID

/** What a share turned into: a job already running, or a valid link waiting for its optional context (D23). */
sealed interface ShareOutcome {
    data class Started(val jobId: String) : ShareOutcome
    data class NeedsContext(val link: String) : ShareOutcome
    /** Share sheet: the video file is already copied to [localUri]; the job is created by Start or Skip. */
    data class FileNeedsContext(val localUri: String, val sharedText: String) : ShareOutcome
}

/**
 * Share intake (T12). A shared video is copied **immediately** (the URI grant is temporary, R14) into
 * filesDir/shared/<uuid>/; text keeps its links for RunJob. Then the job is enqueued.
 */
class ShareIntake(private val context: Context, private val store: JobStore, private val copier: ContentUriFetcher) {

    /**
     * A shared video file starts at once, or with [fileContext] (the share sheet) is only copied, and Start or Skip
     * creates the job. Text with a valid web link creates nothing yet: the context screen starts the job (D23). Text
     * without one still becomes a job, which fails with UNSUPPORTED_LINK and says so.
     * @return null if the intent isn't a usable share.
     */
    suspend fun accept(intent: Intent, fileContext: Boolean = false): ShareOutcome? {
        if (intent.action != Intent.ACTION_SEND) return null
        val type = intent.type.orEmpty()
        val id = if (type.startsWith("video/")) {
            val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java) ?: return null
            if (uri.scheme != "content") return null                      // never read file:// from another app
            val dir = File(context.filesDir, "${ContentUriFetcher.SHARED_DIR}/${UUID.randomUUID()}")
            val copy = copier.fetch(uri.toString(), dir).video
            val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
            if (fileContext) return ShareOutcome.FileNeedsContext(copy.path, text)
            store.create(text, copy.path)
        } else {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() } ?: return null
            LinkRouter.validLink(text)?.let { return ShareOutcome.NeedsContext(it) }
            store.create(text, null)
        }
        JobQueue.enqueue(context, id)
        return ShareOutcome.Started(id)
    }
}

/**
 * T16: until the personal-use notice is accepted, a share is held (raw intent, unvalidated) and creates no job.
 * [accepted] releases it through the same [intake] as any fresh share, so it is validated then, not when held.
 * ponytail: held in memory (+ the activity's saved state); a process death before accept drops it.
 */
class ShareGate(private val notice: Notice, private val intake: suspend (Intent) -> ShareOutcome?) {
    var held: Intent? = null

    /** @return the outcome, or null if held or not a usable share. */
    suspend fun share(intent: Intent): ShareOutcome? {
        if (notice.needed) { held = intent; return null }
        return intake(intent)
    }

    suspend fun accepted(): ShareOutcome? {
        notice.accept()
        return held?.also { held = null }?.let { intake(it) }
    }
}
