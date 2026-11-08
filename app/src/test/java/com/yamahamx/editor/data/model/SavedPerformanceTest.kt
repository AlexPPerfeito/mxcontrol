package com.yamahamx.editor.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedPerformanceTest {

    @Test
    fun testSavedVoiceSerialization() {
        val voice = SavedVoice(
            name = "CncrtGrand",
            category = "AP",
            subCategory = "APno",
            msb = 63,
            lsb = 0,
            programChange = 0
        )
        val json = voice.toJson()
        val restored = SavedVoice.fromJson(json)

        assertEquals(voice.name, restored.name)
        assertEquals(voice.category, restored.category)
        assertEquals(voice.msb, restored.msb)
        assertEquals(voice.lsb, restored.lsb)
        assertEquals(voice.programChange, restored.programChange)
    }

    @Test
    fun testSavedPartSerialization() {
        val voice = SavedVoice(
            name = "Vintage'74",
            category = "KB",
            subCategory = "EP",
            msb = 63,
            lsb = 0,
            programChange = 21
        )
        val part = SavedPart(
            partIndex = 1,
            voice = voice,
            volume = 110,
            pan = 50,
            reverbSend = 32,
            chorusSend = 15,
            noteShift = 64,
            noteLimitLow = 36,
            noteLimitHigh = 96,
            cutoff = 70,
            resonance = 55,
            modulation = 127,
            isMuted = false,
            isSolo = true
        )
        val json = part.toJson()
        val restored = SavedPart.fromJson(json)

        assertEquals(1, restored.partIndex)
        assertEquals("Vintage'74", restored.voice.name)
        assertEquals(110, restored.volume)
        assertEquals(50, restored.pan)
        assertEquals(32, restored.reverbSend)
        assertEquals(15, restored.chorusSend)
        assertEquals(36, restored.noteLimitLow)
        assertEquals(96, restored.noteLimitHigh)
        assertEquals(70, restored.cutoff)
        assertEquals(55, restored.resonance)
        assertEquals(127, restored.modulation)
        assertFalse(restored.isMuted)
        assertTrue(restored.isSolo)
    }

    @Test
    fun testSavedPerformanceStateConversion() {
        val defaultParts = List(16) { idx ->
            MultiPartState(
                partIndex = idx,
                volume = 90 + idx,
                pan = 64
            )
        }
        val perfState = PerformanceState(
            parts = defaultParts,
            common = MultiCommonState(part12Link = true, performanceName = "Rock Night Set")
        )

        val saved = SavedPerformance.fromState(
            name = "Rock Night Set",
            categoryTag = "Rock",
            state = perfState
        )

        assertEquals("Rock Night Set", saved.name)
        assertEquals("Rock", saved.categoryTag)
        assertTrue(saved.part12Link)
        assertEquals(16, saved.parts.size)
        assertEquals(90, saved.parts[0].volume)
        assertEquals(105, saved.parts[15].volume)

        // JSON Roundtrip
        val json = saved.toJson()
        val restored = SavedPerformance.fromJson(json)

        assertEquals(saved.id, restored.id)
        assertEquals("Rock Night Set", restored.name)
        assertEquals("Rock", restored.categoryTag)
        assertTrue(restored.part12Link)
        assertEquals(16, restored.parts.size)
    }

    @Test
    fun testSavedPerformanceWithPreservedId() {
        val existingId = "perf-custom-uuid-12345"
        val parts = List(16) { idx -> MultiPartState(partIndex = idx, volume = 80) }
        val state = PerformanceState(parts = parts)

        val updatedPerf = SavedPerformance.fromState(
            name = "Updated Performance",
            categoryTag = "Pop",
            state = state,
            id = existingId
        )

        assertEquals(existingId, updatedPerf.id)
        assertEquals("Updated Performance", updatedPerf.name)
        assertEquals("Pop", updatedPerf.categoryTag)
    }

    @Test
    fun testPartParametersPreservedAcrossStateMapping() {
        val customPart = MultiPartState(
            partIndex = 1,
            voice = YamahaVoice(id = 12, name = "NoblePad", category = "KB", subCategory = "Syn", msb = 63, lsb = 0, programChange = 12),
            volume = 42,
            pan = 100,
            cutoff = 88,
            resonance = 92,
            reverbSend = 40,
            chorusSend = 25,
            noteLimitLow = 48,
            noteLimitHigh = 84,
            noteShift = 62,
            receiveChannel = 1
        )
        val allParts = (0..15).map { if (it == 1) customPart else MultiPartState(partIndex = it) }
        val state = PerformanceState(parts = allParts)

        val saved = SavedPerformance.fromState(
            name = "Custom Pad Perf",
            categoryTag = "Teclado",
            state = state,
            id = "test-pad-id"
        )

        val restoredParts = (0..15).map { idx ->
            val savedPart = saved.parts.firstOrNull { it.partIndex == idx }
            savedPart?.toMultiPartState() ?: MultiPartState(partIndex = idx)
        }

        val restoredP2 = restoredParts[1]
        assertEquals("NoblePad", restoredP2.voice.name)
        assertEquals(42, restoredP2.volume)
        assertEquals(100, restoredP2.pan)
        assertEquals(88, restoredP2.cutoff)
        assertEquals(92, restoredP2.resonance)
        assertEquals(40, restoredP2.reverbSend)
        assertEquals(25, restoredP2.chorusSend)
        assertEquals(48, restoredP2.noteLimitLow)
        assertEquals(84, restoredP2.noteLimitHigh)
        assertEquals(62, restoredP2.noteShift)
        assertEquals(1, restoredP2.receiveChannel)
    }

    @Test
    fun testPart1ReceiveChannelNeverOffWhenDeserializedOrRestored() {
        val voice = SavedVoice(name = "CncrtGrand", category = "AP", subCategory = "APno", msb = 63, lsb = 0, programChange = 0)
        // Simulate legacy or buggy saved part with Part 1 set to Off (127)
        val corruptedP1 = SavedPart(partIndex = 0, voice = voice, receiveChannel = 127)
        val restoredMultiPart = corruptedP1.toMultiPartState()
        assertEquals(0, restoredMultiPart.receiveChannel)

        val json = corruptedP1.toJson()
        val deserialized = SavedPart.fromJson(json)
        assertEquals(0, deserialized.receiveChannel)
    }
}
