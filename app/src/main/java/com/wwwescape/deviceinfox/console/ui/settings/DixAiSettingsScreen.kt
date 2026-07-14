package com.wwwescape.deviceinfox.console.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wwwescape.deviceinfox.R

/** DIX AI: today just the "Learn more" explainer — the only thing in this category. The actual
 * insight content shows via [com.wwwescape.deviceinfox.console.ui.insights.InsightsOverlay],
 * mounted at the console nav-host level like What's New, not from anywhere on this page. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DixAiSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    var showInfo by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.console_settings_section_dix_ai)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsSectionCard(title = null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth().clickable { showInfo = true },
                ) {
                    Text(
                        text = stringResource(R.string.console_dix_ai_learn_action),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }

    if (showInfo) {
        DixAiInfoDialog(onDismiss = { showInfo = false })
    }
}

/** Explainer shown from the "Learn more about DIX AI" row above — deliberately a plain
 * `AlertDialog` (no lightbulb emoji, no special styling), matching `DuressCodeInfoDialog`'s own
 * shape. Confirmed with the user that the lightbulb/no-background-effect treatment is for
 * `InsightsDialog` itself (the popup showing real insight content), not this settings-only
 * explainer. */
@Composable
private fun DixAiInfoDialog(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.console_dix_ai_info_title)) },
        text = { Text(stringResource(R.string.console_dix_ai_info_body)) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.console_dix_ai_info_action)) }
        },
        modifier = modifier,
    )
}
