package com.yamahamx.editor.data.midi

import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiInputPort
import android.media.midi.MidiManager
import android.media.midi.MidiOutputPort
import android.media.midi.MidiReceiver
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * Transport implementation wrapping Android's native android.media.midi.MidiManager.
 * Used for Class-Compliant USB MIDI devices (e.g. Yamaha MX V2, generic MIDI interfaces).
 */
class AndroidMidiTransport(
    private val midiManager: MidiManager,
    val deviceInfo: MidiDeviceInfo,
    private val mainHandler: Handler = Handler(Looper.getMainLooper()),
    private val onConnectedCallback: ((Boolean, String?) -> Unit)? = null,
    private val preOpenedMidiDevice: MidiDevice? = null
) : MidiTransport {

    companion object {
        private const val TAG = "AndroidMidiTransport"
    }

    private var currentMidiDevice: MidiDevice? = null
    private var currentInputPort: MidiInputPort? = null
    private val currentInputPorts = mutableListOf<MidiInputPort>()
    private val currentOutputPorts = mutableListOf<MidiOutputPort>()

    val midiInputPort: MidiInputPort?
        get() = currentInputPort

    val isYamahaDevice: Boolean
        get() = try {
            val info = deviceInfo as MidiDeviceInfo?
            val props = info?.properties
            val name = props?.getString(MidiDeviceInfo.PROPERTY_NAME) ?: ""
            val product = props?.getString(MidiDeviceInfo.PROPERTY_PRODUCT) ?: ""
            val manufacturer = props?.getString(MidiDeviceInfo.PROPERTY_MANUFACTURER) ?: ""
            name.contains("Yamaha", ignoreCase = true) ||
                name.contains("MX49", ignoreCase = true) ||
                name.contains("MX61", ignoreCase = true) ||
                name.contains("MX88", ignoreCase = true) ||
                product.contains("Yamaha", ignoreCase = true) ||
                product.contains("MX49", ignoreCase = true) ||
                product.contains("MX61", ignoreCase = true) ||
                product.contains("MX88", ignoreCase = true) ||
                manufacturer.contains("Yamaha", ignoreCase = true)
        } catch (_: Throwable) {
            false
        }

    private var receiverCallback: ((ByteArray) -> Unit)? = null
    @Volatile
    private var isConnectedState = false
    @Volatile
    private var connectGeneration = 0

    private val midiReceiver = object : MidiReceiver() {
        override fun onSend(msg: ByteArray?, offset: Int, count: Int, timestamp: Long) {
            try {
                if (msg != null && count > 0 && offset >= 0 && offset + count <= msg.size) {
                    val slice = msg.copyOfRange(offset, offset + count)
                    receiverCallback?.invoke(slice)
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Defensive catch in MidiReceiver.onSend: ${e.message}", e)
            }
        }
    }

    // Asynchronous send worker on Dispatchers.IO to prevent Main/UI thread ANRs
    private var sendScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var sendChannel: Channel<ByteArray>? = null
    private var sendWorkerJob: Job? = null

    override fun connect(): Boolean {
        disconnect()
        val generation = ++connectGeneration

        val existingDevice = preOpenedMidiDevice
        if (existingDevice != null) {
            setupOpenedDevice(existingDevice)
            return true
        }

        midiManager.openDevice(deviceInfo, { midiDevice ->
            if (generation != connectGeneration) {
                try {
                    midiDevice?.close()
                } catch (_: Exception) {}
                return@openDevice
            }

            if (midiDevice == null) {
                Log.e(TAG, "Failed to open native MidiDevice: ${getDeviceName()}")
                onConnectedCallback?.invoke(false, "Falha ao abrir dispositivo MIDI nativo")
                return@openDevice
            }

            setupOpenedDevice(midiDevice)
        }, mainHandler)

        return true
    }

    private fun setupOpenedDevice(midiDevice: MidiDevice) {
        try {
            currentMidiDevice = midiDevice

            val inPortCount = (deviceInfo as MidiDeviceInfo?)?.inputPortCount ?: 0
            for (i in 0 until inPortCount) {
                val port = try { midiDevice.openInputPort(i) } catch (_: Throwable) { null }
                if (port != null) {
                    currentInputPorts.add(port)
                    if (currentInputPort == null) {
                        currentInputPort = port
                    }
                    Log.d(TAG, "Opened MidiInputPort index $i on ${getDeviceName()}")
                    // For Yamaha MX, Port 0 is the synthesizer MIDI IN (Port 1 is DAW Remote)
                    if (isYamahaDevice) break
                }
            }

            // Connect MidiReceiver to MidiOutputPort(s):
            // - For Yamaha MX: connect only to primary Port 0 (Port 1 is DAW Remote / Bulk).
            // - For external USB controllers (e.g. Arturia MiniLab 3): open all available output ports
            //   so MIDI notes, Pitch Bend, and CC from any controller port are unified into the listener.
            val outPortCount = (deviceInfo as MidiDeviceInfo?)?.outputPortCount ?: 0
            for (i in 0 until outPortCount) {
                val outPort = try { midiDevice.openOutputPort(i) } catch (_: Throwable) { null }
                if (outPort != null) {
                    outPort.connect(midiReceiver)
                    currentOutputPorts.add(outPort)
                    Log.d(TAG, "Connected MidiReceiver to MidiOutputPort index $i on ${getDeviceName()}")
                    if (isYamahaDevice) break
                } else {
                    Log.w(TAG, "Could not open MidiOutputPort index $i on ${getDeviceName()}")
                }
            }

            if (currentInputPort != null || currentOutputPorts.isNotEmpty()) {
                isConnectedState = true
                if (currentInputPort != null) {
                    startSendWorker()
                }
                Log.i(
                    TAG,
                    "AndroidMidiTransport connected to ${getDeviceName()} " +
                        "(inputPort=${currentInputPort != null}, outputPorts=${currentOutputPorts.size})"
                )
                onConnectedCallback?.invoke(true, null)
            } else {
                Log.e(TAG, "Failed to open MIDI ports on native MidiDevice")
                disconnect()
                onConnectedCallback?.invoke(false, "Erro ao abrir portas MIDI nativas")
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Exception opening native MIDI ports", e)
            disconnect()
            onConnectedCallback?.invoke(false, "Erro na porta MIDI: ${e.message}")
        }
    }

    private fun startSendWorker() {
        stopSendWorker()
        if (!sendScope.isActive) {
            sendScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        }
        val channel = Channel<ByteArray>(Channel.UNLIMITED)
        sendChannel = channel
        sendWorkerJob = sendScope.launch {
            try {
                for (bytes in channel) {
                    if (!isActive) break
                    val port = currentInputPort ?: break
                    try {
                        port.send(bytes, 0, bytes.size)
                    } catch (e: IOException) {
                        // Do NOT break the worker loop on transient socket buffer full (EAGAIN) during bursts
                        Log.w(TAG, "Transient IOException writing to MIDI port in worker: ${e.message}", e)
                        kotlinx.coroutines.delay(5L)
                    } catch (e: Throwable) {
                        Log.w(TAG, "Unexpected error writing to MIDI port in worker: ${e.message}", e)
                    }
                }
            } catch (e: Throwable) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    Log.w(TAG, "Error in sendWorkerJob loop: ${e.message}", e)
                }
            }
        }
    }

    private fun stopSendWorker() {
        try {
            sendChannel?.close()
        } catch (_: Exception) {}
        sendChannel = null

        try {
            sendWorkerJob?.cancel()
        } catch (_: Exception) {}
        sendWorkerJob = null
    }

    override fun disconnect() {
        connectGeneration++
        isConnectedState = false
        stopSendWorker()

        for (port in currentInputPorts) {
            try {
                port.close()
            } catch (_: Exception) {}
        }
        currentInputPorts.clear()
        currentInputPort = null

        for (port in currentOutputPorts) {
            try {
                port.disconnect(midiReceiver)
            } catch (_: Exception) {}
            try {
                port.close()
            } catch (_: Exception) {}
        }
        currentOutputPorts.clear()

        try {
            currentMidiDevice?.close()
        } catch (_: Exception) {}
        currentMidiDevice = null
    }

    override fun sendBytes(bytes: ByteArray): Boolean {
        return sendBytes(bytes, 0, bytes.size)
    }

    override fun sendBytes(bytes: ByteArray, offset: Int, count: Int): Boolean {
        if (!isConnected()) return false
        if (bytes.isEmpty() || count <= 0 || offset < 0 || offset >= bytes.size) return false
        val safeCount = count.coerceAtMost(bytes.size - offset)
        if (safeCount <= 0) return false
        val slice = bytes.copyOfRange(offset, offset + safeCount)

        val channel = sendChannel
        if (channel != null) {
            return try {
                channel.trySend(slice).isSuccess
            } catch (e: ClosedSendChannelException) {
                Log.w(TAG, "Cannot send MIDI bytes: channel closed", e)
                false
            } catch (e: Exception) {
                Log.e(TAG, "Error queuing MIDI bytes", e)
                false
            }
        }

        return try {
            currentInputPort?.send(bytes, offset, safeCount)
            true
        } catch (e: Throwable) {
            Log.w(TAG, "Error writing directly to MidiInputPort: ${e.message}", e)
            false
        }
    }

    override fun registerReceiver(receiver: (ByteArray) -> Unit) {
        this.receiverCallback = receiver
    }

    override fun isConnected(): Boolean {
        return isConnectedState && (currentInputPort != null || currentOutputPorts.isNotEmpty())
    }

    override fun getDeviceName(): String {
        return try {
            val info = deviceInfo as MidiDeviceInfo?
            val props = info?.properties
            props?.getString(MidiDeviceInfo.PROPERTY_NAME)?.takeIf { it.isNotBlank() }
                ?: props?.getString(MidiDeviceInfo.PROPERTY_PRODUCT)?.takeIf { it.isNotBlank() }
                ?: "Dispositivo MIDI USB #${info?.id ?: 0}"
        } catch (_: Throwable) {
            "Dispositivo MIDI USB"
        }
    }
}
