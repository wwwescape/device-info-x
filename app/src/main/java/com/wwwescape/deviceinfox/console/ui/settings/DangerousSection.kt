package com.wwwescape.deviceinfox.console.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.wwwescape.deviceinfox.R

/** The 6th Settings category — two escalating destructive actions, split into its own file since
 * [ConsoleSettingsScreen] is already large. "Delete all data" wipes the 4 content sections but
 * keeps the account; "Destroy all data" (gated behind PIN re-entry, see [com.wwwescape.deviceinfox.console.ui.auth.DestroyAccountDialog])
 * removes the account itself.
 *
 * No card-level title — the "Dangerous" [CategoryHeader] right above this in [ConsoleSettingsScreen]
 * already says the only thing this single card would repeat, unlike General's cards
 * (Profile/Security/Pairing/Server), which each need their own distinct caption. */
@Composable
fun DangerousSection(onDeleteAllDataClick: () -> Unit, onDestroyAllDataClick: () -> Unit) {
    SettingsSectionCard(title = null) {
        DangerousRow(label = stringResource(R.string.console_settings_delete_all_data_action), onClick = onDeleteAllDataClick)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        DangerousRow(label = stringResource(R.string.console_settings_destroy_all_data_action), onClick = onDestroyAllDataClick)
    }
}

@Composable
private fun DangerousRow(label: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
    }
}
