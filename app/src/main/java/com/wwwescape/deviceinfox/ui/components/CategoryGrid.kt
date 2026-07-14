package com.wwwescape.deviceinfox.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wwwescape.deviceinfox.R
import com.wwwescape.deviceinfox.data.battery.batteryRepositoryEntryPoint
import com.wwwescape.deviceinfox.data.camera.cameraRepositoryEntryPoint
import com.wwwescape.deviceinfox.data.cpu.cpuRepositoryEntryPoint
import com.wwwescape.deviceinfox.data.deviceos.deviceOsRepositoryEntryPoint
import com.wwwescape.deviceinfox.data.display.displayRepositoryEntryPoint
import com.wwwescape.deviceinfox.data.memory.memoryRepositoryEntryPoint
import com.wwwescape.deviceinfox.data.network.networkRepositoryEntryPoint
import com.wwwescape.deviceinfox.data.sensors.sensorsRepositoryEntryPoint
import com.wwwescape.deviceinfox.ui.navigation.Destination
import com.wwwescape.deviceinfox.ui.screens.battery.label
import com.wwwescape.deviceinfox.ui.screens.network.label
import com.wwwescape.deviceinfox.util.formatBytes

/**
 * The Dashboard's category-tile grid. [header] renders above the tiles as a full-width item
 * (the hero card); [footer] renders below the tiles as a full-width item (Live Metrics).
 */
@Composable
fun CategoryGrid(
    categories: List<Destination>,
    onCategoryClick: (Destination) -> Unit,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
) {
    val context = LocalContext.current
    val deviceOsInfo = remember { context.deviceOsRepositoryEntryPoint().collectStatic() }
    val cpuInfo = remember { context.cpuRepositoryEntryPoint().collectStatic() }
    val ramInfo = remember { context.memoryRepositoryEntryPoint().readRamInfo() }
    val batteryInfo = remember { context.batteryRepositoryEntryPoint().currentBatteryInfo() }
    val displayInfo = remember { context.displayRepositoryEntryPoint().collectStatic() }
    val networkSnapshot = remember { context.networkRepositoryEntryPoint().snapshot() }
    val sensorCount = remember { context.sensorsRepositoryEntryPoint().listSensors().size }
    val cameraCount = remember { context.cameraRepositoryEntryPoint().listCameras().size }

    LazyVerticalGrid(
        // Adaptive rather than a fixed count: a fixed 2 columns leaves tiles enormous (mostly
        // empty space) on tablet-width screens. 140.dp keeps 2 columns on phones as narrow as
        // ~344dp while letting wider/tablet screens fill in with more, appropriately-sized columns.
        columns = GridCells.Adaptive(minSize = 140.dp),
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (header != null) {
            item(span = { GridItemSpan(maxLineSpan) }) { header() }
        }
        items(categories, key = { it.route }) { destination ->
            val subtitle = when (destination) {
                Destination.DeviceOs ->
                    stringResource(R.string.headline_device_os, deviceOsInfo.displayName, deviceOsInfo.androidVersion)
                Destination.Cpu ->
                    stringResource(R.string.headline_cpu, cpuInfo.socName, cpuInfo.coreCount)
                Destination.Memory ->
                    stringResource(R.string.headline_memory, formatBytes(ramInfo.totalBytes))
                Destination.Battery ->
                    stringResource(R.string.headline_battery, batteryInfo.percent, batteryInfo.status.label())
                Destination.Display ->
                    stringResource(R.string.headline_display, displayInfo.widthPx, displayInfo.heightPx, displayInfo.refreshRateHz.toInt())
                Destination.Network ->
                    networkSnapshot.connectionType.label()
                Destination.Sensors ->
                    stringResource(R.string.headline_sensors, sensorCount)
                Destination.Camera ->
                    stringResource(R.string.headline_camera, cameraCount)
                else -> stringResource(R.string.subtitle_coming_soon)
            }
            InfoCard(
                title = stringResource(destination.titleRes),
                subtitle = subtitle,
                icon = destination.icon,
                onClick = { onCategoryClick(destination) },
            )
        }
        if (footer != null) {
            item(span = { GridItemSpan(maxLineSpan) }) { footer() }
        }
    }
}
