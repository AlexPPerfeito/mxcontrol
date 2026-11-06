package com.yamahamx.editor.data.model

/**
 * Entity representing a User Voice slot (US001 to US128) on the Yamaha MX (MSB 63, LSB 8).
 *
 * @param slotIndex 0-indexed slot (0..127 corresponding to US001..US128)
 * @param customName User-defined or default factory name
 * @param msb Bank Select MSB (always 63 for User Normal Voices)
 * @param lsb Bank Select LSB (always 8 for User Normal Voices)
 * @param programChange Program Change index (0..127, matching slotIndex)
 * @param categoryTag Category tag ("USR")
 */
data class UserVoiceEntity(
    val slotIndex: Int,
    val customName: String,
    val msb: Int = 63,
    val lsb: Int = 8,
    val programChange: Int = slotIndex,
    val categoryTag: String = "USR"
) {
    val displaySlotNumber: Int get() = slotIndex + 1
    val slotCode: String get() = "US${displaySlotNumber.toString().padStart(3, '0')}"

    /**
     * Converts this User Voice entity into a [YamahaVoice] model.
     */
    fun toYamahaVoice(): YamahaVoice {
        return YamahaVoice(
            id = 2000 + slotIndex,
            name = customName,
            category = categoryTag,
            subCategory = slotCode,
            msb = msb,
            lsb = lsb,
            programChange = programChange
        )
    }
}
