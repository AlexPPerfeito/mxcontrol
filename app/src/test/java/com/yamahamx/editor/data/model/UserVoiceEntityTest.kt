package com.yamahamx.editor.data.model

import com.yamahamx.editor.data.midi.YamahaSysExEncoder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class UserVoiceEntityTest {

    @Test
    fun testUserVoiceEntityCreationAndMapping() {
        val entity = UserVoiceEntity(
            slotIndex = 0,
            customName = "US001: Worship Grand"
        )

        assertEquals(0, entity.slotIndex)
        assertEquals(1, entity.displaySlotNumber)
        assertEquals("US001", entity.slotCode)
        assertEquals(63, entity.msb)
        assertEquals(8, entity.lsb)
        assertEquals(0, entity.programChange)
        assertEquals("USR", entity.categoryTag)

        val voice = entity.toYamahaVoice()
        assertEquals(2000, voice.id)
        assertEquals("US001: Worship Grand", voice.name)
        assertEquals("USR", voice.category)
        assertEquals("US001", voice.subCategory)
        assertEquals(63, voice.msb)
        assertEquals(8, voice.lsb)
        assertEquals(0, voice.programChange)
        assertEquals("USER", voice.bankName)
    }

    @Test
    fun testSlot128Formatting() {
        val entity = UserVoiceEntity(
            slotIndex = 127,
            customName = "My Lead 128"
        )

        assertEquals(127, entity.slotIndex)
        assertEquals(128, entity.displaySlotNumber)
        assertEquals("US128", entity.slotCode)
        assertEquals(127, entity.programChange)

        val voice = entity.toYamahaVoice()
        assertEquals("USER", voice.bankName)
        assertEquals(128, voice.displayProgramNumber)
    }

    @Test
    fun testMidiSysExGenerationForUserVoice() {
        val entity = UserVoiceEntity(
            slotIndex = 4, // US005
            customName = "US005: Jazz Organ 1"
        )
        val voice = entity.toYamahaVoice()

        val messages = YamahaSysExEncoder.createVoiceChangeMessages(
            partIndex = 0,
            msb = voice.msb,
            lsb = voice.lsb,
            programChange = voice.programChange
        )

        assertEquals(6, messages.size)

        // 1. SysEx Bank MSB: F0 43 10 7F 17 37 00 01 3F F7 (3F = 63)
        assertArrayEquals(
            byteArrayOf(0xF0.toByte(), 0x43, 0x10, 0x7F, 0x17, 0x37, 0x00, 0x01, 63, 0xF7.toByte()),
            messages[0]
        )

        // 2. SysEx Bank LSB: F0 43 10 7F 17 37 00 02 08 F7 (08 = 8)
        assertArrayEquals(
            byteArrayOf(0xF0.toByte(), 0x43, 0x10, 0x7F, 0x17, 0x37, 0x00, 0x02, 8, 0xF7.toByte()),
            messages[1]
        )

        // 3. SysEx Program Number: F0 43 10 7F 17 37 00 03 04 F7 (04 = 4)
        assertArrayEquals(
            byteArrayOf(0xF0.toByte(), 0x43, 0x10, 0x7F, 0x17, 0x37, 0x00, 0x03, 4, 0xF7.toByte()),
            messages[2]
        )

        // 4. MIDI CC 0 (MSB): B0 00 3F (63)
        assertArrayEquals(
            byteArrayOf(0xB0.toByte(), 0x00, 63),
            messages[3]
        )

        // 5. MIDI CC 32 (LSB): B0 20 08 (8)
        assertArrayEquals(
            byteArrayOf(0xB0.toByte(), 0x20, 8),
            messages[4]
        )

        // 6. MIDI Program Change: C0 04 (4)
        assertArrayEquals(
            byteArrayOf(0xC0.toByte(), 4),
            messages[5]
        )
    }
}
