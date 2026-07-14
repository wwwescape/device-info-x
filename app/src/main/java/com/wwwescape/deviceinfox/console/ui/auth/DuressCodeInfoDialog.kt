package com.wwwescape.deviceinfox.console.ui.auth

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.wwwescape.deviceinfox.R

/** Explainer for the duress-code trigger (see
 * [com.wwwescape.deviceinfox.console.data.auth.AuthRepository.verifyPin]) — shown once
 * automatically right after a new code is confirmed during setup ([PinGateViewModel.showDuressInfo]),
 * and reachable anytime after via "Learn about Duress Code" in Settings > General > Security. */
@Composable
fun DuressCodeInfoDialog(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.console_duress_info_title)) },
        text = { Text(stringResource(R.string.console_duress_info_body)) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.console_duress_info_action)) }
        },
        modifier = modifier,
    )
}
