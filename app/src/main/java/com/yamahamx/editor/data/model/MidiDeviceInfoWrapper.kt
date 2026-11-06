package com.yamahamx.editor.data.model

import android.hardware.usb.UsbDevice
import android.media.midi.MidiDeviceInfo

/**
 * Wrapper for MidiDeviceInfo to be UI friendly.
 * Supports both Android Native MIDI devices and Direct USB Host devices (Fallback).
 */
data class MidiDeviceInfoWrapper(
    val id: Int = 0,
    val name: String = "",
    val manufacturer: String = "",
    val isYamaha: Boolean = false,
    val inputPortCount: Int = 0,
    val outputPortCount: Int = 0,
    val rawInfo: MidiDeviceInfo? = null,
    val usbDevice: UsbDevice? = null,
    val isUsbDirect: Boolean = false,
    val isBluetooth: Boolean = false,
    val bluetoothAddress: String? = null,
    val isConnected: Boolean = false
)

typealias MidiDeviceUiModel = MidiDeviceInfoWrapper
