package com.yamahamx.editor.data.midi

import android.Manifest
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat
import com.yamahamx.editor.R
import com.yamahamx.editor.data.model.BleMidiDevice
import com.yamahamx.editor.data.model.MidiDeviceInfoWrapper
import com.yamahamx.editor.data.model.MidiDeviceUiModel
import com.yamahamx.editor.data.model.MidiPortType
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed class MidiVoiceEvent {
    data class NoteOn(val channel: Int, val note: Int, val velocity: Int) : MidiVoiceEvent()
    data class NoteOff(val channel: Int, val note: Int, val velocity: Int = 0) : MidiVoiceEvent()
    data class SustainChange(val channel: Int, val isPressed: Boolean) : MidiVoiceEvent()
    data class AllNotesOff(val channel: Int? = null) : MidiVoiceEvent()
    data class ControlChange(
        val channel: Int,
        val controller: Int,
        val value: Int,
        val fromBluetooth: Boolean = false,
        val fromYamahaHardware: Boolean = false,
        val statusByte: Int = 0xB0 or (channel and 0x0F)
    ) : MidiVoiceEvent()
    data class ProgramChange(
        val channel: Int,
        val program: Int,
        val fromYamahaHardware: Boolean = true
    ) : MidiVoiceEvent()
    data class LayerActivation(
        val enabled: Boolean = true
    ) : MidiVoiceEvent()
}

/**
 * Manages USB MIDI device discovery, connection, and I/O using a unified transport architecture:
 * 1. Primary: Android native android.media.midi.MidiManager for Class-Compliant devices (Yamaha MX V2 & generic).
 * 2. Fallback: Direct user-space USB Host driver (YamahaUsbDirectDriver) for Yamaha MX V1 (Vendor ID 0x0499 / 1177).
 */
class MidiConnectionManager(
    private val context: Context,
    private val coroutineScope: CoroutineScope
) {
    companion object {
        private const val TAG = "MidiConnectionManager"
        const val ACTION_USB_PERMISSION = "com.alexperfeito.mxcontrol.USB_PERMISSION"
        val BLE_MIDI_UUID: UUID = UUID.fromString("03B80E5A-EDE8-4B33-A751-6CE34EC4C700")
        val BLE_MIDI_PARCEL_UUID: ParcelUuid = ParcelUuid(BLE_MIDI_UUID)
    }

    private val usbManager: UsbManager? = context.getSystemService(Context.USB_SERVICE) as? UsbManager

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager

    private val midiManager: MidiManager? =
        if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_MIDI)) {
            context.getSystemService(Context.MIDI_SERVICE) as? MidiManager
        } else {
            null
        }

    private val mainHandler = Handler(Looper.getMainLooper())

    private val usbTransports = ConcurrentHashMap<Int, MidiTransport>()
    private val connectingUsbDeviceIds = ConcurrentHashMap.newKeySet<Int>()

    private var usbTransport: MidiTransport? = null
        get() {
            val yamaha = usbTransports.values.firstOrNull { isYamahaTransport(it) && it.isConnected() }
                ?: usbTransports.values.firstOrNull { isYamahaTransport(it) }
            if (yamaha != null) return yamaha
            if (field != null) return field
            return usbTransports.values.firstOrNull { it.isConnected() } ?: usbTransports.values.firstOrNull()
        }
        set(value) {
            field = value
            if (value == null) {
                usbTransports.clear()
            } else {
                val id = getTransportDeviceId(value)
                usbTransports[id] = value
            }
        }
    private var bleTransport: MidiTransport? = null

    /**
     * Resolves the target MIDI channel (0..15) on the Yamaha MX for incoming messages from external
     * controllers (e.g., Arturia MiniLab 3 via USB Hub, M-VAVE SMK25 or iPad via BLE).
     */
    var externalChannelResolver: ((Int) -> Int)? = null

    /**
     * Optional predicate that checks whether at least one Part on the resolved target channel (0..15)
     * is currently active/audible in the app (volume > 0 && !isMuted && (!anySolo || isSolo)).
     */
    var externalChannelAudibleChecker: ((Int) -> Boolean)? = null

    private fun isYamahaTransport(transport: MidiTransport?): Boolean {
        if (transport == null) return false
        if (transport is YamahaUsbDirectDriver) return true
        if (transport is AndroidMidiTransport) return transport.isYamahaDevice
        val name = try { transport.getDeviceName().orEmpty() } catch (_: Throwable) { "" }
        return name.contains("Yamaha", ignoreCase = true) ||
            name.contains("MX49", ignoreCase = true) ||
            name.contains("MX61", ignoreCase = true) ||
            name.contains("MX88", ignoreCase = true)
    }

    private fun getTransportDeviceId(transport: MidiTransport): Int {
        return when (transport) {
            is AndroidMidiTransport -> (transport.deviceInfo as MidiDeviceInfo?)?.id ?: 0
            is YamahaUsbDirectDriver -> (transport.device as UsbDevice?)?.deviceId ?: -100
            else -> 0
        }
    }

    /**
     * Direct reference to the physical USB MIDI output port (AndroidMidiTransport's MidiInputPort) when connected via native MidiManager.
     */
    val usbMidiOutputPort: android.media.midi.MidiInputPort?
        get() = (usbTransport as? AndroidMidiTransport)?.midiInputPort

    val isUsbConnected: Boolean
        get() = usbTransports.values.any { it.isConnected() } || (usbTransport?.isConnected() == true)

    val isYamahaUsbConnected: Boolean
        get() = usbTransports.values.any { isYamahaTransport(it) && it.isConnected() } ||
            (isYamahaTransport(usbTransport) && usbTransport?.isConnected() == true)

    val isBleConnected: Boolean
        get() = bleTransport?.isConnected() == true

    private var activeTransport: MidiTransport?
        get() = usbTransport ?: bleTransport
        set(value) {
            if (value is AndroidMidiTransport && value.deviceInfo.type == MidiDeviceInfo.TYPE_BLUETOOTH) {
                bleTransport = value
            } else {
                usbTransport = value
            }
        }
    private var fallbackJob: Job? = null
    @Volatile
    private var isConnectingUsb = false
        get() = field || connectingUsbDeviceIds.isNotEmpty()
    @Volatile
    private var isConnectingBle = false
    @Volatile
    private var isConnecting = false
        get() = isConnectingUsb || isConnectingBle
    @Volatile
    private var connectingUsbDeviceId: Int? = null
    @Volatile
    private var connectingBleDeviceId: Int? = null

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isUsbConnectedFlow = MutableStateFlow(false)
    val isUsbConnectedFlow: StateFlow<Boolean> = _isUsbConnectedFlow.asStateFlow()

    private val _isBleConnectedFlow = MutableStateFlow(false)
    val isBleConnectedFlow: StateFlow<Boolean> = _isBleConnectedFlow.asStateFlow()

    private val _activePortType = MutableStateFlow(MidiPortType.NONE)
    val activePortType: StateFlow<MidiPortType> = _activePortType.asStateFlow()

    private val _isBleScanning = MutableStateFlow(false)
    val isBleScanning: StateFlow<Boolean> = _isBleScanning.asStateFlow()

    private val _bleDiscoveredDevices = MutableStateFlow<List<BleMidiDevice>>(emptyList())
    val bleDiscoveredDevices: StateFlow<List<BleMidiDevice>> = _bleDiscoveredDevices.asStateFlow()

    private var bleScanCallback: ScanCallback? = null
    private var bleScanTimeoutJob: Job? = null

    private val _connectedDeviceName = MutableStateFlow<String?>(null)
    val connectedDeviceName: StateFlow<String?> = _connectedDeviceName.asStateFlow()

    private val _availableDevices = MutableStateFlow<List<MidiDeviceUiModel>>(emptyList())
    val availableDevices: StateFlow<List<MidiDeviceUiModel>> = _availableDevices.asStateFlow()

    private val _connectedDevices = MutableStateFlow<List<MidiDeviceUiModel>>(emptyList())
    val connectedDevices: StateFlow<List<MidiDeviceUiModel>> = _connectedDevices.asStateFlow()

    private val _statusMessage = MutableStateFlow(context.getString(R.string.midi_status_initializing))
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    internal fun updateConnectionState() {
        try {
            val activeUsbList = usbTransports.values.filter { it.isConnected() }.toMutableList()
            val primaryUsb = usbTransport
            if (activeUsbList.isEmpty() && primaryUsb != null) {
                activeUsbList.add(primaryUsb)
            }
            val hasUsb = activeUsbList.isNotEmpty()
            val hasBle = bleTransport?.isConnected() == true || bleTransport != null
            _isUsbConnectedFlow.value = usbTransports.values.any { it.isConnected() } || (primaryUsb?.isConnected() == true)
            _isBleConnectedFlow.value = bleTransport?.isConnected() == true
            _isConnected.value = hasUsb || hasBle
            _activePortType.value = when {
                hasUsb && hasBle -> MidiPortType.USB
                hasUsb -> MidiPortType.USB
                hasBle -> MidiPortType.BLUETOOTH
                else -> MidiPortType.NONE
            }
            // Order device names with Yamaha first, followed by external USB controllers (e.g. Arturia MiniLab 3), then BLE
            val sortedUsbNames = activeUsbList
                .sortedByDescending { isYamahaTransport(it) }
                .mapNotNull {
                    try {
                        it.getDeviceName()?.takeIf { n -> n.isNotBlank() }
                    } catch (_: Throwable) {
                        null
                    }
                }
                .distinct()
            val bleName = try { bleTransport?.getDeviceName()?.takeIf { it.isNotBlank() } } catch (_: Throwable) { null }
            val allNames = buildList {
                addAll(sortedUsbNames)
                if (bleName != null) add(bleName)
            }
            _connectedDeviceName.value = if (allNames.isNotEmpty()) allNames.joinToString(" + ") else null
        } catch (e: Throwable) {
            Log.w(TAG, "Defensive catch in updateConnectionState: ${e.message}", e)
        }
    }

    private val _midiActivityChannelFlow = MutableSharedFlow<Int>(extraBufferCapacity = 64)
    val midiActivityChannelFlow: SharedFlow<Int> = _midiActivityChannelFlow.asSharedFlow()

    private val _midiVoiceEventFlow = MutableSharedFlow<MidiVoiceEvent>(extraBufferCapacity = 256)
    val midiVoiceEventFlow: SharedFlow<MidiVoiceEvent> = _midiVoiceEventFlow.asSharedFlow()

    internal val midiStreamParser = MidiInputStreamParser(
        onChannelActivity = { channel -> _midiActivityChannelFlow.tryEmit(channel) },
        onVoiceEvent = { event -> _midiVoiceEventFlow.tryEmit(event) }
    )

    val activeNotesCount: StateFlow<Int> = midiStreamParser.activeNotesCount

    val throttler = MidiThrottler(
        scope = coroutineScope,
        sendIntervalMs = 20L,
        onSend = { bytes -> sendRawBytesInternal(bytes) }
    )

    val usbOnlyThrottler = MidiThrottler(
        scope = coroutineScope,
        sendIntervalMs = 20L,
        onSend = { bytes -> sendToUsbOnly(bytes) }
    )

    private val deviceCallback = object : MidiManager.DeviceCallback() {
        override fun onDeviceAdded(device: MidiDeviceInfo) {
            val devId = (device as MidiDeviceInfo?)?.id ?: 0
            Log.d(TAG, "Native MidiDevice added: $devId")
            scanAndAutoConnect(retryCount = 2)
        }

        override fun onDeviceRemoved(device: MidiDeviceInfo) {
            val removedId = (device as MidiDeviceInfo?)?.id ?: return
            Log.d(TAG, "Native MidiDevice removed: $removedId")
            val bleNativeId = (bleTransport as? AndroidMidiTransport)?.deviceInfo?.id
            if (usbTransports.containsKey(removedId)) {
                disconnectUsbDeviceById(removedId)
                _statusMessage.value = this@MidiConnectionManager.context.getString(R.string.midi_status_disconnected_physically)
            }
            if (bleNativeId == removedId) {
                disconnectInternal(disconnectBle = true, disconnectUsb = false)
                _statusMessage.value = this@MidiConnectionManager.context.getString(R.string.midi_status_disconnected_physically)
            }
            scanDevices()
        }

        override fun onDeviceStatusChanged(status: android.media.midi.MidiDeviceStatus) {
            val devId = status.deviceInfo?.id ?: 0
            Log.d(TAG, "Native MidiDevice status changed: $devId")
        }
    }

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            when (action) {
                ACTION_USB_PERMISSION -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    Log.d(TAG, "USB Permission result: granted=$granted for ${device?.deviceName}")
                    if (granted && device != null) {
                        scanDevices()
                        val hasNativeYamaha = _availableDevices.value.any { !it.isUsbDirect && !it.isBluetooth && it.isYamaha }
                        if (!hasNativeYamaha && isYamahaMxDevice(device)) {
                            _statusMessage.value = this@MidiConnectionManager.context.getString(R.string.midi_status_usb_perm_granted_direct)
                            connectUsbDirect(device, disconnectOthers = false)
                        } else {
                            _statusMessage.value = this@MidiConnectionManager.context.getString(R.string.midi_status_usb_perm_granted)
                            scanAndAutoConnect(retryCount = 4)
                        }
                    } else if (!isUsbConnected) {
                        _statusMessage.value = this@MidiConnectionManager.context.getString(R.string.midi_status_usb_perm_denied)
                    }
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }
                    Log.d(TAG, "USB Device Attached: ${device?.deviceName}")
                    onUsbDeviceAttached(device)
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }
                    val detachedId = device?.deviceId
                    Log.d(TAG, "USB Device Detached: ${device?.deviceName} (id=$detachedId)")
                    if (detachedId != null && usbTransports.containsKey(detachedId)) {
                        disconnectUsbDeviceById(detachedId)
                        _statusMessage.value = this@MidiConnectionManager.context.getString(R.string.midi_status_yamaha_disconnected)
                    } else if (detachedId == null && (isUsbConnected || isConnectingUsb)) {
                        disconnectInternal(disconnectBle = false, disconnectUsb = true)
                        _statusMessage.value = this@MidiConnectionManager.context.getString(R.string.midi_status_usb_device_disconnected)
                    }
                    scanDevices()
                }
            }
        }
    }

    init {
        // Register USB BroadcastReceiver with Android 14+ flags
        val filter = IntentFilter().apply {
            addAction(ACTION_USB_PERMISSION)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        val receiverFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.RECEIVER_NOT_EXPORTED
        } else {
            0
        }
        try {
            ContextCompat.registerReceiver(context, usbReceiver, filter, receiverFlags)
        } catch (e: Exception) {
            Log.e(TAG, "Error registering USB receiver", e)
        }

        if (midiManager == null) {
            _statusMessage.value = context.getString(R.string.midi_status_native_unavailable)
            scanAndAutoConnect(retryCount = 3)
        } else {
            midiManager.registerDeviceCallback(deviceCallback, mainHandler)
            scanAndAutoConnect(retryCount = 3)
        }
    }

    fun isYamahaMxDevice(device: UsbDevice?): Boolean {
        if (device == null) return false
        return try {
            // Yamaha Corporation Vendor ID = 0x0499 (1177)
            if (device.vendorId == 0x0499 || device.vendorId == 1177) return true

            val productName = device.productName ?: ""
            val manufacturer = device.manufacturerName ?: ""
            if (productName.contains("Yamaha", ignoreCase = true) ||
                productName.contains("MX49", ignoreCase = true) ||
                productName.contains("MX61", ignoreCase = true) ||
                productName.contains("MX88", ignoreCase = true) ||
                manufacturer.contains("Yamaha", ignoreCase = true)
            ) {
                return true
            }

            false
        } catch (e: Throwable) {
            Log.w(TAG, "Defensive catch in isYamahaMxDevice: ${e.message}")
            false
        }
    }

    fun requestUsbPermission(device: UsbDevice) {
        val manager = usbManager ?: return
        if (manager.hasPermission(device)) {
            Log.d(TAG, "Already has permission for device: ${device.deviceName}")
            scanAndAutoConnect()
            return
        }

        _statusMessage.value = context.getString(R.string.midi_status_requesting_usb_permission, device.productName ?: device.deviceName)
        val intent = Intent(ACTION_USB_PERMISSION).setPackage(context.packageName)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val permissionIntent = PendingIntent.getBroadcast(context, 0, intent, flags)
        manager.requestPermission(device, permissionIntent)
    }

    fun onUsbDeviceAttached(device: UsbDevice?) {
        scanDevices()

        if (device != null && isYamahaMxDevice(device)) {
            val hasNativeYamaha = _availableDevices.value.any { !it.isUsbDirect && !it.isBluetooth && it.isYamaha }
            if (!hasNativeYamaha && !isYamahaUsbConnected) {
                val hasPerm = usbManager?.hasPermission(device) == true
                Log.d(TAG, "Yamaha device attached: ${device.deviceName}, hasPermission=$hasPerm")
                if (!hasPerm) {
                    requestUsbPermission(device)
                }
            }
        }

        scanAndAutoConnect(retryCount = 3)
    }

    fun scanDevices() {
        try {
            val list = mutableListOf<MidiDeviceUiModel>()

            // 1. Scan ALL native MidiManager devices safely (never stop at the first device)
            val nativeDevices = try { midiManager?.devices?.toList().orEmpty() } catch (_: Throwable) { emptyList() }
            for (info in nativeDevices) {
                if (info != null) {
                    wrapDeviceInfo(info)?.let { list.add(it) }
                }
            }

            // Ensure currently connected BLE device (e.g. opened via openBluetoothDevice) is included in list
            val activeBleInfo = (bleTransport as? AndroidMidiTransport)?.deviceInfo
            if (activeBleInfo != null && list.none { it.id == activeBleInfo.id }) {
                wrapDeviceInfo(activeBleInfo)?.let { list.add(it) }
            }

            // 2. Scan USB Host devices via UsbManager (Fallback for Yamaha MX V1)
            val usbDevices = try { usbManager?.deviceList?.values?.toList().orEmpty() } catch (_: Throwable) { emptyList() }
            for (usbDev in usbDevices) {
                if (usbDev != null && isYamahaMxDevice(usbDev)) {
                    // Only consider native USB Yamaha devices (never let a connected Bluetooth or Arturia device hide USB Direct)
                    val alreadyInNativeUsb = list.any { !it.isUsbDirect && !it.isBluetooth && it.isYamaha }
                    if (!alreadyInNativeUsb) {
                        wrapUsbDevice(usbDev)?.let { list.add(it) }
                    }
                }
            }

            val immutableAvailable = Collections.unmodifiableList(ArrayList(list))
            val immutableConnected = Collections.unmodifiableList(ArrayList(list.filter { it.isConnected }))

            if (Looper.myLooper() == Looper.getMainLooper()) {
                _availableDevices.value = immutableAvailable
                _connectedDevices.value = immutableConnected
            } else {
                _availableDevices.value = immutableAvailable
                _connectedDevices.value = immutableConnected
                mainHandler.post {
                    _availableDevices.value = immutableAvailable
                    _connectedDevices.value = immutableConnected
                }
            }

            Log.d(TAG, "Available MIDI devices found: ${immutableAvailable.size}")

            if (immutableAvailable.isEmpty() && !isUsbConnected && !isConnectingUsb) {
                val yamahaUsb = usbDevices.firstOrNull { isYamahaMxDevice(it) }
                if (yamahaUsb != null) {
                    if (usbManager?.hasPermission(yamahaUsb) == false) {
                        _statusMessage.value = context.getString(R.string.midi_status_usb_keyboard_requesting_perm)
                        requestUsbPermission(yamahaUsb)
                    } else {
                        _statusMessage.value = context.getString(R.string.midi_status_yamaha_detected_connecting)
                    }
                } else if (!_isConnected.value) {
                    _statusMessage.value = context.getString(R.string.midi_status_no_usb_midi_detected)
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Defensive catch in scanDevices: ${e.message}", e)
        }
    }

    /**
     * Multi-Device USB Host auto-connect sequence:
     * 1º: Iterates over ALL native MidiManager USB devices (Yamaha MX + external controllers like Arturia MiniLab 3)
     *     and opens simultaneous active connections for every unconnected USB device.
     * 2º: If no Yamaha MX is connected via native MidiManager within 1.5s, checks UsbManager for Yamaha (0x0499)
     *     and connects via YamahaUsbDirectDriver WITHOUT disconnecting other active USB controllers on the hub.
     */
    fun scanAndAutoConnect(retryCount: Int = 3) {
        scanDevices()

        // 1º Iterate over ALL native USB devices (Yamaha MX + Arturia MiniLab 3 + any other USB controllers on Hub)
        val nativeUsbDevices = _availableDevices.value
            .filter { !it.isUsbDirect && !it.isBluetooth }
            .sortedByDescending { it.isYamaha }

        for (dev in nativeUsbDevices) {
            val alreadyConnected = usbTransports[dev.id]?.isConnected() == true
            val alreadyConnecting = connectingUsbDeviceIds.contains(dev.id) || connectingUsbDeviceId == dev.id
            if (!alreadyConnected && !alreadyConnecting) {
                connectToDevice(dev, disconnectOthers = false)
            }
        }

        val hasNativeYamaha = nativeUsbDevices.any { it.isYamaha }
        if (hasNativeYamaha || isYamahaUsbConnected) {
            fallbackJob?.cancel()
            fallbackJob = null
            return
        }

        // 2º Automatic fallback for Yamaha Direct USB Driver after 1.5s (preserving other connected USB controllers)
        fallbackJob?.cancel()
        fallbackJob = coroutineScope.launch {
            delay(1500L)
            if (!isYamahaUsbConnected) {
                val yamahaUsb = try { usbManager?.deviceList?.values?.toList()?.firstOrNull { isYamahaMxDevice(it) } } catch (_: Throwable) { null }
                if (yamahaUsb != null) {
                    val devId = yamahaUsb.deviceId
                    if (!connectingUsbDeviceIds.contains(devId)) {
                        if (usbManager?.hasPermission(yamahaUsb) == true) {
                            Log.i(TAG, "Fallback auto-connect: Connecting to Yamaha MX V1 via Direct USB Host driver")
                            connectUsbDirect(yamahaUsb, disconnectOthers = false)
                        } else {
                            Log.i(TAG, "Fallback: Yamaha MX detected via USB. Requesting permission for Direct USB driver...")
                            _statusMessage.value = context.getString(R.string.midi_status_yamaha_direct_requesting_perm)
                            requestUsbPermission(yamahaUsb)
                        }
                    }
                } else if (retryCount > 0 && !isYamahaUsbConnected) {
                    scanAndAutoConnect(retryCount - 1)
                }
            }
        }
    }

    fun autoConnectFirstYamaha() {
        val usbCandidates = _availableDevices.value
            .filter { !it.isBluetooth }
            .sortedByDescending { it.isYamaha }
        if (usbCandidates.isNotEmpty()) {
            for (target in usbCandidates) {
                connectToDevice(target, disconnectOthers = false)
            }
        } else {
            _availableDevices.value.firstOrNull()?.let { connectToDevice(it, disconnectOthers = false) }
        }
    }

    fun connectToDevice(deviceWrapper: MidiDeviceInfoWrapper) {
        connectToDevice(deviceWrapper, disconnectOthers = false)
    }

    fun connectToDevice(deviceWrapper: MidiDeviceInfoWrapper, disconnectOthers: Boolean) {
        try {
            if (deviceWrapper.isUsbDirect && deviceWrapper.usbDevice != null) {
                if (usbManager?.hasPermission(deviceWrapper.usbDevice) == true) {
                    connectUsbDirect(deviceWrapper.usbDevice, disconnectOthers = disconnectOthers)
                } else {
                    requestUsbPermission(deviceWrapper.usbDevice)
                }
                return
            }

            val manager = midiManager ?: return
            val info = deviceWrapper.rawInfo ?: return
            val infoId = (info as MidiDeviceInfo?)?.id ?: deviceWrapper.id
            val isBt = deviceWrapper.isBluetooth || (info as MidiDeviceInfo?)?.type == MidiDeviceInfo.TYPE_BLUETOOTH

            if (isBt) {
                val currentBleId = (bleTransport as? AndroidMidiTransport)?.deviceInfo?.id
                if ((isBleConnected || isConnectingBle) && (currentBleId == infoId || connectingBleDeviceId == infoId)) {
                    Log.d(TAG, "Already connected or connecting to BLE device ${deviceWrapper.name} (id=$infoId)")
                    return
                }
                _statusMessage.value = context.getString(R.string.midi_status_connecting_to_device, deviceWrapper.name)
                disconnectInternal(disconnectBle = true, disconnectUsb = false)
                isConnectingBle = true
                connectingBleDeviceId = infoId
            } else {
                val existingUsb = usbTransports[infoId]
                if (existingUsb?.isConnected() == true || connectingUsbDeviceIds.contains(infoId) || connectingUsbDeviceId == infoId) {
                    Log.d(TAG, "Already connected or connecting to USB device ${deviceWrapper.name} (id=$infoId)")
                    return
                }
                _statusMessage.value = context.getString(R.string.midi_status_connecting_to_device, deviceWrapper.name)
                if (disconnectOthers) {
                    disconnectInternal(disconnectBle = false, disconnectUsb = true)
                } else {
                    disconnectUsbDeviceById(infoId)
                }
                isConnectingUsb = true
                connectingUsbDeviceId = infoId
                connectingUsbDeviceIds.add(infoId)
            }

            var nativeTransport: AndroidMidiTransport? = null
            nativeTransport = AndroidMidiTransport(
                midiManager = manager,
                deviceInfo = info,
                mainHandler = mainHandler,
                onConnectedCallback = { success, errorMsg ->
                    if (success) {
                        val transport = nativeTransport
                        if (isBt) {
                            isConnectingBle = false
                            bleTransport = transport
                        } else {
                            connectingUsbDeviceIds.remove(infoId)
                            isConnectingUsb = connectingUsbDeviceIds.isNotEmpty()
                            if (connectingUsbDeviceId == infoId) connectingUsbDeviceId = null
                            if (transport != null) {
                                usbTransports[infoId] = transport
                                if (transport.isYamahaDevice || usbTransport == null) {
                                    usbTransport = transport
                                }
                            }
                        }
                        updateConnectionState()
                        scanDevices()
                        val displayTitle = _connectedDeviceName.value ?: deviceWrapper.name
                        _statusMessage.value = context.getString(R.string.midi_status_connected_device, displayTitle)
                    } else {
                        if (isBt) {
                            isConnectingBle = false
                            connectingBleDeviceId = null
                            disconnectInternal(disconnectBle = true, disconnectUsb = false)
                        } else {
                            connectingUsbDeviceIds.remove(infoId)
                            isConnectingUsb = connectingUsbDeviceIds.isNotEmpty()
                            if (connectingUsbDeviceId == infoId) connectingUsbDeviceId = null
                            disconnectUsbDeviceById(infoId)
                        }
                        if (!isConnected.value) {
                            _statusMessage.value = errorMsg ?: context.getString(R.string.midi_status_open_port_failed)
                        }

                        if (!isBt && deviceWrapper.isYamaha) {
                            // Fallback to Yamaha Direct USB Driver if opening native Yamaha port fails
                            val yamahaUsb = try { usbManager?.deviceList?.values?.toList()?.firstOrNull { isYamahaMxDevice(it) } } catch (_: Throwable) { null }
                            if (yamahaUsb != null) {
                                Log.i(TAG, "Native MIDI opening failed. Attempting fallback to Yamaha Direct USB Driver...")
                                if (usbManager?.hasPermission(yamahaUsb) == true) {
                                    connectUsbDirect(yamahaUsb, disconnectOthers = false)
                                } else {
                                    requestUsbPermission(yamahaUsb)
                                }
                            }
                        }
                    }
                }
            )

            if (isBt) {
                bleTransport = nativeTransport
            } else {
                usbTransports[infoId] = nativeTransport
                if (nativeTransport.isYamahaDevice || usbTransport == null) {
                    usbTransport = nativeTransport
                }
            }
            val isFromYamahaHw = !isBt && (deviceWrapper.isYamaha || nativeTransport.isYamahaDevice)
            nativeTransport.registerReceiver { rawMidiBytes ->
                onMidiDataReceived(
                    bytes = rawMidiBytes,
                    offset = 0,
                    count = rawMidiBytes.size,
                    isFromBluetooth = isBt,
                    isFromYamahaHardware = isFromYamahaHw,
                    sourceDeviceId = if (isBt) -1 else infoId
                )
            }
            nativeTransport.connect()
        } catch (e: Throwable) {
            Log.e(TAG, "Defensive catch in connectToDevice: ${e.message}", e)
        }
    }

    /**
     * Connects directly to a Yamaha synthesizer using the user-space USB Host bulk driver.
     */
    fun connectUsbDirect(device: UsbDevice, disconnectOthers: Boolean = false) {
        val manager = usbManager ?: return
        val devId = (device as UsbDevice?)?.deviceId ?: 0
        val existing = usbTransports[devId]
        if ((existing is YamahaUsbDirectDriver && existing.isConnected()) ||
            connectingUsbDeviceIds.contains(devId) ||
            connectingUsbDeviceId == devId
        ) {
            return
        }

        _statusMessage.value = context.getString(R.string.midi_status_connecting_direct)
        if (disconnectOthers) {
            disconnectInternal(disconnectBle = false, disconnectUsb = true)
        } else {
            disconnectUsbDeviceById(devId)
        }

        isConnectingUsb = true
        connectingUsbDeviceId = devId
        connectingUsbDeviceIds.add(devId)

        val driver = YamahaUsbDirectDriver(manager, device)
        driver.registerReceiver { rawMidiBytes ->
            onMidiDataReceived(
                bytes = rawMidiBytes,
                offset = 0,
                count = rawMidiBytes.size,
                isFromBluetooth = false,
                isFromYamahaHardware = true,
                sourceDeviceId = devId
            )
        }
        driver.setOnDisconnectCallback {
            mainHandler.post {
                disconnectUsbDeviceById(devId)
                _statusMessage.value = context.getString(R.string.midi_status_yamaha_direct_disconnected)
                scanDevices()
            }
        }

        if (driver.connect()) {
            connectingUsbDeviceIds.remove(devId)
            isConnectingUsb = connectingUsbDeviceIds.isNotEmpty()
            if (connectingUsbDeviceId == devId) connectingUsbDeviceId = null
            usbTransports[devId] = driver
            usbTransport = driver
            updateConnectionState()
            scanDevices()
            val name = _connectedDeviceName.value ?: driver.getDeviceName()
            _statusMessage.value = context.getString(R.string.midi_status_connected_device, name)
            Log.i(TAG, "Successfully connected via YamahaUsbDirectDriver to ${driver.getDeviceName()}")
        } else {
            connectingUsbDeviceIds.remove(devId)
            isConnectingUsb = connectingUsbDeviceIds.isNotEmpty()
            if (connectingUsbDeviceId == devId) connectingUsbDeviceId = null
            disconnectUsbDeviceById(devId)
            if (!isConnected.value) {
                _statusMessage.value = context.getString(R.string.midi_status_direct_driver_connect_failed)
            }
        }
    }

    /**
     * Resets the active notes counter immediately (used for Panic, Reset, and Disconnect).
     */
    fun resetActiveNotes() {
        midiStreamParser.resetActiveNotes()
    }

    /**
     * Immediately transmits safety flush messages to cut any sounding notes and release sustain pedal.
     * Uses a keyed sequence to avoid spawning 64 concurrent unpaced coroutines on the USB bus.
     */
    fun sendSafetyFlush(channel: Int? = null) {
        if (!_isConnected.value) return
        val messages = if (channel != null && channel in 0..15) {
            YamahaSysExEncoder.createChannelFlushMessages(channel)
        } else {
            YamahaSysExEncoder.createAllChannelsFlushMessages()
        }
        throttler.sendKeyedSequence("SAFETY_FLUSH", messages, delayMs = 1L)
    }

    private fun wrapDeviceInfo(info: MidiDeviceInfo?): MidiDeviceUiModel? {
        if (info == null) return null
        return try {
            val infoId = info.id
            val props = info.properties
            val name = props?.getString(MidiDeviceInfo.PROPERTY_NAME)?.takeIf { it.isNotBlank() }
                ?: props?.getString(MidiDeviceInfo.PROPERTY_PRODUCT)?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.midi_device_usb_fallback_name, infoId)
            val manufacturer = props?.getString(MidiDeviceInfo.PROPERTY_MANUFACTURER) ?: ""
            val isYamaha = name.contains("Yamaha", ignoreCase = true) ||
                    name.contains("MX49", ignoreCase = true) ||
                    name.contains("MX61", ignoreCase = true) ||
                    name.contains("MX88", ignoreCase = true) ||
                    manufacturer.contains("Yamaha", ignoreCase = true)

            val isBt = info.type == MidiDeviceInfo.TYPE_BLUETOOTH
            val inPorts = (info as MidiDeviceInfo?)?.inputPortCount ?: 0
            val outPorts = (info as MidiDeviceInfo?)?.outputPortCount ?: 0
            val isConn = if (isBt) {
                (bleTransport as? AndroidMidiTransport)?.deviceInfo?.id == infoId && bleTransport?.isConnected() == true
            } else {
                usbTransports[infoId]?.isConnected() == true ||
                    ((usbTransport as? AndroidMidiTransport)?.deviceInfo?.id == infoId && usbTransport?.isConnected() == true)
            }

            MidiDeviceInfoWrapper(
                id = infoId,
                name = name,
                manufacturer = manufacturer,
                isYamaha = isYamaha,
                inputPortCount = inPorts,
                outputPortCount = outPorts,
                rawInfo = info,
                usbDevice = null,
                isUsbDirect = false,
                isBluetooth = isBt,
                isConnected = isConn
            )
        } catch (e: Throwable) {
            Log.w(TAG, "Defensive catch in wrapDeviceInfo: ${e.message}", e)
            null
        }
    }

    private fun wrapUsbDevice(device: UsbDevice?): MidiDeviceUiModel? {
        if (device == null) return null
        return try {
            val devId = device.deviceId
            val prodName = device.productName?.trim()
            val name = if (!prodName.isNullOrBlank()) prodName else "Yamaha MX Series"
            val isConn = (usbTransports[devId] as? YamahaUsbDirectDriver)?.isConnected() == true ||
                ((usbTransport as? YamahaUsbDirectDriver)?.device?.deviceId == devId && usbTransport?.isConnected() == true)
            MidiDeviceInfoWrapper(
                id = devId,
                name = context.getString(R.string.midi_device_direct_mode_suffix, name),
                manufacturer = device.manufacturerName ?: "Yamaha",
                isYamaha = true,
                inputPortCount = 1,
                outputPortCount = 1,
                rawInfo = null,
                usbDevice = device,
                isUsbDirect = true,
                isBluetooth = false,
                isConnected = isConn
            )
        } catch (e: Throwable) {
            Log.w(TAG, "Defensive catch in wrapUsbDevice: ${e.message}", e)
            null
        }
    }

    /**
     * Sends bytes via the throttler (recommended for sliders and continuous UI events).
     */
    fun sendThrottled(paramKey: String, bytes: ByteArray) {
        if (!_isConnected.value) return
        throttler.enqueue(paramKey, bytes)
    }

    /**
     * Sends bytes immediately without queue delay (for Panic, buttons, link toggles).
     */
    fun sendImmediate(bytes: ByteArray) {
        if (!_isConnected.value) return
        throttler.sendImmediate(bytes)
    }

    /**
     * Sends an ordered sequence of messages with a small inter-message delay (e.g. 10ms),
     * ensuring that multi-message commands like Voice changes (Bank MSB -> LSB -> Program Change)
     * are processed reliably in strict order by the synthesizer.
     */
    fun sendSequence(messages: List<ByteArray>, delayMs: Long = 10L) {
        if (!_isConnected.value) return
        throttler.sendSequence(messages, delayMs)
    }

    /**
     * Sends a keyed ordered sequence of messages, cancelling any superseded pending sequence
     * with the same key so rapid voice changes do not saturate the USB MIDI bus.
     */
    fun sendKeyedSequence(sequenceKey: String, messages: List<ByteArray>, delayMs: Long = 10L) {
        if (!_isConnected.value) return
        throttler.sendKeyedSequence(sequenceKey, messages, delayMs)
    }

    /**
     * Unified entry point for incoming MIDI packets from all USB devices (Yamaha MX, Arturia MiniLab 3, etc.) and BLE transports.
     */
    internal fun onMidiDataReceived(
        bytes: ByteArray,
        offset: Int = 0,
        count: Int = bytes.size,
        isFromBluetooth: Boolean = false,
        isFromYamahaHardware: Boolean = !isFromBluetooth,
        sourceDeviceId: Int = if (isFromBluetooth) -1 else 0
    ) {
        processIncomingMidi(
            data = bytes,
            offset = offset,
            count = count,
            isFromBluetooth = isFromBluetooth,
            isFromYamahaHardware = isFromYamahaHardware,
            sourceDeviceId = sourceDeviceId
        )
    }

    /**
     * Entry point for incoming MIDI packets from USB or BLE transports.
     */
    internal fun onMidiPacketReceived(
        bytes: ByteArray,
        offset: Int = 0,
        count: Int = bytes.size,
        isFromBluetooth: Boolean = false
    ) {
        processIncomingMidi(
            data = bytes,
            offset = offset,
            count = count,
            isFromBluetooth = isFromBluetooth,
            isFromYamahaHardware = !isFromBluetooth,
            sourceDeviceId = if (isFromBluetooth) -1 else 0
        )
    }

    internal fun processIncomingMidi(
        data: ByteArray,
        offset: Int = 0,
        count: Int = data.size,
        isFromBluetooth: Boolean = false,
        isFromYamahaHardware: Boolean = !isFromBluetooth,
        sourceDeviceId: Int = if (isFromBluetooth) -1 else 0
    ) {
        try {
            if (data.isEmpty() || count <= 0 || offset < 0 || offset >= data.size) return
            val safeCount = count.coerceAtMost(data.size - offset)
            if (safeCount <= 0) return

            val isExternalController = isFromBluetooth || !isFromYamahaHardware

            // MIDI THRU (External USB Controller e.g. Arturia MiniLab 3 or BLE iPad -> Yamaha MX USB):
            // Immediately dispatch incoming Note-On, Note-Off, Pitch Bend, and CC to the physical Yamaha MX USB port
            // on the channel corresponding to the configured Part in the app, while NEVER echoing back to the controller.
            val effectiveSlice: ByteArray
            val effectiveOffset: Int
            val effectiveCount: Int

            if (isExternalController) {
                val remapped = remapExternalControllerChannelIfNeeded(data, offset, safeCount)
                effectiveSlice = remapped.parserBytes
                effectiveOffset = 0
                effectiveCount = remapped.parserBytes.size
                if (remapped.usbBytes.isNotEmpty()) {
                    sendToUsbOnly(remapped.usbBytes, 0, remapped.usbBytes.size)
                }
            } else {
                effectiveSlice = data
                effectiveOffset = offset
                effectiveCount = safeCount
            }

            midiStreamParser.processIncomingMidi(
                data = effectiveSlice,
                offset = effectiveOffset,
                count = effectiveCount,
                isFromBluetooth = isFromBluetooth,
                isFromYamahaHardware = isFromYamahaHardware,
                sourceDeviceId = sourceDeviceId
            )
        } catch (e: Throwable) {
            Log.w(TAG, "Defensive catch in MidiConnectionManager.processIncomingMidi: ${e.message}", e)
        }
    }

    private data class RemappedExternalPacket(
        val usbBytes: ByteArray,
        val parserBytes: ByteArray
    )

    @Volatile
    private var lastExternalRunningStatus: Int = 0

    /**
     * Remaps channel voice status bytes (0x80..0xEF, Note-On, Note-Off, Pitch Bend, CC, Aftertouch)
     * from an external controller (e.g. M-VAVE SMK25 via BLE or Arturia MiniLab 3 via USB)
     * exclusively to the target MIDI channel configured in the app via [externalChannelResolver].
     * Also expands Running Status packets with explicit remapped status bytes so USB-MIDI never
     * leaks notes onto Channel 1 or other parts of the synthesizer.
     */
    private fun remapExternalControllerChannelIfNeeded(
        data: ByteArray,
        offset: Int,
        count: Int
    ): RemappedExternalPacket {
        val resolver = externalChannelResolver
        val audibleCheck = externalChannelAudibleChecker
        val rawSlice = data.copyOfRange(offset, offset + count)
        if (resolver == null && audibleCheck == null) {
            return RemappedExternalPacket(rawSlice, rawSlice)
        }

        val usbOut = java.io.ByteArrayOutputStream(rawSlice.size + 4)
        val parserOut = java.io.ByteArrayOutputStream(rawSlice.size + 4)
        var inSysEx = false
        var runningStatus = lastExternalRunningStatus
        var pendingData1 = -1

        for (i in rawSlice.indices) {
            val b = rawSlice[i].toInt() and 0xFF

            // Ignore Clock (0xF8) and Active Sensing (0xFE) noise from external controllers
            if (b == 0xF8 || b == 0xFE) {
                continue
            }
            if (b in 0xF9..0xFF) {
                continue
            }

            if (b == 0xF0) {
                inSysEx = true
                runningStatus = 0
                pendingData1 = -1
                continue
            }
            if (b == 0xF7) {
                inSysEx = false
                pendingData1 = -1
                continue
            }
            if (inSysEx) {
                continue
            }
            if (b in 0xF1..0xF6) {
                runningStatus = 0
                pendingData1 = -1
                continue
            }

            if (b in 0x80..0xEF) {
                runningStatus = b
                lastExternalRunningStatus = b
                pendingData1 = -1
                continue
            }

            // Data byte (0x00..0x7F)
            if (runningStatus !in 0x80..0xEF) {
                continue
            }

            val statusType = runningStatus and 0xF0
            val incomingCh = runningStatus and 0x0F
            val targetCh = (resolver?.invoke(incomingCh) ?: incomingCh).coerceIn(0, 15)
            val remappedStatus = statusType or targetCh

            if (statusType == 0xC0 || statusType == 0xD0) {
                // 1-data-byte Channel Voice message (Program Change, Channel Pressure)
                val d1 = b and 0x7F
                parserOut.write(remappedStatus)
                parserOut.write(d1)
                usbOut.write(remappedStatus)
                usbOut.write(d1)
                pendingData1 = -1
            } else {
                // 2-data-byte Channel Voice message (Note Off, Note On, Poly Pressure, CC, Pitch Bend)
                if (pendingData1 < 0) {
                    pendingData1 = b and 0x7F
                } else {
                    val d1 = pendingData1 and 0x7F
                    val d2 = b and 0x7F
                    pendingData1 = -1

                    if (statusType == 0xB0) {
                        // Control Change (0xB0..0xBF):
                        // Preserve the exact incoming channel in parserOut so multi-part MIDI Learn
                        // (e.g. Parts 1, 5, 6, 7) and absolute CC routing always see the true controller channel.
                        val originalCcStatus = 0xB0 or incomingCh
                        parserOut.write(originalCcStatus)
                        parserOut.write(d1)
                        parserOut.write(d2)

                        // Only forward Sustain Pedal (CC 64) directly to targetCh in MIDI Thru;
                        // all fader/knob CCs are routed and dispatched by PerformanceViewModel per mapped Part.
                        if (d1 == 64) {
                            usbOut.write(remappedStatus)
                            usbOut.write(d1)
                            usbOut.write(d2)
                        }
                    } else {
                        parserOut.write(remappedStatus)
                        parserOut.write(d1)
                        parserOut.write(d2)

                        val isNoteOnWithVelocity = (statusType == 0x90 && d2 > 0)
                        val isTargetAudible = audibleCheck?.invoke(targetCh) ?: true
                        if (!isNoteOnWithVelocity || isTargetAudible) {
                            usbOut.write(remappedStatus)
                            usbOut.write(d1)
                            usbOut.write(d2)
                        }
                    }
                }
            }
        }

        return RemappedExternalPacket(
            usbBytes = usbOut.toByteArray(),
            parserBytes = parserOut.toByteArray()
        )
    }

    private fun getTargetYamahaUsbTransports(): List<MidiTransport> {
        val allUsb = usbTransports.values.toList()
        val yamahaOnly = allUsb.filter { isYamahaTransport(it) && it.isConnected() }
        if (yamahaOnly.isNotEmpty()) return yamahaOnly
        val primary = usbTransport
        if (primary != null) return listOf(primary)
        return allUsb.filter { it.isConnected() }
    }

    private fun sendRawBytesInternal(bytes: ByteArray) {
        try {
            val targets = getTargetYamahaUsbTransports()
            for (transport in targets) {
                transport.sendBytes(bytes)
            }
            bleTransport?.sendBytes(bytes)
            Log.v(TAG, "Sent MIDI: ${YamahaSysExEncoder.toHexString(bytes)}")
        } catch (e: Throwable) {
            Log.w(TAG, "Defensive catch in sendRawBytesInternal: ${e.message}", e)
        }
    }

    /**
     * Sends bytes directly to USB-OTG physical synthesizer only with throttling, bypassing BLE (Anti-Loop mechanism).
     */
    fun sendThrottledUsbOnly(paramKey: String, bytes: ByteArray) {
        try {
            if (getTargetYamahaUsbTransports().isEmpty()) return
            usbOnlyThrottler.enqueue(paramKey, bytes)
        } catch (e: Throwable) {
            Log.w(TAG, "Defensive catch in sendThrottledUsbOnly: ${e.message}", e)
        }
    }

    /**
     * Sends bytes directly to the Yamaha MX USB-OTG physical synthesizer only, bypassing BLE and external USB controllers.
     */
    fun sendToUsbOnly(bytes: ByteArray, offset: Int = 0, count: Int = bytes.size) {
        try {
            if (bytes.isEmpty() || count <= 0 || offset < 0 || offset >= bytes.size) return
            val safeCount = count.coerceAtMost(bytes.size - offset)
            if (safeCount <= 0) return
            val targets = getTargetYamahaUsbTransports()
            for (transport in targets) {
                transport.sendBytes(bytes, offset, safeCount)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Defensive catch in sendToUsbOnly: ${e.message}", e)
        }
    }

    fun disconnect(reason: String? = null) {
        disconnectInternal(disconnectBle = true, disconnectUsb = true)
        _statusMessage.value = reason ?: context.getString(R.string.disconnected)
        scanDevices()
    }

    private fun disconnectUsbDeviceById(deviceId: Int) {
        connectingUsbDeviceIds.remove(deviceId)
        if (connectingUsbDeviceId == deviceId) {
            connectingUsbDeviceId = null
        }
        isConnectingUsb = connectingUsbDeviceIds.isNotEmpty()
        val removed = usbTransports.remove(deviceId)
        try {
            removed?.disconnect()
        } catch (e: Exception) {
            Log.w(TAG, "Error disconnecting USB transport id=$deviceId", e)
        }
        if (usbTransports.isEmpty()) {
            usbTransport = null
        }
        updateConnectionState()
    }

    private fun disconnectInternal(disconnectBle: Boolean = true, disconnectUsb: Boolean = true) {
        if (disconnectUsb) {
            fallbackJob?.cancel()
            fallbackJob = null
            connectingUsbDeviceIds.clear()
            isConnectingUsb = false
            connectingUsbDeviceId = null
            throttler.clear()
            usbOnlyThrottler.clear()
            val toClose = usbTransports.values.toList()
            usbTransports.clear()
            for (t in toClose) {
                try {
                    t.disconnect()
                } catch (e: Exception) {
                    Log.w(TAG, "Error disconnecting USB transport", e)
                }
            }
            try {
                usbTransport?.disconnect()
            } catch (e: Exception) {
                Log.w(TAG, "Error disconnecting primary USB transport", e)
            }
            usbTransport = null
        }

        if (disconnectBle) {
            isConnectingBle = false
            connectingBleDeviceId = null
            try {
                bleTransport?.disconnect()
            } catch (e: Exception) {
                Log.w(TAG, "Error disconnecting BLE transport", e)
            }
            bleTransport = null
            _bleDiscoveredDevices.update { list -> list.map { it.copy(isConnected = false, isConnecting = false) } }
        }

        if (disconnectUsb && disconnectBle) {
            midiStreamParser.resetParserAndNotes()
        }

        updateConnectionState()
    }

    fun startBleScan() {
        val adapter = bluetoothManager?.adapter
        if (adapter == null || !adapter.isEnabled) {
            _statusMessage.value = "Bluetooth desativado ou indisponível"
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val hasScan = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
            val hasConnect = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            if (!hasScan || !hasConnect) {
                _statusMessage.value = context.getString(R.string.ble_permission_required)
                return
            }
        } else {
            val hasLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (!hasLocation) {
                _statusMessage.value = context.getString(R.string.ble_permission_required)
                return
            }
        }

        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            _statusMessage.value = "Scanner BLE indisponível"
            return
        }

        stopBleScan()

        // Pre-populate only with paired/bonded devices advertising standard MIDI capabilities
        val initialList = mutableListOf<BleMidiDevice>()
        try {
            adapter.bondedDevices?.forEach { dev ->
                val name = dev.name?.trim() ?: ""
                if (name.isNotBlank()) {
                    val hasMidiUuid = dev.uuids?.any { it.uuid == BLE_MIDI_UUID } == true
                    val isKnownYamaha = isYamahaBleName(name) && dev.type != BluetoothDevice.DEVICE_TYPE_CLASSIC
                    if (hasMidiUuid || isKnownYamaha) {
                        val isConn = _isConnected.value && _connectedDeviceName.value?.contains(name, ignoreCase = true) == true
                        initialList.add(
                            BleMidiDevice(
                                name = name,
                                address = dev.address,
                                isConnected = isConn,
                                isYamaha = isYamahaBleName(name),
                                bluetoothDevice = dev
                            )
                        )
                    }
                }
            }
        } catch (_: SecurityException) {}

        _bleDiscoveredDevices.value = initialList.toList()
        _isBleScanning.value = true
        _statusMessage.value = "Escaneando dispositivos Bluetooth MIDI (BLE)..."

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                result ?: return
                val dev = result.device ?: return
                val record = result.scanRecord
                val name = try {
                    record?.deviceName ?: dev.name ?: ""
                } catch (_: SecurityException) {
                    ""
                }
                val trimmedName = name.trim()
                if (trimmedName.isBlank()) return

                // Rigorous BLE-MIDI filtering: must advertise the Standard BLE-MIDI service UUID
                val advertisedUuids = record?.serviceUuids ?: emptyList()
                val hasMidiService = advertisedUuids.any { it.uuid == BLE_MIDI_UUID } ||
                    isYamahaBleName(trimmedName)
                if (!hasMidiService) {
                    return
                }

                val isYamaha = isYamahaBleName(trimmedName)
                val address = dev.address
                val rssi = result.rssi

                _bleDiscoveredDevices.update { list ->
                    val existingIndex = list.indexOfFirst { it.address == address }
                    if (existingIndex >= 0) {
                        list.mapIndexed { idx, item ->
                            if (idx == existingIndex) {
                                item.copy(rssi = rssi, name = if (item.name.isBlank()) trimmedName else item.name)
                            } else item
                        }
                    } else {
                        list + BleMidiDevice(
                            name = trimmedName,
                            address = address,
                            rssi = rssi,
                            isConnected = _isConnected.value && _connectedDeviceName.value?.contains(trimmedName, ignoreCase = true) == true,
                            isYamaha = isYamaha,
                            bluetoothDevice = dev
                        )
                    }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.w(TAG, "BLE Scan failed with errorCode: $errorCode")
                _isBleScanning.value = false
                _statusMessage.value = "Falha no escaneamento BLE (código $errorCode)"
            }
        }

        bleScanCallback = callback

        try {
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            // Standard MIDI BLE Service UUID: 03B80E5A-EDE8-4B33-A751-6CE34EC4C700
            val filter = ScanFilter.Builder()
                .setServiceUuid(BLE_MIDI_PARCEL_UUID)
                .build()

            scanner.startScan(listOf(filter), settings, callback)
        } catch (e: SecurityException) {
            _isBleScanning.value = false
            _statusMessage.value = context.getString(R.string.ble_permission_required)
        } catch (e: Throwable) {
            _isBleScanning.value = false
            _statusMessage.value = "Erro ao iniciar escaneamento BLE: ${e.message}"
        }

        bleScanTimeoutJob?.cancel()
        bleScanTimeoutJob = coroutineScope.launch {
            delay(15000L)
            if (_isBleScanning.value) {
                stopBleScan()
            }
        }
    }

    fun stopBleScan() {
        bleScanTimeoutJob?.cancel()
        bleScanTimeoutJob = null
        val callback = bleScanCallback
        if (callback != null) {
            try {
                bluetoothManager?.adapter?.bluetoothLeScanner?.stopScan(callback)
            } catch (_: Throwable) {}
            bleScanCallback = null
        }
        _isBleScanning.value = false
    }

    fun connectBleDevice(device: BleMidiDevice) {
        val adapter = bluetoothManager?.adapter
        val btDevice = device.bluetoothDevice ?: adapter?.getRemoteDevice(device.address)
        if (btDevice == null) {
            _statusMessage.value = "Dispositivo Bluetooth não encontrado"
            return
        }
        val manager = midiManager
        if (manager == null) {
            _statusMessage.value = "Serviço MIDI nativo indisponível"
            return
        }

        stopBleScan()
        _statusMessage.value = "Conectando a ${device.name} via Bluetooth LE..."
        disconnectInternal(disconnectBle = true, disconnectUsb = false)

        isConnectingBle = true
        _bleDiscoveredDevices.update { list ->
            list.map { if (it.address == device.address) it.copy(isConnecting = true) else it }
        }

        try {
            manager.openBluetoothDevice(btDevice, { midiDevice ->
                mainHandler.post {
                    isConnectingBle = false
                    if (midiDevice != null) {
                        val bleInfo = midiDevice.info
                        connectingBleDeviceId = (bleInfo as MidiDeviceInfo?)?.id
                        _bleDiscoveredDevices.update { list ->
                            list.map {
                                if (it.address == device.address) it.copy(isConnecting = false, isConnected = true)
                                else it.copy(isConnected = false)
                            }
                        }
                        var nativeTransport: AndroidMidiTransport? = null
                        nativeTransport = AndroidMidiTransport(
                            midiManager = manager,
                            deviceInfo = bleInfo,
                            mainHandler = mainHandler,
                            onConnectedCallback = { success, errorMsg ->
                                if (success) {
                                    bleTransport = nativeTransport
                                    updateConnectionState()
                                    scanDevices()
                                    _statusMessage.value = "Conectado a ${device.name} (BLE-MIDI)"
                                    sendSafetyFlush()
                                } else {
                                    _bleDiscoveredDevices.update { list ->
                                        list.map { if (it.address == device.address) it.copy(isConnected = false) else it }
                                    }
                                    disconnectInternal(disconnectBle = true, disconnectUsb = false)
                                    _statusMessage.value = errorMsg ?: "Falha ao abrir portas BLE-MIDI"
                                    scanDevices()
                                }
                            },
                            preOpenedMidiDevice = midiDevice
                        )
                        bleTransport = nativeTransport
                        nativeTransport.registerReceiver { rawMidiBytes ->
                            onMidiDataReceived(
                                bytes = rawMidiBytes,
                                offset = 0,
                                count = rawMidiBytes.size,
                                isFromBluetooth = true,
                                isFromYamahaHardware = false,
                                sourceDeviceId = -1
                            )
                        }
                        nativeTransport.connect()
                    } else {
                        _bleDiscoveredDevices.update { list ->
                            list.map { if (it.address == device.address) it.copy(isConnecting = false, isConnected = false) else it }
                        }
                        disconnectInternal(disconnectBle = true, disconnectUsb = false)
                        _statusMessage.value = "Falha ao abrir dispositivo BLE ${device.name}"
                        scanDevices()
                    }
                }
            }, mainHandler)
        } catch (e: SecurityException) {
            isConnectingBle = false
            _bleDiscoveredDevices.update { list ->
                list.map { if (it.address == device.address) it.copy(isConnecting = false) else it }
            }
            disconnectInternal(disconnectBle = true, disconnectUsb = false)
            _statusMessage.value = context.getString(R.string.ble_permission_required)
        } catch (e: Throwable) {
            isConnectingBle = false
            _bleDiscoveredDevices.update { list ->
                list.map { if (it.address == device.address) it.copy(isConnecting = false) else it }
            }
            disconnectInternal(disconnectBle = true, disconnectUsb = false)
            _statusMessage.value = "Erro BLE: ${e.message}"
        }
    }

    fun disconnectBleDevice(device: BleMidiDevice) {
        disconnectInternal(disconnectBle = true, disconnectUsb = false)
        _statusMessage.value = "Desconectado de ${device.name}"
        _bleDiscoveredDevices.update { list ->
            list.map { if (it.address == device.address) it.copy(isConnected = false, isConnecting = false) else it }
        }
        scanDevices()
    }

    private fun isYamahaBleName(name: String): Boolean {
        return name.contains("Yamaha", ignoreCase = true) ||
                name.contains("MD-BT01", ignoreCase = true) ||
                name.contains("UD-BT01", ignoreCase = true) ||
                name.contains("MX49", ignoreCase = true) ||
                name.contains("MX61", ignoreCase = true) ||
                name.contains("MX88", ignoreCase = true)
    }

    fun release() {
        throttler.stop()
        usbOnlyThrottler.stop()
        disconnectInternal()
        try {
            context.unregisterReceiver(usbReceiver)
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering USB receiver", e)
        }
        try {
            midiManager?.unregisterDeviceCallback(deviceCallback)
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering callback", e)
        }
    }
}

/**
 * Stateful MIDI 1.0 stream parser for incoming USB-MIDI and BLE-MIDI bytes.
 * - Strictly tracks Note On (0x90..0x9F with velocity > 0) -> increments active notes counter.
 * - Strictly tracks Note Off (0x80..0x8F) and Note On with velocity == 0 -> decrements active notes counter (clamped at 0).
 * - Supports MIDI Running Status and fragmented multi-packet deliveries independently per source (USB vs BLE).
 * - Ignores System Real-Time messages (0xF8..0xFF) even when interleaved inside Channel Voice messages.
 * - Ignores Control Change, Program Change, Pitch Bend, Aftertouch, and SysEx for note counting.
 * - Defensively catches all Throwables so no malformed packet or callback failure can terminate the reader loop.
 */
class MidiInputStreamParser(
    private val onChannelActivity: ((Int) -> Unit)? = null,
    private val onVoiceEvent: ((MidiVoiceEvent) -> Unit)? = null
) {
    private class SourceParserState {
        var runningStatus: Int = 0
        var lastNoteStatus: Int = 0
        var pendingData1: Int = 0
        var hasPendingData1: Boolean = false
        var inSysEx: Boolean = false
        var systemCommonRemaining: Int = 0
        val sysExBuffer = java.io.ByteArrayOutputStream(16)

        fun resetActiveNotesOnly() {
            hasPendingData1 = false
            inSysEx = false
            sysExBuffer.reset()
            systemCommonRemaining = 0
            if (runningStatus !in 0x80..0x9F && lastNoteStatus in 0x80..0x9F) {
                runningStatus = lastNoteStatus
            }
        }

        fun resetAll() {
            runningStatus = 0
            lastNoteStatus = 0
            pendingData1 = 0
            hasPendingData1 = false
            inSysEx = false
            sysExBuffer.reset()
            systemCommonRemaining = 0
        }
    }

    private val lock = Any()
    private val usbState = SourceParserState()
    private val bleState = SourceParserState()
    private val extraUsbStates = ConcurrentHashMap<Int, SourceParserState>()

    private val _activeNotesCount = MutableStateFlow(0)
    val activeNotesCount: StateFlow<Int> = _activeNotesCount.asStateFlow()

    fun resetActiveNotes() {
        synchronized(lock) {
            usbState.resetActiveNotesOnly()
            bleState.resetActiveNotesOnly()
            extraUsbStates.values.forEach { it.resetActiveNotesOnly() }
            _activeNotesCount.value = 0
            notifyVoiceEventSafely(MidiVoiceEvent.AllNotesOff(null))
        }
    }

    fun resetParserAndNotes() {
        synchronized(lock) {
            usbState.resetAll()
            bleState.resetAll()
            extraUsbStates.values.forEach { it.resetAll() }
            extraUsbStates.clear()
            _activeNotesCount.value = 0
            notifyVoiceEventSafely(MidiVoiceEvent.AllNotesOff(null))
        }
    }

    private fun notifyChannelActivitySafely(channel: Int) {
        try {
            onChannelActivity?.invoke(channel)
        } catch (e: Throwable) {
            try {
                Log.w("MidiInputStreamParser", "Defensive catch in onChannelActivity: ${e.message}", e)
            } catch (_: Throwable) {}
        }
    }

    private fun notifyVoiceEventSafely(event: MidiVoiceEvent) {
        try {
            onVoiceEvent?.invoke(event)
        } catch (e: Throwable) {
            try {
                Log.w("MidiInputStreamParser", "Defensive catch in onVoiceEvent: ${e.message}", e)
            } catch (_: Throwable) {}
        }
    }

    private fun handleCompletedSysEx(
        sysExBytes: ByteArray,
        isFromBluetooth: Boolean,
        isFromYamahaHardware: Boolean
    ) {
        // Summary discard of all SysEx (0xF0..0xF7) for Control Change / MIDI Learn:
        // Never emit MidiVoiceEvent.ControlChange from SysEx so proprietary Yamaha MX display/parameter
        // SysEx messages never interfere with MIDI Learn or fader scaling (Knob 1 fix).
        if (sysExBytes.size == 10 &&
            (sysExBytes[0].toInt() and 0xFF) == 0xF0 &&
            (sysExBytes[1].toInt() and 0xFF) == 0x43 &&
            (sysExBytes[3].toInt() and 0xFF) == 0x7F &&
            (sysExBytes[4].toInt() and 0xFF) == 0x17 &&
            (sysExBytes[9].toInt() and 0xFF) == 0xF7
        ) {
            val addrHigh = sysExBytes[5].toInt() and 0x7F
            val addrMid = sysExBytes[6].toInt() and 0x7F
            val addrLow = sysExBytes[7].toInt() and 0x7F
            val rawByte = sysExBytes[8]
            val faderValue = rawByte.toInt() and 0x7F

            if (addrHigh == 0x36 && addrLow == 0x44) {
                // Multi Common: Part 1-2 Link ([LAYER] button on physical keyboard)
                notifyVoiceEventSafely(MidiVoiceEvent.LayerActivation(enabled = faderValue != 0))
            } else if (addrHigh == 0x37 && addrMid == 1 && (addrLow == 0x01 || addrLow == 0x02 || addrLow == 0x03 || addrLow == 0x04)) {
                notifyVoiceEventSafely(MidiVoiceEvent.LayerActivation(enabled = true))
            }
        }
    }

    fun processIncomingMidi(
        data: ByteArray?,
        offset: Int = 0,
        count: Int = data?.size ?: 0,
        isFromBluetooth: Boolean = false,
        isFromYamahaHardware: Boolean = !isFromBluetooth,
        sourceDeviceId: Int = if (isFromBluetooth) -1 else 0
    ) {
        if (data == null || count <= 0 || data.isEmpty()) return
        try {
            synchronized(lock) {
                if (offset < 0 || offset >= data.size) return
                val start = offset.coerceAtLeast(0)
                val end = (offset.toLong() + count.toLong()).coerceIn(start.toLong(), data.size.toLong()).toInt()
                if (start >= end) return

                val st = when {
                    isFromBluetooth || sourceDeviceId == -1 -> bleState
                    sourceDeviceId == 0 -> usbState
                    else -> extraUsbStates.getOrPut(sourceDeviceId) { SourceParserState() }
                }

                // Recover if a previous packet started a non-MX truncated SysEx (0xF0) without 0xF7
                // and a new short 2-byte Running Status Note packet arrives without 0xF7.
                if (st.inSysEx && (end - start) == 2) {
                    var hasF7 = false
                    for (k in start until end) {
                        if ((data[k].toInt() and 0xFF) == 0xF7) {
                            hasF7 = true
                            break
                        }
                    }
                    if (!hasF7) {
                        val buf = st.sysExBuffer.toByteArray()
                        val isValidMxPrefix = buf.size >= 5 &&
                            (buf[0].toInt() and 0xFF) == 0xF0 &&
                            (buf[1].toInt() and 0xFF) == 0x43 &&
                            (buf[3].toInt() and 0xFF) == 0x7F &&
                            (buf[4].toInt() and 0xFF) == 0x17
                        if (!isValidMxPrefix) {
                            st.inSysEx = false
                            st.sysExBuffer.reset()
                            if (st.runningStatus == 0 && st.lastNoteStatus in 0x80..0x9F) {
                                st.runningStatus = st.lastNoteStatus
                            }
                        }
                    }
                }

                var i = start
                while (i < end) {
                    val rawByte = data[i]
                    val b = rawByte.toInt() and 0xFF
                    i++

                    // 1. System Real-Time Messages (0xF8..0xFF): 1-byte transparent messages
                    if (b in 0xF8..0xFF) {
                        continue
                    }

                    // 2. System Exclusive (0xF0..0xF7) and System Common (0xF1..0xF6)
                    if (b in 0xF0..0xF7) {
                        st.hasPendingData1 = false
                        when (b) {
                            0xF0 -> { // SysEx Start
                                st.runningStatus = 0
                                st.inSysEx = true
                                st.sysExBuffer.reset()
                                st.sysExBuffer.write(0xF0)
                                st.systemCommonRemaining = 0
                            }
                            0xF7 -> { // SysEx End
                                if (st.inSysEx) {
                                    st.sysExBuffer.write(0xF7)
                                    val completedSysEx = st.sysExBuffer.toByteArray()
                                    st.sysExBuffer.reset()
                                    st.inSysEx = false
                                    handleCompletedSysEx(completedSysEx, isFromBluetooth, isFromYamahaHardware)
                                } else {
                                    st.inSysEx = false
                                    st.sysExBuffer.reset()
                                }
                                st.systemCommonRemaining = 0
                                if (st.runningStatus == 0 && st.lastNoteStatus in 0x80..0x9F) {
                                    st.runningStatus = st.lastNoteStatus
                                }
                            }
                            0xF1, 0xF3 -> { // MTC Quarter Frame / Song Select (1 data byte)
                                st.runningStatus = 0
                                st.inSysEx = false
                                st.sysExBuffer.reset()
                                st.systemCommonRemaining = 1
                            }
                            0xF2 -> { // Song Position Pointer (2 data bytes)
                                st.runningStatus = 0
                                st.inSysEx = false
                                st.sysExBuffer.reset()
                                st.systemCommonRemaining = 2
                            }
                            else -> { // 0xF4, 0xF5, 0xF6 (Tune Request, 0 data bytes)
                                st.inSysEx = false
                                st.sysExBuffer.reset()
                                st.systemCommonRemaining = 0
                                if (st.runningStatus == 0 && st.lastNoteStatus in 0x80..0x9F) {
                                    st.runningStatus = st.lastNoteStatus
                                }
                            }
                        }
                        continue
                    }

                    // 3. Channel Voice Status Bytes (0x80..0xEF)
                    if (b in 0x80..0xEF) {
                        st.inSysEx = false
                        st.sysExBuffer.reset()
                        st.systemCommonRemaining = 0
                        st.runningStatus = b
                        if (b in 0x80..0x9F) {
                            st.lastNoteStatus = b
                        }
                        st.hasPendingData1 = false
                        continue
                    }

                    // 4. Data Bytes (0x00..0x7F)
                    if (st.inSysEx) {
                        if (st.sysExBuffer.size() < 64) {
                            st.sysExBuffer.write(b)
                        }
                        continue
                    }
                    if (st.systemCommonRemaining > 0) {
                        st.systemCommonRemaining--
                        if (st.systemCommonRemaining == 0 && st.runningStatus == 0 && st.lastNoteStatus in 0x80..0x9F) {
                            st.runningStatus = st.lastNoteStatus
                        }
                        continue
                    }
                    if (st.runningStatus !in 0x80..0xEF) {
                        if (st.lastNoteStatus in 0x80..0x9F) {
                            st.runningStatus = st.lastNoteStatus
                        } else {
                            continue
                        }
                    }

                    val statusNibble = st.runningStatus and 0xF0
                    val channel = st.runningStatus and 0x0F

                    when (statusNibble) {
                        0x90 -> { // Note On (0x90..0x9F)
                            if (!st.hasPendingData1) {
                                st.pendingData1 = b
                                st.hasPendingData1 = true
                            } else {
                                val note = st.pendingData1 and 0x7F
                                val velocity = rawByte.toInt() and 0x7F
                                st.hasPendingData1 = false
                                if (velocity > 0) {
                                    _activeNotesCount.update { it + 1 }
                                    notifyChannelActivitySafely(channel)
                                    notifyVoiceEventSafely(MidiVoiceEvent.NoteOn(channel, note, velocity))
                                } else {
                                    _activeNotesCount.update { (it - 1).coerceAtLeast(0) }
                                    notifyVoiceEventSafely(MidiVoiceEvent.NoteOff(channel, note, 0))
                                }
                            }
                        }
                        0x80 -> { // Note Off (0x80..0x8F)
                            if (!st.hasPendingData1) {
                                st.pendingData1 = b
                                st.hasPendingData1 = true
                            } else {
                                val note = st.pendingData1 and 0x7F
                                val velocity = rawByte.toInt() and 0x7F
                                st.hasPendingData1 = false
                                _activeNotesCount.update { (it - 1).coerceAtLeast(0) }
                                notifyVoiceEventSafely(MidiVoiceEvent.NoteOff(channel, note, velocity))
                            }
                        }
                        0xB0, 0xA0, 0xE0 -> { // Control Change (0xB0..0xBF), Poly Aftertouch, Pitch Bend (2 data bytes)
                            if (!st.hasPendingData1) {
                                st.pendingData1 = b
                                st.hasPendingData1 = true
                            } else {
                                // Strictly extract direct absolute values (0..127):
                                // ccNumber = byte1 and 0x7F, faderValue = byte2 and 0x7F.
                                // Never apply relative delta (+/- 1) or delayed interpolation on Channel 1 or any channel.
                                val ccNumber = st.pendingData1 and 0x7F
                                val faderValue = (rawByte.toInt() and 0x7F).coerceIn(0, 127)
                                st.hasPendingData1 = false
                                notifyChannelActivitySafely(channel)
                                if (st.runningStatus in 0xB0..0xBF) {
                                    if (ccNumber == 64) {
                                        notifyVoiceEventSafely(MidiVoiceEvent.SustainChange(channel, faderValue > 0))
                                    } else {
                                        notifyVoiceEventSafely(
                                            MidiVoiceEvent.ControlChange(
                                                channel = channel,
                                                controller = ccNumber,
                                                value = faderValue,
                                                fromBluetooth = isFromBluetooth,
                                                fromYamahaHardware = isFromYamahaHardware,
                                                statusByte = st.runningStatus
                                            )
                                        )
                                    }
                                }
                            }
                        }
                        0xC0 -> { // Program Change (1 data byte)
                            st.hasPendingData1 = false
                            val program = rawByte.toInt() and 0x7F
                            notifyChannelActivitySafely(channel)
                            notifyVoiceEventSafely(
                                MidiVoiceEvent.ProgramChange(
                                    channel = channel,
                                    program = program,
                                    fromYamahaHardware = isFromYamahaHardware
                                )
                            )
                            if (st.lastNoteStatus in 0x80..0x9F) {
                                st.runningStatus = st.lastNoteStatus
                            }
                        }
                        0xD0 -> { // Channel Aftertouch (1 data byte)
                            st.hasPendingData1 = false
                            notifyChannelActivitySafely(channel)
                            if (st.lastNoteStatus in 0x80..0x9F) {
                                st.runningStatus = st.lastNoteStatus
                            }
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            try {
                Log.w("MidiInputStreamParser", "Defensive catch in processIncomingMidi: ${e.message}", e)
            } catch (_: Throwable) {}
        }
    }
}

