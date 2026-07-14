package com.wwwescape.deviceinfox.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wwwescape.deviceinfox.ui.components.CategoryGrid
import com.wwwescape.deviceinfox.ui.components.DashboardHero
import com.wwwescape.deviceinfox.ui.components.FakeNotificationCardHost
import com.wwwescape.deviceinfox.ui.components.LiveMetricsSection
import com.wwwescape.deviceinfox.ui.navigation.Destination

/** The tabless Dashboard: the fake notification card (when there is one) above the hero device
 * summary, the full category grid, and Live Metrics. */
@Composable
fun OverviewScreen(
    onCategoryClick: (Destination) -> Unit,
    modifier: Modifier = Modifier,
) {
    CategoryGrid(
        categories = Destination.detailScreens,
        onCategoryClick = onCategoryClick,
        modifier = modifier,
        header = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                FakeNotificationCardHost()
                DashboardHero()
            }
        },
        footer = { LiveMetricsSection() },
    )
}
