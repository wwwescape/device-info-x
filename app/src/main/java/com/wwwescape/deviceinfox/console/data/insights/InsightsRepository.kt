package com.wwwescape.deviceinfox.console.data.insights

import com.wwwescape.deviceinfox.console.data.network.UsersApi
import com.wwwescape.deviceinfox.console.data.network.consoleApiCall
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class Insight(val id: String, val text: String)

/** Server-side "DIX AI" insights — unlike
 * [com.wwwescape.deviceinfox.console.data.whatsnew.WhatsNewRepository], there's no static
 * client-side catalog to diff against: insights are manually authored server-side via the CLI
 * (`add-insight`), so `GET /users/me/unseen-insights` already returns exactly the still-unseen
 * entries, text included. [unseenInsights] is therefore a plain fetched list, not a computed diff
 * — still starts empty until the one-shot [refresh] resolves, same "don't flash stale state"
 * reasoning as [com.wwwescape.deviceinfox.console.data.whatsnew.WhatsNewRepository]'s own
 * `_isLoaded` gate, just expressed as "empty until loaded" directly since there's no seen-state to
 * gate independently of the content itself. */
@Singleton
class InsightsRepository @Inject constructor(
    private val usersApi: UsersApi,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _unseenInsights = MutableStateFlow<List<Insight>>(emptyList())
    val unseenInsights: StateFlow<List<Insight>> = _unseenInsights.asStateFlow()

    init {
        scope.launch { refresh() }
    }

    private suspend fun refresh() {
        val response = runCatching { consoleApiCall { usersApi.unseenInsights() } }.getOrNull() ?: return
        _unseenInsights.value = response.insights.map { Insight(id = it.id, text = it.text) }
    }

    /** Optimistic local update first, same fire-and-forget-per-id shape as
     * [com.wwwescape.deviceinfox.console.data.whatsnew.WhatsNewRepository.dismiss] — each POST is
     * independently idempotent server-side, so a partial network failure just means that one
     * insight re-shows next time, not a corrupted state. */
    fun dismiss(ids: List<String>) {
        _unseenInsights.value = _unseenInsights.value.filterNot { it.id in ids }
        ids.forEach { id ->
            scope.launch { runCatching { consoleApiCall { usersApi.markInsightSeen(id) } } }
        }
    }
}
