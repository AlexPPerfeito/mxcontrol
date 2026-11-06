package com.yamahamx.editor.ui.viewmodel

import com.yamahamx.editor.data.model.MultiPartState
import com.yamahamx.editor.data.model.PerformanceState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MidiInputMappingTest {

    @Test
    fun testVolumeMappingUpdatesTargetPartState() {
        val initialParts = List(16) { idx -> MultiPartState(partIndex = idx, volume = 50, receiveChannel = idx) }
        var state = PerformanceState(parts = initialParts)

        // Incoming CC 7 on channel 0 (Part 0) with value 115
        val channel = 0
        val targetPart = state.parts.firstOrNull { it.receiveChannel == channel } ?: state.parts[channel]
        val partIndex = targetPart.partIndex
        val newVol = 115.coerceIn(0, 127)

        state = state.copy(parts = state.parts.map {
            if (it.partIndex == partIndex) it.copy(volume = newVol) else it
        })

        assertEquals(115, state.parts[0].volume)
        assertEquals(50, state.parts[1].volume) // other parts untouched
    }

    @Test
    fun testPanMappingUpdatesTargetPartState() {
        val initialParts = List(16) { idx -> MultiPartState(partIndex = idx, pan = 64, receiveChannel = idx) }
        var state = PerformanceState(parts = initialParts)

        // Incoming CC 10 on channel 1 (Part 1) with value 32
        val channel = 1
        val targetPart = state.parts.firstOrNull { it.receiveChannel == channel } ?: state.parts[channel]
        val partIndex = targetPart.partIndex
        val newPan = 32.coerceIn(1, 127)

        state = state.copy(parts = state.parts.map {
            if (it.partIndex == partIndex) it.copy(pan = newPan) else it
        })

        assertEquals(64, state.parts[0].pan)
        assertEquals(32, state.parts[1].pan)
    }

    @Test
    fun testModulationMappingUpdatesTargetPartState() {
        val initialParts = List(16) { idx -> MultiPartState(partIndex = idx, modulation = 0, receiveChannel = idx) }
        var state = PerformanceState(parts = initialParts)

        // Incoming CC 1 on channel 2 (Part 2) with value 90
        val channel = 2
        val targetPart = state.parts.firstOrNull { it.receiveChannel == channel } ?: state.parts[channel]
        val partIndex = targetPart.partIndex
        val newMod = 90.coerceIn(0, 127)

        state = state.copy(parts = state.parts.map {
            if (it.partIndex == partIndex) it.copy(modulation = newMod) else it
        })

        assertEquals(90, state.parts[2].modulation)
        assertEquals(0, state.parts[0].modulation)
    }

    @Test
    fun testCustomReceiveChannelMappingResolvesCorrectPart() {
        // Part 0 listens on Ch 0, Part 1 listens on Ch 0 (Layered), Part 2 listens on Ch 4
        val parts = List(16) { idx ->
            when (idx) {
                0 -> MultiPartState(partIndex = 0, volume = 50, receiveChannel = 0)
                1 -> MultiPartState(partIndex = 1, volume = 60, receiveChannel = 0)
                2 -> MultiPartState(partIndex = 2, volume = 70, receiveChannel = 4)
                else -> MultiPartState(partIndex = idx, volume = 80, receiveChannel = idx)
            }
        }
        val state = PerformanceState(parts = parts)

        // CC arrives on channel 4 -> must resolve to Part 2
        val targetPart = state.parts.firstOrNull { it.receiveChannel == 4 }
        assertEquals(2, targetPart?.partIndex)
    }

    @Test
    fun testResolveExclusiveVolumeCcChannelPreventsCrosstalkOnSharedChannels() {
        // Part 0 (Part 1) and Part 1 (Part 2) share channel 0
        // Part 2 (Part 3) has its own channel 2
        val parts = List(16) { idx ->
            when (idx) {
                0 -> MultiPartState(partIndex = 0, receiveChannel = 0)
                1 -> MultiPartState(partIndex = 1, receiveChannel = 0)
                2 -> MultiPartState(partIndex = 2, receiveChannel = 2)
                else -> MultiPartState(partIndex = idx, receiveChannel = idx)
            }
        }

        // Shared channel 0: neither Part 0 nor Part 1 should receive CC 7 (only per-part SysEx must be used)
        val ccPart0 = PerformanceViewModel.resolveExclusiveVolumeCcChannel(0, 0, parts)
        val ccPart1 = PerformanceViewModel.resolveExclusiveVolumeCcChannel(1, 0, parts)
        org.junit.Assert.assertNull(ccPart0)
        org.junit.Assert.assertNull(ccPart1)

        // Part 2 exclusively owns channel 2: CC 7 is safe
        val ccPart2 = PerformanceViewModel.resolveExclusiveVolumeCcChannel(2, 2, parts)
        assertEquals(2, ccPart2)
    }

    @Test
    fun testResolveActiveKeyboardPartChannelsIncludesFocusedAndLayeredParts() {
        // Part 0 listens on Ch 0
        // Part 1 is not layered, listens on Ch 1
        // Part 2 (Part 3) is focused, listens on Ch 2
        // Part 3 (Part 4) is in Layer, listens on Ch 3
        val parts = List(16) { idx ->
            when (idx) {
                0 -> MultiPartState(partIndex = 0, receiveChannel = 0, isLayerSelected = false)
                1 -> MultiPartState(partIndex = 1, receiveChannel = 1, isLayerSelected = false)
                2 -> MultiPartState(partIndex = 2, receiveChannel = 2, isLayerSelected = false)
                3 -> MultiPartState(partIndex = 3, receiveChannel = 3, isLayerSelected = true)
                else -> MultiPartState(partIndex = idx, receiveChannel = idx, isLayerSelected = false)
            }
        }

        // Focused part is Part 2 (index 2)
        val activeChannels = PerformanceViewModel.resolveActiveKeyboardPartChannels(parts, focusedPartIndex = 2)

        // Must include Channel 0 (Part 0), Channel 2 (Focused Part 2), Channel 3 (Layered Part 3)
        assertTrue(activeChannels.contains(0))
        assertTrue(activeChannels.contains(2))
        assertTrue(activeChannels.contains(3))
        // Must NOT include Part 1 (Ch 1)
        org.junit.Assert.assertFalse(activeChannels.contains(1))
    }
}
