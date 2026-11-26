package com.yamahamx.editor.data.midi

import com.yamahamx.editor.data.model.MultiCommonState
import com.yamahamx.editor.data.model.MultiPartState
import com.yamahamx.editor.data.model.PerformanceState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YamahaMxSysExStorageTest {

    @Test
    fun testCalculateYamahaChecksum_KnownVycroSequences() {
        // From Vycro MX Dump Request: 00 3E 37 00 00 -> Checksum 0x0B
        val block1 = byteArrayOf(0x00.toByte(), 0x3E.toByte(), 0x37.toByte(), 0x00.toByte(), 0x00.toByte())
        val cs1 = YamahaMxSysExStorage.calculateYamahaChecksum(block1)
        assertEquals(0x0B.toByte(), cs1)

        // From Vycro MX Current Performance Dump: 00 00 0E 5F 00 -> Checksum 0x13
        val block2 = byteArrayOf(0x00.toByte(), 0x00.toByte(), 0x0E.toByte(), 0x5F.toByte(), 0x00.toByte())
        val cs2 = YamahaMxSysExStorage.calculateYamahaChecksum(block2)
        assertEquals(0x13.toByte(), cs2)
    }

    @Test
    fun testCalculateYamahaChecksum_MathematicalProperty() {
        // Yamaha checksum rule: (sum(bytes & 0x7F) + checksum) & 0x7F == 0
        val testArrays = listOf(
            byteArrayOf(0x00),
            byteArrayOf(0x01),
            byteArrayOf(0x7F.toByte()),
            byteArrayOf(0x10, 0x20, 0x30, 0x40),
            byteArrayOf(0x08, 0x7F.toByte(), 0x10, 0x64.toByte(), 0x3E.toByte(), 0x25.toByte()),
            ByteArray(62) { (it * 7 % 128).toByte() }
        )

        for (arr in testArrays) {
            val cs = YamahaMxSysExStorage.calculateYamahaChecksum(arr)
            var sum = 0
            for (b in arr) {
                sum += (b.toInt() and 0x7F)
            }
            val total = (sum + cs.toInt()) and 0x7F
            assertEquals("Checksum failed to zero-out lower 7 bits for array", 0, total)
        }
    }

    @Test
    fun testCalculateYamahaChecksum_BoundaryValues() {
        // Sum & 0x7F == 0 -> Checksum = 0
        val zeroSum = byteArrayOf(0x00.toByte(), 0x80.toByte())
        assertEquals(0x00.toByte(), YamahaMxSysExStorage.calculateYamahaChecksum(zeroSum))

        // Sum & 0x7F == 1 -> Checksum = 127 (0x7F)
        val oneSum = byteArrayOf(0x01.toByte())
        assertEquals(0x7F.toByte(), YamahaMxSysExStorage.calculateYamahaChecksum(oneSum))

        // Sum & 0x7F == 127 -> Checksum = 1 (0x01)
        val max7BitSum = byteArrayOf(0x7F.toByte())
        assertEquals(0x01.toByte(), YamahaMxSysExStorage.calculateYamahaChecksum(max7BitSum))
    }

    @Test
    fun testSanitizeMxPerformanceName_AccentsAndDiacritics() {
        // Strips accents while preserving letters
        val sanitized = YamahaMxSysExStorage.sanitizeMxPerformanceName("Órgão Rock")
        assertEquals("Orgao Rock", sanitized)
        assertEquals(10, sanitized.length)
    }

    @Test
    fun testSanitizeMxPerformanceName_TruncationTo10Chars() {
        val longName = "Concert Grand Piano 2026"
        val sanitized = YamahaMxSysExStorage.sanitizeMxPerformanceName(longName)
        assertEquals("Concert Gr", sanitized)
        assertEquals(10, sanitized.length)
    }

    @Test
    fun testSanitizeMxPerformanceName_PaddingTo10Chars() {
        val shortName = "Jazz Org"
        val sanitized = YamahaMxSysExStorage.sanitizeMxPerformanceName(shortName)
        assertEquals("Jazz Org  ", sanitized)
        assertEquals(10, sanitized.length)
    }

    @Test
    fun testSanitizeMxPerformanceName_BlankOrEmpty() {
        val blank = YamahaMxSysExStorage.sanitizeMxPerformanceName("   ")
        assertEquals("USER PERF ", blank)
        assertEquals(10, blank.length)
    }

    @Test
    fun testPerformanceNameToAsciiBytes_ValidAsciiRange() {
        val bytes = YamahaMxSysExStorage.performanceNameToAsciiBytes("Eléctrico!")
        assertEquals(10, bytes.size)
        for (b in bytes) {
            val code = b.toInt() and 0xFF
            assertTrue("Byte out of printable ASCII: $code", code in 32..126)
        }
    }

    @Test
    fun testBuildFullPerformanceEditBufferMessages_GeneratesExpectedSequence() {
        val parts = (0..15).map { MultiPartState(partIndex = it) }
        val state = PerformanceState(parts = parts, common = MultiCommonState(performanceName = "Praise St"))

        val messages = YamahaMxSysExStorage.buildFullPerformanceEditBufferMessages(state, "Praise St")

        // In Mode A, we send 20 name SysEx messages (10 for 0x30, 10 for 0x36) + standard performance sync (~300-360 msgs).
        // It must NOT have the old unmapped 650-message burst.
        assertTrue("Message count should be between 250 and 380, was: ${messages.size}", messages.size in 250..380)

        // Verify all messages are non-empty
        for (msg in messages) {
            assertTrue("Message cannot be empty", msg.isNotEmpty())
        }
    }

    @Test
    fun testBuildFullPerformanceEditBufferMessages_NoPortamentoMessages() {
        val parts = (0..15).map { MultiPartState(partIndex = it) }
        val state = PerformanceState(parts = parts, common = MultiCommonState(performanceName = "Lead Synth"))

        val messages = YamahaMxSysExStorage.buildFullPerformanceEditBufferMessages(state, "Lead Synth")

        for (msg in messages) {
            // Check Control Change messages (0xB0..0xBF)
            val status = msg[0].toInt() and 0xFF
            if (status in 0xB0..0xBF && msg.size >= 3) {
                val ccNumber = msg[1].toInt() and 0x7F
                // Must NOT send CC 65 (Portamento Switch) or CC 5 (Portamento Time)
                assertTrue("Must not transmit CC 65 (Portamento Switch)", ccNumber != 65)
                assertTrue("Must not transmit CC 5 (Portamento Time)", ccNumber != 5)
            }

            // Check SysEx messages (F0 43 10 7F 17 37 [part] [elem] [val] F7)
            if (status == 0xF0 && msg.size >= 9) {
                // Address High: msg[5], Address Mid: msg[6], Address Low: msg[7]
                val addrHigh = msg[5].toInt() and 0xFF
                val addrLow = msg[7].toInt() and 0xFF
                if (addrHigh == 0x37) {
                    // ADDR_LOW_PART_PORTAMENTO_SWITCH is 0x18
                    assertTrue("Must not transmit SysEx Portamento Switch (0x37 [part] 0x18)", addrLow != 0x18)
                }
            }
        }
    }

    @Test
    fun testBuildStandardPerformanceSyncMessages_NoPortamentoMessages() {
        val parts = (0..15).map { MultiPartState(partIndex = it) }
        val state = PerformanceState(parts = parts, common = MultiCommonState(performanceName = "Clean Perf"))

        val messages = YamahaMxSysExStorage.buildStandardPerformanceSyncMessages(state)

        for (msg in messages) {
            val status = msg[0].toInt() and 0xFF
            if (status in 0xB0..0xBF && msg.size >= 3) {
                val ccNumber = msg[1].toInt() and 0x7F
                assertTrue("Standard sync must not transmit CC 65", ccNumber != 65)
                assertTrue("Standard sync must not transmit CC 5", ccNumber != 5)
            }

            if (status == 0xF0 && msg.size >= 9) {
                val addrHigh = msg[5].toInt() and 0xFF
                val addrLow = msg[7].toInt() and 0xFF
                if (addrHigh == 0x37) {
                    assertTrue("Standard sync must not transmit SysEx 0x18", addrLow != 0x18)
                }
            }
        }
    }
}
