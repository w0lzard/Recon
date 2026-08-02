package dev.wolzard.recon.feature.notice

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
import dev.wolzard.recon.data.notice.NOTICE_VERSION
import dev.wolzard.recon.data.notice.Notice
import dev.wolzard.recon.designsystem.Wordmark
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

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
