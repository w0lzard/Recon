package dev.wolzard.recon.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.wolzard.recon.BuildConfig
import dev.wolzard.recon.data.update.UpdateInfo

/**
 * Design 1l UpdateBanner: primary-bordered card, "Download" opens the APK link (https://github.com/ only, see
 * UpdateChecker). [onDismiss] is "Later" (hides it until the next release); null = no Later.
 */
@Composable
fun UpdateBanner(info: UpdateInfo, onOpen: (String) -> Unit, onDismiss: (() -> Unit)?, modifier: Modifier = Modifier) {
    OutlinedCard(
        modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary), colors = CardDefaults.outlinedCardColors(containerColor = Color.Transparent),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Symbols.SystemUpdate, contentDescription = null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Text("Update available", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                Text("v${info.version}", style = MaterialTheme.typography.bodySmall)
            }
            Text(
                "You have v${BuildConfig.VERSION_NAME}. The download is signed with the same certificate shown below.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                onDismiss?.let {
                    TextButton(onClick = it, Modifier.height(48.dp), colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) { Text("Later") }
                }
                Button(onClick = { onOpen(info.apkUrl) }, Modifier.height(48.dp), shape = MaterialTheme.shapes.small) { Text("Download") }
            }
        }
    }
}
