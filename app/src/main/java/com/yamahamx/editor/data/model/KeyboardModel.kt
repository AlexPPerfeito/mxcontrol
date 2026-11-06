package com.yamahamx.editor.data.model

/**
 * Modelos de teclado físico Yamaha MX e faixa completa MIDI.
 * Utilizado para calcular geometricamente os limites físicos das teclas
 * e as divisões de Split (Lower = 1ª metade / Upper = 2ª metade).
 * Notação Universal de Piano: Middle C = C4 (MIDI 60).
 */
enum class KeyboardModel(
    val displayName: String,
    val totalKeys: Int,
    val fullLow: Int,
    val fullHigh: Int,
    val lowerLow: Int,
    val lowerHigh: Int,
    val upperLow: Int,
    val upperHigh: Int
) {
    MX49(
        displayName = "MX49",
        totalKeys = 49,
        fullLow = 48,
        fullHigh = 96,
        lowerLow = 48,
        lowerHigh = 71,
        upperLow = 72,
        upperHigh = 96
    ),
    MX61(
        displayName = "MX61",
        totalKeys = 61,
        fullLow = 36,
        fullHigh = 96,
        lowerLow = 36,
        lowerHigh = 65,
        upperLow = 66,
        upperHigh = 96
    ),
    MX88(
        displayName = "MX88",
        totalKeys = 88,
        fullLow = 21,
        fullHigh = 108,
        lowerLow = 21,
        lowerHigh = 64,
        upperLow = 65,
        upperHigh = 108
    ),
    FULL_MIDI(
        displayName = "Full MIDI",
        totalKeys = 128,
        fullLow = 0,
        fullHigh = 127,
        lowerLow = 0,
        lowerHigh = 59,
        upperLow = 60,
        upperHigh = 127
    );

    val fullLabel: String
        get() = "Full (${NoteHelper.noteNumberToName(fullLow)}..${NoteHelper.noteNumberToName(fullHigh)})"

    val lowerLabel: String
        get() = "Lower (${NoteHelper.noteNumberToName(lowerLow)}..${NoteHelper.noteNumberToName(lowerHigh)})"

    val upperLabel: String
        get() = "Upper (${NoteHelper.noteNumberToName(upperLow)}..${NoteHelper.noteNumberToName(upperHigh)})"
}
