package dev.wolzard.recon.feature.notice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.wolzard.recon.data.notice.NOTICE_VERSION
import dev.wolzard.recon.data.notice.Notice
import dev.wolzard.recon.designsystem.Symbols
import dev.wolzard.recon.designsystem.Wordmark
import dev.wolzard.recon.designsystem.status

/** Design 1b: icon rows with a bold lead, the accept button pinned under the list so nothing clips. */
@Composable
fun NoticeScreen(onAccept: () -> Unit) {
    val line = MaterialTheme.colorScheme.outlineVariant
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    // Same order as Notice.TEXT; the account row takes the Unverified ink, as in the design.
    val icons = listOf(Symbols.CloudUpload, Symbols.Gavel, Symbols.LinkOff, Symbols.ManageAccounts, Symbols.Rule, Symbols.MedicalInformation, Symbols.Android)
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Wordmark(Modifier.weight(1f))
                Text("NOTICE · V$NOTICE_VERSION", color = muted, style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.height(24.dp))
            Text("Before you start", style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp, lineHeight = 30.sp))
            Spacer(Modifier.height(8.dp))
            Text("Recon is a personal-use tool. Read this once.", color = muted, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = line)
            Notice.TEXT.forEachIndexed { i, (lead, rest) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Icon(icons[i], contentDescription = null, Modifier.padding(top = 2.dp).size(18.dp), tint = if (icons[i] == Symbols.ManageAccounts) MaterialTheme.colorScheme.status.unverified else muted)
                    Text(
                        buildAnnotatedString { withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(lead) }; append(" "); append(rest) },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                HorizontalDivider(color = line)
            }
        }
        HorizontalDivider(color = line)
        Button(onAccept, Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 12.dp).height(48.dp), shape = MaterialTheme.shapes.small) {
            Text("I understand", style = MaterialTheme.typography.labelLarge)
        }
    }
}
