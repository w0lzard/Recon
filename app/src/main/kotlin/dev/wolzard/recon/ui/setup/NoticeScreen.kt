package dev.wolzard.recon.ui.setup

import android.content.SharedPreferences
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.Alignment
import dev.wolzard.recon.ui.common.Wordmark
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Raise when the notice text changes in a way users must re-accept. */
const val NOTICE_VERSION = 2 // 2: Gemini web sign-in replaced the API key (D22)

/** T16: the accepted notice version, in prefs. [version] is a parameter only so tests can bump it. */
class Notice(private val prefs: SharedPreferences, private val version: Int = NOTICE_VERSION) {
    val needed: Boolean get() = prefs.getInt(ACCEPTED, 0) < version
    fun accept() { prefs.edit().putInt(ACCEPTED, version).apply() }

    companion object {
        private const val ACCEPTED = "acceptedVersion"
        const val PREFS = "notice"
        val TEXT = listOf(
            "Recon is a personal tool. To analyse a video it downloads a temporary copy, then deletes it when the analysis " +
                "finishes. Instagram, TikTok, X and other platforms may forbid downloading in their terms of service. You are " +
                "responsible for what you share with Recon. Only share content you're allowed to process.",
            "Videos and screenshots are sent to Gemini with your Google account, through its website rather than an official API. Google's terms apply.",
        )
    }
}

@Composable
fun NoticeScreen(onAccept: () -> Unit) {
    val line = MaterialTheme.colorScheme.outlineVariant
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Wordmark(Modifier.weight(1f))
                Text("NOTICE · V$NOTICE_VERSION", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            }
            Text("Before you start", style = MaterialTheme.typography.titleLarge)
            Column {
                HorizontalDivider(color = line)
                Notice.TEXT.forEach { Text(it, Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.bodyMedium); HorizontalDivider(color = line) }
            }
        }
        HorizontalDivider(color = line)
        Button(onAccept, Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp).height(48.dp), shape = MaterialTheme.shapes.small) {
            Text("I understand", style = MaterialTheme.typography.labelLarge)
        }
    }
}
