package com.wwwescape.deviceinfox.console.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wwwescape.deviceinfox.R

/** An optional extra line shown at the bottom of the *partner's* birthday popup (`EventPopup`'s
 * `secondaryMessage`) — authored here, but never shown on this user's own birthday (see
 * `ConsoleTabsViewModel.checkBirthday`). Capped at [MAX_BIRTHDAY_MESSAGE_CHARS] characters
 * client-side via a live counter; Save stays disabled past the limit rather than silently
 * truncating what was typed. Blank saves as "no custom message" (`PartnerRepository.setBirthdayMessage`/
 * the server both treat "" as clearing it), so clearing the field and saving is how you remove it
 * again. The birthday *date* itself stays on [ProfileScreen] rather than moving here — this page
 * only ever held the custom message, per the Settings-restructure TODO's own decision. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BirthdaySettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: ConsoleSettingsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var text by remember(uiState.birthdayCustomMessage) { mutableStateOf(uiState.birthdayCustomMessage.orEmpty()) }
    val isOverLimit = text.length > MAX_BIRTHDAY_MESSAGE_CHARS

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.console_settings_section_birthday)) },
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
                Text(
                    text = stringResource(R.string.console_settings_birthday_message_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.console_settings_birthday_message_label)) },
                    minLines = 2,
                    maxLines = 5,
                    isError = isOverLimit,
                    supportingText = {
                        Text(
                            text = stringResource(
                                R.string.console_settings_birthday_message_char_count,
                                text.length,
                                MAX_BIRTHDAY_MESSAGE_CHARS,
                            ),
                            color = if (isOverLimit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(
                    onClick = { viewModel.setBirthdayCustomMessage(text.trim()) },
                    enabled = !isOverLimit && text.trim() != uiState.birthdayCustomMessage.orEmpty(),
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(stringResource(R.string.console_settings_save_birthday_message))
                }
            }
        }
    }
}

private const val MAX_BIRTHDAY_MESSAGE_CHARS = 75
