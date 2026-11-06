package com.yamahamx.editor.data.model

import android.bluetooth.BluetoothDevice

enum class MidiPortType {
    NONE,
    USB,
    BLUETOOTH
}

/**
 * Representation of a discovered or connected Bluetooth LE MIDI device (e.g. Yamaha MD-BT01, UD-BT01, CME WIDI).
 */
data class BleMidiDevice(
    val name: String,
    val address: String,
    val isConnected: Boolean = false,
    val isConnecting: Boolean = false,
    val rssi: Int = 0,
    val isYamaha: Boolean = false,
    val bluetoothDevice: BluetoothDevice? = null
)
