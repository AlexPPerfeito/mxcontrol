package com.yamahamx.editor.ui.viewmodel

import com.yamahamx.editor.data.midi.PartSpilloverRouter
import com.yamahamx.editor.data.midi.YamahaSysExEncoder
import com.yamahamx.editor.data.model.MultiPartState
import com.yamahamx.editor.data.model.SavedPart
import com.yamahamx.editor.data.model.SavedVoice
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LayerBusinessRuleTest {

    private fun createDefaultParts(): List<MultiPartState> {
        return List(16) { idx ->
            MultiPartState(partIndex = idx, receiveChannel = idx)
        }
    }

    @Test
    fun testLayerActivationKeepsPart1OnChannel0AndMovesLayerPartToChannel0() {
        val router = PartSpilloverRouter()
        var parts = createDefaultParts()
        router.syncFromParts(parts)

        // Part 1 is on Ch 0
        assertEquals(0, parts[0].receiveChannel)
        // Part 2 is on Ch 1
        assertEquals(1, parts[1].receiveChannel)

        // 1. Activate Layer on Part 2 (partIndex = 1)
        val updatedPartsState = parts.map {
            if (it.partIndex == 1) it.copy(isLayerSelected = true) else it
        }
        val layerIndices = updatedPartsState.filter { it.partIndex > 0 && it.isLayerSelected }.map { it.partIndex }.toSet()
        val activeCh0Indices = setOf(0) + layerIndices

        val transition = router.computeSeamlessPartSwitch(
            currentParts = updatedPartsState,
            activeCh0PartIndices = activeCh0Indices
        )

        parts = updatedPartsState.map { part ->
            val idx = part.partIndex
            if (idx == 0) {
                part.copy(receiveChannel = 0)
            } else {
                part.copy(receiveChannel = transition.updatedReceiveChannels[idx])
            }
        }

        // Part 1 MUST remain on Channel 0 (Ch 1 - Physical keyboard)
        assertEquals("Part 1 must remain on Channel 0", 0, parts[0].receiveChannel)
        // Part 2 MUST be routed to Channel 0 (Ch 1) to sound in Layer with Part 1
        assertEquals("Part 2 must be on Channel 0", 0, parts[1].receiveChannel)
        assertTrue(parts[1].isLayerSelected)

        // Ensure NO SysEx message sets Part 0 to Off (127)
        assertFalse(
            "Must never send SysEx 127 (Off) for Part 1",
            transition.midiMessages.any { it.contentEquals(YamahaSysExEncoder.setPartReceiveChannel(0, 127)) }
        )
    }

    @Test
    fun testLayerDeactivationRestoresSecondaryPartAndKeepsPart1OnChannel0() {
        val router = PartSpilloverRouter()
        var parts = createDefaultParts()
        router.syncFromParts(parts)

        // Activate layer on Part 2 and Part 3
        var updated = parts.map {
            if (it.partIndex in listOf(1, 2)) it.copy(isLayerSelected = true, receiveChannel = 0) else it
        }

        // Now deactivate Layer on Part 2 (partIndex = 1)
        updated = updated.map {
            if (it.partIndex == 1) it.copy(isLayerSelected = false) else it
        }
        val layerIndices = updated.filter { it.partIndex > 0 && it.isLayerSelected }.map { it.partIndex }.toSet()
        val activeCh0Indices = setOf(0) + layerIndices // {0, 2}

        val transition = router.computeSeamlessPartSwitch(
            currentParts = updated,
            activeCh0PartIndices = activeCh0Indices
        )

        val finalParts = updated.map { part ->
            val idx = part.partIndex
            if (idx == 0) {
                part.copy(receiveChannel = 0)
            } else {
                part.copy(receiveChannel = transition.updatedReceiveChannels[idx])
            }
        }

        // Part 1 remains intact on Channel 0
        assertEquals(0, finalParts[0].receiveChannel)
        // Part 2 returned to resting channel 1
        assertEquals(1, finalParts[1].receiveChannel)
        assertFalse(finalParts[1].isLayerSelected)
        // Part 3 remains in Layer on Channel 0
        assertEquals(0, finalParts[2].receiveChannel)
        assertTrue(finalParts[2].isLayerSelected)
    }

    @Test
    fun testPart1Legacy127CorruptedStateIsNormalizedToZero() {
        val voice = SavedVoice("CncrtGrand", "AP", "APno", 63, 0, 0)
        val corrupted = SavedPart(partIndex = 0, voice = voice, receiveChannel = 127)

        val restored = corrupted.toMultiPartState()
        assertEquals("Part 1 receiveChannel must be normalized to 0", 0, restored.receiveChannel)

        val json = corrupted.toJson()
        val fromJson = SavedPart.fromJson(json)
        assertEquals("Deserialized Part 1 receiveChannel must be 0", 0, fromJson.receiveChannel)
    }
}
