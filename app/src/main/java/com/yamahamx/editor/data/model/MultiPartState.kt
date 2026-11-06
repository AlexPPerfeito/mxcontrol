package com.yamahamx.editor.data.model

import com.yamahamx.editor.data.repository.VoiceRepository

/**
 * State representing one of the 16 parts in a Yamaha MX Performance.
 */
data class MultiPartState(
    val partIndex: Int, // 0 to 15 (corresponds to Part 1 to 16)
    val voice: YamahaVoice = VoiceRepository.getDefaultVoiceForPart(partIndex),
    val volume: Int = 100, // 0..127 (SysEx AddrLow: 0x0E)
    val pan: Int = 64, // 1..127, Center = 64 (SysEx AddrLow: 0x0F)
    val reverbSend: Int = 12, // 0..127 (SysEx AddrLow: 0x12)
    val chorusSend: Int = 0, // 0..127 (SysEx AddrLow: 0x13)
    val noteShift: Int = 64, // 40..88 (-24..+24 semitones, Center = 64) (SysEx AddrLow: 0x16)
    val noteLimitLow: Int = 0, // 0..127 (SysEx AddrLow: 0x08)
    val noteLimitHigh: Int = 127, // 0..127 (SysEx AddrLow: 0x09)
    val cutoff: Int = 64, // 0..127 (SysEx AddrLow: 0x2D)
    val resonance: Int = 64, // 0..127 (SysEx AddrLow: 0x2E)
    val modulation: Int = 0, // 0..127 (MIDI CC 1)
    val isModulationEnabled: Boolean = true, // Receive Switch: Modulation Wheel (SysEx AddrLow: 0x38)
    val isMuted: Boolean = false,
    val isSolo: Boolean = false,
    val receiveChannel: Int = partIndex, // 0..15 (Ch 1..16), 127 for Off (SysEx AddrLow: 0x04)
    val isLayerSelected: Boolean = false // Multi-selection / active layer flag in UI
) {
    val partNumber: Int get() = partIndex + 1
    val isSplitActive: Boolean get() = noteLimitLow > 0 || noteLimitHigh < 127
    val isLayerActive: Boolean get() = isLayerSelected

    val receiveChannelDisplay: String
        get() = when {
            receiveChannel !in 0..15 -> "Off"
            receiveChannel == 0 -> if (isLayerSelected) "Ch 1 (Layer)" else "Ch 1 (Teclado)"
            else -> "Ch ${receiveChannel + 1}"
        }

    fun isAudible(anySoloActive: Boolean): Boolean {
        return if (anySoloActive) {
            isSolo && !isMuted && volume > 0
        } else {
            !isMuted && volume > 0
        }
    }

    fun isEffectivelyMuted(anySoloActive: Boolean): Boolean {
        return if (anySoloActive) {
            !isSolo || isMuted
        } else {
            isMuted
        }
    }

    fun effectiveVolume(anySoloActive: Boolean): Int {
        return if (isEffectivelyMuted(anySoloActive)) 0 else volume
    }
}
