package com.yamahamx.editor.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class NoteHelperTest {

    @Test
    fun testNoteNumberToName() {
        // International Scientific Pitch Notation (Middle C = C4, octave = (note / 12) - 1)
        assertEquals("C-1", NoteHelper.noteNumberToName(0))   // Lower MIDI limit
        assertEquals("C0", NoteHelper.noteNumberToName(12))
        assertEquals("A0", NoteHelper.noteNumberToName(21))   // First key of 88-key piano / MX88
        assertEquals("C1", NoteHelper.noteNumberToName(24))
        assertEquals("C2", NoteHelper.noteNumberToName(36))   // First physical key of MX61 without octave shift
        assertEquals("C3", NoteHelper.noteNumberToName(48))
        assertEquals("B3", NoteHelper.noteNumberToName(59))   // End of standard lower split
        assertEquals("C4", NoteHelper.noteNumberToName(60))   // Middle C (International standard)
        assertEquals("A4", NoteHelper.noteNumberToName(69))   // 440 Hz Concert Pitch
        assertEquals("C7", NoteHelper.noteNumberToName(96))   // Last physical key of MX61 without octave shift
        assertEquals("C8", NoteHelper.noteNumberToName(108))  // Last key of 88-key piano / MX88
        assertEquals("G9", NoteHelper.noteNumberToName(127))  // Upper MIDI limit
    }

    @Test
    fun testFormatPan() {
        assertEquals("C", NoteHelper.formatPan(64))
        assertEquals("L63", NoteHelper.formatPan(1))
        assertEquals("R63", NoteHelper.formatPan(127))
        assertEquals("L32", NoteHelper.formatPan(32))
        assertEquals("R32", NoteHelper.formatPan(96))
    }

    @Test
    fun testFormatNoteShift() {
        assertEquals("0 st", NoteHelper.formatNoteShift(64))
        assertEquals("-24 st", NoteHelper.formatNoteShift(40))
        assertEquals("+24 st", NoteHelper.formatNoteShift(88))
        assertEquals("+12 st", NoteHelper.formatNoteShift(76))
        assertEquals("-12 st", NoteHelper.formatNoteShift(52))
    }
}
