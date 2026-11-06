package com.yamahamx.editor.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceRepositoryTest {

    @Test
    fun testAllVoicesCount() {
        val voices = VoiceRepository.getAllVoices()
        assertEquals(1167, voices.size)
    }

    @Test
    fun testExactOfficialVoiceParameters() {
        // CncrtGrand: MSB=63, LSB=0, PC in Data List=1 -> programChange=0
        val cncrtGrand = VoiceRepository.getAllVoices().first { it.name == "CncrtGrand" }
        assertEquals(63, cncrtGrand.msb)
        assertEquals(0, cncrtGrand.lsb)
        assertEquals(0, cncrtGrand.programChange)
        assertEquals(1, cncrtGrand.displayProgramNumber)

        // Vintage'74: MSB=63, LSB=0, PC in Data List=22 -> programChange=21
        val vintage74 = VoiceRepository.getAllVoices().first { it.name == "Vintage'74" }
        assertEquals(63, vintage74.msb)
        assertEquals(0, vintage74.lsb)
        assertEquals(21, vintage74.programChange)
        assertEquals(22, vintage74.displayProgramNumber)

        // PwrStdKit1: MSB=63, LSB=32, PC in Data List=1 -> programChange=0
        val pwrStdKit1 = VoiceRepository.getAllVoices().first { it.name == "PwrStdKit1" }
        assertEquals(63, pwrStdKit1.msb)
        assertEquals(32, pwrStdKit1.lsb)
        assertEquals(0, pwrStdKit1.programChange)
        assertEquals(1, pwrStdKit1.displayProgramNumber)
    }

    @Test
    fun testCategoryFiltering() {
        val pianos = VoiceRepository.searchVoices("", "AP")
        assertEquals(25, pianos.size)
        assertTrue(pianos.all { it.category == "AP" })

        val basses = VoiceRepository.searchVoices("", "BAS")
        assertEquals(122, basses.size)
        assertTrue(basses.all { it.category == "BAS" })

        val drums = VoiceRepository.searchVoices("", "DR")
        assertEquals(61, drums.size)
        assertTrue(drums.all { it.category == "DR" })
    }

    @Test
    fun testTextSearchQuery() {
        val grandResults = VoiceRepository.searchVoices("Grand", null)
        assertTrue(grandResults.any { it.name == "CncrtGrand" })
        assertTrue(grandResults.any { it.name == "Mono Grand" })

        val dxResults = VoiceRepository.searchVoices("DX", null)
        assertTrue(dxResults.isNotEmpty())
    }

    @Test
    fun testDefaultVoiceAssignment() {
        val part1Voice = VoiceRepository.getDefaultVoiceForPart(0)
        assertEquals("CncrtGrand", part1Voice.name)
        assertEquals("AP", part1Voice.category)

        val part10Voice = VoiceRepository.getDefaultVoiceForPart(9)
        assertEquals("DR", part10Voice.category)
    }

    @Test
    fun testBankNameFormatting() {
        val voicePre1 = VoiceRepository.getAllVoices().first { it.msb == 63 && it.lsb == 0 }
        assertEquals("PRE1", voicePre1.bankName)

        val voiceDrum = VoiceRepository.getAllVoices().first { it.msb == 63 && it.lsb == 32 }
        assertEquals("DRUM", voiceDrum.bankName)
    }

    @Test
    fun testUserCategoryFilteringAndSearch() {
        val sampleUserVoices = (0..127).map { slot ->
            com.yamahamx.editor.data.model.UserVoiceEntity(
                slotIndex = slot,
                customName = if (slot == 0) "Custom Worship Pad" else "US${(slot + 1).toString().padStart(3, '0')}: User Voice ${slot + 1}"
            ).toYamahaVoice()
        }

        // Test filtering by "USER" category
        val userVoicesResult = VoiceRepository.searchVoices("", "USER", sampleUserVoices)
        assertEquals(128, userVoicesResult.size)
        assertTrue(userVoicesResult.all { it.msb == 63 && it.lsb == 8 && it.category == "USR" })

        // Test search query within User Voices
        val queryResult = VoiceRepository.searchVoices("Worship", null, sampleUserVoices)
        assertEquals(1, queryResult.size)
        assertEquals("Custom Worship Pad", queryResult.first().name)
        assertEquals("USER", queryResult.first().bankName)
        assertEquals(63, queryResult.first().msb)
        assertEquals(8, queryResult.first().lsb)
        assertEquals(0, queryResult.first().programChange)
    }

    @Test
    fun testVoiceNavigationIndexBoundaries() {
        val pianos = VoiceRepository.searchVoices("", "AP")
        assertTrue(pianos.size > 1)

        val firstIndex = 0
        val canDecrementFirst = firstIndex > 0
        assertFalse(canDecrementFirst)

        val canIncrementFirst = firstIndex < pianos.lastIndex
        assertTrue(canIncrementFirst)

        val lastIndex = pianos.lastIndex
        val canDecrementLast = lastIndex > 0
        assertTrue(canDecrementLast)

        val canIncrementLast = lastIndex < pianos.lastIndex
        assertFalse(canIncrementLast)

        // Stepping forward and backward
        val nextVoice = pianos[firstIndex + 1]
        assertNotNull(nextVoice)

        val prevVoice = pianos[lastIndex - 1]
        assertNotNull(prevVoice)
    }
}
