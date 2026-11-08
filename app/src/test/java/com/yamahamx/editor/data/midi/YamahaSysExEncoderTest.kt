package com.yamahamx.editor.data.midi

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class YamahaSysExEncoderTest {

    @Test
    fun testVoiceChangeSysExSequence() {
        // Voice: ConcertGrand (MSB 63, LSB 0, PC 0) for Part 1 (index 0)
        val messages = YamahaSysExEncoder.createVoiceChangeMessages(0, 63, 0, 0)
        assertEquals(6, messages.size)

        // 1. Bank Select MSB SysEx: AddrHigh=0x37, AddrMid=0x00, AddrLow=0x01, Data=63 (0x3F)
        val expectedMsb = byteArrayOf(
            0xF0.toByte(), 0x43.toByte(), 0x10.toByte(), 0x7F.toByte(), 0x17.toByte(),
            0x37.toByte(), 0x00.toByte(), 0x01.toByte(), 0x3F.toByte(),
            0xF7.toByte()
        )
        assertArrayEquals(expectedMsb, messages[0])

        // 2. Bank Select LSB SysEx: AddrHigh=0x37, AddrMid=0x00, AddrLow=0x02, Data=0 (0x00)
        val expectedLsb = byteArrayOf(
            0xF0.toByte(), 0x43.toByte(), 0x10.toByte(), 0x7F.toByte(), 0x17.toByte(),
            0x37.toByte(), 0x00.toByte(), 0x02.toByte(), 0x00.toByte(),
            0xF7.toByte()
        )
        assertArrayEquals(expectedLsb, messages[1])

        // 3. Program Number SysEx: AddrHigh=0x37, AddrMid=0x00, AddrLow=0x03, Data=0 (0x00)
        val expectedPc = byteArrayOf(
            0xF0.toByte(), 0x43.toByte(), 0x10.toByte(), 0x7F.toByte(), 0x17.toByte(),
            0x37.toByte(), 0x00.toByte(), 0x03.toByte(), 0x00.toByte(),
            0xF7.toByte()
        )
        assertArrayEquals(expectedPc, messages[2])

        // 4. MIDI CC 0 (Bank MSB)
        assertArrayEquals(byteArrayOf(0xB0.toByte(), 0x00.toByte(), 0x3F.toByte()), messages[3])
        // 5. MIDI CC 32 (Bank LSB)
        assertArrayEquals(byteArrayOf(0xB0.toByte(), 0x20.toByte(), 0x00.toByte()), messages[4])
        // 6. MIDI Program Change
        assertArrayEquals(byteArrayOf(0xC0.toByte(), 0x00.toByte()), messages[5])
    }

    @Test
    fun testVoiceChangeSysExForPart10Drums() {
        // Drum Kit for Part 10 (index 9), MSB 63, LSB 32 (0x20), PC 4 (0x04)
        val messages = YamahaSysExEncoder.createVoiceChangeMessages(9, 63, 32, 4)
        assertEquals(6, messages.size)

        // MSB
        assertArrayEquals(
            byteArrayOf(
                0xF0.toByte(), 0x43.toByte(), 0x10.toByte(), 0x7F.toByte(), 0x17.toByte(),
                0x37.toByte(), 0x09.toByte(), 0x01.toByte(), 0x3F.toByte(), 0xF7.toByte()
            ),
            messages[0]
        )
        // LSB
        assertArrayEquals(
            byteArrayOf(
                0xF0.toByte(), 0x43.toByte(), 0x10.toByte(), 0x7F.toByte(), 0x17.toByte(),
                0x37.toByte(), 0x09.toByte(), 0x02.toByte(), 0x20.toByte(), 0xF7.toByte()
            ),
            messages[1]
        )
        // PC
        assertArrayEquals(
            byteArrayOf(
                0xF0.toByte(), 0x43.toByte(), 0x10.toByte(), 0x7F.toByte(), 0x17.toByte(),
                0x37.toByte(), 0x09.toByte(), 0x03.toByte(), 0x04.toByte(), 0xF7.toByte()
            ),
            messages[2]
        )

        // MIDI CC 0, CC 32, PC on channel 10 (index 9)
        assertArrayEquals(byteArrayOf(0xB9.toByte(), 0x00.toByte(), 0x3F.toByte()), messages[3])
        assertArrayEquals(byteArrayOf(0xB9.toByte(), 0x20.toByte(), 0x20.toByte()), messages[4])
        assertArrayEquals(byteArrayOf(0xC9.toByte(), 0x04.toByte()), messages[5])
    }

    @Test
    fun testPartVolumeSysEx() {
        // Part 1 (index 0), Volume 100 (0x64)
        val expected = byteArrayOf(
            0xF0.toByte(), 0x43.toByte(), 0x10.toByte(), 0x7F.toByte(), 0x17.toByte(),
            0x37.toByte(), 0x00.toByte(), 0x0E.toByte(), 0x64.toByte(),
            0xF7.toByte()
        )
        val actual = YamahaSysExEncoder.setPartVolume(0, 100)
        assertArrayEquals(expected, actual)
    }

    @Test
    fun testPart16PanSysEx() {
        // Part 16 (index 15 = 0x0F), Pan Center 64 (0x40)
        val expected = byteArrayOf(
            0xF0.toByte(), 0x43.toByte(), 0x10.toByte(), 0x7F.toByte(), 0x17.toByte(),
            0x37.toByte(), 0x0F.toByte(), 0x0F.toByte(), 0x40.toByte(),
            0xF7.toByte()
        )
        val actual = YamahaSysExEncoder.setPartPan(15, 64)
        assertArrayEquals(expected, actual)
    }

    @Test
    fun testReverbAndChorusSendSysEx() {
        // Part 2 (index 1), Reverb Send 40
        val expectedReverb = byteArrayOf(
            0xF0.toByte(), 0x43.toByte(), 0x10.toByte(), 0x7F.toByte(), 0x17.toByte(),
            0x37.toByte(), 0x01.toByte(), 0x12.toByte(), 0x28.toByte(),
            0xF7.toByte()
        )
        assertArrayEquals(expectedReverb, YamahaSysExEncoder.setPartReverbSend(1, 40))

        // Part 2 (index 1), Chorus Send 15
        val expectedChorus = byteArrayOf(
            0xF0.toByte(), 0x43.toByte(), 0x10.toByte(), 0x7F.toByte(), 0x17.toByte(),
            0x37.toByte(), 0x01.toByte(), 0x13.toByte(), 0x0F.toByte(),
            0xF7.toByte()
        )
        assertArrayEquals(expectedChorus, YamahaSysExEncoder.setPartChorusSend(1, 15))
    }

    @Test
    fun testNoteLimitsSysEx() {
        // Part 1 Note Limit Low = 36 (0x24) -> C1
        val expectedLow = byteArrayOf(
            0xF0.toByte(), 0x43.toByte(), 0x10.toByte(), 0x7F.toByte(), 0x17.toByte(),
            0x37.toByte(), 0x00.toByte(), 0x08.toByte(), 0x24.toByte(),
            0xF7.toByte()
        )
        assertArrayEquals(expectedLow, YamahaSysExEncoder.setPartNoteLimitLow(0, 36))

        // Part 1 Note Limit High = 96 (0x60) -> C6
        val expectedHigh = byteArrayOf(
            0xF0.toByte(), 0x43.toByte(), 0x10.toByte(), 0x7F.toByte(), 0x17.toByte(),
            0x37.toByte(), 0x00.toByte(), 0x09.toByte(), 0x60.toByte(),
            0xF7.toByte()
        )
        assertArrayEquals(expectedHigh, YamahaSysExEncoder.setPartNoteLimitHigh(0, 96))
    }

    @Test
    fun testNoteShiftSysEx() {
        // Part 1 Note Shift: 64 (0 semitones)
        val expectedCenter = byteArrayOf(
            0xF0.toByte(), 0x43.toByte(), 0x10.toByte(), 0x7F.toByte(), 0x17.toByte(),
            0x37.toByte(), 0x00.toByte(), 0x16.toByte(), 0x40.toByte(),
            0xF7.toByte()
        )
        assertArrayEquals(expectedCenter, YamahaSysExEncoder.setPartNoteShift(0, 64))

        // Part 1 Note Shift: 40 (-24 semitones)
        val expectedMinus24 = byteArrayOf(
            0xF0.toByte(), 0x43.toByte(), 0x10.toByte(), 0x7F.toByte(), 0x17.toByte(),
            0x37.toByte(), 0x00.toByte(), 0x16.toByte(), 0x28.toByte(),
            0xF7.toByte()
        )
        assertArrayEquals(expectedMinus24, YamahaSysExEncoder.setPartNoteShift(0, 40))
    }

    @Test
    fun testFilterCutoffAndResonanceSysEx() {
        // Part 1 Cutoff 90 (0x5A)
        val expectedCutoff = byteArrayOf(
            0xF0.toByte(), 0x43.toByte(), 0x10.toByte(), 0x7F.toByte(), 0x17.toByte(),
            0x37.toByte(), 0x00.toByte(), 0x2D.toByte(), 0x5A.toByte(),
            0xF7.toByte()
        )
        assertArrayEquals(expectedCutoff, YamahaSysExEncoder.setPartCutoff(0, 90))

        // Part 1 Resonance 75 (0x4B)
        val expectedReso = byteArrayOf(
            0xF0.toByte(), 0x43.toByte(), 0x10.toByte(), 0x7F.toByte(), 0x17.toByte(),
            0x37.toByte(), 0x00.toByte(), 0x2E.toByte(), 0x4B.toByte(),
            0xF7.toByte()
        )
        assertArrayEquals(expectedReso, YamahaSysExEncoder.setPartResonance(0, 75))
    }

    @Test
    fun testPart12LinkSysEx() {
        // Part 1-2 Link ON (AddrHigh 0x36, AddrMid 0x00, AddrLow 0x44, Data 0x01)
        val expectedOn = byteArrayOf(
            0xF0.toByte(), 0x43.toByte(), 0x10.toByte(), 0x7F.toByte(), 0x17.toByte(),
            0x36.toByte(), 0x00.toByte(), 0x44.toByte(), 0x01.toByte(),
            0xF7.toByte()
        )
        assertArrayEquals(expectedOn, YamahaSysExEncoder.setPart12Link(true))

        // Part 1-2 Link OFF (Data 0x00)
        val expectedOff = byteArrayOf(
            0xF0.toByte(), 0x43.toByte(), 0x10.toByte(), 0x7F.toByte(), 0x17.toByte(),
            0x36.toByte(), 0x00.toByte(), 0x44.toByte(), 0x00.toByte(),
            0xF7.toByte()
        )
        assertArrayEquals(expectedOff, YamahaSysExEncoder.setPart12Link(false))
    }

    @Test
    fun testPartReceiveChannelSysEx() {
        // Part 1 (index 0) Receive Channel: Channel 1 (Data = 0x00)
        val expectedCh1 = byteArrayOf(
            0xF0.toByte(), 0x43.toByte(), 0x10.toByte(), 0x7F.toByte(), 0x17.toByte(),
            0x37.toByte(), 0x00.toByte(), 0x04.toByte(), 0x00.toByte(),
            0xF7.toByte()
        )
        assertArrayEquals(expectedCh1, YamahaSysExEncoder.setPartReceiveChannel(0, 0))

        // Part 2 (index 1) Receive Channel: Channel 1 (Layer with Part 1) -> AddrMid=0x01, Data=0x00
        val expectedLayer = byteArrayOf(
            0xF0.toByte(), 0x43.toByte(), 0x10.toByte(), 0x7F.toByte(), 0x17.toByte(),
            0x37.toByte(), 0x01.toByte(), 0x04.toByte(), 0x00.toByte(),
            0xF7.toByte()
        )
        assertArrayEquals(expectedLayer, YamahaSysExEncoder.setPartReceiveChannel(1, 0))

        // Part 16 (index 15) Receive Channel: Off (Data = 0x7F)
        val expectedOff = byteArrayOf(
            0xF0.toByte(), 0x43.toByte(), 0x10.toByte(), 0x7F.toByte(), 0x17.toByte(),
            0x37.toByte(), 0x0F.toByte(), 0x04.toByte(), 0x7F.toByte(),
            0xF7.toByte()
        )
        assertArrayEquals(expectedOff, YamahaSysExEncoder.setPartReceiveChannel(15, 127))
    }

    @Test
    fun testSafetyFlushMessages() {
        val flushCh1 = YamahaSysExEncoder.createChannelFlushMessages(0)
        assertEquals(4, flushCh1.size)
        // CC 64 Sustain = 0
        assertArrayEquals(byteArrayOf(0xB0.toByte(), 64.toByte(), 0x00.toByte()), flushCh1[0])
        // CC 123 All Notes Off
        assertArrayEquals(byteArrayOf(0xB0.toByte(), 123.toByte(), 0x00.toByte()), flushCh1[1])
        // CC 120 All Sound Off
        assertArrayEquals(byteArrayOf(0xB0.toByte(), 120.toByte(), 0x00.toByte()), flushCh1[2])
        // CC 121 Reset All Controllers
        assertArrayEquals(byteArrayOf(0xB0.toByte(), 121.toByte(), 0x00.toByte()), flushCh1[3])
    }

    @Test
    fun testSafeReceiveChannelChangeSequence() {
        // Part 2 (index 1) moving from Ch 2 (index 1) to Ch 1 (index 0 - Layer)
        val sequence = YamahaSysExEncoder.createSafeReceiveChannelChangeSequence(1, 1, 0)
        // 4 messages (old channel flush) + 1 SysEx Receive Channel + 4 messages (target channel flush) = 9
        assertEquals(9, sequence.size)
        // Check old channel CC 64 = 0 on Channel 2
        assertArrayEquals(byteArrayOf(0xB1.toByte(), 64.toByte(), 0x00.toByte()), sequence[0])
        // Check SysEx Receive Channel on Part 2 set to Channel 1 (0x00)
        assertArrayEquals(
            byteArrayOf(
                0xF0.toByte(), 0x43.toByte(), 0x10.toByte(), 0x7F.toByte(), 0x17.toByte(),
                0x37.toByte(), 0x01.toByte(), 0x04.toByte(), 0x00.toByte(),
                0xF7.toByte()
            ),
            sequence[4]
        )
        // Check target channel CC 64 = 0 on Channel 1
        assertArrayEquals(byteArrayOf(0xB0.toByte(), 64.toByte(), 0x00.toByte()), sequence[5])
    }

    @Test
    fun testCreateControlChangeMessage() {
        // Channel 1 (index 0), CC 1 (Modulation), value 127
        val modMax = YamahaSysExEncoder.createControlChangeMessage(0, 1, 127)
        assertArrayEquals(byteArrayOf(0xB0.toByte(), 0x01.toByte(), 0x7F.toByte()), modMax)

        // Channel 2 (index 1), CC 1 (Modulation), value 0
        val modZero = YamahaSysExEncoder.createControlChangeMessage(1, 1, 0)
        assertArrayEquals(byteArrayOf(0xB1.toByte(), 0x01.toByte(), 0x00.toByte()), modZero)

        // Channel 16 (index 15), CC 1 (Modulation), value 64
        val modCh16 = YamahaSysExEncoder.createControlChangeMessage(15, 1, 64)
        assertArrayEquals(byteArrayOf(0xBF.toByte(), 0x01.toByte(), 0x40.toByte()), modCh16)
    }

    @Test
    fun testPartModulationReceiveSwitchSysEx() {
        // Part 1 (index 0) Modulation Receive Switch ON (Data = 0x01)
        val expectedOnPart1 = byteArrayOf(
            0xF0.toByte(), 0x43.toByte(), 0x10.toByte(), 0x7F.toByte(), 0x17.toByte(),
            0x37.toByte(), 0x00.toByte(), 0x38.toByte(), 0x01.toByte(),
            0xF7.toByte()
        )
        assertArrayEquals(expectedOnPart1, YamahaSysExEncoder.setPartModulationReceiveSwitch(0, true))

        // Part 1 (index 0) Modulation Receive Switch OFF (Data = 0x00)
        val expectedOffPart1 = byteArrayOf(
            0xF0.toByte(), 0x43.toByte(), 0x10.toByte(), 0x7F.toByte(), 0x17.toByte(),
            0x37.toByte(), 0x00.toByte(), 0x38.toByte(), 0x00.toByte(),
            0xF7.toByte()
        )
        assertArrayEquals(expectedOffPart1, YamahaSysExEncoder.setPartModulationReceiveSwitch(0, false))

        // Part 2 (index 1) Modulation Receive Switch OFF (Data = 0x00)
        val expectedOffPart2 = byteArrayOf(
            0xF0.toByte(), 0x43.toByte(), 0x10.toByte(), 0x7F.toByte(), 0x17.toByte(),
            0x37.toByte(), 0x01.toByte(), 0x38.toByte(), 0x00.toByte(),
            0xF7.toByte()
        )
        assertArrayEquals(expectedOffPart2, YamahaSysExEncoder.setPartModulationReceiveSwitch(1, false))
    }

    @Test
    fun testPartVolumeNativeAndSustainReceiveSwitchSysEx() {
        // Native Part 1 Volume = 100 (0x64): F0 43 10 7F 17 37 00 00 00 64 F7
        val expectedNativePart1 = byteArrayOf(
            0xF0.toByte(), 0x43.toByte(), 0x10.toByte(), 0x7F.toByte(), 0x17.toByte(),
            0x37.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x64.toByte(),
            0xF7.toByte()
        )
        assertArrayEquals(expectedNativePart1, YamahaSysExEncoder.setPartVolumeNative(0, 100))

        // Native Part 3 Volume = 80 (0x50): F0 43 10 7F 17 37 02 00 00 50 F7 (verifying Part 3 base-0 = 0x02)
        val expectedNativePart3 = byteArrayOf(
            0xF0.toByte(), 0x43.toByte(), 0x10.toByte(), 0x7F.toByte(), 0x17.toByte(),
            0x37.toByte(), 0x02.toByte(), 0x00.toByte(), 0x00.toByte(), 0x50.toByte(),
            0xF7.toByte()
        )
        assertArrayEquals(expectedNativePart3, YamahaSysExEncoder.setPartVolumeNative(2, 80))

        // Receive Sustain Switch ON (0x01) on Part 1 (0x00): F0 43 10 7F 17 37 00 3A 01 F7
        val expectedSustainOnPart1 = byteArrayOf(
            0xF0.toByte(), 0x43.toByte(), 0x10.toByte(), 0x7F.toByte(), 0x17.toByte(),
            0x37.toByte(), 0x00.toByte(), 0x3A.toByte(), 0x01.toByte(),
            0xF7.toByte()
        )
        assertArrayEquals(expectedSustainOnPart1, YamahaSysExEncoder.setPartSustainReceiveSwitch(0, true))

        // Receive Sustain Switch ON (0x01) on Part 3 (0x02): F0 43 10 7F 17 37 02 3A 01 F7
        val expectedSustainOnPart3 = byteArrayOf(
            0xF0.toByte(), 0x43.toByte(), 0x10.toByte(), 0x7F.toByte(), 0x17.toByte(),
            0x37.toByte(), 0x02.toByte(), 0x3A.toByte(), 0x01.toByte(),
            0xF7.toByte()
        )
        assertArrayEquals(expectedSustainOnPart3, YamahaSysExEncoder.setPartSustainReceiveSwitch(2, true))
    }

    @Test
    fun testPartExpressionReceiveSwitchSysEx() {
        // Part 1 (index 0, Piano) Receive Expression OFF (0x00)
        val expectedOffPart1 = byteArrayOf(
            0xF0.toByte(), 0x43.toByte(), 0x10.toByte(), 0x7F.toByte(), 0x17.toByte(),
            0x37.toByte(), 0x00.toByte(), 0x3B.toByte(), 0x00.toByte(),
            0xF7.toByte()
        )
        assertArrayEquals(expectedOffPart1, YamahaSysExEncoder.setPartExpressionReceiveSwitch(0, false))

        // Part 2 (index 1, Layer Strings) Receive Expression ON (0x01)
        val expectedOnPart2 = byteArrayOf(
            0xF0.toByte(), 0x43.toByte(), 0x10.toByte(), 0x7F.toByte(), 0x17.toByte(),
            0x37.toByte(), 0x01.toByte(), 0x3B.toByte(), 0x01.toByte(),
            0xF7.toByte()
        )
        assertArrayEquals(expectedOnPart2, YamahaSysExEncoder.setPartExpressionReceiveSwitch(1, true))
    }

    @Test
    fun testInsertionPartSwitchSysEx() {
        // Test Part 1 enabled -> bit 15 set -> 0x8000 -> nibbles 8, 0, 0, 0
        // Address 36 00 4C
        val msgPart1 = YamahaSysExEncoder.createInsertionPartSwitchMessage(setOf(0))
        val expectedPart1 = byteArrayOf(
            0xF0.toByte(), 0x43.toByte(), 0x10.toByte(), 0x7F.toByte(), 0x17.toByte(),
            0x36.toByte(), 0x00.toByte(), 0x4C.toByte(),
            0x08.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0xF7.toByte()
        )
        assertArrayEquals(expectedPart1, msgPart1)

        // Test Part 1 + Part 2 enabled -> bits 15 and 14 set -> 0xC000 -> nibbles 0x0C, 0, 0, 0
        val msgPart1And2 = YamahaSysExEncoder.createInsertionPartSwitchMessage(setOf(0, 1))
        val expectedPart1And2 = byteArrayOf(
            0xF0.toByte(), 0x43.toByte(), 0x10.toByte(), 0x7F.toByte(), 0x17.toByte(),
            0x36.toByte(), 0x00.toByte(), 0x4C.toByte(),
            0x0C.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0xF7.toByte()
        )
        assertArrayEquals(expectedPart1And2, msgPart1And2)
    }

    @Test
    fun testCompressorParametersSysEx() {
        // Part 1 (index 0) Threshold = 72 (0x48)
        val msgThresh = YamahaSysExEncoder.setPartCompressorThreshold(0, 72)
        val expectedThresh = byteArrayOf(
            0xF0.toByte(), 0x43.toByte(), 0x10.toByte(), 0x7F.toByte(), 0x17.toByte(),
            0x37.toByte(), 0x00.toByte(), 0x30.toByte(), 0x48.toByte(),
            0xF7.toByte()
        )
        assertArrayEquals(expectedThresh, msgThresh)

        // Part 2 (index 1) Output Gain = 64 (0x40)
        val msgGain = YamahaSysExEncoder.setPartCompressorOutputGain(1, 64)
        val expectedGain = byteArrayOf(
            0xF0.toByte(), 0x43.toByte(), 0x10.toByte(), 0x7F.toByte(), 0x17.toByte(),
            0x37.toByte(), 0x01.toByte(), 0x31.toByte(), 0x40.toByte(),
            0xF7.toByte()
        )
        assertArrayEquals(expectedGain, msgGain)

        // Part 1 (index 0) Ratio = 3 (0x03)
        val msgRatio = YamahaSysExEncoder.setPartCompressorRatio(0, 3)
        val expectedRatio = byteArrayOf(
            0xF0.toByte(), 0x43.toByte(), 0x10.toByte(), 0x7F.toByte(), 0x17.toByte(),
            0x37.toByte(), 0x00.toByte(), 0x32.toByte(), 0x03.toByte(),
            0xF7.toByte()
        )
        assertArrayEquals(expectedRatio, msgRatio)

        // Part 1 (index 0) Attack = 19 (0x13)
        val msgAttack = YamahaSysExEncoder.setPartCompressorAttack(0, 19)
        val expectedAttack = byteArrayOf(
            0xF0.toByte(), 0x43.toByte(), 0x10.toByte(), 0x7F.toByte(), 0x17.toByte(),
            0x37.toByte(), 0x00.toByte(), 0x33.toByte(), 0x13.toByte(),
            0xF7.toByte()
        )
        assertArrayEquals(expectedAttack, msgAttack)

        // Part 1 (index 0) Release = 4 (0x04)
        val msgRelease = YamahaSysExEncoder.setPartCompressorRelease(0, 4)
        val expectedRelease = byteArrayOf(
            0xF0.toByte(), 0x43.toByte(), 0x10.toByte(), 0x7F.toByte(), 0x17.toByte(),
            0x37.toByte(), 0x00.toByte(), 0x34.toByte(), 0x04.toByte(),
            0xF7.toByte()
        )
        assertArrayEquals(expectedRelease, msgRelease)
    }

    @Test
    fun testCompressorFormatting() {
        assertEquals("3.0:1", YamahaSysExEncoder.formatCompressorRatio(3))
        assertEquals("40 ms", YamahaSysExEncoder.formatCompressorAttackMs(19))
        assertEquals("45 ms", YamahaSysExEncoder.formatCompressorReleaseMs(4))
        assertEquals("+0.0 dB", YamahaSysExEncoder.formatCompressorOutputGainDb(64))
    }
}
