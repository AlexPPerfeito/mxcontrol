package com.yamahamx.editor.data.model

/**
 * Utility for converting MIDI note numbers (0..127) to musical note names following
 * the International Standard (Scientific Pitch Notation, where Middle C / MIDI 60 = C4).
 */
object NoteHelper {
    private val NOTE_NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    /**
     * Converts MIDI note number (0..127) to string representation like "C4", "F#4", "G9", "C-1".
     * Follows International Standard (Scientific Pitch Notation: Middle C / MIDI 60 = C4, octave = (note / 12) - 1).
     *
     * Reference points:
     * - MIDI 0   -> C-1
     * - MIDI 12  -> C0
     * - MIDI 21  -> A0 (Lowest key on 88-key piano / MX88)
     * - MIDI 36  -> C2 (Lowest physical key on MX61 without octave shift)
     * - MIDI 48  -> C3
     * - MIDI 60  -> C4 (Middle C)
     * - MIDI 69  -> A4 (440 Hz Concert Pitch)
     * - MIDI 96  -> C7 (Highest physical key on MX61 without octave shift)
     * - MIDI 108 -> C8 (Highest key on 88-key piano / MX88)
     * - MIDI 127 -> G9 (Upper MIDI limit)
     */
    fun noteNumberToName(noteNumber: Int): String {
        val clamped = noteNumber.coerceIn(0, 127)
        val noteIndex = clamped % 12
        val octave = (clamped / 12) - 1
        return "${NOTE_NAMES[noteIndex]}$octave"
    }

    /**
     * Formats note transposition (offset from center 64, range 40..88 -> -24..+24 semitones).
     */
    fun formatNoteShift(rawShift: Int): String {
        val semitones = rawShift - 64
        return when {
            semitones > 0 -> "+$semitones st"
            semitones < 0 -> "$semitones st"
            else -> "0 st"
        }
    }

    /**
     * Formats Pan value (1..127, center = 64) to display like L63, C, R63.
     */
    fun formatPan(pan: Int): String {
        val clamped = pan.coerceIn(1, 127)
        val diff = clamped - 64
        return when {
            diff < 0 -> "L${-diff}"
            diff > 0 -> "R$diff"
            else -> "C"
        }
    }

    /**
     * Formats Volume / Cutoff / Send values (0..127).
     */
    fun formatLevel(level: Int): String = level.coerceIn(0, 127).toString()
}
