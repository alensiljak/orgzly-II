package com.orgzly.android.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppBar(var modes: Map<Int, Int?>) {

    val mode: SingleLiveEvent<Int> = SingleLiveEvent()
    private val _currentMode = MutableStateFlow(0)
    val currentMode = _currentMode.asStateFlow()

    fun toModeFromSelectionCount(count: Int) {
        if (count == 0) {
            // No selection, default mode
            toMode(0)
        } else {
            // Use currentMode (StateFlow, always initialised to 0) rather than the SingleLiveEvent
            // mode, whose value is null until first set — otherwise the first selection never
            // switches to selection mode.
            if (currentMode.value == 0) {
                // Selection, from default mode
                toMode(1)
            }
            // else: keep current mode
        }
    }

    fun toMode(id: Int) {
        this.mode.value = id
        _currentMode.value = id
    }

    fun handleOnBackPressed() {
        mode.value?.let { currentMode ->
            val previousMode = modes[currentMode]
            if (previousMode != null) {
                toMode(previousMode)
            }
        }
    }
}