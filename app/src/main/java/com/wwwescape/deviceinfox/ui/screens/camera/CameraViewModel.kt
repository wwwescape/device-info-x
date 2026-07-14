package com.wwwescape.deviceinfox.ui.screens.camera

import androidx.lifecycle.ViewModel
import com.wwwescape.deviceinfox.data.camera.CameraLensInfo
import com.wwwescape.deviceinfox.data.camera.CameraRepository
import com.wwwescape.deviceinfox.util.CameraLensRole
import com.wwwescape.deviceinfox.util.classifyLensRoles
import com.wwwescape.deviceinfox.util.mainLens
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class CameraViewModel @Inject constructor(
    cameraRepository: CameraRepository,
) : ViewModel() {
    val cameras: List<CameraLensInfo> = cameraRepository.listCameras()
    val lensRoles: Map<String, CameraLensRole> = classifyLensRoles(cameras)
    val mainLens: CameraLensInfo? = mainLens(cameras, lensRoles)
}
