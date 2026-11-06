package com.yamahamx.editor.data.model

/**
 * State representing Multi Common settings of the Yamaha MX synthesizer.
 */
data class MultiCommonState(
    val part12Link: Boolean = false, // AddrHigh: 0x36, AddrMid: 0x00, AddrLow: 0x44 (0=Off, 1=On)
    val masterVolume: Int = 127,
    val performanceName: String = "Init Performance"
)
