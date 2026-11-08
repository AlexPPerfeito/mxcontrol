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

        // 1. Activate Layer on Part 2 (partIndex = 1) while focused on Part 1 (partIndex = 0)
        val focusedPart = 0
        val updatedPartsState = parts.map {
            if (it.partIndex == 1) it.copy(isLayerSelected = true) else it
        }
        val layerIndices = updatedPartsState.filter { it.isLayerSelected && it.partIndex != focusedPart }.map { it.partIndex }.toSet()
        val activeCh0Indices = setOf(focusedPart) + layerIndices

        val transition = router.computeSeamlessPartSwitch(
            currentParts = updatedPartsState,
            activeCh0PartIndices = activeCh0Indices
        )

        parts = updatedPartsState.map { part ->
            val idx = part.partIndex
            part.copy(receiveChannel = transition.updatedReceiveChannels[idx])
        }

        // Part 1 MUST remain on Channel 0 (Ch 1 - Physical keyboard)
        assertEquals("Part 1 must remain on Channel 0", 0, parts[0].receiveChannel)
        // Part 2 MUST be routed to Channel 0 (Ch 1) to sound in Layer with Part 1
        assertEquals("Part 2 must be on Channel 0", 0, parts[1].receiveChannel)
        assertTrue(parts[1].isLayerSelected)

        // Ensure NO SysEx message sets Part 0 to Off (127) when Part 1 is in activeKeyboardParts
        assertFalse(
            "Must never send SysEx 127 (Off) for Part 1 when active",
            transition.midiMessages.any { it.contentEquals(YamahaSysExEncoder.setPartReceiveChannel(0, 127)) }
        )
    }

    @Test
    fun testLayerDeactivationRestoresSecondaryPartAndKeepsPart1OnChannel0() {
        val router = PartSpilloverRouter()
        var parts = createDefaultParts()
        router.syncFromParts(parts)

        // Activate layer on Part 2 and Part 3 while focused on Part 1 (0)
        var updated = parts.map {
            if (it.partIndex in listOf(1, 2)) it.copy(isLayerSelected = true, receiveChannel = 0) else it
        }

        // Now deactivate Layer on Part 2 (partIndex = 1)
        updated = updated.map {
            if (it.partIndex == 1) it.copy(isLayerSelected = false) else it
        }
        val focusedPart = 0
        val layerIndices = updated.filter { it.isLayerSelected && it.partIndex != focusedPart }.map { it.partIndex }.toSet()
        val activeCh0Indices = setOf(focusedPart) + layerIndices // {0, 2}

        val transition = router.computeSeamlessPartSwitch(
            currentParts = updated,
            activeCh0PartIndices = activeCh0Indices
        )

        val finalParts = updated.map { part ->
            val idx = part.partIndex
            part.copy(receiveChannel = transition.updatedReceiveChannels[idx])
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
    fun testSwitchFocusToPart2SoloRoutesPart2ToChannel0AndDecouplesPart1() {
        val router = PartSpilloverRouter()
        val parts = createDefaultParts()
        router.syncFromParts(parts)

        // Initial state: Part 1 is on Ch 0 (Physical keyboard Ch 1)
        assertEquals(0, parts[0].receiveChannel)
        assertEquals(1, parts[1].receiveChannel)

        // User focuses on Part 2 (partIndex = 1) with NO layer
        val targetIndex = 1
        val layerIndices = parts.filter { it.isLayerSelected && it.partIndex != targetIndex }.map { it.partIndex }.toSet()
        val activeCh0Indices = setOf(targetIndex) + layerIndices // {1}

        val transition = router.computeSeamlessPartSwitch(
            currentParts = parts,
            activeCh0PartIndices = activeCh0Indices
        )

        val updatedParts = parts.map { part ->
            part.copy(receiveChannel = transition.updatedReceiveChannels[part.partIndex])
        }

        // Part 2 MUST receive on Channel 0 (Ch 1 - Physical keyboard) to play solo
        assertEquals("Part 2 must receive on Channel 0", 0, updatedParts[1].receiveChannel)
        // Part 1 MUST NOT receive on Channel 0 (it should be 127/Off so it doesn't sound with Part 2)
        assertFalse("Part 1 must NOT receive on Channel 0 when Part 2 is soloed", updatedParts[0].receiveChannel == 0)
        assertEquals("Part 1 should be decoupled to 127 (Off)", 127, updatedParts[0].receiveChannel)

        // Transition messages must route Part 2 to 0 and Part 1 to 127
        assertTrue(
            "Transition messages must include routing Part 2 to 0",
            transition.midiMessages.any { it.contentEquals(YamahaSysExEncoder.setPartReceiveChannel(1, 0)) }
        )
        assertTrue(
            "Transition messages must decouple Part 1 with SysEx 127",
            transition.midiMessages.any { it.contentEquals(YamahaSysExEncoder.setPartReceiveChannel(0, 127)) }
        )
    }

    @Test
    fun testFocusOnPart2WithLayerOnPart3RoutesBothToChannel0WhilePart1RemainsDecoupled() {
        val router = PartSpilloverRouter()
        val parts = createDefaultParts()
        router.syncFromParts(parts)

        // Part 2 is focused, Part 3 has layer active
        val targetIndex = 1
        val partsWithLayer = parts.map {
            if (it.partIndex == 2) it.copy(isLayerSelected = true) else it
        }
        val layerIndices = partsWithLayer.filter { it.isLayerSelected && it.partIndex != targetIndex }.map { it.partIndex }.toSet()
        val activeCh0Indices = setOf(targetIndex) + layerIndices // {1, 2}

        val transition = router.computeSeamlessPartSwitch(
            currentParts = partsWithLayer,
            activeCh0PartIndices = activeCh0Indices
        )

        val updatedParts = partsWithLayer.map { part ->
            part.copy(receiveChannel = transition.updatedReceiveChannels[part.partIndex])
        }

        // Part 2 and Part 3 must both be on Channel 0
        assertEquals("Part 2 must receive on Channel 0", 0, updatedParts[1].receiveChannel)
        assertEquals("Part 3 must receive on Channel 0", 0, updatedParts[2].receiveChannel)
        // Part 1 remains decoupled
        assertEquals("Part 1 must remain decoupled on 127", 127, updatedParts[0].receiveChannel)
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
