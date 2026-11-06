package com.yamahamx.editor.data.midi

import java.io.ByteArrayOutputStream

/**
 * Encodes and decodes standard USB-MIDI 1.0 4-byte Event Packets.
 *
 * Each USB-MIDI event packet consists of 4 bytes:
 * [Cable Number (4 bits) | Code Index Number / CIN (4 bits)] [MIDI Byte 1] [MIDI Byte 2] [MIDI Byte 3]
 *
 * Standard CIN mapping (USB-MIDI 1.0 Specification Table 4-1):
 * - 0x2: 2-byte System Common (MTC Quarter Frame, Song Select)
 * - 0x3: 3-byte System Common (Song Position Pointer)
 * - 0x4: SysEx starts or continues (3 bytes)
 * - 0x5: Single-byte System Common or SysEx ends with 1 byte
 * - 0x6: SysEx ends with 2 bytes
 * - 0x7: SysEx ends with 3 bytes
 * - 0x8: Note Off (3 bytes)
 * - 0x9: Note On (3 bytes)
 * - 0xA: Poly-KeyPress / Poly Pressure (3 bytes)
 * - 0xB: Control Change (3 bytes)
 * - 0xC: Program Change (2 bytes)
 * - 0xD: Channel Pressure / Aftertouch (2 bytes)
 * - 0xE: Pitch Bend Change (3 bytes)
 * - 0xF: Single Byte / Real-Time (1 byte)
 */
object UsbMidiPacketConverter {

    /**
     * Converts a raw MIDI byte stream (including SysEx, Channel Voice messages, Real-Time)
     * into standard 4-byte USB-MIDI 1.0 event packets.
     */
    fun encodeToUsbMidiPackets(bytes: ByteArray, cableNumber: Int = 0): ByteArray {
        if (bytes.isEmpty()) return ByteArray(0)

        val out = ByteArrayOutputStream((bytes.size / 3 + 1) * 4)
        val cableShl4 = (cableNumber and 0x0F) shl 4

        var i = 0
        val n = bytes.size
        var runningStatus = 0

        while (i < n) {
            val b0 = bytes[i].toInt() and 0xFF

            if (b0 == 0xF0) {
                runningStatus = 0
                // System Exclusive (SysEx) Start
                // Find terminating 0xF7 or take until end of stream
                var endIdx = i
                while (endIdx < n && (bytes[endIdx].toInt() and 0xFF) != 0xF7) {
                    endIdx++
                }
                // endIdx is now at 0xF7 (if found) or at n-1 (if unterminated)
                val sysexLen = if (endIdx < n) endIdx - i + 1 else n - i

                var offset = 0
                while (offset < sysexLen) {
                    val remaining = sysexLen - offset
                    val p0 = bytes[i + offset]
                    if (remaining > 3) {
                        // CIN 0x4: SysEx starts or continues (3 bytes)
                        val p1 = bytes[i + offset + 1]
                        val p2 = bytes[i + offset + 2]
                        out.write(cableShl4 or 0x04)
                        out.write(p0.toInt())
                        out.write(p1.toInt())
                        out.write(p2.toInt())
                        offset += 3
                    } else if (remaining == 3) {
                        val p1 = bytes[i + offset + 1]
                        val p2 = bytes[i + offset + 2]
                        val isTerminated = (p2.toInt() and 0xFF) == 0xF7
                        val cin = if (isTerminated) 0x07 else 0x04
                        out.write(cableShl4 or cin)
                        out.write(p0.toInt())
                        out.write(p1.toInt())
                        out.write(p2.toInt())
                        offset += 3
                    } else if (remaining == 2) {
                        val p1 = bytes[i + offset + 1]
                        val isTerminated = (p1.toInt() and 0xFF) == 0xF7
                        val cin = if (isTerminated) 0x06 else 0x04
                        out.write(cableShl4 or cin)
                        out.write(p0.toInt())
                        out.write(p1.toInt())
                        out.write(0)
                        offset += 2
                    } else { // remaining == 1
                        val isTerminated = (p0.toInt() and 0xFF) == 0xF7
                        val cin = if (isTerminated) 0x05 else 0x04
                        out.write(cableShl4 or cin)
                        out.write(p0.toInt())
                        out.write(0)
                        out.write(0)
                        offset += 1
                    }
                }
                i += sysexLen
            } else if (b0 in 0x80..0xEF) {
                // Channel Voice Messages
                runningStatus = b0
                val statusType = b0 and 0xF0
                when (statusType) {
                    0x80 -> { // Note Off (3 bytes)
                        val b1 = if (i + 1 < n) bytes[i + 1].toInt() else 0
                        val b2 = if (i + 2 < n) bytes[i + 2].toInt() else 0
                        out.write(cableShl4 or 0x08)
                        out.write(b0)
                        out.write(b1)
                        out.write(b2)
                        i += 3
                    }
                    0x90 -> { // Note On (3 bytes)
                        val b1 = if (i + 1 < n) bytes[i + 1].toInt() else 0
                        val b2 = if (i + 2 < n) bytes[i + 2].toInt() else 0
                        out.write(cableShl4 or 0x09)
                        out.write(b0)
                        out.write(b1)
                        out.write(b2)
                        i += 3
                    }
                    0xA0 -> { // Poly Pressure (3 bytes)
                        val b1 = if (i + 1 < n) bytes[i + 1].toInt() else 0
                        val b2 = if (i + 2 < n) bytes[i + 2].toInt() else 0
                        out.write(cableShl4 or 0x0A)
                        out.write(b0)
                        out.write(b1)
                        out.write(b2)
                        i += 3
                    }
                    0xB0 -> { // Control Change (3 bytes)
                        val b1 = if (i + 1 < n) bytes[i + 1].toInt() else 0
                        val b2 = if (i + 2 < n) bytes[i + 2].toInt() else 0
                        out.write(cableShl4 or 0x0B)
                        out.write(b0)
                        out.write(b1)
                        out.write(b2)
                        i += 3
                    }
                    0xC0 -> { // Program Change (2 bytes)
                        val b1 = if (i + 1 < n) bytes[i + 1].toInt() else 0
                        out.write(cableShl4 or 0x0C)
                        out.write(b0)
                        out.write(b1)
                        out.write(0)
                        i += 2
                    }
                    0xD0 -> { // Channel Pressure (2 bytes)
                        val b1 = if (i + 1 < n) bytes[i + 1].toInt() else 0
                        out.write(cableShl4 or 0x0D)
                        out.write(b0)
                        out.write(b1)
                        out.write(0)
                        i += 2
                    }
                    0xE0 -> { // Pitch Bend (3 bytes)
                        val b1 = if (i + 1 < n) bytes[i + 1].toInt() else 0
                        val b2 = if (i + 2 < n) bytes[i + 2].toInt() else 0
                        out.write(cableShl4 or 0x0E)
                        out.write(b0)
                        out.write(b1)
                        out.write(b2)
                        i += 3
                    }
                    else -> {
                        out.write(cableShl4 or 0x0F)
                        out.write(b0)
                        out.write(0)
                        out.write(0)
                        i++
                    }
                }
            } else if (b0 in 0xF8..0xFF) {
                // Real-Time (1 byte, does not clear runningStatus)
                out.write(cableShl4 or 0x0F)
                out.write(b0)
                out.write(0)
                out.write(0)
                i++
            } else if (b0 == 0xF1 || b0 == 0xF3) {
                runningStatus = 0
                // 2-byte System Common (MTC Quarter Frame, Song Select)
                val b1 = if (i + 1 < n) bytes[i + 1].toInt() else 0
                out.write(cableShl4 or 0x02)
                out.write(b0)
                out.write(b1)
                out.write(0)
                i += 2
            } else if (b0 == 0xF2) {
                runningStatus = 0
                // 3-byte System Common (Song Position Pointer)
                val b1 = if (i + 1 < n) bytes[i + 1].toInt() else 0
                val b2 = if (i + 2 < n) bytes[i + 2].toInt() else 0
                out.write(cableShl4 or 0x03)
                out.write(b0)
                out.write(b1)
                out.write(b2)
                i += 3
            } else if (b0 == 0xF6 || b0 == 0xF7) {
                runningStatus = 0
                // 1-byte System Common / Tune Request / End of SysEx
                out.write(cableShl4 or 0x05)
                out.write(b0)
                out.write(0)
                out.write(0)
                i++
            } else if (b0 < 0x80 && runningStatus in 0x80..0xEF) {
                // Running Status Channel Voice Message
                val statusType = runningStatus and 0xF0
                when (statusType) {
                    0x80, 0x90, 0xA0, 0xB0, 0xE0 -> {
                        val cin = (statusType ushr 4) and 0x0F
                        val b2 = if (i + 1 < n) bytes[i + 1].toInt() else 0
                        out.write(cableShl4 or cin)
                        out.write(runningStatus)
                        out.write(b0)
                        out.write(b2)
                        i += 2
                    }
                    0xC0, 0xD0 -> {
                        val cin = (statusType ushr 4) and 0x0F
                        out.write(cableShl4 or cin)
                        out.write(runningStatus)
                        out.write(b0)
                        out.write(0)
                        i += 1
                    }
                    else -> {
                        out.write(cableShl4 or 0x0F)
                        out.write(b0)
                        out.write(0)
                        out.write(0)
                        i++
                    }
                }
            } else {
                // Fallback for single byte
                out.write(cableShl4 or 0x0F)
                out.write(b0)
                out.write(0)
                out.write(0)
                i++
            }
        }

        return out.toByteArray()
    }

    /**
     * Decodes 4-byte USB-MIDI 1.0 event packets from a raw USB Bulk IN buffer
     * into a stream of standard MIDI bytes.
     */
    fun decodeUsbMidiPackets(buffer: ByteArray, count: Int): ByteArray {
        return try {
            val safeCount = count.coerceIn(0, buffer.size)
            if (safeCount <= 0) return ByteArray(0)
            if (safeCount < 4) {
                val b0 = buffer[0].toInt() and 0xFF
                return if (b0 >= 0x80) buffer.copyOfRange(0, safeCount) else ByteArray(0)
            }

            val out = ByteArrayOutputStream(safeCount)
            var step = 0
            var inUsbSysEx = false

            while (step + 4 <= safeCount) {
                val header = buffer[step].toInt() and 0xFF
                val cable = (header ushr 4) and 0x0F
                val cin = header and 0x0F

                // Ignore secondary USB virtual cables (e.g. Cable 1 DAW Remote) and CIN 0x0/0x1 padding
                if (cable != 0 || cin == 0x0 || cin == 0x1) {
                    step += 4
                    continue
                }

                val b1 = buffer[step + 1].toInt() and 0xFF
                val b2 = buffer[step + 2].toInt() and 0xFF
                val b3 = buffer[step + 3].toInt() and 0xFF

                when (cin) {
                    // 3-byte Channel Voice / System Common messages
                    0x8, 0x9, 0xA, 0xB, 0xE, 0x3 -> {
                        if (b1 >= 0x80) {
                            inUsbSysEx = false
                            out.write(b1)
                            out.write(b2)
                            out.write(b3)
                        }
                    }
                    // SysEx starts or continues (3 bytes)
                    0x4 -> {
                        if (!inUsbSysEx && b1 != 0xF0) {
                            out.write(0xF0)
                        }
                        inUsbSysEx = true
                        out.write(b1)
                        out.write(b2)
                        out.write(b3)
                    }
                    // SysEx ends with 3 bytes
                    0x7 -> {
                        if (!inUsbSysEx && b1 != 0xF0) {
                            out.write(0xF0)
                        }
                        inUsbSysEx = false
                        out.write(b1)
                        out.write(b2)
                        out.write(b3)
                    }
                    // 2-byte Channel Voice / System Common messages
                    0x2, 0xC, 0xD -> {
                        if (b1 >= 0x80) {
                            inUsbSysEx = false
                            out.write(b1)
                            out.write(b2)
                        }
                    }
                    // SysEx ends with 2 bytes
                    0x6 -> {
                        if (!inUsbSysEx && b1 != 0xF0) {
                            out.write(0xF0)
                        }
                        inUsbSysEx = false
                        out.write(b1)
                        out.write(b2)
                    }
                    // Single-byte System Common or SysEx ends with 1 byte (0xF7)
                    0x5 -> {
                        inUsbSysEx = false
                        out.write(b1)
                    }
                    // Single byte (System Real-Time or raw byte)
                    0xF -> {
                        out.write(b1)
                    }
                }
                step += 4
            }

            out.toByteArray()
        } catch (e: Throwable) {
            try {
                android.util.Log.w("UsbMidiPacketConverter", "Defensive catch in decodeUsbMidiPackets", e)
            } catch (_: Throwable) {}
            ByteArray(0)
        }
    }
}

