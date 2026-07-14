package com.wwwescape.deviceinfox.console.data.network.dto

import com.wwwescape.deviceinfox.console.data.db.CycleFlowIntensity

fun FlowIntensityDto.toCycleFlowIntensity(): CycleFlowIntensity = when (this) {
    FlowIntensityDto.LIGHT -> CycleFlowIntensity.LIGHT
    FlowIntensityDto.MEDIUM -> CycleFlowIntensity.MEDIUM
    FlowIntensityDto.HEAVY -> CycleFlowIntensity.HEAVY
}

fun CycleFlowIntensity.toFlowIntensityDto(): FlowIntensityDto = when (this) {
    CycleFlowIntensity.LIGHT -> FlowIntensityDto.LIGHT
    CycleFlowIntensity.MEDIUM -> FlowIntensityDto.MEDIUM
    CycleFlowIntensity.HEAVY -> FlowIntensityDto.HEAVY
}
