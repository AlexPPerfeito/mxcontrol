package com.yamahamx.editor.data.midi

import com.yamahamx.editor.data.model.MultiPartState
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PartSpilloverRouterTest {

    private fun createDefaultParts(): List<MultiPartState> {
        return List(16) { idx ->
            MultiPartState(partIndex = idx, receiveChannel = idx)
        }
    }

    private fun isAllSoundOffOrAllNotesOff(msg: ByteArray): Boolean {
        if (msg.size != 3) return false
        val statusNibble = msg[0].toInt() and 0xF0
        val cc = msg[1].toInt() and 0x7F
        return statusNibble == 0xB0 && (cc == 120 || cc == 123 || cc == 121)
    }

    private fun isSustainOff(msg: ByteArray): Boolean {
        if (msg.size != 3) return false
        val statusNibble = msg[0].toInt() and 0xF0
        val cc = msg[1].toInt() and 0x7F
        val value = msg[2].toInt() and 0x7F
        return statusNibble == 0xB0 && cc == 64 && value == 0
    }

    @Test
    fun testPartSwitchNeverSendsCc120Cc123OrSustainOff() {
        val router = PartSpilloverRouter()
        var parts = createDefaultParts()
        router.syncFromParts(parts)

        // Hold Note 60 on Part 1 (index 0, Ch 0) with Sustain Pedal pressed
        router.onIncomingVoiceEvent(MidiVoiceEvent.SustainChange(0, true), parts, 0)
        router.onIncomingVoiceEvent(MidiVoiceEvent.NoteOn(0, 60, 100), parts, 0)

        // Switch active focus from Part 1 (index 0) to Part 2 (index 1)
        val transition = router.computeSeamlessPartSwitch(parts, setOf(1))

        // Verify NO CC 120, CC 123, CC 121, or CC 64=0 is sent during Part Switch
        assertFalse(
            "Part switch must never send CC 120 / CC 123 / CC 121",
            transition.midiMessages.any { isAllSoundOffOrAllNotesOff(it) }
        )
        assertFalse(
            "Part switch must never send CC 64 = 0 when pedal is held",
            transition.midiMessages.any { isSustainOff(it) }
        )
    }

    @Test
    fun testHeldNotesOnPart2DispatchNoteOffToPart2WhenReleasedAfterSwitchToPart3() {
        val router = PartSpilloverRouter()
        var parts = createDefaultParts()

        // First focus Part 2 (index 1 -> Ch 0)
        val initSwitch = router.computeSeamlessPartSwitch(parts, setOf(1))
        parts = parts.map { it.copy(receiveChannel = initSwitch.updatedReceiveChannels[it.partIndex]) }

        // Play and hold C4 (60) and E4 (64) on Part 2 (currently on Ch 0)
        router.onIncomingVoiceEvent(MidiVoiceEvent.NoteOn(0, 60, 100), parts, 1)
        router.onIncomingVoiceEvent(MidiVoiceEvent.NoteOn(0, 64, 90), parts, 1)

        // Switch focus to Part 3 (index 2 -> Ch 0) while still holding 60 and 64
        val switchToP3 = router.computeSeamlessPartSwitch(parts, setOf(2))
        parts = parts.map { it.copy(receiveChannel = switchToP3.updatedReceiveChannels[it.partIndex]) }

        // Part 2 (index 1) is now on Receive Channel 1 (MIDI Ch 2), Part 3 (index 2) is on Receive Channel 0 (MIDI Ch 1)
        assertEquals(1, parts[1].receiveChannel)
        assertEquals(0, parts[2].receiveChannel)

        // Play new note G4 (67) on Part 3 (Ch 0)
        val newNoteMsgs = router.onIncomingVoiceEvent(MidiVoiceEvent.NoteOn(0, 67, 110), parts, 2)
        assertTrue(newNoteMsgs.isEmpty())

        // Now release C4 (60) from physical keyboard (which sends Note Off on Ch 0):
        // Router must dispatch Note Off (0x81, 60, 0) to Part 2's channel (channel 1)!
        val release60Msgs = router.onIncomingVoiceEvent(MidiVoiceEvent.NoteOff(0, 60, 0), parts, 2)
        assertEquals(1, release60Msgs.size)
        assertArrayEquals(byteArrayOf(0x81.toByte(), 60.toByte(), 0x00.toByte()), release60Msgs[0])

        // Release E4 (64) from physical keyboard -> dispatches Note Off (0x81, 64, 0) to Part 2's channel (1)
        val release64Msgs = router.onIncomingVoiceEvent(MidiVoiceEvent.NoteOff(0, 64, 0), parts, 2)
        assertEquals(1, release64Msgs.size)
        assertArrayEquals(byteArrayOf(0x81.toByte(), 64.toByte(), 0x00.toByte()), release64Msgs[0])
        assertNull(router.getSpilloverChannelForPart(1))
    }

    @Test
    fun testPart1SpilloverUsesTemporaryChannelAndRestoresToOff127WhenReleased() {
        val router = PartSpilloverRouter()
        var parts = createDefaultParts()
        router.syncFromParts(parts)

        // Hold Note 60 on Part 1 (index 0, Ch 0)
        router.onIncomingVoiceEvent(MidiVoiceEvent.NoteOn(0, 60, 100), parts, 0)

        // Switch focus from Part 1 (index 0) to Part 2 (index 1)
        val switchToP2 = router.computeSeamlessPartSwitch(parts, setOf(1))
        parts = parts.map { it.copy(receiveChannel = switchToP2.updatedReceiveChannels[it.partIndex]) }

        // Part 1 (index 0) is assigned spillover channel 1 (vacated by Part 2 which is now on Ch 0)
        assertEquals(1, router.getSpilloverChannelForPart(0))

        // Release Note 60 on physical keyboard (Ch 0):
        // Must send Note Off (0x81, 60, 0) to spillover channel 1 AND then restore Part 1 to Off (127) via SysEx!
        val releaseMsgs = router.onIncomingVoiceEvent(MidiVoiceEvent.NoteOff(0, 60, 0), parts, 1)
        assertEquals(2, releaseMsgs.size)
        assertArrayEquals(byteArrayOf(0x81.toByte(), 60.toByte(), 0x00.toByte()), releaseMsgs[0])
        assertArrayEquals(YamahaSysExEncoder.setPartReceiveChannel(0, 127), releaseMsgs[1])
        assertNull(router.getSpilloverChannelForPart(0))
    }

    @Test
    fun testSustainPedalSpilloverAndCleanReleaseAcrossParts() {
        val examplesEvents = mutableListOf<MidiVoiceEvent>()
        val parser = MidiInputStreamParser(onVoiceEvent = { examplesEvents.add(it) })
        val router = PartSpilloverRouter()
        var parts = createDefaultParts()

        // Focus Part 2 (index 1 -> Ch 0)
        val initSwitch = router.computeSeamlessPartSwitch(parts, setOf(1))
        parts = parts.map { it.copy(receiveChannel = initSwitch.updatedReceiveChannels[it.partIndex]) }

        // 1. Musician presses Sustain Pedal (CC 64 = 127) and plays + releases Note 60 on Part 2
        parser.processIncomingMidi(
            byteArrayOf(
                0xB0.toByte(), 64, 127,       // Sustain ON
                0x90.toByte(), 60, 100,       // Note 60 ON
                0x80.toByte(), 60, 0          // Note 60 OFF (finger lifted, note sustained by pedal)
            )
        )
        assertEquals(0, parser.activeNotesCount.value)
        assertEquals(3, examplesEvents.size)
        for (ev in examplesEvents) {
            router.onIncomingVoiceEvent(ev, parts, 1)
        }
        examplesEvents.clear()

        // 2. While Sustain Pedal is STILL physically pressed, musician switches from Part 2 to Part 3 (index 2)
        val switchToP3 = router.computeSeamlessPartSwitch(parts, setOf(2))
        parts = parts.map { it.copy(receiveChannel = switchToP3.updatedReceiveChannels[it.partIndex]) }

        // Verify Part 2 did NOT receive CC 64 = 0 during the switch, and remains tracked in spillover
        assertFalse(switchToP3.midiMessages.any { isSustainOff(it) })
        assertEquals(1, router.getSpilloverChannelForPart(1))

        // 3. Musician plays a note on Part 3 (67) and then releases the physical Sustain Pedal (CC 64 = 0)
        parser.processIncomingMidi(
            byteArrayOf(
                0x90.toByte(), 67, 100,       // Note 67 ON on Part 3 -> activeNotesCount = 1
                0xB0.toByte(), 64, 0          // Sustain Pedal RELEASED (CC 64 = 0)
            )
        )
        assertEquals(1, parser.activeNotesCount.value)

        val noteOnMsgs = router.onIncomingVoiceEvent(examplesEvents[0], parts, 2)
        assertTrue(noteOnMsgs.isEmpty())

        val sustainOffMsgs = router.onIncomingVoiceEvent(examplesEvents[1], parts, 2)
        // Must send CC 64 = 0 on Channel 1 (0xB1, 64, 0) to release Part 2's sustained tail!
        assertTrue(
            "Expected CC 64 = 0 on Part 2 channel (0xB1 64 0)",
            sustainOffMsgs.any { it.contentEquals(byteArrayOf(0xB1.toByte(), 64.toByte(), 0x00.toByte())) }
        )
        assertNull(router.getSpilloverChannelForPart(1))

        // Release Note 67 -> activeNotesCount = 0
        parser.processIncomingMidi(byteArrayOf(0x80.toByte(), 67, 0))
        assertEquals(0, parser.activeNotesCount.value)
    }
}
