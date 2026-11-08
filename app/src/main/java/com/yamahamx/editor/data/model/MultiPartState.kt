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
    val attack: Int = 64, // 0..127 (MIDI CC 73 - AEG Attack Time, Center = 64)
    val decay: Int = 64, // 0..127 (MIDI CC 75 - AEG Decay Time, Center = 64)
    val release: Int = 64, // 0..127 (MIDI CC 72 - AEG Release Time, Center = 64)
    val modulation: Int = 0, // 0..127 (MIDI CC 1)
    val isModulationEnabled: Boolean = true, // Receive Switch: Modulation Wheel (SysEx AddrLow: 0x38)
    val isMuted: Boolean = false,
    val isSolo: Boolean = false,
    val receiveChannel: Int = partIndex, // 0..15 (Ch 1..16), 127 for Off (SysEx AddrLow: 0x04)
    val isLayerSelected: Boolean = false, // Multi-selection / active layer flag in UI
    val isCompressorEnabled: Boolean = false, // Insertion Effect Switch
    val compressorThreshold: Int = 72, // 0..127 (-48 dB .. 0 dB)
    val compressorOutputGain: Int = 64, // 0..127 (-12 dB .. +12 dB, Center 64 = 0 dB)
    val compressorRatio: Int = 3, // Index in COMPRESSOR_RATIO_LABELS (0..7, default 3 = 3.0:1)
    val compressorAttack: Int = 19, // Index in COMPRESSOR_ATTACK_VALUES (0..29, default 19 = 40 ms)
    val compressorRelease: Int = 4 // Index in COMPRESSOR_RELEASE_VALUES (0..23, default 4 = 45 ms)
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
