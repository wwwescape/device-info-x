package com.wwwescape.deviceinfox.console.ui.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.wwwescape.deviceinfox.R

/** Same Dialog/Card shape as `WhatsNewDialog`, deliberately without its `Fireworks` background —
 * DIX AI's own calmer visual identity (a lightbulb, not a celebration), per direct confirmation.
 * No `Box.fillMaxSize()` wrapper either, since there's no full-screen background layer to stack
 * the card over. */
@Composable
fun InsightsDialog(items: List<String>, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = modifier.padding(horizontal = 32.dp).fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.TopEnd),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.console_insights_dismiss),
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 24.dp, top = 44.dp, bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(text = "💡", style = MaterialTheme.typography.displaySmall)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.console_insights_title),
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(items) { item ->
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "•",
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(end = 8.dp),
                                )
                                Text(text = parseInsightMarkup(item), style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = onDismiss) {
                        Text(stringResource(R.string.console_insights_dismiss))
                    }
                }
            }
        }
    }
}
