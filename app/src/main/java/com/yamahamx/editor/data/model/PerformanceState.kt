package com.yamahamx.editor.data.model

/**
 * Main application state for Yamaha MX Performance Editor.
 */
data class PerformanceState(
    val parts: List<MultiPartState> = List(16) { index -> MultiPartState(partIndex = index) },
    val common: MultiCommonState = MultiCommonState(),
    val selectedPartIndex: Int = 0,
    val activeFocusedPartIndex: Int = 0, // The part currently routed to keyboard keys (MIDI Ch 1)
    val isConnected: Boolean = false,
    val connectedDeviceName: String? = null,
    val availableDevices: List<MidiDeviceInfoWrapper> = emptyList(),
    val activePolyphony: Int = 0,
    val isTransmitting: Boolean = false,
    val statusMessage: String = "Pronto"
) {
    val selectedPart: MultiPartState
        get() = parts.getOrElse(selectedPartIndex) { parts.first() }

    val masterEqGains: List<Int>
        get() = common.masterEqGains

    val focusedPart: MultiPartState
        get() = parts.getOrElse(activeFocusedPartIndex) { parts.first() }

    val anySoloActive: Boolean
        get() = parts.any { it.isSolo }

    fun isPartAudible(partIndex: Int): Boolean {
        val part = parts.getOrNull(partIndex) ?: return false
        return part.isAudible(anySoloActive)
    }

    fun isPartEffectivelyMuted(partIndex: Int): Boolean {
        val part = parts.getOrNull(partIndex) ?: return true
        return part.isEffectivelyMuted(anySoloActive)
    }

    fun getEffectiveVolume(partIndex: Int): Int {
        val part = parts.getOrNull(partIndex) ?: return 0
        return part.effectiveVolume(anySoloActive)
    }
}
