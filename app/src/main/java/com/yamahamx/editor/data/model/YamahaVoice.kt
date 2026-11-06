package com.yamahamx.editor.data.model

/**
 * Data model representing a Yamaha MX Voice (Preset / User sound).
 *
 * @param id Unique identifier
 * @param name Name of the Voice (e.g., "Full Concert Grand", "DX Crisp", "Vintage'74")
 * @param category Main Category code (e.g., "AP", "KB", "ORG", "GTR", "BAS", "STR", "BRS", "WND", "LD", "PAD", "CMP", "CP", "DR", "ETH", "SFX", "MFX")
 * @param subCategory Subcategory or descriptive tag (e.g., "Acoustic Piano", "Electric Piano", "Drawbar")
 * @param msb Bank Select MSB (usually 63 for Normal Preset voices, 63 for Drum Preset)
 * @param lsb Bank Select LSB (0..7 for Preset 1..8, 32 for Drum Preset, 40 for GM, etc.)
 * @param programChange Program Change / Number (0..127 corresponding to 1..128)
 */
data class YamahaVoice(
    val id: Int,
    val name: String,
    val category: String,
    val subCategory: String = "",
    val msb: Int = 63,
    val lsb: Int = 0,
    val programChange: Int = 0
) {
    val displayProgramNumber: Int get() = programChange + 1

    val bankName: String
        get() = when {
            msb == 63 && lsb in 0..7 -> "PRE${lsb + 1}"
            msb == 63 && lsb == 8 -> "USER"
            msb == 63 && lsb == 32 -> "DRUM"
            msb == 0 && lsb == 0 -> "GM"
            else -> "MSB:$msb LSB:$lsb"
        }

    val fullDisplayName: String
        get() = "$bankName: ${displayProgramNumber.toString().padStart(3, '0')} $name"

    val categoryDisplayName: String
        get() = when (category.uppercase()) {
            "AP" -> "Acoustic Piano"
            "KB" -> "Keyboard / EP"
            "ORG" -> "Organ"
            "GTR" -> "Guitar"
            "BAS" -> "Bass"
            "STR" -> "Strings"
            "BRS" -> "Brass"
            "WND" -> "Woodwind / Reed"
            "LD" -> "Synth Lead"
            "PAD" -> "Pad / Choir"
            "CMP" -> "Synth Comp"
            "CP" -> "Chromatic Perc"
            "DR" -> "Drum / Percussion"
            "ETH" -> "Ethnic"
            "SFX" -> "Sound FX"
            "MFX" -> "Musical FX"
            else -> category
        }
}
