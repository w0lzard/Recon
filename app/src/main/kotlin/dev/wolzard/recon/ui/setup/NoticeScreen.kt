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
