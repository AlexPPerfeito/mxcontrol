package com.yamahamx.editor.data.midi

/**
 * Common abstraction interface for MIDI transports in MX Control.
 * Unifies Android Native MidiManager (Class-Compliant, MX V2)
 * and Direct USB Host Bulk Driver (Yamaha proprietary, MX V1).
 */
interface MidiTransport {
    /**
     * Attempts to open and establish the MIDI connection.
     * @return true if successfully opened and ready for I/O.
     */
    fun connect(): Boolean

    /**
     * Closes ports, releases interfaces and tears down the connection.
     */
    fun disconnect()

    /**
     * Transmits raw MIDI or SysEx bytes across the transport.
     * @return true if transmission succeeded.
     */
    fun sendBytes(bytes: ByteArray): Boolean

    /**
     * Transmits a slice of raw MIDI or SysEx bytes across the transport.
     * @return true if transmission succeeded.
     */
    fun sendBytes(bytes: ByteArray, offset: Int, count: Int): Boolean {
        if (bytes.isEmpty() || count <= 0 || offset < 0 || offset >= bytes.size) return false
        val safeCount = count.coerceAtMost(bytes.size - offset)
        if (safeCount <= 0) return false
        val slice = if (offset == 0 && safeCount == bytes.size) bytes else bytes.copyOfRange(offset, offset + safeCount)
        return sendBytes(slice)
    }

    /**
     * Registers a listener to receive raw MIDI bytes arriving from the hardware synth.
     */
    fun registerReceiver(receiver: (ByteArray) -> Unit)

    /**
     * Checks if the transport is actively connected and ready for communication.
     */
    fun isConnected(): Boolean

    /**
     * Returns the human-readable display name of the connected device.
     */
    fun getDeviceName(): String?
}
