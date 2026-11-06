package com.yamahamx.editor.data.midi

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsbMidiPacketConverterTest {

    @Test
    fun testNoteOnEncodingAndDecoding() {
        val rawMidi = byteArrayOf(0x90.toByte(), 0x3C.toByte(), 0x64.toByte()) // Note On Ch 1, C4, vel 100
        val encoded = UsbMidiPacketConverter.encodeToUsbMidiPackets(rawMidi)

        assertEquals(4, encoded.size)
        assertEquals(0x09.toByte(), encoded[0]) // CIN 0x9
        assertEquals(0x90.toByte(), encoded[1])
        assertEquals(0x3C.toByte(), encoded[2])
        assertEquals(0x64.toByte(), encoded[3])

        val decoded = UsbMidiPacketConverter.decodeUsbMidiPackets(encoded, encoded.size)
        assertArrayEquals(rawMidi, decoded)
    }

    @Test
    fun testNoteOffEncodingAndDecoding() {
        val rawMidi = byteArrayOf(0x80.toByte(), 0x3C.toByte(), 0x40.toByte()) // Note Off Ch 1, C4
        val encoded = UsbMidiPacketConverter.encodeToUsbMidiPackets(rawMidi)

        assertEquals(4, encoded.size)
        assertEquals(0x08.toByte(), encoded[0]) // CIN 0x8
        assertEquals(0x80.toByte(), encoded[1])
        assertEquals(0x3C.toByte(), encoded[2])
        assertEquals(0x40.toByte(), encoded[3])

        val decoded = UsbMidiPacketConverter.decodeUsbMidiPackets(encoded, encoded.size)
        assertArrayEquals(rawMidi, decoded)
    }

    @Test
    fun testControlChangeEncodingAndDecoding() {
        val rawMidi = byteArrayOf(0xB0.toByte(), 0x01.toByte(), 0x7F.toByte()) // CC 1 (Modulation) value 127
        val encoded = UsbMidiPacketConverter.encodeToUsbMidiPackets(rawMidi)

        assertEquals(4, encoded.size)
        assertEquals(0x0B.toByte(), encoded[0]) // CIN 0xB
        assertEquals(0xB0.toByte(), encoded[1])
        assertEquals(0x01.toByte(), encoded[2])
        assertEquals(0x7F.toByte(), encoded[3])

        val decoded = UsbMidiPacketConverter.decodeUsbMidiPackets(encoded, encoded.size)
        assertArrayEquals(rawMidi, decoded)
    }

    @Test
    fun testProgramChangeEncodingAndDecoding() {
        val rawMidi = byteArrayOf(0xC0.toByte(), 0x15.toByte()) // Program Change 21
        val encoded = UsbMidiPacketConverter.encodeToUsbMidiPackets(rawMidi)

        assertEquals(4, encoded.size)
        assertEquals(0x0C.toByte(), encoded[0]) // CIN 0xC
        assertEquals(0xC0.toByte(), encoded[1])
        assertEquals(0x15.toByte(), encoded[2])
        assertEquals(0x00.toByte(), encoded[3])

        val decoded = UsbMidiPacketConverter.decodeUsbMidiPackets(encoded, encoded.size)
        assertArrayEquals(rawMidi, decoded)
    }

    @Test
    fun testPitchBendEncodingAndDecoding() {
        val rawMidi = byteArrayOf(0xE0.toByte(), 0x00.toByte(), 0x40.toByte()) // Pitch Bend center
        val encoded = UsbMidiPacketConverter.encodeToUsbMidiPackets(rawMidi)

        assertEquals(4, encoded.size)
        assertEquals(0x0E.toByte(), encoded[0]) // CIN 0xE
        assertEquals(0xE0.toByte(), encoded[1])
        assertEquals(0x00.toByte(), encoded[2])
        assertEquals(0x40.toByte(), encoded[3])

        val decoded = UsbMidiPacketConverter.decodeUsbMidiPackets(encoded, encoded.size)
        assertArrayEquals(rawMidi, decoded)
    }

    @Test
    fun testSysEx10BytesEncodingAndDecoding() {
        // Typical Yamaha SysEx: F0 43 10 7F 17 37 00 0E 64 F7 (10 bytes)
        val rawSysEx = byteArrayOf(
            0xF0.toByte(), 0x43.toByte(), 0x10.toByte(), 0x7F.toByte(), 0x17.toByte(),
            0x37.toByte(), 0x00.toByte(), 0x0E.toByte(), 0x64.toByte(), 0xF7.toByte()
        )
        val encoded = UsbMidiPacketConverter.encodeToUsbMidiPackets(rawSysEx)

        // 10 bytes = 3 packets of 3 bytes + 1 packet of 1 byte = 4 packets = 16 bytes
        assertEquals(16, encoded.size)
        assertEquals(0x04.toByte(), encoded[0]) // CIN 0x4: start/continue
        assertEquals(0x04.toByte(), encoded[4]) // CIN 0x4: continue
        assertEquals(0x04.toByte(), encoded[8]) // CIN 0x4: continue
        assertEquals(0x05.toByte(), encoded[12]) // CIN 0x5: ends with 1 byte (F7)
        assertEquals(0xF7.toByte(), encoded[13])

        val decoded = UsbMidiPacketConverter.decodeUsbMidiPackets(encoded, encoded.size)
        assertArrayEquals(rawSysEx, decoded)
    }

    @Test
    fun testSysEx9BytesEncodingAndDecoding() {
        // SysEx with 9 bytes (ends with 3 bytes)
        val rawSysEx = byteArrayOf(
            0xF0.toByte(), 0x43.toByte(), 0x10.toByte(), 0x7F.toByte(), 0x17.toByte(),
            0x37.toByte(), 0x00.toByte(), 0x0E.toByte(), 0xF7.toByte()
        )
        val encoded = UsbMidiPacketConverter.encodeToUsbMidiPackets(rawSysEx)

        // 9 bytes = 2 packets of 3 bytes + 1 packet of 3 bytes ending in F7 = 3 packets = 12 bytes
        assertEquals(12, encoded.size)
        assertEquals(0x04.toByte(), encoded[0]) // CIN 0x4: continue
        assertEquals(0x04.toByte(), encoded[4]) // CIN 0x4: continue
        assertEquals(0x07.toByte(), encoded[8]) // CIN 0x7: ends with 3 bytes
        assertEquals(0xF7.toByte(), encoded[11])

        val decoded = UsbMidiPacketConverter.decodeUsbMidiPackets(encoded, encoded.size)
        assertArrayEquals(rawSysEx, decoded)
    }

    @Test
    fun testSysEx8BytesEncodingAndDecoding() {
        // SysEx with 8 bytes (ends with 2 bytes)
        val rawSysEx = byteArrayOf(
            0xF0.toByte(), 0x43.toByte(), 0x10.toByte(), 0x7F.toByte(), 0x17.toByte(),
            0x37.toByte(), 0x00.toByte(), 0xF7.toByte()
        )
        val encoded = UsbMidiPacketConverter.encodeToUsbMidiPackets(rawSysEx)

        // 8 bytes = 2 packets of 3 bytes + 1 packet of 2 bytes ending in F7 = 3 packets = 12 bytes
        assertEquals(12, encoded.size)
        assertEquals(0x04.toByte(), encoded[0]) // CIN 0x4: continue
        assertEquals(0x04.toByte(), encoded[4]) // CIN 0x4: continue
        assertEquals(0x06.toByte(), encoded[8]) // CIN 0x6: ends with 2 bytes
        assertEquals(0xF7.toByte(), encoded[10])

        val decoded = UsbMidiPacketConverter.decodeUsbMidiPackets(encoded, encoded.size)
        assertArrayEquals(rawSysEx, decoded)
    }

    @Test
    fun testMultipleConcatenatedMessages() {
        // Sequence: CC 0 (Bank MSB) + CC 32 (Bank LSB) + Program Change
        val rawMidi = byteArrayOf(
            0xB0.toByte(), 0x00.toByte(), 0x3F.toByte(), // MSB 63 (3 bytes)
            0xB0.toByte(), 0x20.toByte(), 0x08.toByte(), // LSB 8 (3 bytes)
            0xC0.toByte(), 0x0A.toByte()                 // PC 10 (2 bytes)
        )
        val encoded = UsbMidiPacketConverter.encodeToUsbMidiPackets(rawMidi)

        // 3 packets: 4 + 4 + 4 = 12 bytes
        assertEquals(12, encoded.size)
        assertEquals(0x0B.toByte(), encoded[0]) // CIN 0xB
        assertEquals(0x0B.toByte(), encoded[4]) // CIN 0xB
        assertEquals(0x0C.toByte(), encoded[8]) // CIN 0xC

        val decoded = UsbMidiPacketConverter.decodeUsbMidiPackets(encoded, encoded.size)
        assertArrayEquals(rawMidi, decoded)
    }

    @Test
    fun testRealtimeClockMessage() {
        val rawMidi = byteArrayOf(0xF8.toByte()) // MIDI Timing Clock
        val encoded = UsbMidiPacketConverter.encodeToUsbMidiPackets(rawMidi)

        assertEquals(4, encoded.size)
        assertEquals(0x0F.toByte(), encoded[0]) // CIN 0xF
        assertEquals(0xF8.toByte(), encoded[1])

        val decoded = UsbMidiPacketConverter.decodeUsbMidiPackets(encoded, encoded.size)
        assertArrayEquals(rawMidi, decoded)
    }

    @Test
    fun testParserNoteOnAndNoteOffAndZeroFloor() {
        val events = mutableListOf<MidiVoiceEvent>()
        val parser = MidiInputStreamParser(
            onVoiceEvent = { events.add(it) }
        )

        // Extra Note Off when count is 0 must stay at 0 (never negative)
        parser.processIncomingMidi(byteArrayOf(0x80.toByte(), 60, 0), 0, 3)
        assertEquals(0, parser.activeNotesCount.value)

        // Note On Ch 1 (0x90) vel 100 -> count = 1
        parser.processIncomingMidi(byteArrayOf(0x90.toByte(), 60, 100), 0, 3)
        assertEquals(1, parser.activeNotesCount.value)

        // Note On Ch 16 (0x9F) vel 80 -> count = 2 (any MIDI channel)
        parser.processIncomingMidi(byteArrayOf(0x9F.toByte(), 64, 80), 0, 3)
        assertEquals(2, parser.activeNotesCount.value)

        // Note On with Velocity == 0 acts as Note Off -> count = 1
        parser.processIncomingMidi(byteArrayOf(0x90.toByte(), 60, 0), 0, 3)
        assertEquals(1, parser.activeNotesCount.value)

        // Standard Note Off (0x8F) -> count = 0
        parser.processIncomingMidi(byteArrayOf(0x8F.toByte(), 64, 64), 0, 3)
        assertEquals(0, parser.activeNotesCount.value)

        // Extra Note On with Velocity == 0 when already 0 -> stays 0
        parser.processIncomingMidi(byteArrayOf(0x90.toByte(), 60, 0), 0, 3)
        assertEquals(0, parser.activeNotesCount.value)
    }

    @Test
    fun testParserRunningStatusAndInterleavedRealTime() {
        val parser = MidiInputStreamParser()

        // 3-note chord using Running Status + interleaved 0xF8 (Clock) and 0xFE (Active Sensing)
        val chordOn = byteArrayOf(
            0x90.toByte(), 60, 0xF8.toByte(), 100, // Note 1 (with mid-message clock)
            64, 100,                               // Note 2 (running status 0x90)
            0xFE.toByte(), 67, 100                 // Note 3 (running status 0x90 after Active Sensing)
        )
        parser.processIncomingMidi(chordOn, 0, chordOn.size)
        assertEquals(3, parser.activeNotesCount.value)

        // Release 2 notes via Running Status Note On vel=0
        val releaseTwo = byteArrayOf(60, 0, 64, 0)
        parser.processIncomingMidi(releaseTwo, 0, releaseTwo.size)
        assertEquals(1, parser.activeNotesCount.value)
    }

    @Test
    fun testParserIgnoresCcPcPitchBendAndSysEx() {
        val parser = MidiInputStreamParser()

        // Hold 2 notes
        parser.processIncomingMidi(byteArrayOf(0x90.toByte(), 60, 100, 0x91.toByte(), 64, 100), 0, 6)
        assertEquals(2, parser.activeNotesCount.value)

        // Send CC 64 (Sustain), CC 123 (All Notes Off CC), Program Change, Pitch Bend, and SysEx
        val nonNoteMessages = byteArrayOf(
            0xB0.toByte(), 64, 127,                                     // CC 64 Sustain On
            0xB0.toByte(), 123, 0,                                      // CC 123
            0xC0.toByte(), 10,                                          // Program Change
            0xE0.toByte(), 0x00, 0x40,                                  // Pitch Bend
            0xF0.toByte(), 0x43, 0x10, 0x7F, 0x17, 0x00, 0xF7.toByte()  // Yamaha SysEx
        )
        parser.processIncomingMidi(nonNoteMessages, 0, nonNoteMessages.size)

        // Count must remain strictly 2
        assertEquals(2, parser.activeNotesCount.value)

        // Panic / Reset must zero the counter immediately
        parser.resetActiveNotes()
        assertEquals(0, parser.activeNotesCount.value)
    }

    @Test
    fun testRapidMultipleVoiceSwitchesWhilePlayingNotesContinuously() {
        val parser = MidiInputStreamParser()

        // User presses 2 notes on physical keyboard
        parser.processIncomingMidi(byteArrayOf(0x90.toByte(), 60, 100, 0x90.toByte(), 64, 95))
        assertEquals(2, parser.activeNotesCount.value)

        // Rapidly switch 20 voices in succession across multiple parts (SysEx + CC 0/32 + Program Change bursts)
        for (i in 0 until 20) {
            val partIdx = i % 16
            val voiceMsgs = YamahaSysExEncoder.createVoiceChangeMessages(
                partIndex = partIdx,
                msb = 63,
                lsb = i % 8,
                programChange = (i * 5) % 128
            )
            for (msg in voiceMsgs) {
                parser.processIncomingMidi(msg)
            }

            // Play and release a note using Running Status (without repeating 0x90) right after the voice switch burst!
            parser.processIncomingMidi(byteArrayOf(67, 110)) // Running Status Note On -> 3
            assertEquals("Failed on voice switch iteration $i (Note On)", 3, parser.activeNotesCount.value)

            parser.processIncomingMidi(byteArrayOf(67, 0)) // Running Status Note Off (vel 0) -> 2
            assertEquals("Failed on voice switch iteration $i (Note Off)", 2, parser.activeNotesCount.value)
        }

        // Release initial 2 notes
        parser.processIncomingMidi(byteArrayOf(0x80.toByte(), 60, 0, 0x80.toByte(), 64, 0))
        assertEquals(0, parser.activeNotesCount.value)
    }

    @Test
    fun testParserResilienceAgainstTruncatedSysExMalformedBoundsAndCallbackExceptions() {
        var shouldThrow = true
        val parser = MidiInputStreamParser(
            onChannelActivity = {
                if (shouldThrow) throw RuntimeException("Simulated downstream UI/Flow exception")
            },
            onVoiceEvent = {
                if (shouldThrow) throw IllegalStateException("Simulated downstream listener exception")
            }
        )

        // 1. Malformed offsets and counts must never throw
        parser.processIncomingMidi(null, -10, 50)
        parser.processIncomingMidi(ByteArray(0), 5, 10)
        parser.processIncomingMidi(byteArrayOf(0x90.toByte(), 60, 100), -5, 999)
        parser.processIncomingMidi(byteArrayOf(0x90.toByte(), 60, 100), 10, 5)
        assertEquals(0, parser.activeNotesCount.value)

        // 2. Listener throwing an exception must NOT kill the parser or prevent counting
        parser.processIncomingMidi(byteArrayOf(0x90.toByte(), 60, 100))
        assertEquals(1, parser.activeNotesCount.value)

        // 3. Truncated SysEx (0xF0 without 0xF7) followed by Running Status Note On/Off
        parser.processIncomingMidi(byteArrayOf(0xF0.toByte(), 0x43, 0x10, 0x7F, 0x1C))
        // Next packet is a 2-byte Running Status Note On (note 64, vel 100)
        parser.processIncomingMidi(byteArrayOf(64, 100))
        assertEquals(2, parser.activeNotesCount.value)

        // 4. Reset active notes (Panic) and immediately play a Running Status note
        shouldThrow = false
        parser.resetActiveNotes()
        assertEquals(0, parser.activeNotesCount.value)

        parser.processIncomingMidi(byteArrayOf(67, 90))
        assertEquals(1, parser.activeNotesCount.value)
        parser.processIncomingMidi(byteArrayOf(67, 0))
        assertEquals(0, parser.activeNotesCount.value)
    }

    @Test
    fun testUsbMidiPacketConverterIgnoresCable1AndZeroHeaderPaddingWithStaleBytes() {
        val parser = MidiInputStreamParser()

        // Construct a 64-byte USB Bulk IN buffer containing:
        // Packet 0: Cable 0 Note On (0x09, 0x90, 60, 100)
        // Packet 1: Cable 1 DAW Remote SysEx Continue (0x14, 60, 0, 0) -> must be ignored!
        // Packet 2: Zero-header padding slot with stale Note Off bytes (0x00, 0x80, 60, 0) -> must be ignored!
        // Packet 3: Interleaved Cable 0 SysEx start (0x04, 0xF0, 0x43, 0x10), Cable 0 Note On (0x09, 0x90, 64, 100),
        //           and Cable 0 SysEx continue (0x04, 64, 0, 0) + end (0x05, 0xF7, 0, 0)
        val usbBuffer = byteArrayOf(
            0x09, 0x90.toByte(), 60, 100,       // Valid Cable 0 Note On (+1)
            0x14, 60, 0, 0,                     // Cable 1 data (must NOT be parsed as Running Status Note Off)
            0x00, 0x80.toByte(), 60, 0,         // CIN 0x0 padding with stale 0x80 (must NOT be parsed as Note Off)
            0x04, 0xF0.toByte(), 0x43, 0x10,    // Cable 0 SysEx start
            0x09, 0x90.toByte(), 64, 100,       // Interleaved Cable 0 Note On (+1 -> total 2)
            0x04, 64, 0, 0,                     // Cable 0 SysEx continuation (must NOT be parsed as Note 64 Vel 0!)
            0x05, 0xF7.toByte(), 0, 0           // Cable 0 SysEx end
        )

        val decoded = UsbMidiPacketConverter.decodeUsbMidiPackets(usbBuffer, usbBuffer.size)
        parser.processIncomingMidi(decoded)

        // Both notes (60 and 64) must remain active (count = 2)
        assertEquals(2, parser.activeNotesCount.value)
    }

    @Test
    fun testParserEmitsControlChangeEventWithBluetoothFlag() {
        val events = mutableListOf<MidiVoiceEvent>()
        val parser = MidiInputStreamParser(
            onVoiceEvent = { events.add(it) }
        )

        // CC 7 (Volume = 100) on Channel 1 (status 0xB0) from Bluetooth
        val cc7Bytes = byteArrayOf(0xB0.toByte(), 7, 100)
        parser.processIncomingMidi(cc7Bytes, 0, cc7Bytes.size, isFromBluetooth = true)

        assertEquals(1, events.size)
        val event1 = events[0] as MidiVoiceEvent.ControlChange
        assertEquals(0, event1.channel)
        assertEquals(7, event1.controller)
        assertEquals(100, event1.value)
        assertTrue(event1.fromBluetooth)

        // CC 10 (Pan = 64) on Channel 2 (status 0xB1) from USB (not bluetooth)
        val cc10Bytes = byteArrayOf(0xB1.toByte(), 10, 64)
        parser.processIncomingMidi(cc10Bytes, 0, cc10Bytes.size, isFromBluetooth = false)

        assertEquals(2, events.size)
        val event2 = events[1] as MidiVoiceEvent.ControlChange
        assertEquals(1, event2.channel)
        assertEquals(10, event2.controller)
        assertEquals(64, event2.value)
        assertFalse(event2.fromBluetooth)

        // CC 1 (Modulation = 127) on Channel 16 (status 0xBF) from Bluetooth
        val cc1Bytes = byteArrayOf(0xBF.toByte(), 1, 127)
        parser.processIncomingMidi(cc1Bytes, 0, cc1Bytes.size, isFromBluetooth = true)

        assertEquals(3, events.size)
        val event3 = events[2] as MidiVoiceEvent.ControlChange
        assertEquals(15, event3.channel)
        assertEquals(1, event3.controller)
        assertEquals(127, event3.value)
        assertTrue(event3.fromBluetooth)
    }

    @Test
    fun testBleRunningStatusToUsbMidiPacketsEncoding() {
        // Multi-note chord from iPad BLE using Running Status: 0x90 60 100, 64 100, 67 100
        val bleChordWithRunningStatus = byteArrayOf(
            0x90.toByte(), 60, 100,
            64, 100,
            67, 100
        )
        val encoded = UsbMidiPacketConverter.encodeToUsbMidiPackets(bleChordWithRunningStatus)

        // Should produce 3 valid 4-byte USB-MIDI Note On packets (12 bytes total)
        assertEquals(12, encoded.size)
        assertEquals(0x09.toByte(), encoded[0])
        assertEquals(0x90.toByte(), encoded[1])
        assertEquals(60.toByte(), encoded[2])
        assertEquals(100.toByte(), encoded[3])

        assertEquals(0x09.toByte(), encoded[4])
        assertEquals(0x90.toByte(), encoded[5])
        assertEquals(64.toByte(), encoded[6])
        assertEquals(100.toByte(), encoded[7])

        assertEquals(0x09.toByte(), encoded[8])
        assertEquals(0x90.toByte(), encoded[9])
        assertEquals(67.toByte(), encoded[10])
        assertEquals(100.toByte(), encoded[11])
    }

    @Test
    fun testIndependentUsbAndBluetoothParserStateNoCollision() {
        val events = mutableListOf<MidiVoiceEvent>()
        val parser = MidiInputStreamParser(
            onVoiceEvent = { events.add(it) }
        )

        // Start a partial CC message on USB (0xB0, 7) while Bluetooth sends a complete Note On (0x90, 60, 100)
        parser.processIncomingMidi(byteArrayOf(0xB0.toByte(), 7), 0, 2, isFromBluetooth = false)
        parser.processIncomingMidi(byteArrayOf(0x90.toByte(), 60, 100), 0, 3, isFromBluetooth = true)
        // Complete the USB CC message (value = 95)
        parser.processIncomingMidi(byteArrayOf(95), 0, 1, isFromBluetooth = false)

        assertEquals(2, events.size)
        val bleNote = events[0] as MidiVoiceEvent.NoteOn
        assertEquals(0, bleNote.channel)
        assertEquals(60, bleNote.note)
        assertEquals(100, bleNote.velocity)

        val usbCc = events[1] as MidiVoiceEvent.ControlChange
        assertEquals(0, usbCc.channel)
        assertEquals(7, usbCc.channel.let { usbCc.controller })
        assertEquals(95, usbCc.value)
        assertFalse(usbCc.fromBluetooth)
    }

    @Test
    fun testYamahaPhysicalKnobAbsoluteValuesAndStrictSysExDiscard() {
        val events = mutableListOf<MidiVoiceEvent>()
        val parser = MidiInputStreamParser(
            onVoiceEvent = { events.add(it) }
        )

        // 1. Proprietary Yamaha MX display/parameter SysEx (0xF0..0xF7), Clock (0xF8), and Active Sensing (0xFE)
        //    emitted before physical Knob 1 CC MUST be summarily discarded and NEVER emit ControlChange!
        parser.processIncomingMidi(
            byteArrayOf(0xF8.toByte(), 0xFE.toByte(), 0xF0.toByte(), 0x43, 0x10),
            isFromBluetooth = false,
            isFromYamahaHardware = true
        )
        parser.processIncomingMidi(byteArrayOf(0x7F, 0x17, 0x37), isFromBluetooth = false, isFromYamahaHardware = true)
        parser.processIncomingMidi(byteArrayOf(0x00, 0x0E, 0x28), isFromBluetooth = false, isFromYamahaHardware = true)
        parser.processIncomingMidi(byteArrayOf(0xF7.toByte(), 0xFE.toByte()), isFromBluetooth = false, isFromYamahaHardware = true)

        assertTrue("SysEx, Clock (0xF8), and Active Sensing (0xFE) must never emit ControlChange", events.isEmpty())

        // 2. Direct Control Change (0xB0..0xBF) from physical Yamaha knob sweeping 0 -> 64 -> 127 (strictly 1:1 absolute)
        parser.processIncomingMidi(byteArrayOf(0xB0.toByte(), 74, 0), isFromBluetooth = false, isFromYamahaHardware = true)
        parser.processIncomingMidi(byteArrayOf(0xB0.toByte(), 74, 64), isFromBluetooth = false, isFromYamahaHardware = true)
        parser.processIncomingMidi(byteArrayOf(0xB0.toByte(), 74, 127), isFromBluetooth = false, isFromYamahaHardware = true)

        assertEquals(3, events.size)
        val cc0 = events[0] as MidiVoiceEvent.ControlChange
        val cc1 = events[1] as MidiVoiceEvent.ControlChange
        val cc2 = events[2] as MidiVoiceEvent.ControlChange
        assertEquals(0xB0, cc0.statusByte)
        assertEquals(74, cc0.controller)
        assertEquals(0, cc0.value)
        assertEquals(64, cc1.value)
        assertEquals(127, cc2.value)
        assertTrue(cc2.fromYamahaHardware)
    }

    @Test
    fun testLayerActivationAndProgramChangeEventsFromHardware() {
        val events = mutableListOf<MidiVoiceEvent>()
        val parser = MidiInputStreamParser(
            onVoiceEvent = { events.add(it) }
        )

        // 1. Hardware [LAYER] SysEx: F0 43 10 7F 17 36 00 44 01 F7
        val layerSysEx = YamahaSysExEncoder.setPart12Link(true)
        parser.processIncomingMidi(layerSysEx, isFromBluetooth = false, isFromYamahaHardware = true)

        // 2. Hardware Program Change on Channel 2 (Part 2): 0xC1 0x05
        parser.processIncomingMidi(byteArrayOf(0xC1.toByte(), 0x05), isFromBluetooth = false, isFromYamahaHardware = true)

        assertEquals(2, events.size)
        val layerEvt = events[0] as MidiVoiceEvent.LayerActivation
        assertTrue(layerEvt.enabled)

        val pcEvt = events[1] as MidiVoiceEvent.ProgramChange
        assertEquals(1, pcEvt.channel)
        assertEquals(5, pcEvt.program)
        assertTrue(pcEvt.fromYamahaHardware)
    }

    @Test
    fun testMultiDeviceUsbYamahaAndArturiaSimultaneousStreams() {
        val events = mutableListOf<MidiVoiceEvent>()
        val parser = MidiInputStreamParser(
            onVoiceEvent = { events.add(it) }
        )

        // Yamaha (sourceDeviceId = 101) starts a partial CC 74 message while Arturia (sourceDeviceId = 202) sends Note On
        parser.processIncomingMidi(
            byteArrayOf(0xB0.toByte(), 74),
            isFromBluetooth = false,
            isFromYamahaHardware = true,
            sourceDeviceId = 101
        )
        parser.processIncomingMidi(
            byteArrayOf(0x90.toByte(), 60, 110),
            isFromBluetooth = false,
            isFromYamahaHardware = false,
            sourceDeviceId = 202
        )
        parser.processIncomingMidi(
            byteArrayOf(127),
            isFromBluetooth = false,
            isFromYamahaHardware = true,
            sourceDeviceId = 101
        )

        assertEquals(2, events.size)
        val arturiaNote = events[0] as MidiVoiceEvent.NoteOn
        assertEquals(60, arturiaNote.note)
        assertEquals(110, arturiaNote.velocity)

        val yamahaKnob = events[1] as MidiVoiceEvent.ControlChange
        assertEquals(74, yamahaKnob.controller)
        assertEquals(127, yamahaKnob.value)
        assertTrue(yamahaKnob.fromYamahaHardware)
    }
}


