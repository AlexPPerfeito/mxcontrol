package com.yamahamx.editor.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class KeyboardModelTest {

    @Test
    fun testMX49LimitsAndLabels() {
        val model = KeyboardModel.MX49
        assertEquals("MX49", model.displayName)
        assertEquals(49, model.totalKeys)

        // Full: 48 (C3) .. 96 (C7)
        assertEquals(48, model.fullLow)
        assertEquals(96, model.fullHigh)
        assertEquals(49, model.fullHigh - model.fullLow + 1)
        assertEquals("Full (C3..C7)", model.fullLabel)

        // Lower: 48 (C3) .. 71 (B4) [24 keys]
        assertEquals(48, model.lowerLow)
        assertEquals(71, model.lowerHigh)
        assertEquals(24, model.lowerHigh - model.lowerLow + 1)
        assertEquals("Lower (C3..B4)", model.lowerLabel)

        // Upper: 72 (C5) .. 96 (C7) [25 keys]
        assertEquals(72, model.upperLow)
        assertEquals(96, model.upperHigh)
        assertEquals(25, model.upperHigh - model.upperLow + 1)
        assertEquals("Upper (C5..C7)", model.upperLabel)

        // Contiguity
        assertEquals(model.lowerHigh + 1, model.upperLow)
    }

    @Test
    fun testMX61LimitsAndLabels() {
        val model = KeyboardModel.MX61
        assertEquals("MX61", model.displayName)
        assertEquals(61, model.totalKeys)

        // Full: 36 (C2) .. 96 (C7)
        assertEquals(36, model.fullLow)
        assertEquals(96, model.fullHigh)
        assertEquals(61, model.fullHigh - model.fullLow + 1)
        assertEquals("Full (C2..C7)", model.fullLabel)

        // Lower: 36 (C2) .. 65 (F4) [30 keys]
        assertEquals(36, model.lowerLow)
        assertEquals(65, model.lowerHigh)
        assertEquals(30, model.lowerHigh - model.lowerLow + 1)
        assertEquals("Lower (C2..F4)", model.lowerLabel)

        // Upper: 66 (F#4) .. 96 (C7) [31 keys]
        assertEquals(66, model.upperLow)
        assertEquals(96, model.upperHigh)
        assertEquals(31, model.upperHigh - model.upperLow + 1)
        assertEquals("Upper (F#4..C7)", model.upperLabel)

        // Contiguity
        assertEquals(model.lowerHigh + 1, model.upperLow)
    }

    @Test
    fun testMX88LimitsAndLabels() {
        val model = KeyboardModel.MX88
        assertEquals("MX88", model.displayName)
        assertEquals(88, model.totalKeys)

        // Full: 21 (A0) .. 108 (C8)
        assertEquals(21, model.fullLow)
        assertEquals(108, model.fullHigh)
        assertEquals(88, model.fullHigh - model.fullLow + 1)
        assertEquals("Full (A0..C8)", model.fullLabel)

        // Lower: 21 (A0) .. 64 (E4) [44 keys - exact 50%]
        assertEquals(21, model.lowerLow)
        assertEquals(64, model.lowerHigh)
        assertEquals(44, model.lowerHigh - model.lowerLow + 1)
        assertEquals("Lower (A0..E4)", model.lowerLabel)

        // Upper: 65 (F4) .. 108 (C8) [44 keys - exact 50%]
        assertEquals(65, model.upperLow)
        assertEquals(108, model.upperHigh)
        assertEquals(44, model.upperHigh - model.upperLow + 1)
        assertEquals("Upper (F4..C8)", model.upperLabel)

        // Contiguity
        assertEquals(model.lowerHigh + 1, model.upperLow)
    }

    @Test
    fun testFullMidiLimitsAndLabels() {
        val model = KeyboardModel.FULL_MIDI
        assertEquals("Full MIDI", model.displayName)
        assertEquals(128, model.totalKeys)

        // Full: 0 (C-1) .. 127 (G9)
        assertEquals(0, model.fullLow)
        assertEquals(127, model.fullHigh)
        assertEquals(128, model.fullHigh - model.fullLow + 1)
        assertEquals("Full (C-1..G9)", model.fullLabel)

        // Lower: 0 (C-1) .. 59 (B3) [60 notes]
        assertEquals(0, model.lowerLow)
        assertEquals(59, model.lowerHigh)
        assertEquals(60, model.lowerHigh - model.lowerLow + 1)
        assertEquals("Lower (C-1..B3)", model.lowerLabel)

        // Upper: 60 (C4) .. 127 (G9) [68 notes]
        assertEquals(60, model.upperLow)
        assertEquals(127, model.upperHigh)
        assertEquals(68, model.upperHigh - model.upperLow + 1)
        assertEquals("Upper (C4..G9)", model.upperLabel)

        // Contiguity
        assertEquals(model.lowerHigh + 1, model.upperLow)
    }
}
