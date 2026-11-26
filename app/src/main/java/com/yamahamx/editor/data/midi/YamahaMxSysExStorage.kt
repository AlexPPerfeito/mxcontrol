package com.yamahamx.editor.data.midi

import com.yamahamx.editor.data.model.MultiPartState
import com.yamahamx.editor.data.model.PerformanceState
import java.text.Normalizer
import java.util.Locale

/**
 * State representing the status of saving a Performance to the physical Yamaha MX hardware.
 */
sealed class SaveToHardwareState {
    object Idle : SaveToHardwareState()

    data class Progress(
        val percentage: Int, // 0..100
        val currentStep: String
    ) : SaveToHardwareState()

    data class Success(
        val message: String
    ) : SaveToHardwareState()

    data class Error(
        val message: String,
        val isConnectionError: Boolean = false
    ) : SaveToHardwareState()
}

/**
 * Yamaha MX49 / MX61 / MX88 Performance Storage Engine.
 *
 * Exclusively uses the 100% safe Assisted Edit Buffer Sync architecture:
 * Transmits all parameters to the active temporary buffer (Edit Buffer), allowing the user
 * to confirm and store safely via the physical `[STORE]` button on the Yamaha MX hardware panel.
 */
object YamahaMxSysExStorage {

    const val MAX_NAME_LENGTH = 10

    // Addresses for Performance Common in Edit Buffer
    const val ADDR_HIGH_PERF_COMMON: Byte = 0x30.toByte()
    const val ADDR_MID_PERF_COMMON: Byte = 0x00.toByte()

    /**
     * Calculates the Yamaha standard checksum:
     * Checksum = (128 - (sum & 0x7F)) & 0x7F
     *
     * In Yamaha SysEx Bulk Dumps and requests, the checksum covers the bytes starting
     * from the Byte Count (or Address) through the end of the Data payload.
     * When the checksum is added to the 7-bit sum of these bytes, the lower 7 bits equal 0.
     *
     * @param addressAndData The byte sequence over which the checksum is computed.
     * @return The 7-bit checksum byte (0x00..0x7F).
     */
    fun calculateYamahaChecksum(addressAndData: ByteArray): Byte {
        var sum = 0
        for (b in addressAndData) {
            sum += (b.toInt() and 0x7F)
        }
        return ((128 - (sum and 0x7F)) and 0x7F).toByte()
    }

    /**
     * Sanitizes a string into a clean, ASCII-compliant 10-character name for the Yamaha MX LCD display:
     * 1. Strips diacritics / accents (e.g. "Órgão" -> "Orgao", "Eléctrico" -> "Electrico").
     * 2. Removes non-printable or non-ASCII characters.
     * 3. Truncates to at most 10 characters.
     * 4. Pads with spaces up to exactly 10 characters if shorter.
     */
    fun sanitizeMxPerformanceName(rawName: String): String {
        val trimmed = rawName.trim()
        if (trimmed.isEmpty()) return "USER PERF "

        // Decompose accents (NFD) and remove combining diacritical marks
        val normalized = Normalizer.normalize(trimmed, Normalizer.Form.NFD)
        val withoutDiacritics = normalized.replace("\\p{M}+".toRegex(), "")

        // Filter for printable ASCII characters (code 32..126)
        val asciiOnly = withoutDiacritics.filter { it.code in 32..126 }
        val clean = if (asciiOnly.isBlank()) "USER PERF" else asciiOnly

        val truncated = if (clean.length > MAX_NAME_LENGTH) clean.substring(0, MAX_NAME_LENGTH) else clean
        return truncated.padEnd(MAX_NAME_LENGTH, ' ')
    }

    /**
     * Returns exactly 10 ASCII bytes representing the sanitized Performance name.
     */
    fun performanceNameToAsciiBytes(name: String): ByteArray {
        val sanitized = sanitizeMxPerformanceName(name)
        return sanitized.toByteArray(Charsets.US_ASCII)
    }

    /**
     * Generates SysEx Parameter Change messages to set the Performance Name on the MX LCD:
     * - Performance Mode Common Name: Address 0x30 0x00 0x00..0x09
     * - Multi Mode Common Name: Address 0x36 0x00 0x00..0x09
     */
    fun buildPerformanceNameMessages(name: String): List<ByteArray> {
        val asciiBytes = performanceNameToAsciiBytes(name)
        val messages = mutableListOf<ByteArray>()

        // 1. Performance Mode Common Name (0x30 0x00 [00..09])
        for (i in 0 until MAX_NAME_LENGTH) {
            messages.add(
                YamahaSysExEncoder.buildParamChange(
                    ADDR_HIGH_PERF_COMMON,
                    ADDR_MID_PERF_COMMON,
                    i.toByte(),
                    asciiBytes[i]
                )
            )
        }

        // 2. Multi Mode Common Name (0x36 0x00 [00..09])
        for (i in 0 until MAX_NAME_LENGTH) {
            messages.add(
                YamahaSysExEncoder.buildParamChange(
                    YamahaSysExEncoder.ADDR_HIGH_MULTI_COMMON,
                    0x00.toByte(),
                    i.toByte(),
                    asciiBytes[i]
                )
            )
        }

        return messages
    }

    /**
     * Builds the standard proven Performance synchronization sequence (identical to the sequence
     * used when loading a Performance from Setlist).
     *
     * Transmits:
     * - Part 1-2 Link status (0x36 0x00 0x44)
     * - Master EQ 5-Band Gains (0x36 0x10)
     * - For each of the 16 Parts:
     *   * Receive Channel (0x37 Part 0x04)
     *   * Voice Change (Bank MSB, LSB, Program Change via SysEx and MIDI CC 0, 32, PC)
     *   * Volume via per-part SysEx (standard + native) and MIDI CC 7 (when channel is exclusive)
     *   * Sustain Switch = ON (0x37 Part 0x3A)
     *   * Pan (0x37 Part 0x0F)
     *   * Note Limits Low & High (0x37 Part 0x08, 0x09)
     *   * Effects: Reverb & Chorus Sends (0x37 Part 0x12, 0x13)
     *   * Note Shift / Transpose (0x37 Part 0x16)
     *   * Filter Cutoff & Resonance (0x37 Part 0x2D, 0x2E)
     *   * Envelopes (AEG Attack CC 73, Decay CC 75, Release CC 72) - only if edited
     *   * Expression Pedal isolation (0x37 Part 0x3B)
     *   * Compressor / Insertion Effect configuration (VCM 376 parameters or bypass)
     * - Multi Common Insertion Part Switch bitmask (0x36 0x00 0x4C)
     *
     * Note: Never modifies Portamento (no CC 65, CC 5, or SysEx offset 0x18), preserving the
     * native factory portamento programming of each selected Voice.
     */
    fun buildStandardPerformanceSyncMessages(state: PerformanceState): List<ByteArray> {
        val batchMessages = mutableListOf<ByteArray>()

        // 1. Part 1-2 Link status
        batchMessages.add(YamahaSysExEncoder.setPart12Link(state.common.part12Link))

        // 2. Master 5-Band EQ Gains
        state.common.masterEqGains.forEachIndexed { bandIdx, gain ->
            if (bandIdx in 0..4) {
                batchMessages.add(YamahaSysExEncoder.setMasterEqBandGain(bandIdx, gain))
            }
        }

        // 3. Multi Part Parameters for Parts 1 to 16
        state.parts.forEach { part ->
            val pIdx = part.partIndex.coerceIn(0, 15)

            // Receive Channel (protect Part 1 from being disabled)
            val rxCh = if (pIdx == 0 && (part.receiveChannel == 127 || part.receiveChannel !in 0..15)) 0 else part.receiveChannel
            batchMessages.add(YamahaSysExEncoder.setPartReceiveChannel(pIdx, rxCh))

            // Voice Change sequence (SysEx Bank MSB, LSB, Program Change + MIDI CC 0, 32, PC)
            val voiceChangeMsgs = YamahaSysExEncoder.createVoiceChangeMessages(
                partIndex = pIdx,
                msb = part.voice.msb,
                lsb = part.voice.lsb,
                programChange = part.voice.programChange
            )
            batchMessages.addAll(voiceChangeMsgs)

            // Volume (effective volume = 0 when muted or silenced by solo)
            val effectiveVol = part.effectiveVolume(state.anySoloActive)
            batchMessages.add(YamahaSysExEncoder.setPartVolume(pIdx, effectiveVol))
            batchMessages.add(YamahaSysExEncoder.setPartVolumeNative(pIdx, effectiveVol))

            // CC 7 only when the Part owns its channel exclusively
            val isExclusiveChannel = rxCh in 0..15 && state.parts.none { it.partIndex != pIdx && it.receiveChannel == rxCh }
            if (isExclusiveChannel) {
                batchMessages.add(YamahaSysExEncoder.createControlChangeMessage(rxCh, 7, effectiveVol))
            }

            // Receive Sustain (CC 64) = ON on every Part
            batchMessages.add(YamahaSysExEncoder.setPartSustainReceiveSwitch(pIdx, true))

            // Pan
            batchMessages.add(YamahaSysExEncoder.setPartPan(pIdx, part.pan))

            // Note Limits (Splits)
            batchMessages.add(YamahaSysExEncoder.setPartNoteLimitLow(pIdx, part.noteLimitLow))
            batchMessages.add(YamahaSysExEncoder.setPartNoteLimitHigh(pIdx, part.noteLimitHigh))

            // Effects: Reverb & Chorus Sends
            batchMessages.add(YamahaSysExEncoder.setPartReverbSend(pIdx, part.reverbSend))
            batchMessages.add(YamahaSysExEncoder.setPartChorusSend(pIdx, part.chorusSend))

            // Note Shift / Transpose
            batchMessages.add(YamahaSysExEncoder.setPartNoteShift(pIdx, part.noteShift))

            // Filter Cutoff & Resonance
            batchMessages.add(YamahaSysExEncoder.setPartCutoff(pIdx, part.cutoff))
            batchMessages.add(YamahaSysExEncoder.setPartResonance(pIdx, part.resonance))

            // Envelope (AEG): Attack (CC 73), Decay (CC 75), Release (CC 72) - only if edited!
            if (rxCh in 0..15 && (part.attack != 64 || part.decay != 64 || part.release != 64)) {
                batchMessages.add(YamahaSysExEncoder.createControlChangeMessage(rxCh, 73, part.attack))
                batchMessages.add(YamahaSysExEncoder.createControlChangeMessage(rxCh, 75, part.decay))
                batchMessages.add(YamahaSysExEncoder.createControlChangeMessage(rxCh, 72, part.release))
            }

            // Expression Pedal (CC 11) Isolation
            val rcvExpr = rxCh == 0 && (part.isLayerSelected || pIdx == 1)
            batchMessages.add(YamahaSysExEncoder.setPartExpressionReceiveSwitch(pIdx, rcvExpr))

            // Insertion Effect / Compressor Parameters & Type
            if (part.isCompressorEnabled) {
                batchMessages.addAll(YamahaSysExEncoder.createSetPartCompressorTypeMessages(pIdx, isVcm = true))
                batchMessages.addAll(YamahaSysExEncoder.setPartInsertionSwitch(pIdx, true))
                batchMessages.addAll(YamahaSysExEncoder.setPartCompressorThresholdAll(pIdx, part.compressorThreshold))
                batchMessages.addAll(YamahaSysExEncoder.setPartCompressorOutputGainAll(pIdx, part.compressorOutputGain))
                batchMessages.addAll(YamahaSysExEncoder.setPartCompressorRatioAll(pIdx, part.compressorRatio))
                batchMessages.addAll(YamahaSysExEncoder.setPartCompressorAttackAll(pIdx, part.compressorAttack))
                batchMessages.addAll(YamahaSysExEncoder.setPartCompressorReleaseAll(pIdx, part.compressorRelease))
            } else {
                batchMessages.addAll(YamahaSysExEncoder.setPartInsertionSwitch(pIdx, false))
            }
        }

        // 4. Multi Common Insertion Part Switch (bitmask for Parts 1..16)
        val enabledInsertParts = state.parts.filter { it.isCompressorEnabled }.map { it.partIndex }.toSet()
        batchMessages.add(YamahaSysExEncoder.createInsertionPartSwitchMessage(enabledInsertParts))

        return batchMessages
    }

    /**
     * Builds the complete list of SysEx and MIDI messages to synchronize the full Performance
     * into the Yamaha MX Edit Buffer (Mode A):
     *
     * 1. Performance & Multi Common Name on MX LCD (20 messages: 10 to 0x30, 10 to 0x36)
     * 2. Standard proven Performance synchronization sequence (identical to Setlist load)
     *
     * No Portamento messages (CC 65, CC 5, or SysEx 0x18) are ever sent, preserving the native
     * factory Portamento of each Voice.
     */
    fun buildFullPerformanceEditBufferMessages(
        state: PerformanceState,
        performanceName: String = state.common.performanceName
    ): List<ByteArray> {
        val messages = mutableListOf<ByteArray>()

        // 1. Performance & Multi Common Name (LCD screen)
        messages.addAll(buildPerformanceNameMessages(performanceName))

        // 2. Proven standard Performance synchronization sequence
        messages.addAll(buildStandardPerformanceSyncMessages(state))

        return messages
    }
}
