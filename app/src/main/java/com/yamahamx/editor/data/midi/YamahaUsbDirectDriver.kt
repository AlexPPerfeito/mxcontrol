package com.yamahamx.editor.data.midi

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.util.Log

/**
 * Direct USB Host Driver for Yamaha MX V1 synthesizers (Vendor ID 0x0499).
 * Communicates directly with USB Bulk IN / Bulk OUT endpoints using user-space USB Host API,
 * bypassing Android native MidiManager limitations on legacy/proprietary USB descriptors.
 */
class YamahaUsbDirectDriver(
    private val usbManager: UsbManager,
    val device: UsbDevice
) : MidiTransport {

    companion object {
        private const val TAG = "YamahaUsbDirectDriver"
        const val YAMAHA_VENDOR_ID = 0x0499 // 1177
    }

    private var connection: UsbDeviceConnection? = null
    private var midiInterface: UsbInterface? = null
    private var endpointIn: UsbEndpoint? = null
    private var endpointOut: UsbEndpoint? = null

    private var readerThread: Thread? = null
    @Volatile
    private var isReading = false
    private val writeLock = Any()

    private var receiverCallback: ((ByteArray) -> Unit)? = null
    private var onDisconnectCallback: (() -> Unit)? = null

    fun setOnDisconnectCallback(callback: () -> Unit) {
        this.onDisconnectCallback = callback
    }

    override fun connect(): Boolean {
        disconnect()

        val endpoints = findMidiEndpoints(device)
        if (endpoints == null) {
            Log.e(TAG, "No valid Bulk IN and Bulk OUT endpoints found on Yamaha device: ${device.deviceName}")
            return false
        }

        val conn = usbManager.openDevice(device)
        if (conn == null) {
            Log.e(TAG, "usbManager.openDevice returned null for ${device.deviceName}. Permission granted: ${usbManager.hasPermission(device)}")
            return false
        }

        if (!conn.claimInterface(endpoints.usbInterface, true)) {
            Log.e(TAG, "Failed to claim USB interface ${endpoints.usbInterface.id} for ${device.deviceName}")
            conn.close()
            return false
        }

        try {
            conn.setInterface(endpoints.usbInterface)
        } catch (e: Exception) {
            Log.w(TAG, "setInterface notice for ${device.deviceName}: ${e.message}")
        }

        connection = conn
        midiInterface = endpoints.usbInterface
        endpointIn = endpoints.endpointIn
        endpointOut = endpoints.endpointOut
        isReading = true

        startReaderThread()
        Log.i(TAG, "YamahaUsbDirectDriver successfully connected to ${getDeviceName()}")
        return true
    }

    private fun startReaderThread() {
        val inEp = endpointIn ?: return
        val conn = connection ?: return

        readerThread = Thread({
            val maxPacket = inEp.maxPacketSize
            val bufferSize = if (maxPacket > 0) maxPacket else 64
            val buffer = ByteArray(bufferSize)
            var consecutiveFastErrors = 0

            while (isReading) {
                try {
                    val startNs = System.nanoTime()
                    val bytesRead = conn.bulkTransfer(inEp, buffer, buffer.size, 250)
                    if (bytesRead > 0) {
                        consecutiveFastErrors = 0
                        val midiBytes = UsbMidiPacketConverter.decodeUsbMidiPackets(buffer, bytesRead)
                        if (midiBytes.isNotEmpty()) {
                            receiverCallback?.invoke(midiBytes)
                        }
                    } else if (bytesRead < 0) {
                        if (!isReading) break
                        if (conn.fileDescriptor == -1) {
                            Log.w(TAG, "USB file descriptor invalid (-1), terminating reader thread")
                            break
                        }
                        val elapsedMs = (System.nanoTime() - startNs) / 1_000_000L
                        if (elapsedMs < 15L) {
                            consecutiveFastErrors++
                            if (consecutiveFastErrors >= 10) {
                                consecutiveFastErrors = 0
                                try {
                                    midiInterface?.let { iface -> conn.setInterface(iface) }
                                } catch (e: Throwable) {
                                    Log.w(TAG, "USB IN endpoint recovery notice: ${e.message}")
                                }
                                try {
                                    Thread.sleep(10L)
                                } catch (_: InterruptedException) {
                                    if (!isReading) break
                                }
                            } else {
                                try {
                                    Thread.sleep(2L)
                                } catch (_: InterruptedException) {
                                    if (!isReading) break
                                }
                            }
                        } else {
                            consecutiveFastErrors = 0
                        }
                    }
                } catch (e: Throwable) {
                    if (!isReading) break
                    Log.w(TAG, "Defensive catch in USB reader thread (continuing loop): ${e.message}", e)
                    try {
                        Thread.sleep(5L)
                    } catch (_: InterruptedException) {
                        if (!isReading) break
                    }
                }
            }

            if (isReading) {
                isReading = false
                try {
                    onDisconnectCallback?.invoke()
                } catch (e: Throwable) {
                    Log.w(TAG, "Error in onDisconnectCallback: ${e.message}", e)
                }
            }
        }, "YamahaUsbReader-${device.deviceId}").apply {
            isDaemon = true
            start()
        }
    }

    override fun disconnect() {
        isReading = false
        try {
            readerThread?.interrupt()
        } catch (_: Exception) {}
        readerThread = null

        try {
            midiInterface?.let { iface ->
                connection?.releaseInterface(iface)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing USB interface", e)
        }
        midiInterface = null

        try {
            connection?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing USB device connection", e)
        }
        connection = null
        endpointIn = null
        endpointOut = null
    }

    override fun sendBytes(bytes: ByteArray): Boolean {
        return sendBytes(bytes, 0, bytes.size)
    }

    override fun sendBytes(bytes: ByteArray, offset: Int, count: Int): Boolean {
        val conn = connection ?: return false
        val outEp = endpointOut ?: return false
        if (bytes.isEmpty() || count <= 0 || offset < 0 || offset >= bytes.size) return false
        val safeCount = count.coerceAtMost(bytes.size - offset)
        if (safeCount <= 0) return false
        val slice = if (offset == 0 && safeCount == bytes.size) bytes else bytes.copyOfRange(offset, offset + safeCount)

        return try {
            val usbPackets = UsbMidiPacketConverter.encodeToUsbMidiPackets(slice)
            if (usbPackets.isEmpty()) return true

            synchronized(writeLock) {
                val result = conn.bulkTransfer(outEp, usbPackets, usbPackets.size, 1000)
                result >= 0
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Defensive catch in sendBytes: ${e.message}", e)
            false
        }
    }

    override fun registerReceiver(receiver: (ByteArray) -> Unit) {
        this.receiverCallback = receiver
    }

    override fun isConnected(): Boolean {
        return isReading && connection != null && (endpointOut != null || endpointIn != null)
    }

    override fun getDeviceName(): String {
        return try {
            val prod = (device as UsbDevice?)?.productName?.trim()
            val name = if (!prod.isNullOrBlank()) prod else "Yamaha MX Series"
            "$name (Modo USB Direto)"
        } catch (_: Throwable) {
            "Yamaha MX Series (Modo USB Direto)"
        }
    }

    private data class Endpoints(
        val usbInterface: UsbInterface,
        val endpointIn: UsbEndpoint,
        val endpointOut: UsbEndpoint
    )

    private fun findMidiEndpoints(dev: UsbDevice?): Endpoints? {
        if (dev == null) return null
        val ifaceCount = try { dev.interfaceCount } catch (_: Throwable) { 0 }

        // Priority 1: Audio Class (1), Subclass (3) MIDStreaming
        for (i in 0 until ifaceCount) {
            val iface = try { dev.getInterface(i) } catch (_: Throwable) { null } ?: continue
            if (iface.interfaceClass == UsbConstants.USB_CLASS_AUDIO && iface.interfaceSubclass == 3) {
                val epIn = findEndpoint(iface, UsbConstants.USB_DIR_IN)
                val epOut = findEndpoint(iface, UsbConstants.USB_DIR_OUT)
                if (epIn != null && epOut != null) {
                    Log.d(TAG, "Discovered MIDI endpoints via Audio Class 1 Subclass 3 on interface $i")
                    return Endpoints(iface, epIn, epOut)
                }
            }
        }

        // Priority 2: Yamaha Vendor Specific Class (0xFF), Subclass (3) MIDStreaming
        for (i in 0 until ifaceCount) {
            val iface = try { dev.getInterface(i) } catch (_: Throwable) { null } ?: continue
            if (iface.interfaceClass == UsbConstants.USB_CLASS_VENDOR_SPEC && iface.interfaceSubclass == 3) {
                val epIn = findEndpoint(iface, UsbConstants.USB_DIR_IN)
                val epOut = findEndpoint(iface, UsbConstants.USB_DIR_OUT)
                if (epIn != null && epOut != null) {
                    Log.d(TAG, "Discovered MIDI endpoints via Vendor Class 0xFF Subclass 3 on interface $i")
                    return Endpoints(iface, epIn, epOut)
                }
            }
        }

        // Priority 3: Any interface with Bulk/Interrupt IN and OUT (Yamaha proprietary Vendor Class 0xFF or alternate)
        for (i in 0 until ifaceCount) {
            val iface = try { dev.getInterface(i) } catch (_: Throwable) { null } ?: continue
            val epIn = findEndpoint(iface, UsbConstants.USB_DIR_IN)
            val epOut = findEndpoint(iface, UsbConstants.USB_DIR_OUT)
            if (epIn != null && epOut != null) {
                Log.d(TAG, "Discovered MIDI endpoints via Bulk/Int IN/OUT fallback on interface $i (class: ${iface.interfaceClass}, subclass: ${iface.interfaceSubclass})")
                return Endpoints(iface, epIn, epOut)
            }
        }

        return null
    }

    private fun findEndpoint(iface: UsbInterface?, direction: Int): UsbEndpoint? {
        if (iface == null) return null
        val epCount = try { iface.endpointCount } catch (_: Throwable) { 0 }
        for (i in 0 until epCount) {
            val ep = try { iface.getEndpoint(i) } catch (_: Throwable) { null } ?: continue
            if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK && ep.direction == direction) {
                return ep
            }
        }
        for (i in 0 until epCount) {
            val ep = try { iface.getEndpoint(i) } catch (_: Throwable) { null } ?: continue
            if (ep.type == UsbConstants.USB_ENDPOINT_XFER_INT && ep.direction == direction) {
                return ep
            }
        }
        return null
    }
}
