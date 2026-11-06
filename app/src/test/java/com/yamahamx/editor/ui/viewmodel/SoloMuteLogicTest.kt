package com.yamahamx.editor.ui.viewmodel

import com.yamahamx.editor.data.model.MultiPartState
import com.yamahamx.editor.data.model.PerformanceState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SoloMuteLogicTest {

    @Test
    fun testSingleSoloSilencesAllNonSoloParts() {
        val initialParts = List(16) { idx -> MultiPartState(partIndex = idx, volume = 100) }
        var state = PerformanceState(parts = initialParts)

        assertFalse(state.anySoloActive)
        assertTrue(state.isPartAudible(0))
        assertTrue(state.isPartAudible(1))

        // Activate SOLO on Part 1 (index 0)
        state = state.copy(parts = state.parts.map {
            if (it.partIndex == 0) it.copy(isSolo = true) else it
        })

        assertTrue(state.anySoloActive)
        // Part 0 is soloed -> audible
        assertTrue(state.isPartAudible(0))
        assertFalse(state.isPartEffectivelyMuted(0))
        assertEquals(100, state.getEffectiveVolume(0))

        // All other parts (1..15) are NOT soloed -> silenced
        for (i in 1..15) {
            assertFalse("Part $i should not be audible", state.isPartAudible(i))
            assertTrue("Part $i should be effectively muted", state.isPartEffectivelyMuted(i))
            assertEquals("Part $i effective volume should be 0", 0, state.getEffectiveVolume(i))
            // But their underlying isMuted flag remains unchanged (false)
            assertFalse(state.parts[i].isMuted)
        }
    }

    @Test
    fun testMultiPartSoloAllowsMultiplePartsAudible() {
        val initialParts = List(16) { idx -> MultiPartState(partIndex = idx, volume = 90 + idx) }
        var state = PerformanceState(parts = initialParts)

        // Activate SOLO on Part 1 (index 0) and Part 3 (index 2)
        state = state.copy(parts = state.parts.map {
            when (it.partIndex) {
                0 -> it.copy(isSolo = true)
                2 -> it.copy(isSolo = true)
                else -> it
            }
        })

        assertTrue(state.anySoloActive)
        assertTrue(state.parts[0].isSolo)
        assertTrue(state.parts[2].isSolo)

        // Both solo parts are audible
        assertTrue(state.isPartAudible(0))
        assertEquals(90, state.getEffectiveVolume(0))
        assertTrue(state.isPartAudible(2))
        assertEquals(92, state.getEffectiveVolume(2))

        // Non-solo parts are silenced
        assertFalse(state.isPartAudible(1))
        assertEquals(0, state.getEffectiveVolume(1))
        assertFalse(state.isPartAudible(3))
        assertEquals(0, state.getEffectiveVolume(3))

        // Deactivate solo on Part 1, Part 3 remains solo
        state = state.copy(parts = state.parts.map {
            if (it.partIndex == 0) it.copy(isSolo = false) else it
        })

        assertTrue(state.anySoloActive)
        assertFalse(state.parts[0].isSolo)
        assertTrue(state.parts[2].isSolo)

        assertFalse(state.isPartAudible(0))
        assertEquals(0, state.getEffectiveVolume(0))
        assertTrue(state.isPartAudible(2))
        assertEquals(92, state.getEffectiveVolume(2))
    }

    @Test
    fun testDeactivatingAllSolosRigorouslyRestoresPreviousMuteState() {
        val initialParts = List(16) { idx ->
            MultiPartState(
                partIndex = idx,
                volume = 100,
                // Part 2 (index 1) and Part 5 (index 4) were explicitly muted by user
                isMuted = (idx == 1 || idx == 4)
            )
        }
        var state = PerformanceState(parts = initialParts)

        assertFalse(state.anySoloActive)
        assertTrue(state.isPartAudible(0))
        assertFalse(state.isPartAudible(1)) // previously muted
        assertTrue(state.isPartAudible(2))
        assertFalse(state.isPartAudible(4)) // previously muted

        // User activates SOLO on Part 1 (index 0)
        state = state.copy(parts = state.parts.map {
            if (it.partIndex == 0) it.copy(isSolo = true) else it
        })

        assertTrue(state.anySoloActive)
        assertTrue(state.isPartAudible(0))
        assertEquals(100, state.getEffectiveVolume(0))
        // Non-solo parts silenced
        assertEquals(0, state.getEffectiveVolume(1))
        assertEquals(0, state.getEffectiveVolume(2))
        assertEquals(0, state.getEffectiveVolume(4))

        // User deactivates SOLO on Part 1 (now NO solos active)
        state = state.copy(parts = state.parts.map {
            if (it.partIndex == 0) it.copy(isSolo = false) else it
        })

        assertFalse(state.anySoloActive)

        // Part 0: unmuted -> audible at volume 100
        assertTrue(state.isPartAudible(0))
        assertEquals(100, state.getEffectiveVolume(0))

        // Part 1: was previously muted -> MUST REMAIN MUTED (volume 0)
        assertTrue("Previously muted Part 1 must still have isMuted=true", state.parts[1].isMuted)
        assertFalse("Previously muted Part 1 must not be audible", state.isPartAudible(1))
        assertEquals("Previously muted Part 1 must have effectiveVolume=0", 0, state.getEffectiveVolume(1))

        // Part 2: was unmuted -> audible at volume 100
        assertFalse(state.parts[2].isMuted)
        assertTrue(state.isPartAudible(2))
        assertEquals(100, state.getEffectiveVolume(2))

        // Part 4: was previously muted -> MUST REMAIN MUTED (volume 0)
        assertTrue("Previously muted Part 4 must still have isMuted=true", state.parts[4].isMuted)
        assertFalse("Previously muted Part 4 must not be audible", state.isPartAudible(4))
        assertEquals("Previously muted Part 4 must have effectiveVolume=0", 0, state.getEffectiveVolume(4))
    }

    @Test
    fun testMutingSoloedPartSilencesIt() {
        val initialParts = List(16) { idx -> MultiPartState(partIndex = idx, volume = 100) }
        var state = PerformanceState(parts = initialParts)

        // Solo Part 0
        state = state.copy(parts = state.parts.map {
            if (it.partIndex == 0) it.copy(isSolo = true) else it
        })
        assertTrue(state.isPartAudible(0))
        assertEquals(100, state.getEffectiveVolume(0))

        // Now user also mutes Part 0 while it is soloed
        state = state.copy(parts = state.parts.map {
            if (it.partIndex == 0) it.copy(isMuted = true) else it
        })
        assertFalse("Soloed part when muted must not be audible", state.isPartAudible(0))
        assertTrue("Soloed part when muted must be effectively muted", state.isPartEffectivelyMuted(0))
        assertEquals(0, state.getEffectiveVolume(0))

        // Unmuting Part 0 restores it to audible
        state = state.copy(parts = state.parts.map {
            if (it.partIndex == 0) it.copy(isMuted = false) else it
        })
        assertTrue(state.isPartAudible(0))
        assertEquals(100, state.getEffectiveVolume(0))
    }

    @Test
    fun testVolumeFaderChangeOnSilencedPartPreservesEffectiveZeroVolume() {
        val initialParts = List(16) { idx -> MultiPartState(partIndex = idx, volume = 80) }
        var state = PerformanceState(parts = initialParts)

        // Solo Part 0 -> Part 1 silenced
        state = state.copy(parts = state.parts.map {
            if (it.partIndex == 0) it.copy(isSolo = true) else it
        })
        assertEquals(0, state.getEffectiveVolume(1))

        // User adjusts Part 1 volume fader to 120
        state = state.copy(parts = state.parts.map {
            if (it.partIndex == 1) it.copy(volume = 120) else it
        })
        // State volume is updated for the fader UI
        assertEquals(120, state.parts[1].volume)
        // But effective volume sent to hardware remains 0 while solo is active
        assertEquals(0, state.getEffectiveVolume(1))

        // Turn off solo
        state = state.copy(parts = state.parts.map {
            if (it.partIndex == 0) it.copy(isSolo = false) else it
        })
        // Effective volume is now restored to the new fader value of 120
        assertEquals(120, state.getEffectiveVolume(1))
    }
}
