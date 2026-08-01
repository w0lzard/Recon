package dev.wolzard.recon.designsystem

import android.content.Context
import android.content.Intent
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri

/** Full browser (downloads land in its download manager); no-op if none is installed. */
fun Context.openInBrowser(url: String) {
    runCatching { startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

fun Context.openCustomTab(url: String) = CustomTabsIntent.Builder().build().launchUrl(this, url.toUri())
