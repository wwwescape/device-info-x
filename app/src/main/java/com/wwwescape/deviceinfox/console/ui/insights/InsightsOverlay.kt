package com.wwwescape.deviceinfox.console.ui.insights

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wwwescape.deviceinfox.console.data.insights.Insight
import com.wwwescape.deviceinfox.console.data.insights.InsightsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class InsightsViewModel @Inject constructor(
    private val repository: InsightsRepository,
) : ViewModel() {
    val unseenInsights: StateFlow<List<Insight>> = repository.unseenInsights

    fun dismiss(ids: List<String>) = repository.dismiss(ids)
}

/** Mounted once at [com.wwwescape.deviceinfox.console.ui.nav.ConsoleNavHost]'s level, alongside
 * `WhatsNewOverlay` — same "once per console open, regardless of which tab" reasoning. Renders
 * nothing until the one-shot fetch resolves with at least one unseen insight. */
@Composable
fun InsightsOverlay(modifier: Modifier = Modifier, viewModel: InsightsViewModel = hiltViewModel()) {
    val insights by viewModel.unseenInsights.collectAsStateWithLifecycle()
    if (insights.isEmpty()) return
    InsightsDialog(
        items = insights.map { it.text },
        onDismiss = { viewModel.dismiss(insights.map { it.id }) },
        modifier = modifier,
    )
}
