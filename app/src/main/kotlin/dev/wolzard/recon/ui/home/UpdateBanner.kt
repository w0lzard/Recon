package dev.wolzard.recon.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.wolzard.recon.data.update.UpdateInfo

/** "Download" and "What's new" open in the browser (links are https://github.com/ only, see UpdateChecker). [onDismiss] null = no Dismiss. */
@Composable
fun UpdateBanner(info: UpdateInfo, onOpen: (String) -> Unit, onDismiss: (() -> Unit)?, modifier: Modifier = Modifier) {
    OutlinedCard(modifier.fillMaxWidth(), border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary), colors = CardDefaults.outlinedCardColors(containerColor = Color.Transparent)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Recon ${info.version} is available", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onOpen(info.apkUrl) }, Modifier.height(48.dp), shape = MaterialTheme.shapes.small) { Text("Download") }
                TextButton(onClick = { onOpen(info.releasePageUrl) }, Modifier.height(48.dp)) { Text("What's new") }
                onDismiss?.let { TextButton(onClick = it) { Text("Dismiss") } }
            }
        }
    }
}
