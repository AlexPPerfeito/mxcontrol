package com.yamahamx.editor.ui.viewmodel

import android.app.Activity
import android.hardware.usb.UsbDevice
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yamahamx.editor.data.billing.BillingManager
import com.yamahamx.editor.data.midi.MidiConnectionManager
import com.yamahamx.editor.data.midi.MidiVoiceEvent
import com.yamahamx.editor.data.midi.PartSpilloverRouter
import com.yamahamx.editor.data.midi.YamahaSysExEncoder
import com.yamahamx.editor.data.model.BleMidiDevice
import com.yamahamx.editor.data.model.MidiDeviceInfoWrapper
import com.yamahamx.editor.data.model.MidiDeviceUiModel
import com.yamahamx.editor.data.model.MidiPortType
import com.yamahamx.editor.data.model.MultiCommonState
import com.yamahamx.editor.data.model.MultiPartState
import com.yamahamx.editor.data.model.PerformanceState
import com.yamahamx.editor.data.model.SavedPerformance
import com.yamahamx.editor.data.model.YamahaVoice
import com.yamahamx.editor.data.repository.MidiLearnableParameter
import com.yamahamx.editor.data.repository.MidiMappingConfig
import com.yamahamx.editor.data.repository.MidiMappingRepository
import com.yamahamx.editor.data.repository.MidiTargetKey
import com.yamahamx.editor.data.repository.PerformanceStorageRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class NoteKey(val channel: Int, val note: Int)

class PerformanceViewModel(
    private val midiManager: MidiConnectionManager,
    private val storageRepository: PerformanceStorageRepository,
    private val billingManager: BillingManager? = null,
    private val userVoiceRepository: com.yamahamx.editor.data.repository.UserVoiceRepository? = null,
    val midiMappingRepository: MidiMappingRepository = MidiMappingRepository()
) : ViewModel() {

    private val _fallbackProUser = MutableStateFlow(storageRepository.isProUnlocked())
    val isProUser: StateFlow<Boolean> = billingManager?.isProUnlocked ?: _fallbackProUser.asStateFlow()
    val productPrice: StateFlow<String> = billingManager?.formattedPrice ?: MutableStateFlow(BillingManager.DEFAULT_PRICE_FALLBACK)

    fun refreshProStatus() {
        billingManager?.refreshProStatus()
        _fallbackProUser.value = storageRepository.isProUnlocked()
    }

    val userVoices: StateFlow<List<com.yamahamx.editor.data.model.UserVoiceEntity>> =
        userVoiceRepository?.userVoicesFlow ?: MutableStateFlow(emptyList())

    private val _showUpgradeDialog = MutableStateFlow(false)
    val showUpgradeDialog: StateFlow<Boolean> = _showUpgradeDialog.asStateFlow()

    private var hasDismissedStartupPaywallInSession = false
    private val _showStartupPaywallDialog = MutableStateFlow(false)
    val showStartupPaywallDialog: StateFlow<Boolean> = _showStartupPaywallDialog.asStateFlow()

    fun dismissStartupPaywall() {
        hasDismissedStartupPaywallInSession = true
        _showStartupPaywallDialog.value = false
    }

    fun updateUserVoiceName(slotIndex: Int, newName: String) {
        userVoiceRepository?.updateUserVoiceName(slotIndex, newName)
        val trimmed = newName.trim()
        if (trimmed.isNotBlank()) {
            _uiState.update { current ->
                val updatedParts = current.parts.map { part ->
                    if (part.voice.msb == 63 && part.voice.lsb == 8 && part.voice.programChange == slotIndex) {
                        part.copy(voice = part.voice.copy(name = trimmed))
                    } else {
                        part
                    }
                }
                current.copy(parts = updatedParts)
            }
        }
    }

    fun openUpgradeDialog() {
        _showUpgradeDialog.value = true
    }

    fun closeUpgradeDialog() {
        _showUpgradeDialog.value = false
    }

    fun purchaseProLicense(activity: Activity) {
        billingManager?.launchBillingFlow(activity)
    }

    fun restoreProPurchases() {
        billingManager?.restorePurchases()
    }

    private val _uiState = MutableStateFlow(PerformanceState())
    val uiState: StateFlow<PerformanceState> = _uiState.asStateFlow()
    val performanceState: StateFlow<PerformanceState> get() = uiState

    val activePortType: StateFlow<MidiPortType> = midiManager.activePortType
    val isBleScanning: StateFlow<Boolean> = midiManager.isBleScanning
    val bleDiscoveredDevices: StateFlow<List<BleMidiDevice>> = midiManager.bleDiscoveredDevices
    val connectedDevices: StateFlow<List<MidiDeviceUiModel>> =
        runCatching { midiManager.connectedDevices }.getOrNull() ?: MutableStateFlow(emptyList())

    fun startBleScan() {
        if (!isProUser.value) {
            openUpgradeDialog()
            return
        }
        midiManager.startBleScan()
    }

    fun stopBleScan() = midiManager.stopBleScan()

    fun connectBleDevice(device: BleMidiDevice) {
        if (!isProUser.value) {
            openUpgradeDialog()
            return
        }
        midiManager.connectBleDevice(device)
    }

    fun disconnectBleDevice(device: BleMidiDevice) = midiManager.disconnectBleDevice(device)

    // --- Interactive MIDI Learn State (PadLab-style) ---
    val midiMappings: StateFlow<Map<MidiTargetKey, MidiMappingConfig>> = midiMappingRepository.mappingsFlow

    private val _isMidiLearnActive = MutableStateFlow(false)
    val isMidiLearnActive: StateFlow<Boolean> = _isMidiLearnActive.asStateFlow()

    private val _midiLearnTarget = MutableStateFlow<MidiTargetKey?>(null)
    val midiLearnTarget: StateFlow<MidiTargetKey?> = _midiLearnTarget.asStateFlow()

    private val _midiLearnFeedback = MutableStateFlow<String?>(null)
    val midiLearnFeedback: StateFlow<String?> = _midiLearnFeedback.asStateFlow()

    fun startMidiLearn(parameter: MidiLearnableParameter, partIndex: Int) {
        if (partIndex !in 0..15) return
        _midiLearnTarget.value = MidiTargetKey(parameter, partIndex)
        _isMidiLearnActive.value = true
    }

    fun cancelMidiLearn() {
        _isMidiLearnActive.value = false
        _midiLearnTarget.value = null
    }

    fun restoreDefaultMidiMapping(
        parameter: MidiLearnableParameter? = null,
        partIndex: Int? = null
    ) {
        val target = if (parameter != null && partIndex != null) {
            MidiTargetKey(parameter, partIndex)
        } else {
            _midiLearnTarget.value
        } ?: return

        midiMappingRepository.restoreDefault(target.parameter, target.partIndex)
        _isMidiLearnActive.value = false
        _midiLearnTarget.value = null
        _midiLearnFeedback.value =
            "Padrão Yamaha restaurado (CC ${target.parameter.defaultCc}) em ${target.parameter.displayName} - Parte ${target.partIndex + 1}"
    }

    fun removeMidiMapping(
        parameter: MidiLearnableParameter? = null,
        partIndex: Int? = null
    ) {
        val target = if (parameter != null && partIndex != null) {
            MidiTargetKey(parameter, partIndex)
        } else {
            _midiLearnTarget.value
        } ?: return

        midiMappingRepository.removeMapping(target.parameter, target.partIndex)
        _isMidiLearnActive.value = false
        _midiLearnTarget.value = null
        _midiLearnFeedback.value =
            "Mapeamento removido para ${target.parameter.displayName} - Parte ${target.partIndex + 1}"
    }

    fun clearMidiLearnFeedback() {
        _midiLearnFeedback.value = null
    }

    private val _savedPerformances = MutableStateFlow<List<SavedPerformance>>(emptyList())
    val savedPerformances: StateFlow<List<SavedPerformance>> = _savedPerformances.asStateFlow()

    private val _categories = MutableStateFlow<List<String>>(storageRepository.getCategories())
    val categories: StateFlow<List<String>> = _categories.asStateFlow()

    private val _currentLoadedPerformanceId = MutableStateFlow<String?>(null)
    val currentLoadedPerformanceId: StateFlow<String?> = _currentLoadedPerformanceId.asStateFlow()
    val loadedPerformanceId: StateFlow<String?> get() = currentLoadedPerformanceId

    private val _partActivityStates = MutableStateFlow<Map<Int, Boolean>>(emptyMap())
    val partActivityStates: StateFlow<Map<Int, Boolean>> = _partActivityStates.asStateFlow()

    private val activityResetJobs = ConcurrentHashMap<Int, Job>()
    private var persistDebounceJob: Job? = null

    private val _activePolyphony = MutableStateFlow(0)
    val activePolyphony: StateFlow<Int> = _activePolyphony.asStateFlow()

    internal val spilloverRouter = PartSpilloverRouter()

    @Volatile
    private var configuredExternalChannel: Int? = null

    companion object {
        /**
         * Returns the MIDI channel (0..15) on which `CC 7` may safely be sent to change the volume of
         * [partIndex] alone, or `null` when the channel is invalid/Off or shared with any other Part
         * (in which case only the per-part SysEx volume must be used).
         */
        internal fun resolveExclusiveVolumeCcChannel(
            partIndex: Int,
            receiveChannel: Int,
            parts: List<MultiPartState>
        ): Int? {
            if (partIndex !in 0..15 || receiveChannel !in 0..15) return null
            val shared = parts.any { it.partIndex != partIndex && it.receiveChannel == receiveChannel }
            return if (shared) null else receiveChannel
        }

        /**
         * Resolves the distinct MIDI channels (0..15) of all Parts currently active/selected for playing from the
         * main keyboard (Channel 1 = 0): parts routed to Ch 1, parts in the active Layer group, and the focused Part.
         */
        internal fun resolveActiveKeyboardPartChannels(
            parts: List<MultiPartState>,
            focusedPartIndex: Int
        ): Set<Int> {
            val channels = LinkedHashSet<Int>()
            for (part in parts) {
                val isActive = part.receiveChannel == 0 ||
                    part.isLayerSelected ||
                    part.partIndex == focusedPartIndex
                if (!isActive) continue
                val ch = if (part.receiveChannel in 0..15) part.receiveChannel else continue
                channels.add(ch)
            }
            return channels
        }
    }

    init {
        // Load saved performances from local storage
        refreshSavedPerformances()

        // Restore last active performance and focused part state
        restoreLastActiveState()
        spilloverRouter.syncFromParts(_uiState.value.parts)

        // Configure external controller (e.g. M-VAVE SMK25 via BLE, Arturia MiniLab 3 -> Yamaha MX) channel resolver & audible guard
        runCatching {
            midiManager.externalChannelResolver = { incomingChannel ->
                resolveExternalControllerChannel(incomingChannel)
            }
            midiManager.externalChannelAudibleChecker = { targetChannel ->
                isChannelAudibleForExternalController(targetChannel)
            }
        }

        // Auto-close dialogs when Pro is unlocked, or trigger startup paywall if free user
        viewModelScope.launch {
            isProUser.collect { unlocked ->
                if (unlocked) {
                    _showStartupPaywallDialog.value = false
                    if (_showUpgradeDialog.value) {
                        closeUpgradeDialog()
                    }
                } else if (!hasDismissedStartupPaywallInSession) {
                    _showStartupPaywallDialog.value = true
                }
            }
        }

        // Collect MIDI connection manager states
        viewModelScope.launch {
            var wasConnected = false
            midiManager.isConnected.collect { connected ->
                _uiState.update { it.copy(isConnected = connected) }
                if (!connected) {
                    spilloverRouter.reset()
                    _activePolyphony.value = 0
                    _uiState.update { it.copy(activePolyphony = 0) }
                } else if (!wasConnected) {
                    viewModelScope.launch {
                        delay(150L)
                        syncAllPartsToHardware()
                    }
                }
                wasConnected = connected
            }
        }
        viewModelScope.launch {
            var wasUsbConnected = false
            val usbFlow = runCatching { midiManager.isUsbConnectedFlow }.getOrNull()
            usbFlow?.collect { usbConnected ->
                if (usbConnected && !wasUsbConnected) {
                    viewModelScope.launch {
                        delay(180L)
                        syncAllPartsToHardware()
                    }
                }
                wasUsbConnected = usbConnected
            }
        }
        viewModelScope.launch {
            var wasBleConnected = false
            val bleFlow = runCatching { midiManager.isBleConnectedFlow }.getOrNull()
            bleFlow?.collect { bleConnected ->
                if (bleConnected && !wasBleConnected) {
                    viewModelScope.launch {
                        delay(180L)
                        syncAllPartsToHardware()
                    }
                }
                wasBleConnected = bleConnected
            }
        }
        viewModelScope.launch {
            midiManager.connectedDeviceName.collect { devName ->
                _uiState.update { it.copy(connectedDeviceName = devName) }
            }
        }
        viewModelScope.launch {
            midiManager.availableDevices.collect { devList ->
                val safeCopy = try {
                    java.util.Collections.unmodifiableList(ArrayList(devList))
                } catch (_: Throwable) {
                    emptyList()
                }
                _uiState.update { it.copy(availableDevices = safeCopy) }
            }
        }
        viewModelScope.launch {
            midiManager.statusMessage.collect { msg ->
                _uiState.update { it.copy(statusMessage = msg) }
            }
        }
        viewModelScope.launch {
            midiManager.midiActivityChannelFlow.collect { channel ->
                onMidiChannelActivityReceived(channel)
            }
        }
        viewModelScope.launch {
            midiManager.activeNotesCount.collect { count ->
                _activePolyphony.value = count
                _uiState.update { state ->
                    if (state.activePolyphony == count) state else state.copy(activePolyphony = count)
                }
            }
        }
        viewModelScope.launch {
            midiManager.midiVoiceEventFlow.collect { event ->
                when (event) {
                    is MidiVoiceEvent.ControlChange -> {
                        handleIncomingControlChange(event)
                    }
                    is MidiVoiceEvent.ProgramChange -> {
                        onIncomingProgramChangeOrLayer(event.channel, event.program)
                    }
                    is MidiVoiceEvent.LayerActivation -> {
                        onIncomingLayerActivation(event.enabled)
                    }
                    is MidiVoiceEvent.SustainChange -> {
                        handleIncomingSustainChange(event)
                        processSpilloverVoiceEvent(event)
                    }
                    else -> {
                        processSpilloverVoiceEvent(event)
                    }
                }
            }
        }
    }

    /**
     * Resolves the target MIDI channel (0..15) on the Yamaha MX for incoming Note-On, Note-Off,
     * Pitch Bend, and CC messages from external controllers (e.g. M-VAVE SMK25 via BLE or Arturia MiniLab 3 via USB).
     * Ensures notes from the external controller route exclusively to the target channel configured in the app
     * without triggering other parts of the synthesizer.
     */
    internal fun resolveExternalControllerChannel(incomingChannel: Int): Int {
        val safeIncoming = incomingChannel.coerceIn(0, 15)
        val state = _uiState.value
        val parts = state.parts
        if (parts.isEmpty()) return safeIncoming

        // 1. If the external controller (e.g. SMK25) transmits on a specific non-zero channel (Ch 2..16, index 1..15):
        //    Route strictly to that channel (or the configured receiveChannel of that Part in 1..15),
        //    and NEVER fall back to Channel 0 (which would trigger Part 1 / Layer on the main keyboard).
        if (safeIncoming in 1..15) {
            if (parts.any { it.receiveChannel == safeIncoming }) {
                return safeIncoming
            }
            val naturalPart = parts.getOrNull(safeIncoming)
            if (naturalPart != null && naturalPart.receiveChannel in 1..15) {
                return naturalPart.receiveChannel
            }
            return safeIncoming
        }

        // 2. If the user explicitly configured a dedicated external receive channel in the app (Ch 2..16), use it
        val explicitExternalCh = configuredExternalChannel
        if (explicitExternalCh != null && explicitExternalCh in 1..15 && parts.any { it.receiveChannel == explicitExternalCh }) {
            return explicitExternalCh
        }

        // 3. If the currently selected Part in the UI is a secondary Part with a dedicated receiveChannel (1..15), route to it
        val selectedPart = parts.getOrNull(state.selectedPartIndex)
        if (selectedPart != null && selectedPart.receiveChannel in 1..15) {
            return selectedPart.receiveChannel
        }

        // 4. If a secondary Part (index 1..15) has a custom MIDI Learn mapping on an external channel, route to that Part's channel
        val mappedPartIdx = midiMappings.value.entries
            .firstOrNull { (key, cfg) -> !cfg.isDisabled && key.partIndex in 1..15 }
            ?.key?.partIndex
        if (mappedPartIdx != null) {
            val mappedPart = parts.getOrNull(mappedPartIdx)
            if (mappedPart != null && mappedPart.receiveChannel in 1..15) {
                return mappedPart.receiveChannel
            }
        }

        // 5. Fallback to selected or focused Part's configured receiveChannel
        if (selectedPart != null && selectedPart.receiveChannel in 0..15) {
            return selectedPart.receiveChannel
        }

        val focusedPart = parts.getOrNull(state.activeFocusedPartIndex)
        if (focusedPart != null && focusedPart.receiveChannel in 0..15) {
            return focusedPart.receiveChannel
        }

        return safeIncoming
    }

    /**
     * Checks whether at least one Part listening on [targetChannel] is currently active/audible
     * in the app (volume > 0 && !isMuted && (!anySolo || isSolo)).
     */
    internal fun isChannelAudibleForExternalController(targetChannel: Int): Boolean {
        val state = _uiState.value
        val parts = state.parts
        if (parts.isEmpty()) return true
        val anySolo = state.anySoloActive
        val matchingParts = parts.filter { it.receiveChannel == targetChannel }
        if (matchingParts.isNotEmpty()) {
            return matchingParts.any { it.isAudible(anySolo) }
        }
        val fallbackPart = parts.getOrNull(targetChannel) ?: return true
        return fallbackPart.isAudible(anySolo)
    }

    /**
     * Determines whether [part] should have `Receive Expression` (CC 11) enabled (`true`) or disabled (`false`)
     * in the Yamaha MX tone generator:
     * - `Receive Expression = ON` (`true`) EXCLUSIVELY for intentional Layer parts (e.g. Part 2 / Strings/Pad in Layer).
     * - `Receive Expression = OFF` (`false`) for Part 1 (main Piano), fixed Organs (`ORG`), and dedicated external
     *   controller parts (e.g. SMK25) or non-Layer parts.
     */
    internal fun shouldPartReceiveExpression(
        part: MultiPartState,
        common: MultiCommonState = _uiState.value.common
    ): Boolean {
        // Part 1 (index 0, e.g. Piano) must never oscillate with the expression pedal
        if (part.partIndex == 0) return false

        // Fixed organs must never oscillate with the expression pedal
        val cat = part.voice.category.trim()
        val subCat = part.voice.subCategory.trim()
        val voiceName = part.voice.name.trim()
        val isFixedOrgan = cat.equals("ORG", ignoreCase = true) ||
            subCat.contains("Org", ignoreCase = true) ||
            voiceName.contains("Organ", ignoreCase = true)
        if (isFixedOrgan) return false

        // Exclusively intentional Layer parts:
        // - Explicitly selected in Layer (isLayerSelected == true)
        // - Part 2 (partIndex == 1) when Part 1-2 Link is active, routed to Ch 1 (0), or configured with Strings/Pad Layer
        val isPart2Layer = part.partIndex == 1 && (
            common.part12Link ||
                part.receiveChannel == 0 ||
                part.isLayerSelected ||
                cat.equals("STR", ignoreCase = true) ||
                cat.equals("PAD", ignoreCase = true) ||
                subCat.equals("Layer", ignoreCase = true)
            )
        return part.isLayerSelected || isPart2Layer
    }

    /**
     * Dispatches volume changes for any part (Parts 1..16, index 0..15) to the Yamaha MX hardware:
     * - Sends standard Yamaha Part Volume SysEx (`0x37 <part> 0x0E <vol>`) AND native Yamaha MX Part Volume
     *   SysEx (`F0 43 10 7F 17 37 <partByte> 00 00 <volumeByte> F7`), both addressed strictly by base-0 [partIndex].
     * - Sends `CC 7 (Volume)` on the part's receive channel ONLY when no other Part shares that channel
     *   (CC 7 is channel-wide: on a shared channel it would also change the volume of the other Part,
     *   e.g. the Part 3 fader changing Part 2).
     * - Never touches the part's internal audio output switch: muting / zero volume is handled exclusively
     *   via `CC 7 = 0` and the volume SysEx, so release envelopes and long sustains (organs, strings, pads)
     *   are never aborted.
     */
    internal fun dispatchPartVolumeToHardware(
        partIndex: Int,
        targetChannel: Int,
        effectiveVol: Int,
        isFromBluetooth: Boolean = false
    ) {
        if (partIndex !in 0..15) return
        val clampedVol = effectiveVol.coerceIn(0, 127)
        val parts = _uiState.value.parts

        val messages = mutableListOf<Pair<String, ByteArray>>()

        // 1. Yamaha MX Part Volume SysEx (standard 0x0E and native 0x00 0x00), base-0 part addressing
        messages.add("PART_${partIndex}_VOL" to YamahaSysExEncoder.setPartVolume(partIndex, clampedVol))
        messages.add("PART_${partIndex}_VOL_NATIVE" to YamahaSysExEncoder.setPartVolumeNative(partIndex, clampedVol))

        // 2. CC 7 only on a channel exclusively owned by this Part
        val ccChannel = resolveExclusiveVolumeCcChannel(partIndex, targetChannel, parts)
        if (ccChannel != null) {
            messages.add("PART_${partIndex}_CC7" to YamahaSysExEncoder.createControlChangeMessage(ccChannel, 7, clampedVol))
        }

        for ((key, bytes) in messages) {
            if (bytes.isEmpty()) continue
            if (isFromBluetooth) {
                midiManager.sendThrottledUsbOnly(key, bytes)
            } else {
                midiManager.sendThrottled(key, bytes)
            }
        }
    }

    /**
     * Intercepts initial [LAYER] activation from the physical Yamaha MX keyboard.
     * Zero-volume protection acts ONLY on initial hardware [LAYER] activation (`enabled == true`),
     * never against manual user fader/knob/control input.
     */
    fun onIncomingLayerActivation(enabled: Boolean = true) {
        if (enabled) {
            enforcePart2SilenceIfZeroOrMuted()
        }
    }

    fun onIncomingProgramChangeOrLayer(channel: Int, program: Int) {
        // Do not lock or force CC 7 = 0 on normal Program Changes; zero-volume protection acts only on initial [LAYER] activation
    }

    internal fun enforcePart2SilenceIfZeroOrMuted() {
        val state = _uiState.value
        val part2 = state.parts.getOrNull(1) ?: return
        val shouldSilencePart2 = part2.volume == 0 || part2.isMuted || (state.anySoloActive && !part2.isSolo)
        if (shouldSilencePart2) {
            val sysexZeroPart2 = YamahaSysExEncoder.setPartVolume(1, 0)
            val sysexNativeZeroPart2 = YamahaSysExEncoder.setPartVolumeNative(1, 0)
            midiManager.sendImmediate(sysexZeroPart2)
            midiManager.sendImmediate(sysexNativeZeroPart2)
            // CC 7 = 0 only when Part 2 owns its channel exclusively (never silence Part 1 sharing Ch 1)
            val ccChannel = resolveExclusiveVolumeCcChannel(1, part2.receiveChannel, state.parts)
            if (ccChannel != null) {
                midiManager.sendImmediate(YamahaSysExEncoder.createControlChangeMessage(ccChannel, 7, 0))
            }
        }
    }

    private fun handleIncomingControlChange(event: MidiVoiceEvent.ControlChange) {
        // Strict filter: summarily discard any non-Control-Change status byte (e.g. SysEx 0xF0..0xF7, Clock 0xF8, Active Sensing 0xFE)
        if (!MidiMappingRepository.isStrictControlChangeStatus(event.statusByte)) return
        onIncomingControlChange(
            channel = event.channel,
            controller = event.controller,
            value = event.value,
            isFromBluetooth = event.fromBluetooth,
            fromYamahaHardware = event.fromYamahaHardware,
            statusByte = event.statusByte
        )
    }

    /**
     * Handles external MIDI Control Change reception (e.g. from Yamaha MX physical knobs, Arturia MiniLab 3, or Bluetooth SMK25).
     *
     * - Strictly validates that [statusByte] is a Control Change (0xB0..0xBF), summarily discarding SysEx (0xF0..0xF7),
     *   Active Sensing (0xFE), and Clock (0xF8).
     * - Extracts strictly direct absolute values: `ccNumber = controller and 0x7F` (byte1) and `faderValue = value and 0x7F` (byte2, 0..127).
     * - No attenuation filter, no logarithmic scaling, no relative delta (+/- 1), and no rigid zero-volume lock.
     */
    fun onIncomingControlChange(
        channel: Int,
        controller: Int,
        value: Int,
        isFromBluetooth: Boolean = false,
        fromYamahaHardware: Boolean = false,
        statusByte: Int = 0xB0 or (channel and 0x0F)
    ) {
        if (!MidiMappingRepository.isStrictControlChangeStatus(statusByte)) return
        if (channel !in 0..15 || controller !in 0..127) return
        val ccNumber = controller and 0x7F
        val faderValue = (value and 0x7F).coerceIn(0, 127)

        // 1. Interactive MIDI Learn Auto-Capture (strictly 0xB0..0xBF Control Change only)
        if (_isMidiLearnActive.value) {
            val target = _midiLearnTarget.value
            if (target != null) {
                midiMappingRepository.assignMapping(
                    parameter = target.parameter,
                    partIndex = target.partIndex,
                    ccNumber = ccNumber,
                    channel = channel
                )
                _isMidiLearnActive.value = false
                _midiLearnTarget.value = null
                _midiLearnFeedback.value = "Vinculado com sucesso ao CC $ccNumber, Canal ${channel + 1}!"
                // Associate the absolute value of byte2 (0..127) immediately to the mapped parameter
                applyParameterFromExternalMidi(
                    parameter = target.parameter,
                    partIndex = target.partIndex,
                    value = faderValue,
                    isFromBluetooth = isFromBluetooth,
                    fromYamahaHardware = fromYamahaHardware
                )
                return
            }
        }

        // 2. Resolve target(s): Custom mapping priority -> Yamaha Default Fallback
        val resolvedTargets = midiMappingRepository.resolveTargetsForIncomingCc(
            ccNumber = ccNumber,
            channel = channel,
            parts = _uiState.value.parts
        )
        for (target in resolvedTargets) {
            applyParameterFromExternalMidi(
                parameter = target.parameter,
                partIndex = target.partIndex,
                value = faderValue,
                isFromBluetooth = isFromBluetooth,
                fromYamahaHardware = fromYamahaHardware
            )
        }
    }

    private fun applyParameterFromExternalMidi(
        parameter: MidiLearnableParameter,
        partIndex: Int,
        value: Int,
        isFromBluetooth: Boolean,
        fromYamahaHardware: Boolean = false
    ) {
        if (partIndex !in 0..15) return
        val stateBefore = _uiState.value
        val partBefore = stateBefore.parts.getOrNull(partIndex) ?: return
        val targetChannel = if (partBefore.receiveChannel in 0..15) partBefore.receiveChannel else partIndex
        // Direct 1:1 absolute CC value (0..127) without any relative delta or channel 1 delay
        val faderValue = (value and 0x7F).coerceIn(0, 127)

        when (parameter) {
            MidiLearnableParameter.VOLUME -> {
                // When newVolume > 0: immediately update Part state and clear reactive mute if present
                updatePartFromExternalMidi(partIndex) { current ->
                    current.copy(
                        volume = faderValue,
                        isMuted = if (faderValue > 0) false else current.isMuted
                    )
                }
                val stateAfter = _uiState.value
                val updatedPart = stateAfter.parts.getOrNull(partIndex) ?: return
                val effectiveVol = updatedPart.effectiveVolume(stateAfter.anySoloActive)
                dispatchPartVolumeToHardware(
                    partIndex = partIndex,
                    targetChannel = targetChannel,
                    effectiveVol = effectiveVol,
                    isFromBluetooth = isFromBluetooth
                )
            }
            MidiLearnableParameter.PAN -> {
                val panValue = faderValue.coerceIn(1, 127)
                updatePartFromExternalMidi(partIndex) { it.copy(pan = panValue) }
                val sysex = YamahaSysExEncoder.setPartPan(partIndex, panValue)
                val cc = YamahaSysExEncoder.createControlChangeMessage(targetChannel, 10, panValue)
                if (isFromBluetooth) {
                    midiManager.sendThrottledUsbOnly("PART_${partIndex}_PAN", sysex)
                    midiManager.sendThrottledUsbOnly("PART_${partIndex}_CC10", cc)
                } else {
                    midiManager.sendThrottled("PART_${partIndex}_PAN", sysex)
                    midiManager.sendThrottled("PART_${partIndex}_CC10", cc)
                }
            }
            MidiLearnableParameter.MODULATION -> {
                updatePartFromExternalMidi(partIndex) { it.copy(modulation = faderValue) }
                if (partBefore.isModulationEnabled) {
                    val cc = YamahaSysExEncoder.createControlChangeMessage(targetChannel, 1, faderValue)
                    if (isFromBluetooth) {
                        midiManager.sendThrottledUsbOnly("PART_${partIndex}_MOD", cc)
                    } else {
                        midiManager.sendThrottled("PART_${partIndex}_MOD", cc)
                    }
                }
            }
            MidiLearnableParameter.CUTOFF -> {
                updatePartFromExternalMidi(partIndex) { it.copy(cutoff = faderValue) }
                val sysex = YamahaSysExEncoder.setPartCutoff(partIndex, faderValue)
                val cc = YamahaSysExEncoder.createControlChangeMessage(targetChannel, 74, faderValue)
                if (isFromBluetooth) {
                    midiManager.sendThrottledUsbOnly("PART_${partIndex}_CUTOFF", sysex)
                    midiManager.sendThrottledUsbOnly("PART_${partIndex}_CC74", cc)
                } else {
                    midiManager.sendThrottled("PART_${partIndex}_CUTOFF", sysex)
                }
            }
            MidiLearnableParameter.RESONANCE -> {
                updatePartFromExternalMidi(partIndex) { it.copy(resonance = faderValue) }
                val sysex = YamahaSysExEncoder.setPartResonance(partIndex, faderValue)
                val cc = YamahaSysExEncoder.createControlChangeMessage(targetChannel, 71, faderValue)
                if (isFromBluetooth) {
                    midiManager.sendThrottledUsbOnly("PART_${partIndex}_RESO", sysex)
                    midiManager.sendThrottledUsbOnly("PART_${partIndex}_CC71", cc)
                } else {
                    midiManager.sendThrottled("PART_${partIndex}_RESO", sysex)
                }
            }
            MidiLearnableParameter.REVERB_SEND -> {
                updatePartFromExternalMidi(partIndex) { it.copy(reverbSend = faderValue) }
                val sysex = YamahaSysExEncoder.setPartReverbSend(partIndex, faderValue)
                val cc = YamahaSysExEncoder.createControlChangeMessage(targetChannel, 91, faderValue)
                if (isFromBluetooth) {
                    midiManager.sendThrottledUsbOnly("PART_${partIndex}_REV", sysex)
                    midiManager.sendThrottledUsbOnly("PART_${partIndex}_CC91", cc)
                } else {
                    midiManager.sendThrottled("PART_${partIndex}_REV", sysex)
                }
            }
            MidiLearnableParameter.CHORUS_SEND -> {
                updatePartFromExternalMidi(partIndex) { it.copy(chorusSend = faderValue) }
                val sysex = YamahaSysExEncoder.setPartChorusSend(partIndex, faderValue)
                val cc = YamahaSysExEncoder.createControlChangeMessage(targetChannel, 93, faderValue)
                if (isFromBluetooth) {
                    midiManager.sendThrottledUsbOnly("PART_${partIndex}_CHO", sysex)
                    midiManager.sendThrottledUsbOnly("PART_${partIndex}_CC93", cc)
                } else {
                    midiManager.sendThrottled("PART_${partIndex}_CHO", sysex)
                }
            }
            MidiLearnableParameter.ATTACK -> {
                updatePartFromExternalMidi(partIndex) { it.copy(attack = faderValue) }
                val cc = YamahaSysExEncoder.createControlChangeMessage(targetChannel, 73, faderValue)
                if (isFromBluetooth) {
                    midiManager.sendThrottledUsbOnly("PART_${partIndex}_CC73", cc)
                } else {
                    midiManager.sendThrottled("PART_${partIndex}_CC73", cc)
                }
            }
            MidiLearnableParameter.DECAY -> {
                updatePartFromExternalMidi(partIndex) { it.copy(decay = faderValue) }
                val cc = YamahaSysExEncoder.createControlChangeMessage(targetChannel, 75, faderValue)
                if (isFromBluetooth) {
                    midiManager.sendThrottledUsbOnly("PART_${partIndex}_CC75", cc)
                } else {
                    midiManager.sendThrottled("PART_${partIndex}_CC75", cc)
                }
            }
            MidiLearnableParameter.RELEASE -> {
                updatePartFromExternalMidi(partIndex) { it.copy(release = faderValue) }
                val cc = YamahaSysExEncoder.createControlChangeMessage(targetChannel, 72, faderValue)
                if (isFromBluetooth) {
                    midiManager.sendThrottledUsbOnly("PART_${partIndex}_CC72", cc)
                } else {
                    midiManager.sendThrottled("PART_${partIndex}_CC72", cc)
                }
            }
        }
    }

    /**
     * Forwards Sustain Pedal (CC 64) messages originating from the main physical keyboard (Channel 1 / index 0)
     * to all Parts currently active or selected for playing from the main keyboard (active layer parts and focused part)
     * on their respective MIDI channels, ensuring secondary parts (e.g. Part 3 or 4) respond to sustain seamlessly.
     */
    internal fun handleIncomingSustainChange(event: MidiVoiceEvent.SustainChange) {
        try {
            if (event.channel == 0) {
                val state = _uiState.value
                val activeChannels = resolveActiveKeyboardPartChannels(state.parts, state.activeFocusedPartIndex)
                val valByte = if (event.isPressed) 127 else 0
                for (ch in activeChannels) {
                    if (ch != 0) {
                        midiManager.sendImmediate(YamahaSysExEncoder.createControlChangeMessage(ch, 64, valByte))
                    }
                }
            }
        } catch (_: Throwable) {}
    }

    private fun processSpilloverVoiceEvent(event: MidiVoiceEvent) {
        try {
            val state = _uiState.value
            val spilloverMessages = spilloverRouter.onIncomingVoiceEvent(
                event = event,
                currentParts = state.parts,
                focusedPartIndex = state.activeFocusedPartIndex
            )
            for (msg in spilloverMessages) {
                midiManager.sendToUsbOnly(msg)
            }
        } catch (_: Throwable) {}
    }

    private fun onMidiChannelActivityReceived(channel: Int) {
        try {
            val currentParts = _uiState.value.parts
            val activeIndices = mutableListOf<Int>()

            for (part in currentParts) {
                if (channel == 0 && part.receiveChannel == 0) {
                    activeIndices.add(part.partIndex)
                } else if (part.receiveChannel == channel) {
                    activeIndices.add(part.partIndex)
                }
            }

            if (activeIndices.isEmpty() && channel == 0) {
                activeIndices.add(_uiState.value.activeFocusedPartIndex)
            }

            for (idx in activeIndices) {
                _partActivityStates.update { it + (idx to true) }
                activityResetJobs[idx]?.cancel()
                activityResetJobs[idx] = viewModelScope.launch {
                    delay(90L)
                    _partActivityStates.update { it + (idx to false) }
                }
            }
        } catch (_: Throwable) {}
    }

    internal fun handleMidiVoiceEvent(event: MidiVoiceEvent) {
        processSpilloverVoiceEvent(event)
        when (event) {
            is MidiVoiceEvent.NoteOn -> {
                if (event.velocity > 0) {
                    _activePolyphony.update { current ->
                        val next = current + 1
                        _uiState.update { it.copy(activePolyphony = next) }
                        next
                    }
                } else {
                    _activePolyphony.update { current ->
                        val next = (current - 1).coerceAtLeast(0)
                        _uiState.update { it.copy(activePolyphony = next) }
                        next
                    }
                }
            }
            is MidiVoiceEvent.NoteOff -> {
                _activePolyphony.update { current ->
                    val next = (current - 1).coerceAtLeast(0)
                    _uiState.update { it.copy(activePolyphony = next) }
                    next
                }
            }
            is MidiVoiceEvent.AllNotesOff -> {
                _activePolyphony.value = 0
                _uiState.update { it.copy(activePolyphony = 0) }
            }
            is MidiVoiceEvent.SustainChange -> {
                handleIncomingSustainChange(event)
            }
            is MidiVoiceEvent.ControlChange -> {
                // Control Change messages must not interfere with the active notes count
            }
            is MidiVoiceEvent.ProgramChange -> {
                // Program Change messages must not interfere with the active notes count
            }
            is MidiVoiceEvent.LayerActivation -> {
                // Layer activation messages must not interfere with the active notes count
            }
        }
    }

    private fun restoreLastActiveState() {
        val working = storageRepository.getWorkingPerformance()
        val lastFocusedPart = storageRepository.getLastFocusedPartIndex().coerceIn(0, 15)
        val lastId = storageRepository.getLastPerformanceId()

        if (working != null) {
            _currentLoadedPerformanceId.value = if (working.id == "current_session") lastId else working.id
            val restoredParts = (0..15).map { idx ->
                val savedPart = working.parts.firstOrNull { it.partIndex == idx }
                val partState = savedPart?.toMultiPartState() ?: MultiPartState(partIndex = idx)
                if (idx == 0 && (partState.receiveChannel == 127 || partState.receiveChannel !in 0..15)) {
                    partState.copy(receiveChannel = 0)
                } else {
                    partState
                }
            }
            _uiState.update { state ->
                state.copy(
                    parts = restoredParts,
                    common = MultiCommonState(
                        part12Link = working.part12Link,
                        masterVolume = working.masterVolume,
                        performanceName = working.name
                    ),
                    activeFocusedPartIndex = lastFocusedPart,
                    selectedPartIndex = lastFocusedPart
                )
            }
        } else if (lastId != null) {
            val saved = _savedPerformances.value.firstOrNull { it.id == lastId }
            if (saved != null) {
                _currentLoadedPerformanceId.value = saved.id
                val restoredParts = (0..15).map { idx ->
                    val savedPart = saved.parts.firstOrNull { it.partIndex == idx }
                    val partState = savedPart?.toMultiPartState() ?: MultiPartState(partIndex = idx)
                    if (idx == 0 && (partState.receiveChannel == 127 || partState.receiveChannel !in 0..15)) {
                        partState.copy(receiveChannel = 0)
                    } else {
                        partState
                    }
                }
                _uiState.update { state ->
                    state.copy(
                        parts = restoredParts,
                        common = MultiCommonState(
                            part12Link = saved.part12Link,
                            masterVolume = saved.masterVolume,
                            performanceName = saved.name
                        ),
                        activeFocusedPartIndex = lastFocusedPart,
                        selectedPartIndex = lastFocusedPart
                    )
                }
            } else {
                _uiState.update { state ->
                    state.copy(
                        activeFocusedPartIndex = lastFocusedPart,
                        selectedPartIndex = lastFocusedPart
                    )
                }
            }
        } else {
            _uiState.update { state ->
                state.copy(
                    activeFocusedPartIndex = lastFocusedPart,
                    selectedPartIndex = lastFocusedPart
                )
            }
        }
    }

    fun selectPart(partIndex: Int, focusLive: Boolean = false) {
        if (focusLive) {
            switchLiveFocusedPart(partIndex)
        } else {
            if (partIndex !in 0..15) return
            if (!isProUser.value && partIndex >= 2) {
                openUpgradeDialog()
                return
            }
            val targetPart = _uiState.value.parts.getOrNull(partIndex)
            if (targetPart != null && targetPart.receiveChannel in 1..15) {
                configuredExternalChannel = targetPart.receiveChannel
            }
            _uiState.update { it.copy(selectedPartIndex = partIndex) }
        }
    }

    fun scanDevices() {
        try {
            midiManager.scanAndAutoConnect(retryCount = 3)
        } catch (_: Throwable) {}
    }

    fun onUsbDeviceAttached(device: UsbDevice?) {
        try {
            midiManager.onUsbDeviceAttached(device)
        } catch (_: Throwable) {}
    }

    fun requestUsbPermission(device: UsbDevice) {
        try {
            midiManager.requestUsbPermission(device)
        } catch (_: Throwable) {}
    }

    fun connectDevice(device: MidiDeviceInfoWrapper) {
        if (device.isBluetooth && !isProUser.value) {
            openUpgradeDialog()
            return
        }
        try {
            midiManager.connectToDevice(device)
        } catch (_: Throwable) {}
    }

    fun disconnectDevice() {
        try {
            midiManager.disconnect()
        } catch (_: Throwable) {}
    }

    // --- Performance Storage / Preset CRUD Operations ---

    fun refreshSavedPerformances() {
        _savedPerformances.value = storageRepository.getAllPerformances()
    }

    fun saveCurrentPerformance(name: String, tag: String, saveAsNew: Boolean = false) {
        val currentId = if (saveAsNew) null else _currentLoadedPerformanceId.value
        if (currentId == null && !isProUser.value && _savedPerformances.value.size >= 2) {
            openUpgradeDialog()
            return
        }
        val performance = if (currentId != null) {
            val existing = _savedPerformances.value.firstOrNull { it.id == currentId }
            val finalTag = tag.ifBlank { existing?.categoryTag ?: "Geral" }
            SavedPerformance.fromState(
                name = name.ifBlank { existing?.name ?: "Performance sem nome" },
                categoryTag = finalTag,
                state = _uiState.value,
                id = currentId
            )
        } else {
            SavedPerformance.fromState(
                name = name.ifBlank { "Performance sem nome" },
                categoryTag = tag.ifBlank { "Geral" },
                state = _uiState.value,
                id = UUID.randomUUID().toString()
            )
        }
        storageRepository.savePerformance(performance)
        _currentLoadedPerformanceId.value = performance.id
        _uiState.update { it.copy(common = it.common.copy(performanceName = performance.name)) }
        storageRepository.saveLastActiveState(performance.id, _uiState.value.activeFocusedPartIndex)
        persistCurrentWorkingState()
        refreshSavedPerformances()
    }

    fun updateCurrentSavedPerformance() {
        val currentId = _currentLoadedPerformanceId.value
        if (currentId != null) {
            val existing = _savedPerformances.value.firstOrNull { it.id == currentId }
            if (existing != null) {
                saveCurrentPerformance(existing.name, existing.categoryTag, saveAsNew = false)
                return
            }
        }
        saveCurrentPerformance("Minha Performance", "Geral", saveAsNew = true)
    }

    fun recarregarPerformance(performanceId: String? = _currentLoadedPerformanceId.value) {
        val targetId = performanceId ?: _currentLoadedPerformanceId.value
        val targetPerf = if (targetId != null) {
            _savedPerformances.value.firstOrNull { it.id == targetId }
                ?: storageRepository.getAllPerformances().firstOrNull { it.id == targetId }
        } else {
            storageRepository.getWorkingPerformance()
        }
        if (targetPerf != null) {
            loadPerformance(targetPerf)
        } else {
            refreshSavedPerformances()
        }
    }

    fun loadPerformance(performance: SavedPerformance) {
        _currentLoadedPerformanceId.value = performance.id
        storageRepository.saveLastActiveState(performance.id, _uiState.value.activeFocusedPartIndex)

        // 1. Update UI state in app - explicitly guarantee all 16 parts in order
        val restoredParts = (0..15).map { idx ->
            val savedPart = performance.parts.firstOrNull { it.partIndex == idx }
            val partState = savedPart?.toMultiPartState() ?: MultiPartState(partIndex = idx)
            if (idx == 0 && (partState.receiveChannel == 127 || partState.receiveChannel !in 0..15)) {
                partState.copy(receiveChannel = 0)
            } else {
                partState
            }
        }
        spilloverRouter.syncFromParts(restoredParts)
        _uiState.update { state ->
            state.copy(
                parts = restoredParts,
                common = MultiCommonState(
                    part12Link = performance.part12Link,
                    masterVolume = performance.masterVolume,
                    performanceName = performance.name,
                    masterEqGains = performance.masterEqGains
                ),
                selectedPartIndex = state.selectedPartIndex.coerceIn(0, 15),
                activeFocusedPartIndex = state.activeFocusedPartIndex.coerceIn(0, 15)
            )
        }
        persistCurrentWorkingState()

        // 2. Transmit batch SysEx and MIDI parameters to Yamaha MX hardware
        syncAllPartsToHardware()
    }

    /**
     * Synchronizes all 16 parts and common parameters from the app to the physical Yamaha MX synthesizer.
     * Transmits:
     * - Safety Flush
     * - Part 1-2 Link status
     * - For each of the 16 parts:
     *   * Receive Channel
     *   * Voice Change (Bank MSB, Bank LSB, Program Change)
     *   * Volume and Pan
     *   * Note Limit Low & High (Splits)
     *   * Reverb & Chorus Sends
     *   * Note Shift / Transpose
     *   * Filter Cutoff & Resonance
     * Uses 15ms spacing to guarantee buffer safety on the Yamaha MX hardware.
     */
    fun syncAllPartsToHardware() {
        val currentState = _uiState.value
        spilloverRouter.syncFromParts(currentState.parts)
        if (!currentState.isConnected) return

        // 1. Safety Flush
        midiManager.sendSafetyFlush()

        val batchMessages = mutableListOf<ByteArray>()

        // Ensure Part 1-2 Link status is transmitted
        batchMessages.add(YamahaSysExEncoder.setPart12Link(currentState.common.part12Link))

        // Master 5-Band EQ Gains (Multi Common 0x36 0x10 [0x00, 0x04, 0x08, 0x0C, 0x10])
        currentState.common.masterEqGains.forEachIndexed { bandIdx, gain ->
            if (bandIdx in 0..4) {
                batchMessages.add(YamahaSysExEncoder.setMasterEqBandGain(bandIdx, gain))
            }
        }

        // Multi Part Parameters
        currentState.parts.forEach { part ->
            // 1. Receive Channel (Part 1 is strictly protected from being sent Off 127)
            val rxCh = if (part.partIndex == 0 && (part.receiveChannel == 127 || part.receiveChannel !in 0..15)) 0 else part.receiveChannel
            batchMessages.add(YamahaSysExEncoder.setPartReceiveChannel(part.partIndex, rxCh))

            // 2. Voice Change (Bank MSB, Bank LSB, Program Change)
            val voiceChangeMsgs = YamahaSysExEncoder.createVoiceChangeMessages(
                partIndex = part.partIndex,
                msb = part.voice.msb,
                lsb = part.voice.lsb,
                programChange = part.voice.programChange
            )
            batchMessages.addAll(voiceChangeMsgs)

            // 3. Volume (if muted or silenced by solo, 0; else part volume) via per-part SysEx (standard + native).
            //    CC 7 only when the Part owns its channel exclusively (never overwrite another Part on a shared channel).
            val effectiveVol = part.effectiveVolume(currentState.anySoloActive)
            batchMessages.add(YamahaSysExEncoder.setPartVolume(part.partIndex, effectiveVol))
            batchMessages.add(YamahaSysExEncoder.setPartVolumeNative(part.partIndex, effectiveVol))
            resolveExclusiveVolumeCcChannel(part.partIndex, part.receiveChannel, currentState.parts)?.let { ch ->
                batchMessages.add(YamahaSysExEncoder.createControlChangeMessage(ch, 7, effectiveVol))
            }

            // 3b. Receive Sustain (CC 64) = ON on every Part so any Part selected for playing responds to the pedal
            batchMessages.add(YamahaSysExEncoder.setPartSustainReceiveSwitch(part.partIndex, true))

            // 4. Pan
            batchMessages.add(YamahaSysExEncoder.setPartPan(part.partIndex, part.pan))

            // 5. Note Limits (Splits)
            batchMessages.add(YamahaSysExEncoder.setPartNoteLimitLow(part.partIndex, part.noteLimitLow))
            batchMessages.add(YamahaSysExEncoder.setPartNoteLimitHigh(part.partIndex, part.noteLimitHigh))

            // 6. Effects: Reverb & Chorus Sends
            batchMessages.add(YamahaSysExEncoder.setPartReverbSend(part.partIndex, part.reverbSend))
            batchMessages.add(YamahaSysExEncoder.setPartChorusSend(part.partIndex, part.chorusSend))

            // 7. Note Shift / Transpose
            batchMessages.add(YamahaSysExEncoder.setPartNoteShift(part.partIndex, part.noteShift))

            // 8. Filter Cutoff & Resonance
            batchMessages.add(YamahaSysExEncoder.setPartCutoff(part.partIndex, part.cutoff))
            batchMessages.add(YamahaSysExEncoder.setPartResonance(part.partIndex, part.resonance))

            // 8b. Envelope (AEG): Attack (CC 73), Decay (CC 75), Release (CC 72)
            if (part.receiveChannel in 0..15 && (part.attack != 64 || part.decay != 64 || part.release != 64)) {
                batchMessages.add(YamahaSysExEncoder.createControlChangeMessage(part.receiveChannel, 73, part.attack))
                batchMessages.add(YamahaSysExEncoder.createControlChangeMessage(part.receiveChannel, 75, part.decay))
                batchMessages.add(YamahaSysExEncoder.createControlChangeMessage(part.receiveChannel, 72, part.release))
            }

            // 9. Expression Pedal (CC 11) Isolation:
            //    ON exclusively for intentional Layer parts (e.g. Part 2 / Strings);
            //    OFF for Part 1 (Piano), fixed Organs, and external controller parts (e.g. SMK25).
            val rcvExpr = shouldPartReceiveExpression(part, currentState.common)
            batchMessages.add(YamahaSysExEncoder.setPartExpressionReceiveSwitch(part.partIndex, rcvExpr))

            // 10. Insertion Effect / Compressor Parameters & Type
            if (part.isCompressorEnabled) {
                batchMessages.addAll(YamahaSysExEncoder.createSetPartCompressorTypeMessages(part.partIndex, isVcm = true))
                batchMessages.addAll(YamahaSysExEncoder.setPartInsertionSwitch(part.partIndex, true))
                batchMessages.addAll(YamahaSysExEncoder.setPartCompressorThresholdAll(part.partIndex, part.compressorThreshold))
                batchMessages.addAll(YamahaSysExEncoder.setPartCompressorOutputGainAll(part.partIndex, part.compressorOutputGain))
                batchMessages.addAll(YamahaSysExEncoder.setPartCompressorRatioAll(part.partIndex, part.compressorRatio))
                batchMessages.addAll(YamahaSysExEncoder.setPartCompressorAttackAll(part.partIndex, part.compressorAttack))
                batchMessages.addAll(YamahaSysExEncoder.setPartCompressorReleaseAll(part.partIndex, part.compressorRelease))
            } else {
                batchMessages.addAll(YamahaSysExEncoder.setPartInsertionSwitch(part.partIndex, false))
            }
        }

        // 11. Multi Common Insertion Part Switch (bitmask for Parts 1..16)
        val enabledInsertParts = currentState.parts.filter { it.isCompressorEnabled }.map { it.partIndex }.toSet()
        batchMessages.add(YamahaSysExEncoder.createInsertionPartSwitchMessage(enabledInsertParts))

        // Send all messages in sequence with 15ms safe spacing
        midiManager.sendSequence(batchMessages, delayMs = 15L)
    }

    fun deletePerformance(id: String) {
        if (_currentLoadedPerformanceId.value == id) {
            _currentLoadedPerformanceId.value = null
        }
        storageRepository.deletePerformance(id)
        refreshSavedPerformances()
    }

    fun duplicatePerformance(performance: SavedPerformance) {
        if (!isProUser.value && _savedPerformances.value.size >= 2) {
            openUpgradeDialog()
            return
        }
        storageRepository.duplicatePerformance(performance)
        refreshSavedPerformances()
    }

    fun renamePerformance(id: String, newName: String, newTag: String) {
        storageRepository.renamePerformance(id, newName, newTag)
        if (_currentLoadedPerformanceId.value == id) {
            _uiState.update { it.copy(common = it.common.copy(performanceName = newName)) }
        }
        refreshSavedPerformances()
    }

    fun renameCategory(oldCategory: String, newCategory: String): Boolean {
        val success = storageRepository.renameCategory(oldCategory, newCategory)
        if (success) {
            _categories.value = storageRepository.getCategories()
            refreshSavedPerformances()
        }
        return success
    }

    fun exportSetlistJson(): String {
        return storageRepository.exportJson()
    }

    fun validateSetlistJson(jsonString: String): List<SavedPerformance>? {
        return storageRepository.parseJson(jsonString)
    }

    fun importSetlistJson(jsonString: String, replaceAll: Boolean = false): Int {
        val count = storageRepository.importJson(jsonString, replaceAll)
        if (count > 0) {
            if (replaceAll) {
                _currentLoadedPerformanceId.value = null
            }
            refreshSavedPerformances()
        }
        return count
    }

    // --- Live Part Switch / Foco Rápido de Timbre & Seamless Sound Spillover ---

    /**
     * Switches the active live playing part (focused part) to targetIndex with Seamless Sound Spillover:
     * - Does NOT send CC 120 (All Sound Off), CC 123 (All Notes Off), or CC 64=0 (Sustain Off),
     *   allowing held keys and sustain tails on the previous part to decay naturally.
     * - Ensures Part 1-2 Link is disabled in hardware.
     * - Couples targetIndex (and any active Layer group parts) to physical keyboard keys (Channel 1 = 0x00).
     * - Keeps outgoing parts with pending held notes or sustain on a valid spillover channel (1..15)
     *   until their physical keys and sustain pedal are released.
     */
    fun switchLiveFocusedPart(targetIndex: Int) {
        if (targetIndex !in 0..15) return
        if (!isProUser.value && targetIndex >= 2) {
            openUpgradeDialog()
            return
        }

        // 1. Ensure Part 1-2 Link is OFF in hardware
        if (_uiState.value.common.part12Link) {
            _uiState.update { it.copy(common = it.common.copy(part12Link = false)) }
        }
        midiManager.sendImmediate(YamahaSysExEncoder.setPart12Link(false))

        // 2. Determine all parts that should now be coupled to the physical keyboard (Ch 1 = 0)
        val currentParts = _uiState.value.parts
        val layerIndices = currentParts.filter { it.isLayerSelected && it.partIndex != targetIndex }.map { it.partIndex }.toSet()
        val activeCh0Indices = setOf(targetIndex) + layerIndices

        // 3. Compute seamless Receive Channel transition without cutting sounding voices
        val transition = spilloverRouter.computeSeamlessPartSwitch(
            currentParts = currentParts,
            activeCh0PartIndices = activeCh0Indices
        )

        val updatedParts = currentParts.map { part ->
            val idx = part.partIndex
            part.copy(receiveChannel = transition.updatedReceiveChannels[idx])
        }

        val commonNow = _uiState.value.common
        val routingAndExprMessages = buildList {
            addAll(transition.midiMessages)
            updatedParts.forEach { part ->
                add(YamahaSysExEncoder.setPartExpressionReceiveSwitch(part.partIndex, shouldPartReceiveExpression(part, commonNow)))
                add(YamahaSysExEncoder.setPartSustainReceiveSwitch(part.partIndex, true))
            }
        }
        if (routingAndExprMessages.isNotEmpty()) {
            midiManager.sendKeyedSequence("CHANNEL_ROUTING", routingAndExprMessages, delayMs = 2L)
        }

        _uiState.update { state ->
            state.copy(
                parts = updatedParts,
                activeFocusedPartIndex = targetIndex,
                selectedPartIndex = targetIndex
            )
        }
        persistCurrentWorkingState()

        storageRepository.saveLastActiveState(_currentLoadedPerformanceId.value, targetIndex)
    }

    fun setPartReceiveChannel(partIndex: Int, channel: Int) {
        val oldChannel = _uiState.value.parts[partIndex].receiveChannel
        val clampedChannel = if (channel in 0..15) channel else 127
        if (clampedChannel in 1..15) {
            configuredExternalChannel = clampedChannel
        }

        // 1. Safety Flush: kill any sounding notes or sustain on old and new channels
        midiManager.sendSafetyFlush()

        // 2. Update UI state
        updatePart(partIndex) { it.copy(receiveChannel = clampedChannel) }
        spilloverRouter.syncFromParts(_uiState.value.parts)

        // 3. Send safe channel change sequence with flushes + Receive Expression isolation
        val updatedPart = _uiState.value.parts.getOrNull(partIndex)
        val rcvExprMsg = updatedPart?.let {
            YamahaSysExEncoder.setPartExpressionReceiveSwitch(partIndex, shouldPartReceiveExpression(it, _uiState.value.common))
        }
        val messages = buildList {
            addAll(
                YamahaSysExEncoder.createSafeReceiveChannelChangeSequence(
                    partIndex = partIndex,
                    oldChannel = oldChannel,
                    newChannel = clampedChannel
                )
            )
            add(YamahaSysExEncoder.setPartSustainReceiveSwitch(partIndex, true))
            if (rcvExprMsg != null) add(rcvExprMsg)
        }
        midiManager.sendKeyedSequence("CHANNEL_ROUTING_$partIndex", messages, delayMs = 5L)
    }

    /**
     * Toggles a part's inclusion in an active Dual/Multi voice Layer group with Seamless Sound Spillover:
     * - Does NOT send CC 120, CC 123, or CC 64=0 when adding or removing parts from the Layer.
     * - Disables Part 1-2 Link in hardware.
     * - Routes layered and focused parts to Channel 1 (0x00) while preserving spillover on outgoing parts.
     * - Strictly preserves Part 1 (partIndex == 0) on Channel 1 (rxChannel = 0).
     */
    fun togglePartLayerSelection(partIndex: Int) {
        if (partIndex !in 0..15) return
        if (partIndex == 0) {
            // Part 1 is the permanent base keyboard voice on Yamaha MX.
            // It remains on Channel 1 (rxChannel = 0) and cannot be toggled into/out of layer.
            return
        }
        if (!isProUser.value && partIndex >= 2) {
            openUpgradeDialog()
            return
        }

        val currentParts = _uiState.value.parts
        val newLayerSelected = !currentParts[partIndex].isLayerSelected

        // 1. Proactively ensure Part 1-2 Link is OFF in Yamaha MX hardware
        if (_uiState.value.common.part12Link) {
            _uiState.update { it.copy(common = it.common.copy(part12Link = false)) }
        }
        midiManager.sendImmediate(YamahaSysExEncoder.setPart12Link(false))

        // 2. Compute updated layer membership for all parts
        val updatedPartsState = currentParts.map { part ->
            if (part.partIndex == partIndex) {
                part.copy(isLayerSelected = newLayerSelected)
            } else {
                part
            }
        }

        // 3. Determine all parts coupled to physical keyboard keys (Ch 1 = 0x00):
        // Keyboard couples the currently focused part plus any parts marked with Layer.
        val focusedPart = _uiState.value.activeFocusedPartIndex
        val layerIndices = updatedPartsState.filter { it.isLayerSelected && it.partIndex != focusedPart }.map { it.partIndex }.toSet()
        val activeCh0Indices = setOf(focusedPart) + layerIndices

        // 4. Compute seamless Receive Channel transition without cutting sounding voices
        val transition = spilloverRouter.computeSeamlessPartSwitch(
            currentParts = updatedPartsState,
            activeCh0PartIndices = activeCh0Indices
        )

        val finalParts = updatedPartsState.map { part ->
            val idx = part.partIndex
            part.copy(receiveChannel = transition.updatedReceiveChannels[idx])
        }

        // 5. Transmit MIDI/SysEx sequence
        val commonNow = _uiState.value.common
        val routingAndExprMessages = buildList {
            addAll(transition.midiMessages)
            finalParts.forEach { part ->
                add(YamahaSysExEncoder.setPartExpressionReceiveSwitch(part.partIndex, shouldPartReceiveExpression(part, commonNow)))
                add(YamahaSysExEncoder.setPartSustainReceiveSwitch(part.partIndex, true))
            }
        }
        if (routingAndExprMessages.isNotEmpty()) {
            midiManager.sendKeyedSequence("CHANNEL_ROUTING", routingAndExprMessages, delayMs = 2L)
        }

        // 6. Update UI State
        _uiState.update { state ->
            state.copy(
                parts = finalParts
            )
        }
        persistCurrentWorkingState()

        storageRepository.saveLastActiveState(_currentLoadedPerformanceId.value, _uiState.value.activeFocusedPartIndex)
    }

    fun togglePartLayer(partIndex: Int) {
        togglePartLayerSelection(partIndex)
    }

    fun setPartLayer(partIndex: Int, isLayer: Boolean) {
        if (partIndex !in 0..15) return
        if (partIndex == _uiState.value.activeFocusedPartIndex) return
        if (!isProUser.value && partIndex >= 2) {
            openUpgradeDialog()
            return
        }
        val currentPart = _uiState.value.parts.getOrNull(partIndex) ?: return
        if (currentPart.isLayerSelected == isLayer) return
        togglePartLayerSelection(partIndex)
    }

    fun syncRxChannels() {
        val currentParts = _uiState.value.parts
        val focusedPart = _uiState.value.activeFocusedPartIndex
        val activeKeyboardParts = (setOf(focusedPart) + currentParts.filter { it.isLayerSelected && it.partIndex != focusedPart }.map { it.partIndex }).toSet()

        val updatedParts = currentParts.map { part ->
            when {
                part.partIndex in activeKeyboardParts -> {
                    part.copy(receiveChannel = 0)
                }
                part.receiveChannel == 0 -> {
                    // Exiting keyboard focus / layer: restore to natural channel (or 127 for Part 1 when not active on keyboard)
                    val naturalCh = if (part.partIndex == 0) 127 else part.partIndex
                    part.copy(receiveChannel = naturalCh)
                }
                else -> part
            }
        }

        val messages = mutableListOf<ByteArray>()
        updatedParts.forEach { part ->
            messages.add(YamahaSysExEncoder.setPartReceiveChannel(part.partIndex, part.receiveChannel))
        }

        spilloverRouter.syncFromParts(updatedParts)
        midiManager.sendKeyedSequence("CHANNEL_ROUTING", messages, delayMs = 2L)
        _uiState.update { it.copy(parts = updatedParts) }
        persistCurrentWorkingState()
    }

    // --- Voice Selection with Immediate SysEx Transmission ---

    // --- Voice Selection & Audition / Preview Mode ---

    private val _voicePreviewInitialVoices = mutableMapOf<Int, YamahaVoice>()
    private val _previewVoiceState = MutableStateFlow<Map<Int, YamahaVoice>>(emptyMap())
    val previewVoiceState: StateFlow<Map<Int, YamahaVoice>> = _previewVoiceState.asStateFlow()

    fun previewVoice(partIndex: Int, voice: YamahaVoice) {
        if (partIndex !in 0..15) return
        if (!isProUser.value && partIndex >= 2) {
            openUpgradeDialog()
            return
        }

        // Store initial voice before first preview if not already tracked
        if (!_voicePreviewInitialVoices.containsKey(partIndex)) {
            _voicePreviewInitialVoices[partIndex] = _uiState.value.parts[partIndex].voice
        }
        _previewVoiceState.update { it + (partIndex to voice) }

        // Update UI state in memory WITHOUT persisting to Room/JSON
        _uiState.update { state ->
            val updated = state.parts.mapIndexed { idx, part ->
                if (idx == partIndex) part.copy(voice = voice) else part
            }
            state.copy(parts = updated)
        }

        val updatedPart = _uiState.value.parts.getOrNull(partIndex)
        val exprSwitchMsg = updatedPart?.let {
            YamahaSysExEncoder.setPartExpressionReceiveSwitch(partIndex, shouldPartReceiveExpression(it, _uiState.value.common))
        }
        if (exprSwitchMsg != null) {
            midiManager.sendImmediate(exprSwitchMsg)
        }

        // Immediately dispatch MIDI / SysEx Voice Change messages to physical synthesizer
        // Cancels any superseded pending preview sequence for this part
        val messages = buildList {
            addAll(
                YamahaSysExEncoder.createVoiceChangeMessages(
                    partIndex = partIndex,
                    msb = voice.msb,
                    lsb = voice.lsb,
                    programChange = voice.programChange
                )
            )
            add(YamahaSysExEncoder.setPartSustainReceiveSwitch(partIndex, true))
            if (exprSwitchMsg != null) add(exprSwitchMsg)
        }
        midiManager.sendKeyedSequence("VOICE_CHANGE_$partIndex", messages, delayMs = 15L)
    }

    fun confirmVoiceSelection(partIndex: Int, voice: YamahaVoice) {
        if (partIndex !in 0..15) return
        if (!isProUser.value && partIndex >= 2) {
            openUpgradeDialog()
            return
        }

        _voicePreviewInitialVoices.remove(partIndex)
        _previewVoiceState.update { it - partIndex }

        val currentVoice = _uiState.value.parts.getOrNull(partIndex)?.voice
        if (currentVoice != voice) {
            setPartVoice(partIndex, voice)
        } else {
            updatePart(partIndex) { it.copy(voice = voice) }
            val updatedPart = _uiState.value.parts.getOrNull(partIndex) ?: return
            val exprSwitchMsg = YamahaSysExEncoder.setPartExpressionReceiveSwitch(
                partIndex,
                shouldPartReceiveExpression(updatedPart, _uiState.value.common)
            )
            midiManager.sendImmediate(exprSwitchMsg)
        }
    }

    fun cancelVoiceSelection(partIndex: Int, fallbackInitialVoice: YamahaVoice? = null) {
        if (partIndex !in 0..15) return
        val originalVoice = _voicePreviewInitialVoices.remove(partIndex) ?: fallbackInitialVoice
        _previewVoiceState.update { it - partIndex }

        if (originalVoice != null) {
            val currentVoice = _uiState.value.parts.getOrNull(partIndex)?.voice
            if (currentVoice != originalVoice) {
                _uiState.update { state ->
                    val updated = state.parts.mapIndexed { idx, part ->
                        if (idx == partIndex) part.copy(voice = originalVoice) else part
                    }
                    state.copy(parts = updated)
                }
                persistCurrentWorkingState()

                val updatedPart = _uiState.value.parts.getOrNull(partIndex)
                val exprSwitchMsg = updatedPart?.let {
                    YamahaSysExEncoder.setPartExpressionReceiveSwitch(partIndex, shouldPartReceiveExpression(it, _uiState.value.common))
                }
                if (exprSwitchMsg != null) {
                    midiManager.sendImmediate(exprSwitchMsg)
                }

                val messages = buildList {
                    addAll(
                        YamahaSysExEncoder.createVoiceChangeMessages(
                            partIndex = partIndex,
                            msb = originalVoice.msb,
                            lsb = originalVoice.lsb,
                            programChange = originalVoice.programChange
                        )
                    )
                    if (exprSwitchMsg != null) add(exprSwitchMsg)
                }
                midiManager.sendKeyedSequence("VOICE_CHANGE_$partIndex", messages, delayMs = 15L)
            }
        }
    }

    fun setPartVoice(partIndex: Int, voice: YamahaVoice) {
        if (!isProUser.value && partIndex >= 2) {
            openUpgradeDialog()
            return
        }
        _voicePreviewInitialVoices.remove(partIndex)
        _previewVoiceState.update { it - partIndex }

        updatePart(partIndex) { it.copy(voice = voice) }

        val updatedPart = _uiState.value.parts.getOrNull(partIndex)
        val exprSwitchMsg = updatedPart?.let {
            YamahaSysExEncoder.setPartExpressionReceiveSwitch(partIndex, shouldPartReceiveExpression(it, _uiState.value.common))
        }
        if (exprSwitchMsg != null) {
            midiManager.sendImmediate(exprSwitchMsg)
        }

        // Send Bank MSB, Bank LSB, Program Change, and Expression Receive Switch sequence in strict order
        val messages = buildList {
            addAll(
                YamahaSysExEncoder.createVoiceChangeMessages(
                    partIndex = partIndex,
                    msb = voice.msb,
                    lsb = voice.lsb,
                    programChange = voice.programChange
                )
            )
            add(YamahaSysExEncoder.setPartSustainReceiveSwitch(partIndex, true))
            if (exprSwitchMsg != null) add(exprSwitchMsg)
        }
        midiManager.sendKeyedSequence("VOICE_CHANGE_$partIndex", messages, delayMs = 15L)
    }

    // --- Parameter Modifiers with SysEx Transmission ---

    fun setPartVolume(partIndex: Int, volume: Int) {
        if (!isProUser.value && partIndex >= 2) {
            openUpgradeDialog()
            return
        }
        val clamped = volume.coerceIn(0, 127)
        // When newVolume > 0: immediately update Part state and clear reactive mute if present
        updatePart(partIndex) { current ->
            current.copy(
                volume = clamped,
                isMuted = if (clamped > 0) false else current.isMuted
            )
        }

        val state = _uiState.value
        val part = state.parts.getOrNull(partIndex) ?: return
        val anySolo = state.anySoloActive
        val effectiveVol = part.effectiveVolume(anySolo)
        val targetChannel = if (part.receiveChannel in 0..15) part.receiveChannel else partIndex

        dispatchPartVolumeToHardware(
            partIndex = partIndex,
            targetChannel = targetChannel,
            effectiveVol = effectiveVol,
            isFromBluetooth = false
        )
    }

    fun setPartPan(partIndex: Int, pan: Int) {
        val clamped = pan.coerceIn(1, 127)
        updatePart(partIndex) { it.copy(pan = clamped) }

        val sysex = YamahaSysExEncoder.setPartPan(partIndex, clamped)
        midiManager.sendThrottled("PART_${partIndex}_PAN", sysex)
    }

    fun setPartReverbSend(partIndex: Int, reverb: Int) {
        val clamped = reverb.coerceIn(0, 127)
        updatePart(partIndex) { it.copy(reverbSend = clamped) }

        val sysex = YamahaSysExEncoder.setPartReverbSend(partIndex, clamped)
        midiManager.sendThrottled("PART_${partIndex}_REV", sysex)
    }

    fun setPartChorusSend(partIndex: Int, chorus: Int) {
        val clamped = chorus.coerceIn(0, 127)
        updatePart(partIndex) { it.copy(chorusSend = clamped) }

        val sysex = YamahaSysExEncoder.setPartChorusSend(partIndex, clamped)
        midiManager.sendThrottled("PART_${partIndex}_CHO", sysex)
    }

    fun setPartNoteShift(partIndex: Int, shift: Int) {
        val clamped = shift.coerceIn(40, 88)
        updatePart(partIndex) { it.copy(noteShift = clamped) }

        val sysex = YamahaSysExEncoder.setPartNoteShift(partIndex, clamped)
        midiManager.sendThrottled("PART_${partIndex}_SHIFT", sysex)
    }

    fun shiftPartOctave(partIndex: Int, octaveDelta: Int) {
        val part = _uiState.value.parts.getOrNull(partIndex) ?: return
        val newShift = if (octaveDelta == 0) {
            64
        } else {
            (part.noteShift + (octaveDelta * 12)).coerceIn(40, 88)
        }
        setPartNoteShift(partIndex, newShift)
    }

    fun setPartNoteLimitLow(partIndex: Int, noteLow: Int) {
        val currentHigh = _uiState.value.parts[partIndex].noteLimitHigh
        val clampedLow = noteLow.coerceIn(0, currentHigh)
        updatePart(partIndex) { it.copy(noteLimitLow = clampedLow) }

        val sysex = YamahaSysExEncoder.setPartNoteLimitLow(partIndex, clampedLow)
        midiManager.sendThrottled("PART_${partIndex}_NOTE_LOW", sysex)
    }

    fun setPartNoteLimitHigh(partIndex: Int, noteHigh: Int) {
        val currentLow = _uiState.value.parts[partIndex].noteLimitLow
        val clampedHigh = noteHigh.coerceIn(currentLow, 127)
        updatePart(partIndex) { it.copy(noteLimitHigh = clampedHigh) }

        val sysex = YamahaSysExEncoder.setPartNoteLimitHigh(partIndex, clampedHigh)
        midiManager.sendThrottled("PART_${partIndex}_NOTE_HIGH", sysex)
    }

    fun setPartNoteLimits(partIndex: Int, low: Int, high: Int) {
        val safeLow = low.coerceIn(0, 127)
        val safeHigh = high.coerceIn(safeLow, 127)
        updatePart(partIndex) { it.copy(noteLimitLow = safeLow, noteLimitHigh = safeHigh) }

        val sysexLow = YamahaSysExEncoder.setPartNoteLimitLow(partIndex, safeLow)
        val sysexHigh = YamahaSysExEncoder.setPartNoteLimitHigh(partIndex, safeHigh)
        midiManager.sendThrottled("PART_${partIndex}_NOTE_LOW", sysexLow)
        midiManager.sendThrottled("PART_${partIndex}_NOTE_HIGH", sysexHigh)
    }

    fun setPartCutoff(partIndex: Int, cutoff: Int) {
        val clamped = cutoff.coerceIn(0, 127)
        updatePart(partIndex) { it.copy(cutoff = clamped) }

        val sysex = YamahaSysExEncoder.setPartCutoff(partIndex, clamped)
        midiManager.sendThrottled("PART_${partIndex}_CUTOFF", sysex)
    }

    fun setPartResonance(partIndex: Int, resonance: Int) {
        val clamped = resonance.coerceIn(0, 127)
        updatePart(partIndex) { it.copy(resonance = clamped) }

        val sysex = YamahaSysExEncoder.setPartResonance(partIndex, clamped)
        midiManager.sendThrottled("PART_${partIndex}_RESO", sysex)
    }

    /**
     * Sends MIDI CC 73 (Attack Time) on the selected part's MIDI channel (0..127, neutral center = 64).
     */
    fun setPartAttack(partIndex: Int, attack: Int) {
        if (partIndex !in 0..15) return
        val clamped = attack.coerceIn(0, 127)
        updatePart(partIndex) { it.copy(attack = clamped) }

        val part = _uiState.value.parts.getOrNull(partIndex)
        val channel = if (part != null && part.receiveChannel in 0..15) part.receiveChannel else partIndex
        val msg = YamahaSysExEncoder.createControlChangeMessage(channel, 73, clamped)
        midiManager.sendThrottled("PART_${partIndex}_CC73", msg)
    }

    /**
     * Sends MIDI CC 75 (Decay Time) on the selected part's MIDI channel (0..127, neutral center = 64).
     */
    fun setPartDecay(partIndex: Int, decay: Int) {
        if (partIndex !in 0..15) return
        val clamped = decay.coerceIn(0, 127)
        updatePart(partIndex) { it.copy(decay = clamped) }

        val part = _uiState.value.parts.getOrNull(partIndex)
        val channel = if (part != null && part.receiveChannel in 0..15) part.receiveChannel else partIndex
        val msg = YamahaSysExEncoder.createControlChangeMessage(channel, 75, clamped)
        midiManager.sendThrottled("PART_${partIndex}_CC75", msg)
    }

    /**
     * Sends MIDI CC 72 (Release Time) on the selected part's MIDI channel (0..127, neutral center = 64).
     */
    fun setPartRelease(partIndex: Int, release: Int) {
        if (partIndex !in 0..15) return
        val clamped = release.coerceIn(0, 127)
        updatePart(partIndex) { it.copy(release = clamped) }

        val part = _uiState.value.parts.getOrNull(partIndex)
        val channel = if (part != null && part.receiveChannel in 0..15) part.receiveChannel else partIndex
        val msg = YamahaSysExEncoder.createControlChangeMessage(channel, 72, clamped)
        midiManager.sendThrottled("PART_${partIndex}_CC72", msg)
    }

    /**
     * Sends MIDI Control Change 1 (Modulation Wheel / Rotary Speed) for the specified part.
     * Transmits on the part's assigned receiveChannel (0..15).
     * If modulation is bypassed/disabled for this part, the CC 1 message is suppressed.
     */
    fun sendModulation(partIndex: Int, value: Int) {
        val clamped = value.coerceIn(0, 127)
        updatePart(partIndex) { it.copy(modulation = clamped) }

        val part = _uiState.value.parts.getOrNull(partIndex)
        if (part != null && !part.isModulationEnabled) {
            return
        }

        val channel = if (part != null && part.receiveChannel in 0..15) part.receiveChannel else partIndex
        val msg = YamahaSysExEncoder.createControlChangeMessage(channel, 1, clamped)
        midiManager.sendThrottled("PART_${partIndex}_CC1", msg)
    }

    /**
     * Toggles Modulation Wheel Receive Switch on/off for the specified part via SysEx (0x37 [part] 0x38 [0 or 1]).
     * - When enabled: enables the hardware receive switch and re-transmits current fader modulation if > 0.
     * - When disabled (Bypass): disables the hardware receive switch (blocking physical keyboard MW and app CC 1)
     *   and immediately transmits CC 1 = 0 to clear any lingering modulation in the sound engine.
     */
    fun togglePartModulation(partIndex: Int, enabled: Boolean) {
        if (partIndex !in 0..15) return
        val currentPart = _uiState.value.parts.getOrNull(partIndex) ?: return
        updatePart(partIndex) { it.copy(isModulationEnabled = enabled) }

        // 1. Dispatch SysEx parameter change to toggle Receive Switch in hardware
        val sysex = YamahaSysExEncoder.setPartModulationReceiveSwitch(partIndex, enabled)
        midiManager.sendImmediate(sysex)

        // 2. Channel calculation
        val channel = if (currentPart.receiveChannel in 0..15) currentPart.receiveChannel else partIndex

        if (!enabled) {
            // When bypassed/disabled: immediately send CC 1 = 0 to clear any lingering modulation
            val zeroMsg = YamahaSysExEncoder.createControlChangeMessage(channel, 1, 0)
            midiManager.sendImmediate(zeroMsg)
        } else {
            // When re-enabled: re-transmit the current fader modulation value
            if (currentPart.modulation > 0) {
                val modMsg = YamahaSysExEncoder.createControlChangeMessage(channel, 1, currentPart.modulation)
                midiManager.sendImmediate(modMsg)
            }
        }
    }

    fun togglePartMute(partIndex: Int) {
        if (!isProUser.value && partIndex >= 2) {
            openUpgradeDialog()
            return
        }
        val current = _uiState.value.parts[partIndex]
        val newMuted = !current.isMuted
        updatePart(partIndex) { it.copy(isMuted = newMuted) }

        dispatchMuteSoloMidiUpdates()
    }

    fun togglePartSolo(partIndex: Int) {
        if (!isProUser.value && partIndex >= 2) {
            openUpgradeDialog()
            return
        }
        val currentSolo = _uiState.value.parts[partIndex].isSolo
        val newSolo = !currentSolo

        // Toggle solo on this part, preserving other parts' solo states (multi-solo support)
        _uiState.update { state ->
            val updated = state.parts.mapIndexed { idx, part ->
                if (idx == partIndex) {
                    part.copy(isSolo = newSolo)
                } else {
                    part
                }
            }
            state.copy(parts = updated)
        }
        persistCurrentWorkingState()

        dispatchMuteSoloMidiUpdates()
    }

    /**
     * Synchronizes audio mute/solo state to the Yamaha MX synthesizer:
     * - When any part is in SOLO: silences all non-solo parts via SysEx Part Volume and MIDI CC 7/11.
     * - When all parts have SOLO deactivated: rigorously restores the audio volume according to the
     *   previous MUTE/UNMUTE configuration (previously muted parts remain muted).
     * - Dispatches messages with safe inter-message pacing to prevent hardware buffer drops.
     */
    internal fun dispatchMuteSoloMidiUpdates() {
        val state = _uiState.value
        val anySolo = state.anySoloActive
        val parts = state.parts

        val messages = mutableListOf<ByteArray>()

        parts.forEach { part ->
            val effectiveVol = part.effectiveVolume(anySolo)

            // 1. SysEx Multi Part Parameter Change: Part Volume (Standard + Native), base-0 part addressing
            messages.add(YamahaSysExEncoder.setPartVolume(part.partIndex, effectiveVol))
            messages.add(YamahaSysExEncoder.setPartVolumeNative(part.partIndex, effectiveVol))

            // 2. MIDI Channel Control Change messages: CC 7 (Volume) only, and only on channels exclusively owned by this Part.
            //    Do NOT send CC 11 = 0 so the physical Expression Pedal (CC 11) remains active for Layer parts.
            resolveExclusiveVolumeCcChannel(part.partIndex, part.receiveChannel, parts)?.let { ch ->
                messages.add(YamahaSysExEncoder.createControlChangeMessage(ch, 7, effectiveVol))
            }
        }

        // Transmit sequence with 5ms inter-message spacing to prevent Yamaha MX USB buffer overflow
        midiManager.sendKeyedSequence("SOLO_MUTE_UPDATE", messages, delayMs = 5L)
    }


    /**
     * Updates the gain of one of the 5 Master EQ bands (bandIndex: 0..4, gainValue: 0..127, center 64 = 0.0 dB)
     * and dispatches the Yamaha MX Master EQ Parameter Change SysEx message with throttled buffer protection.
     */
    fun setMasterEqBandGain(bandIndex: Int, gainValue: Int) {
        if (bandIndex !in 0..4) return
        val clamped = gainValue.coerceIn(0, 127)
        _uiState.update { state ->
            val currentGains = state.common.masterEqGains
            val updatedGains = List(5) { idx ->
                if (idx == bandIndex) clamped else currentGains.getOrElse(idx) { 64 }
            }
            state.copy(common = state.common.copy(masterEqGains = updatedGains))
        }
        persistCurrentWorkingState()

        val sysex = YamahaSysExEncoder.setMasterEqBandGain(bandIndex, clamped)
        midiManager.sendThrottled("MASTER_EQ_BAND_$bandIndex", sysex)
    }

    /**
     * Resets all 5 bands of the Master EQ to 0.0 dB (64) and transmits the SysEx reset sequence with safe spacing.
     */
    fun resetMasterEq() {
        val flatGains = List(5) { 64 }
        _uiState.update { state ->
            state.copy(common = state.common.copy(masterEqGains = flatGains))
        }
        persistCurrentWorkingState()

        val messages = (0..4).map { bandIdx ->
            YamahaSysExEncoder.setMasterEqBandGain(bandIdx, 64)
        }
        midiManager.sendKeyedSequence("MASTER_EQ_RESET", messages, delayMs = 5L)
    }

    // --- Part Compressor / Insert FX ---

    fun togglePartCompressor(partIndex: Int, enabled: Boolean) {
        if (partIndex !in 0..15) return
        updatePart(partIndex) { it.copy(isCompressorEnabled = enabled) }
        val updatedParts = _uiState.value.parts
        val currentPart = updatedParts[partIndex]

        val messages = mutableListOf<ByteArray>()

        if (enabled) {
            // 1. Load Effect Type in Voice Insert Eff: CMP: VCM COMPRESSOR 376 (MSB 0x08, LSB 0x00, Preset 0x00)
            messages.addAll(YamahaSysExEncoder.createSetPartCompressorTypeMessages(partIndex, isVcm = true))

            // 2. Multi Common Insertion Part Switch (Address 36 00 4C 4-byte bitmask)
            messages.add(YamahaSysExEncoder.setPartInsertionEffectSwitch(partIndex, true, updatedParts))

            // 3. Multi Part Play Mode InsSw
            messages.addAll(YamahaSysExEncoder.setPartInsertionSwitch(partIndex, true))

            // 4. Send current compressor parameters (Threshold, Gain, Ratio, Attack, Release)
            messages.addAll(YamahaSysExEncoder.setPartCompressorThresholdAll(partIndex, currentPart.compressorThreshold))
            messages.addAll(YamahaSysExEncoder.setPartCompressorOutputGainAll(partIndex, currentPart.compressorOutputGain))
            messages.addAll(YamahaSysExEncoder.setPartCompressorRatioAll(partIndex, currentPart.compressorRatio))
            messages.addAll(YamahaSysExEncoder.setPartCompressorAttackAll(partIndex, currentPart.compressorAttack))
            messages.addAll(YamahaSysExEncoder.setPartCompressorReleaseAll(partIndex, currentPart.compressorRelease))
        } else {
            // 1. Multi Common Insertion Part Switch (Address 36 00 4C)
            messages.add(YamahaSysExEncoder.setPartInsertionEffectSwitch(partIndex, false, updatedParts))

            // 2. Multi Part Play Mode InsSw OFF
            messages.addAll(YamahaSysExEncoder.setPartInsertionSwitch(partIndex, false))
        }

        // Diagnostic Logging with Tag "MidiSysEx"
        Log.d("MidiSysEx", "=== togglePartCompressor(part=$partIndex, enabled=$enabled) ===")
        messages.forEachIndexed { i, msg ->
            Log.d("MidiSysEx", "TX[$i]: ${YamahaSysExEncoder.toHexString(msg)}")
        }

        // Transmit sequence with 5ms pacing to protect Yamaha MX buffer
        midiManager.sendKeyedSequence("PART_${partIndex}_COMP_TOGGLE", messages, delayMs = 5L)
    }

    fun setPartCompressorThreshold(partIndex: Int, threshold: Int) {
        if (partIndex !in 0..15) return
        val clamped = threshold.coerceIn(0, 127)
        updatePart(partIndex) { it.copy(compressorThreshold = clamped) }

        val msgs = YamahaSysExEncoder.setPartCompressorThresholdAll(partIndex, clamped)
        Log.d("MidiSysEx", "=== setPartCompressorThreshold(part=$partIndex, threshold=$clamped) ===")
        msgs.forEachIndexed { i, msg ->
            Log.d("MidiSysEx", "TX Threshold[$i]: ${YamahaSysExEncoder.toHexString(msg)}")
            midiManager.sendThrottled("PART_${partIndex}_COMP_THRESH_$i", msg)
        }
    }

    fun setPartCompressorOutputGain(partIndex: Int, outputGain: Int) {
        if (partIndex !in 0..15) return
        val clamped = outputGain.coerceIn(0, 127)
        updatePart(partIndex) { it.copy(compressorOutputGain = clamped) }

        val msgs = YamahaSysExEncoder.setPartCompressorOutputGainAll(partIndex, clamped)
        Log.d("MidiSysEx", "=== setPartCompressorOutputGain(part=$partIndex, gain=$clamped) ===")
        msgs.forEachIndexed { i, msg ->
            Log.d("MidiSysEx", "TX OutputGain[$i]: ${YamahaSysExEncoder.toHexString(msg)}")
            midiManager.sendThrottled("PART_${partIndex}_COMP_GAIN_$i", msg)
        }
    }

    fun setPartCompressorRatio(partIndex: Int, ratio: Int) {
        if (partIndex !in 0..15) return
        val clamped = ratio.coerceIn(0, YamahaSysExEncoder.COMPRESSOR_RATIO_LABELS.size - 1)
        updatePart(partIndex) { it.copy(compressorRatio = clamped) }

        val msgs = YamahaSysExEncoder.setPartCompressorRatioAll(partIndex, clamped)
        Log.d("MidiSysEx", "=== setPartCompressorRatio(part=$partIndex, ratio=$clamped) ===")
        msgs.forEachIndexed { i, msg ->
            Log.d("MidiSysEx", "TX Ratio[$i]: ${YamahaSysExEncoder.toHexString(msg)}")
            midiManager.sendThrottled("PART_${partIndex}_COMP_RATIO_$i", msg)
        }
    }

    fun setPartCompressorAttack(partIndex: Int, attack: Int) {
        if (partIndex !in 0..15) return
        val clamped = attack.coerceIn(0, YamahaSysExEncoder.COMPRESSOR_ATTACK_VALUES.size - 1)
        updatePart(partIndex) { it.copy(compressorAttack = clamped) }

        val msgs = YamahaSysExEncoder.setPartCompressorAttackAll(partIndex, clamped)
        Log.d("MidiSysEx", "=== setPartCompressorAttack(part=$partIndex, attack=$clamped) ===")
        msgs.forEachIndexed { i, msg ->
            Log.d("MidiSysEx", "TX Attack[$i]: ${YamahaSysExEncoder.toHexString(msg)}")
            midiManager.sendThrottled("PART_${partIndex}_COMP_ATTACK_$i", msg)
        }
    }

    fun setPartCompressorRelease(partIndex: Int, release: Int) {
        if (partIndex !in 0..15) return
        val clamped = release.coerceIn(0, YamahaSysExEncoder.COMPRESSOR_RELEASE_VALUES.size - 1)
        updatePart(partIndex) { it.copy(compressorRelease = clamped) }

        val msgs = YamahaSysExEncoder.setPartCompressorReleaseAll(partIndex, clamped)
        Log.d("MidiSysEx", "=== setPartCompressorRelease(part=$partIndex, release=$clamped) ===")
        msgs.forEachIndexed { i, msg ->
            Log.d("MidiSysEx", "TX Release[$i]: ${YamahaSysExEncoder.toHexString(msg)}")
            midiManager.sendThrottled("PART_${partIndex}_COMP_RELEASE_$i", msg)
        }
    }

    fun resetPartCompressor(partIndex: Int) {
        if (partIndex !in 0..15) return
        val defaultThreshold = 72
        val defaultGain = 64
        val defaultRatio = 3
        val defaultAttack = 19
        val defaultRelease = 4
        updatePart(partIndex) {
            it.copy(
                compressorThreshold = defaultThreshold,
                compressorOutputGain = defaultGain,
                compressorRatio = defaultRatio,
                compressorAttack = defaultAttack,
                compressorRelease = defaultRelease
            )
        }

        val msgs = mutableListOf<ByteArray>()
        msgs.addAll(YamahaSysExEncoder.setPartCompressorThresholdAll(partIndex, defaultThreshold))
        msgs.addAll(YamahaSysExEncoder.setPartCompressorOutputGainAll(partIndex, defaultGain))
        msgs.addAll(YamahaSysExEncoder.setPartCompressorRatioAll(partIndex, defaultRatio))
        msgs.addAll(YamahaSysExEncoder.setPartCompressorAttackAll(partIndex, defaultAttack))
        msgs.addAll(YamahaSysExEncoder.setPartCompressorReleaseAll(partIndex, defaultRelease))

        Log.d("MidiSysEx", "=== resetPartCompressor(part=$partIndex) ===")
        msgs.forEachIndexed { i, msg ->
            Log.d("MidiSysEx", "TX Reset[$i]: ${YamahaSysExEncoder.toHexString(msg)}")
        }

        midiManager.sendKeyedSequence("PART_${partIndex}_COMP_RESET", msgs, delayMs = 5L)
    }

    fun setPart12Link(enabled: Boolean) {
        _uiState.update { it.copy(common = it.common.copy(part12Link = enabled)) }
        val sysex = YamahaSysExEncoder.setPart12Link(enabled)
        midiManager.sendImmediate(sysex)
        val state = _uiState.value
        state.parts.getOrNull(0)?.let { part1 ->
            midiManager.sendImmediate(
                YamahaSysExEncoder.setPartExpressionReceiveSwitch(0, shouldPartReceiveExpression(part1, state.common))
            )
        }
        state.parts.getOrNull(1)?.let { part2 ->
            midiManager.sendImmediate(
                YamahaSysExEncoder.setPartExpressionReceiveSwitch(1, shouldPartReceiveExpression(part2, state.common))
            )
        }
    }

    fun sendPanic() {
        spilloverRouter.reset()
        _activePolyphony.value = 0
        _uiState.update { it.copy(activePolyphony = 0) }
        midiManager.resetActiveNotes()
        midiManager.sendSafetyFlush()
        _uiState.value.parts.forEach { part ->
            midiManager.sendImmediate(YamahaSysExEncoder.setPartReceiveChannel(part.partIndex, part.receiveChannel))
        }
        val messages = YamahaSysExEncoder.buildPanicMessages()
        messages.forEach { msg ->
            midiManager.sendImmediate(msg)
        }
    }

    fun resetAllPartsToDefault() {
        _activePolyphony.value = 0
        midiManager.resetActiveNotes()
        midiManager.sendSafetyFlush()
        val defaultParts = List(16) { index -> MultiPartState(partIndex = index) }
        val defaultCommon = MultiCommonState()
        spilloverRouter.syncFromParts(defaultParts)
        _currentLoadedPerformanceId.value = null
        _uiState.update {
            it.copy(parts = defaultParts, common = defaultCommon, activePolyphony = 0)
        }
        persistCurrentWorkingState()

        // Transmit default initialization to synth
        defaultParts.forEach { part ->
            // Reset receive channel
            midiManager.sendImmediate(YamahaSysExEncoder.setPartReceiveChannel(part.partIndex, part.partIndex))

            val voiceMsgs = YamahaSysExEncoder.createVoiceChangeMessages(
                part.partIndex,
                part.voice.msb,
                part.voice.lsb,
                part.voice.programChange
            )
            midiManager.sendSequence(voiceMsgs, delayMs = 10L)

            midiManager.sendImmediate(YamahaSysExEncoder.setPartVolume(part.partIndex, part.volume))
            midiManager.sendImmediate(YamahaSysExEncoder.setPartVolumeNative(part.partIndex, part.volume))
            midiManager.sendImmediate(YamahaSysExEncoder.setPartSustainReceiveSwitch(part.partIndex, true))
            midiManager.sendImmediate(YamahaSysExEncoder.setPartPan(part.partIndex, part.pan))
            midiManager.sendImmediate(YamahaSysExEncoder.setPartReverbSend(part.partIndex, part.reverbSend))
            midiManager.sendImmediate(YamahaSysExEncoder.setPartChorusSend(part.partIndex, part.chorusSend))
            midiManager.sendImmediate(YamahaSysExEncoder.setPartNoteShift(part.partIndex, part.noteShift))
            midiManager.sendImmediate(YamahaSysExEncoder.setPartNoteLimitLow(part.partIndex, part.noteLimitLow))
            midiManager.sendImmediate(YamahaSysExEncoder.setPartNoteLimitHigh(part.partIndex, part.noteLimitHigh))
            midiManager.sendImmediate(
                YamahaSysExEncoder.setPartExpressionReceiveSwitch(
                    part.partIndex,
                    shouldPartReceiveExpression(part, defaultCommon)
                )
            )
        }
        storageRepository.saveLastActiveState(null, 0)
        (0..4).forEach { bandIdx ->
            midiManager.sendImmediate(YamahaSysExEncoder.setMasterEqBandGain(bandIdx, 64))
        }
    }

    private fun updatePart(partIndex: Int, transform: (MultiPartState) -> MultiPartState) {
        _uiState.update { state ->
            val updated = state.parts.mapIndexed { idx, part ->
                if (idx == partIndex) transform(part) else part
            }
            state.copy(parts = updated)
        }
        persistCurrentWorkingState()
    }

    private fun updatePartFromExternalMidi(partIndex: Int, transform: (MultiPartState) -> MultiPartState) {
        _uiState.update { state ->
            val updated = state.parts.mapIndexed { idx, part ->
                if (idx == partIndex) transform(part) else part
            }
            state.copy(parts = updated)
        }
        persistDebounceJob?.cancel()
        persistDebounceJob = viewModelScope.launch {
            delay(250L)
            persistCurrentWorkingState()
        }
    }

    private fun persistCurrentWorkingState(state: PerformanceState = _uiState.value) {
        try {
            val performance = SavedPerformance.fromState(
                name = state.common.performanceName.ifBlank { "Minha Performance" },
                categoryTag = "Trabalho",
                state = state,
                id = _currentLoadedPerformanceId.value ?: "current_session"
            )
            storageRepository.saveWorkingPerformance(performance, state.activeFocusedPartIndex)
        } catch (e: Exception) {
            // Ignore persistence errors in test mocks
        }
    }

    override fun onCleared() {
        super.onCleared()
        persistCurrentWorkingState()
    }
}
