package com.wwwescape.deviceinfox.console.ui.whatsnew

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wwwescape.deviceinfox.console.data.whatsnew.WhatsNewEntry
import com.wwwescape.deviceinfox.console.data.whatsnew.WhatsNewRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class WhatsNewViewModel @Inject constructor(
    private val repository: WhatsNewRepository,
) : ViewModel() {
    val unseenEntries: StateFlow<List<WhatsNewEntry>> = repository.unseenEntries

    fun dismiss(tags: List<String>) = repository.dismiss(tags)
}

/** Mounted once at [com.wwwescape.deviceinfox.console.ui.nav.ConsoleNavHost]'s level, alongside
 * `FeatureTourOverlay` — checked once per console open, shown regardless of which tab the user
 * lands on. Renders nothing until the one-shot fetch resolves with at least one unseen entry. */
@Composable
fun WhatsNewOverlay(modifier: Modifier = Modifier, viewModel: WhatsNewViewModel = hiltViewModel()) {
    val entries by viewModel.unseenEntries.collectAsStateWithLifecycle()
    if (entries.isEmpty()) return
    val items = entries.map { stringResource(it.textRes) }
    WhatsNewDialog(
        items = items,
        onDismiss = { viewModel.dismiss(entries.map { it.tag }) },
        modifier = modifier,
    )
}
