package com.yamahamx.editor.data.midi

/**
 * Yamaha MX49 / MX61 System Exclusive (SysEx) Parameter Change and MIDI message encoder.
 *
 * Base SysEx format:
 * F0 43 10 7F 17 AddrHigh AddrMid AddrLow Data F7
 *
 * Manufacturer ID: 0x43 (Yamaha)
 * Device ID: 0x10
 * Model ID: 0x7F 0x17 (MX49 / MX61)
 */
object YamahaSysExEncoder {

    const val SYSEX_START: Byte = 0xF0.toByte()
    const val SYSEX_END: Byte = 0xF7.toByte()
    const val YAMAHA_ID: Byte = 0x43.toByte()
    const val DEVICE_NUMBER: Byte = 0x10.toByte()
    const val MODEL_ID_1: Byte = 0x7F.toByte()
    const val MODEL_ID_2: Byte = 0x17.toByte()

    // High Addresses
    const val ADDR_HIGH_MULTI_COMMON: Byte = 0x36.toByte()
    const val ADDR_HIGH_MULTI_PART: Byte = 0x37.toByte()

    // Multi Common Low Addresses
    const val ADDR_LOW_COMMON_PART_1_2_LINK: Byte = 0x44.toByte()

    // Multi Master EQ Addresses (AddrHigh = 0x36, AddrMid = 0x10)
    const val ADDR_MID_MASTER_EQ: Byte = 0x10.toByte()
    const val ADDR_LOW_MEQ_BAND_1_GAIN: Byte = 0x00.toByte() // Low Gain
    const val ADDR_LOW_MEQ_BAND_2_GAIN: Byte = 0x04.toByte() // Low-Mid Gain
    const val ADDR_LOW_MEQ_BAND_3_GAIN: Byte = 0x08.toByte() // Mid Gain
    const val ADDR_LOW_MEQ_BAND_4_GAIN: Byte = 0x0C.toByte() // High-Mid Gain
    const val ADDR_LOW_MEQ_BAND_5_GAIN: Byte = 0x10.toByte() // High Gain

    private val MASTER_EQ_GAIN_OFFSETS = byteArrayOf(
        ADDR_LOW_MEQ_BAND_1_GAIN,
        ADDR_LOW_MEQ_BAND_2_GAIN,
        ADDR_LOW_MEQ_BAND_3_GAIN,
        ADDR_LOW_MEQ_BAND_4_GAIN,
        ADDR_LOW_MEQ_BAND_5_GAIN
    )

    // Multi Part Low Addresses
    const val ADDR_LOW_PART_BANK_MSB: Byte = 0x01.toByte()
    const val ADDR_LOW_PART_BANK_LSB: Byte = 0x02.toByte()
    const val ADDR_LOW_PART_PROGRAM_NUMBER: Byte = 0x03.toByte()
    const val ADDR_LOW_PART_RECEIVE_CHANNEL: Byte = 0x04.toByte() // 0x00..0x0F (Ch 1..16), 0x7F (Off)
    const val ADDR_LOW_PART_NOTE_LIMIT_LOW: Byte = 0x08.toByte()
    const val ADDR_LOW_PART_NOTE_LIMIT_HIGH: Byte = 0x09.toByte()
    const val ADDR_LOW_PART_VOLUME: Byte = 0x0E.toByte()
    const val ADDR_LOW_PART_PAN: Byte = 0x0F.toByte()
    const val ADDR_LOW_PART_REVERB_SEND: Byte = 0x12.toByte()
    const val ADDR_LOW_PART_CHORUS_SEND: Byte = 0x13.toByte()
    const val ADDR_LOW_PART_NOTE_SHIFT: Byte = 0x16.toByte()
    const val ADDR_LOW_PART_CUTOFF: Byte = 0x2D.toByte()
    const val ADDR_LOW_PART_RESONANCE: Byte = 0x2E.toByte()
    const val ADDR_LOW_PART_RCV_MODULATION_SWITCH: Byte = 0x38.toByte()
    const val ADDR_LOW_PART_RCV_SUSTAIN_SWITCH: Byte = 0x3A.toByte() // 0x00 (Off), 0x01 (On)
    const val ADDR_LOW_PART_RCV_EXPRESSION_SWITCH: Byte = 0x3B.toByte() // 0x00 (Off), 0x01 (On)

    /**
     * Creates a single Parameter Change SysEx message.
     */
    fun buildParamChange(
        addrHigh: Byte,
        addrMid: Byte,
        addrLow: Byte,
        data: Byte
    ): ByteArray {
        return byteArrayOf(
            SYSEX_START,
            YAMAHA_ID,
            DEVICE_NUMBER,
            MODEL_ID_1,
            MODEL_ID_2,
            addrHigh,
            addrMid,
            addrLow,
            data,
            SYSEX_END
        )
    }

    // --- Multi Common Messages ---

    fun setPart12Link(enabled: Boolean): ByteArray {
        val data = if (enabled) 0x01.toByte() else 0x00.toByte()
        return buildParamChange(ADDR_HIGH_MULTI_COMMON, 0x00.toByte(), ADDR_LOW_COMMON_PART_1_2_LINK, data)
    }

    // --- Multi Part Messages (partIndex: 0..15 for Parts 1..16) ---

    /**
     * Builds the Yamaha MX Master EQ Band Gain SysEx Parameter Change message:
     * `F0 43 10 7F 17 36 10 <addrLow> <dataByte> F7`
     *
     * @param bandIndex 0..4 corresponding to Bands 1..5 (Low, Low-Mid, Mid, High-Mid, High).
     *   Addresses: Band 1 = 0x00, Band 2 = 0x04, Band 3 = 0x08, Band 4 = 0x0C, Band 5 = 0x10.
     * @param uiGainValue 0..127 (center 64 = 0.0 dB, 0 = -12.0 dB, 127 = +12.0 dB).
     *   Mapped to Yamaha MX hardware data byte range 52..76 (0x34..0x4C, center 0x40 = 64 = 0 dB).
     */
    fun setMasterEqBandGain(bandIndex: Int, uiGainValue: Int): ByteArray {
        val clampedBand = bandIndex.coerceIn(0, 4)
        val addrLow = MASTER_EQ_GAIN_OFFSETS[clampedBand]
        val clampedUi = uiGainValue.coerceIn(0, 127)
        val dbOffset = when {
            clampedUi == 64 -> 0
            clampedUi > 64 -> kotlin.math.round(((clampedUi - 64) / 63f) * 12f).toInt()
            else -> kotlin.math.round(((clampedUi - 64) / 64f) * 12f).toInt()
        }
        val hardwareByte = (64 + dbOffset).coerceIn(52, 76).toByte()
        return buildParamChange(ADDR_HIGH_MULTI_COMMON, ADDR_MID_MASTER_EQ, addrLow, hardwareByte)
    }

    /**
     * Converts a 0..127 UI EQ fader value (center 64 = 0.0 dB) to decibels (-12.0 .. +12.0 dB) in 0.5 dB steps.
     */
    fun rawToEqDb(rawValue: Int): Float {
        val clamped = rawValue.coerceIn(0, 127)
        val rawDb = when {
            clamped == 64 -> 0f
            clamped > 64 -> ((clamped - 64) / 63f) * 12f
            else -> ((clamped - 64) / 64f) * 12f
        }
        return kotlin.math.round(rawDb * 2f) / 2f
    }

    /**
     * Formats a 0..127 UI EQ fader value into a real-time dB label (e.g. "+2.5 dB", "0.0 dB", "-4.0 dB").
     */
    fun formatEqGainDb(rawValue: Int): String {
        val db = rawToEqDb(rawValue)
        return when {
            db > 0f -> String.format(java.util.Locale.US, "+%.1f dB", db)
            db < 0f -> String.format(java.util.Locale.US, "%.1f dB", db)
            else -> "0.0 dB"
        }
    }

    fun setPartBankMSB(partIndex: Int, msb: Int): ByteArray {
        val clampedPart = partIndex.coerceIn(0, 15).toByte()
        val clampedData = msb.coerceIn(0, 127).toByte()
        return buildParamChange(ADDR_HIGH_MULTI_PART, clampedPart, ADDR_LOW_PART_BANK_MSB, clampedData)
    }

    fun setPartBankLSB(partIndex: Int, lsb: Int): ByteArray {
        val clampedPart = partIndex.coerceIn(0, 15).toByte()
        val clampedData = lsb.coerceIn(0, 127).toByte()
        return buildParamChange(ADDR_HIGH_MULTI_PART, clampedPart, ADDR_LOW_PART_BANK_LSB, clampedData)
    }

    fun setPartProgramNumber(partIndex: Int, programNumber: Int): ByteArray {
        val clampedPart = partIndex.coerceIn(0, 15).toByte()
        val clampedData = programNumber.coerceIn(0, 127).toByte()
        return buildParamChange(ADDR_HIGH_MULTI_PART, clampedPart, ADDR_LOW_PART_PROGRAM_NUMBER, clampedData)
    }

    /**
     * Sets the MIDI Receive Channel for a part.
     * @param channel 0..15 for MIDI channels 1..16, or 127 (0x7F) for Off.
     */
    fun setPartReceiveChannel(partIndex: Int, channel: Int): ByteArray {
        val clampedPart = partIndex.coerceIn(0, 15).toByte()
        val channelByte = if (channel in 0..15) channel.toByte() else 0x7F.toByte()
        return buildParamChange(ADDR_HIGH_MULTI_PART, clampedPart, ADDR_LOW_PART_RECEIVE_CHANNEL, channelByte)
    }

    /**
     * Creates the sequence of SysEx and MIDI messages required to switch Voices on the Yamaha MX:
     * 1. Bank Select MSB (SysEx AddrLow: 0x01)
     * 2. Bank Select LSB (SysEx AddrLow: 0x02)
     * 3. Program Number (SysEx AddrLow: 0x03, Base-0: 0..127)
     * 4. Channel Voice Bank Select MSB (CC 0), LSB (CC 32) and Program Change (0xC0)
     */
    fun createVoiceChangeMessages(
        partIndex: Int,
        msb: Int,
        lsb: Int,
        programChange: Int
    ): List<ByteArray> {
        val clampedPart = partIndex.coerceIn(0, 15)
        val clampedMsb = msb.coerceIn(0, 127)
        val clampedLsb = lsb.coerceIn(0, 127)
        val clampedPc = programChange.coerceIn(0, 127)

        val channelStatus = (0xB0 or clampedPart).toByte()
        val pcStatus = (0xC0 or clampedPart).toByte()

        return listOf(
            // SysEx Multi Part Parameter Change Sequence
            setPartBankMSB(clampedPart, clampedMsb),
            setPartBankLSB(clampedPart, clampedLsb),
            setPartProgramNumber(clampedPart, clampedPc),
            // Standard MIDI Channel Voice Messages
            byteArrayOf(channelStatus, 0x00.toByte(), clampedMsb.toByte()),
            byteArrayOf(channelStatus, 0x20.toByte(), clampedLsb.toByte()),
            byteArrayOf(pcStatus, clampedPc.toByte())
        )
    }

    fun setPartVolume(partIndex: Int, volume: Int): ByteArray {
        val clampedPart = partIndex.coerceIn(0, 15).toByte()
        val clampedData = volume.coerceIn(0, 127).toByte()
        return buildParamChange(ADDR_HIGH_MULTI_PART, clampedPart, ADDR_LOW_PART_VOLUME, clampedData)
    }

    /**
     * Native Yamaha MX Part Volume SysEx message:
     * `F0 43 10 7F 17 37 <partByte> 00 00 <volumeByte> F7`
     *
     * Part addressing is strictly base-0 (`partByte = partIndex`):
     * Part 1 = 0x00, Part 2 = 0x01, Part 3 = 0x02, Part 4 = 0x03 ... Part 16 = 0x0F.
     * Out-of-range indices return an empty array instead of being clamped onto another Part.
     */
    fun setPartVolumeNative(partIndex: Int, volume: Int): ByteArray {
        if (partIndex !in 0..15) return ByteArray(0)
        val partByte = partIndex.toByte()
        val volumeByte = volume.coerceIn(0, 127).toByte()
        return byteArrayOf(
            SYSEX_START,
            YAMAHA_ID,
            DEVICE_NUMBER,
            MODEL_ID_1,
            MODEL_ID_2,
            ADDR_HIGH_MULTI_PART,
            partByte,
            0x00.toByte(),
            0x00.toByte(),
            volumeByte,
            SYSEX_END
        )
    }

    fun setPartPan(partIndex: Int, pan: Int): ByteArray {
        val clampedPart = partIndex.coerceIn(0, 15).toByte()
        val clampedData = pan.coerceIn(1, 127).toByte()
        return buildParamChange(ADDR_HIGH_MULTI_PART, clampedPart, ADDR_LOW_PART_PAN, clampedData)
    }

    fun setPartReverbSend(partIndex: Int, reverb: Int): ByteArray {
        val clampedPart = partIndex.coerceIn(0, 15).toByte()
        val clampedData = reverb.coerceIn(0, 127).toByte()
        return buildParamChange(ADDR_HIGH_MULTI_PART, clampedPart, ADDR_LOW_PART_REVERB_SEND, clampedData)
    }

    fun setPartChorusSend(partIndex: Int, chorus: Int): ByteArray {
        val clampedPart = partIndex.coerceIn(0, 15).toByte()
        val clampedData = chorus.coerceIn(0, 127).toByte()
        return buildParamChange(ADDR_HIGH_MULTI_PART, clampedPart, ADDR_LOW_PART_CHORUS_SEND, clampedData)
    }

    fun setPartNoteShift(partIndex: Int, noteShift: Int): ByteArray {
        val clampedPart = partIndex.coerceIn(0, 15).toByte()
        val clampedData = noteShift.coerceIn(40, 88).toByte()
        return buildParamChange(ADDR_HIGH_MULTI_PART, clampedPart, ADDR_LOW_PART_NOTE_SHIFT, clampedData)
    }

    fun setPartNoteLimitLow(partIndex: Int, noteLimitLow: Int): ByteArray {
        val clampedPart = partIndex.coerceIn(0, 15).toByte()
        val clampedData = noteLimitLow.coerceIn(0, 127).toByte()
        return buildParamChange(ADDR_HIGH_MULTI_PART, clampedPart, ADDR_LOW_PART_NOTE_LIMIT_LOW, clampedData)
    }

    fun setPartNoteLimitHigh(partIndex: Int, noteLimitHigh: Int): ByteArray {
        val clampedPart = partIndex.coerceIn(0, 15).toByte()
        val clampedData = noteLimitHigh.coerceIn(0, 127).toByte()
        return buildParamChange(ADDR_HIGH_MULTI_PART, clampedPart, ADDR_LOW_PART_NOTE_LIMIT_HIGH, clampedData)
    }

    fun setPartCutoff(partIndex: Int, cutoff: Int): ByteArray {
        val clampedPart = partIndex.coerceIn(0, 15).toByte()
        val clampedData = cutoff.coerceIn(0, 127).toByte()
        return buildParamChange(ADDR_HIGH_MULTI_PART, clampedPart, ADDR_LOW_PART_CUTOFF, clampedData)
    }

    fun setPartResonance(partIndex: Int, resonance: Int): ByteArray {
        val clampedPart = partIndex.coerceIn(0, 15).toByte()
        val clampedData = resonance.coerceIn(0, 127).toByte()
        return buildParamChange(ADDR_HIGH_MULTI_PART, clampedPart, ADDR_LOW_PART_RESONANCE, clampedData)
    }

    /**
     * Sets the Modulation Wheel Receive Switch for a part (0x37 [part] 0x38 [0 or 1]).
     * When disabled (false / 0x00), the part ignores all incoming Modulation Wheel (CC 1) messages,
     * including from the physical keyboard's modulation wheel and external controllers.
     */
    fun setPartModulationReceiveSwitch(partIndex: Int, enabled: Boolean): ByteArray {
        val clampedPart = partIndex.coerceIn(0, 15).toByte()
        val data = if (enabled) 0x01.toByte() else 0x00.toByte()
        return buildParamChange(ADDR_HIGH_MULTI_PART, clampedPart, ADDR_LOW_PART_RCV_MODULATION_SWITCH, data)
    }

    /**
     * Sets the Expression Pedal (CC 11) Receive Switch for a part (0x37 [part] 0x3B [0 or 1]).
     * - `enabled = true` (0x01): Part responds to Expression Pedal (CC 11), e.g., intentional Layer parts (Part 2 Strings/Pad).
     * - `enabled = false` (0x00): Part is isolated from Expression Pedal (CC 11), e.g., Part 1 Piano, fixed Organs, or external controller parts.
     */
    fun setPartExpressionReceiveSwitch(partIndex: Int, enabled: Boolean): ByteArray {
        val clampedPart = partIndex.coerceIn(0, 15).toByte()
        val data = if (enabled) 0x01.toByte() else 0x00.toByte()
        return buildParamChange(ADDR_HIGH_MULTI_PART, clampedPart, ADDR_LOW_PART_RCV_EXPRESSION_SWITCH, data)
    }

    /**
     * Sets the Sustain Pedal (CC 64) Receive Switch for a part:
     * `F0 43 10 7F 17 37 <partByte> 3A <00|01> F7` (0x01 = ON, 0x00 = OFF).
     */
    fun setPartSustainReceiveSwitch(partIndex: Int, enabled: Boolean): ByteArray {
        val clampedPart = partIndex.coerceIn(0, 15).toByte()
        val data = if (enabled) 0x01.toByte() else 0x00.toByte()
        return buildParamChange(ADDR_HIGH_MULTI_PART, clampedPart, ADDR_LOW_PART_RCV_SUSTAIN_SWITCH, data)
    }

    // --- Safety Flush & Anti-Stuck Notes Commands ---

    /**
     * Builds safety flush messages for a specific MIDI channel (0..15):
     * - CC 64 = 0 (Release Sustain Pedal)
     * - CC 123 = 0 (All Notes Off)
     * - CC 120 = 0 (All Sound Off)
     * - CC 121 = 0 (Reset All Controllers)
     */
    fun createChannelFlushMessages(channel: Int): List<ByteArray> {
        if (channel !in 0..15) return emptyList()
        val status = (0xB0 or channel).toByte()
        return listOf(
            byteArrayOf(status, 64.toByte(), 0x00.toByte()),  // CC 64: Sustain Pedal OFF
            byteArrayOf(status, 123.toByte(), 0x00.toByte()), // CC 123: All Notes Off
            byteArrayOf(status, 120.toByte(), 0x00.toByte()), // CC 120: All Sound Off
            byteArrayOf(status, 121.toByte(), 0x00.toByte())  // CC 121: Reset All Controllers
        )
    }

    /**
     * Builds safety flush messages across all 16 MIDI channels to eliminate any stuck notes.
     */
    fun createAllChannelsFlushMessages(): List<ByteArray> {
        val list = mutableListOf<ByteArray>()
        for (ch in 0..15) {
            list.addAll(createChannelFlushMessages(ch))
        }
        return list
    }

    /**
     * Creates a safe sequence for changing the Receive Channel of a Part without leaving hanging notes:
     * 1. Flush old channel (CC 64=0, CC 123=0, CC 120=0)
     * 2. Send Receive Channel SysEx Parameter Change
     * 3. Flush target channel (CC 64=0, CC 123=0)
     */
    fun createSafeReceiveChannelChangeSequence(
        partIndex: Int,
        oldChannel: Int,
        newChannel: Int
    ): List<ByteArray> {
        val list = mutableListOf<ByteArray>()

        // 1. Flush old channel if valid
        if (oldChannel in 0..15) {
            list.addAll(createChannelFlushMessages(oldChannel))
        }

        // 2. SysEx Parameter Change for Receive Channel (0x37 [part] 0x04 [channel])
        list.add(setPartReceiveChannel(partIndex, newChannel))

        // 3. Flush target channel if valid
        if (newChannel in 0..15) {
            list.addAll(createChannelFlushMessages(newChannel))
        }

        return list
    }

    /**
     * Builds Panic messages (All Sound Off, All Notes Off, Reset All Controllers) for all 16 channels.
     */
    fun buildPanicMessages(): List<ByteArray> {
        return createAllChannelsFlushMessages()
    }

    /**
     * Builds a standard MIDI Control Change (CC) message.
     * @param channel 0..15 (MIDI Channels 1..16)
     * @param controller 0..127 (e.g. 1 for Modulation Wheel / Rotary Speed, 64 for Sustain Pedal)
     * @param value 0..127
     */
    fun createControlChangeMessage(channel: Int, controller: Int, value: Int): ByteArray {
        val status = (0xB0 or channel.coerceIn(0, 15)).toByte()
        val cc = controller.coerceIn(0, 127).toByte()
        val data = value.coerceIn(0, 127).toByte()
        return byteArrayOf(status, cc, data)
    }

    /**
     * Builds a standard MIDI Note Off (0x80..0x8F) message for releasing spillover notes on a target MIDI channel.
     * @param channel 0..15 (MIDI Channels 1..16)
     * @param note 0..127
     * @param velocity 0..127 (default 0)
     */
    fun createNoteOffMessage(channel: Int, note: Int, velocity: Int = 0): ByteArray {
        val status = (0x80 or channel.coerceIn(0, 15)).toByte()
        val n = note.coerceIn(0, 127).toByte()
        val v = velocity.coerceIn(0, 127).toByte()
        return byteArrayOf(status, n, v)
    }

    /**
     * Formats bytes to hex string for logging and debugging.
     */
    fun toHexString(bytes: ByteArray): String {
        return bytes.joinToString(" ") { String.format("%02X", it) }
    }
}

