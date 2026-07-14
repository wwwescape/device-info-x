package com.wwwescape.deviceinfox.ui.screens.display

import androidx.lifecycle.ViewModel
import com.wwwescape.deviceinfox.data.display.DisplayInfo
import com.wwwescape.deviceinfox.data.display.DisplayRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class DisplayViewModel @Inject constructor(
    displayRepository: DisplayRepository,
) : ViewModel() {
    val displayInfo: DisplayInfo = displayRepository.collectStatic()
}
