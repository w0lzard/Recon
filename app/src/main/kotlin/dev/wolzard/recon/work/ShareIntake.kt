package dev.wolzard.recon.work

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import dev.wolzard.recon.core.port.JobStore
import dev.wolzard.recon.data.media.ContentUriFetcher
import dev.wolzard.recon.ui.setup.Notice
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
