package com.yamahamx.editor.ui.screens

import com.yamahamx.editor.data.model.MultiPartState
import com.yamahamx.editor.data.model.PerformanceState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MixerScreenScrollIsolationTest {

    @Test
    fun testTogglingLayerOnP1PreservesActiveFocusedPartAtP5() {
        // Setup initial state: P5 (index 4) is active focused part
        val initialParts = List(16) { idx -> MultiPartState(partIndex = idx) }
        var state = PerformanceState(
            parts = initialParts,
            activeFocusedPartIndex = 4,
            selectedPartIndex = 4
        )

        assertEquals(4, state.activeFocusedPartIndex)
        assertEquals(4, state.selectedPartIndex)
        assertFalse(state.parts[0].isLayerSelected)

        // Simulate toggling Layer on P1 (index 0)
        val updatedParts = state.parts.map { part ->
            if (part.partIndex == 0) {
                part.copy(isLayerSelected = !part.isLayerSelected)
            } else {
                part
            }
        }
        // State update must NOT alter activeFocusedPartIndex or selectedPartIndex
        state = state.copy(parts = updatedParts)

        // Verification: P1 layer is active, but focused part index is strictly preserved at P5 (4)
        assertTrue(state.parts[0].isLayerSelected)
        assertTrue(state.parts[0].isLayerActive)
        assertEquals("activeFocusedPartIndex must remain 4 (P5)", 4, state.activeFocusedPartIndex)
        assertEquals("selectedPartIndex must remain 4 (P5)", 4, state.selectedPartIndex)

        // Toggle P1 layer OFF again
        val toggledOffParts = state.parts.map { part ->
            if (part.partIndex == 0) {
                part.copy(isLayerSelected = !part.isLayerSelected)
            } else {
                part
            }
        }
        state = state.copy(parts = toggledOffParts)

        assertFalse(state.parts[0].isLayerSelected)
        assertFalse(state.parts[0].isLayerActive)
        assertEquals("activeFocusedPartIndex must remain 4 (P5)", 4, state.activeFocusedPartIndex)
        assertEquals("selectedPartIndex must remain 4 (P5)", 4, state.selectedPartIndex)
    }

    @Test
    fun testInternalPartPropertyModificationsDoNotAlterFocusedPart() {
        val initialParts = List(16) { idx -> MultiPartState(partIndex = idx, volume = 100) }
        var state = PerformanceState(
            parts = initialParts,
            activeFocusedPartIndex = 4,
            selectedPartIndex = 4
        )

        // Modify volume on P1 (index 0)
        state = state.copy(parts = state.parts.map {
            if (it.partIndex == 0) it.copy(volume = 80) else it
        })
        assertEquals(80, state.parts[0].volume)
        assertEquals(4, state.activeFocusedPartIndex)

        // Modify pan on P1 (index 0)
        state = state.copy(parts = state.parts.map {
            if (it.partIndex == 0) it.copy(pan = 32) else it
        })
        assertEquals(32, state.parts[0].pan)
        assertEquals(4, state.activeFocusedPartIndex)

        // Modify mute & solo on P1 (index 0)
        state = state.copy(parts = state.parts.map {
            if (it.partIndex == 0) it.copy(isMuted = true, isSolo = true) else it
        })
        assertTrue(state.parts[0].isMuted)
        assertTrue(state.parts[0].isSolo)
        assertEquals(4, state.activeFocusedPartIndex)
    }

    @Test
    fun testExplicitNavigationChangesFocusedPart() {
        val initialParts = List(16) { idx -> MultiPartState(partIndex = idx) }
        var state = PerformanceState(
            parts = initialParts,
            activeFocusedPartIndex = 4,
            selectedPartIndex = 4
        )

        // User explicitly taps P1 in navigation tabs
        val targetIndex = 0
        state = state.copy(
            activeFocusedPartIndex = targetIndex,
            selectedPartIndex = targetIndex
        )

        assertEquals(0, state.activeFocusedPartIndex)
        assertEquals(0, state.selectedPartIndex)
    }
}
