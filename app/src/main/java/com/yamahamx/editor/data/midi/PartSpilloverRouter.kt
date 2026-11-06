package com.yamahamx.editor.data.midi

import com.yamahamx.editor.data.model.MultiPartState

/**
 * Manages Seamless Sound Switching (Sound Spillover) strictly when switching active Parts
 * (e.g., changing focus between Part 1, Part 2, Part 3, or toggling Layer groups).
 *
 * Design guarantees:
 * 1. No Forced Cut on Part Switch:
 *    - Never sends CC 120 (All Sound Off), CC 123 (All Notes Off), or preventive CC 64 = 0
 *      when a Part loses focus.
 * 2. Pending Note Off Tracking & Dispatch:
 *    - Tracks which physical keys (0..127) were initiated on which Parts (0..15).
 *    - New Note On events after a Part switch are routed by the hardware exclusively to the newly
 *      focused Part (and its active Layer group) on MIDI Channel 1 (0x00).
 *    - Outgoing Parts that still have held notes or active sustain remain assigned to a valid
 *      dedicated MIDI channel (1..15) so their voices continue sounding and can receive Note Off
 *      and Sustain Off messages.
 *    - When a physical key pressed before the Part switch is released, a targeted Note Off
 *      message (0x8n, note, 0) is dispatched to the outgoing Part's spillover MIDI channel.
 * 3. Sustain Pedal (CC 64) Management:
 *    - Does not send CC 64 = 0 to the outgoing Part at the moment of the Part switch if the
 *      sustain pedal is physically pressed.
 *    - Preserves sustain (Hold1) on the outgoing Part's spillover channel during the switch.
 *    - When the physical pedal release event (CC 64 = 0) arrives from the keyboard, dispatches
 *      CC 64 = 0 to all Parts that had pending sustained notes.
 * 4. Panic / Reset:
 *    - Immediately clears all held notes, sustain states, and temporary spillover channel bindings.
 */
class PartSpilloverRouter {

    data class RoutingTransition(
        val updatedReceiveChannels: IntArray,
        val midiMessages: List<ByteArray>
    )

    private val lock = Any()

    // Maps MIDI note number (0..127) -> set of partIndex (0..15) currently holding that key down
    private val heldNotesByNote = HashMap<Int, MutableSet<Int>>()

    // Tracks whether the physical Sustain Pedal (CC 64) is currently pressed (> 0)
    private var isSustainPressed: Boolean = false

    // Set of partIndex (0..15) that currently have sustain engaged (Hold1 active)
    private val sustainedParts = HashSet<Int>()

    // Logical target Receive Channel for each Part (0 for focused/layered, 127 for unfocused Part 1, idx for Parts 2..16)
    private val logicalChannelByPart = IntArray(16) { idx -> if (idx == 0) 0 else idx }

    // Actual Receive Channel currently configured in the Yamaha MX hardware for each Part (0..15 or 127)
    private val hardwareChannelByPart = IntArray(16) { idx -> if (idx == 0) 0 else idx }

    // Maps partIndex (0..15) -> active hardware spillover channel (1..15) for parts that lost focus while sounding
    private val spilloverChannelByPart = HashMap<Int, Int>()

    /**
     * Synchronizes internal channel tracking with a loaded or restored performance state.
     */
    fun syncFromParts(parts: List<MultiPartState>) {
        synchronized(lock) {
            heldNotesByNote.clear()
            sustainedParts.clear()
            spilloverChannelByPart.clear()
            isSustainPressed = false
            for (part in parts) {
                val idx = part.partIndex
                if (idx in 0..15) {
                    logicalChannelByPart[idx] = part.receiveChannel
                    hardwareChannelByPart[idx] = part.receiveChannel
                }
            }
        }
    }

    /**
     * Clears all spillover tracking state (used on Panic, Reset, or Disconnect).
     */
    fun reset() {
        synchronized(lock) {
            heldNotesByNote.clear()
            sustainedParts.clear()
            spilloverChannelByPart.clear()
            isSustainPressed = false
            for (idx in 0..15) {
                hardwareChannelByPart[idx] = logicalChannelByPart[idx]
            }
        }
    }

    /**
     * Processes an incoming MIDI voice event (NoteOn, NoteOff, SustainChange, AllNotesOff)
     * and returns any spillover MIDI messages (Note Off, CC 64 = 0, or post-spillover channel cleanup)
     * that must be transmitted to the synthesizer.
     */
    fun onIncomingVoiceEvent(
        event: MidiVoiceEvent,
        currentParts: List<MultiPartState>,
        focusedPartIndex: Int
    ): List<ByteArray> {
        synchronized(lock) {
            return when (event) {
                is MidiVoiceEvent.NoteOn -> {
                    if (event.channel != 0) return emptyList()
                    if (event.velocity > 0) {
                        handleNoteOnLocked(event.channel, event.note, currentParts, focusedPartIndex)
                        emptyList()
                    } else {
                        handleNoteOffLocked(event.channel, event.note)
                    }
                }
                is MidiVoiceEvent.NoteOff -> {
                    if (event.channel != 0) return emptyList()
                    handleNoteOffLocked(event.channel, event.note)
                }
                is MidiVoiceEvent.SustainChange -> {
                    if (event.channel != 0) return emptyList()
                    handleSustainChangeLocked(event.channel, event.isPressed, currentParts, focusedPartIndex)
                }
                is MidiVoiceEvent.AllNotesOff -> {
                    heldNotesByNote.clear()
                    sustainedParts.clear()
                    spilloverChannelByPart.clear()
                    isSustainPressed = false
                    emptyList()
                }
                is MidiVoiceEvent.ControlChange,
                is MidiVoiceEvent.ProgramChange,
                is MidiVoiceEvent.LayerActivation -> {
                    emptyList()
                }
            }
        }
    }

    /**
     * Computes the seamless Receive Channel transition when switching the active focused part
     * or toggling Layer membership, without sending CC 120 / CC 123 / CC 64 = 0.
     *
     * @param currentParts Current 16 parts state from UI.
     * @param activeCh0PartIndices Set of part indices (0..15) that should now receive on Channel 1 (0x00).
     */
    fun computeSeamlessPartSwitch(
        currentParts: List<MultiPartState>,
        activeCh0PartIndices: Set<Int>
    ): RoutingTransition {
        synchronized(lock) {
            // Ensure current logical channels are aligned before transition
            for (part in currentParts) {
                val idx = part.partIndex
                if (idx in 0..15 && idx !in spilloverChannelByPart) {
                    logicalChannelByPart[idx] = part.receiveChannel
                    hardwareChannelByPart[idx] = part.receiveChannel
                }
            }

            // 1. If any part returning to Channel 0 was previously in spillover, it is now back on Channel 0
            for (activeIdx in activeCh0PartIndices) {
                spilloverChannelByPart.remove(activeIdx)
                if (isSustainPressed) {
                    sustainedParts.add(activeIdx)
                }
            }

            // 2. Identify parts that were on Channel 0 and are now losing focus
            val outgoingParts = (0..15).filter { idx ->
                val wasOnCh0 = logicalChannelByPart[idx] == 0 || currentParts.getOrNull(idx)?.receiveChannel == 0
                wasOnCh0 && idx !in activeCh0PartIndices
            }

            // 3. For each outgoing part, check if it has active held notes or active sustain
            for (outIdx in outgoingParts) {
                val hasHeld = hasAnyHeldNotesLocked(outIdx)
                val hasSustain = isSustainPressed || (outIdx in sustainedParts)
                if (hasHeld || hasSustain) {
                    if (isSustainPressed) {
                        sustainedParts.add(outIdx)
                    }
                    if (!spilloverChannelByPart.containsKey(outIdx)) {
                        val spillCh = allocateSpilloverChannelLocked(outIdx, activeCh0PartIndices)
                        spilloverChannelByPart[outIdx] = spillCh
                    }
                }
            }

            // 4. Compute logical and hardware Receive Channels for all 16 parts
            val updatedLogicalChannels = IntArray(16)
            val messages = mutableListOf<ByteArray>()

            for (idx in 0..15) {
                val existingCh = currentParts.getOrNull(idx)?.receiveChannel ?: idx
                val logicalTarget = when {
                    idx in activeCh0PartIndices -> 0
                    idx == 0 -> 127
                    existingCh in 1..15 || existingCh == 127 -> existingCh
                    else -> idx
                }
                updatedLogicalChannels[idx] = logicalTarget
                logicalChannelByPart[idx] = logicalTarget

                val hwTarget = when {
                    idx in activeCh0PartIndices -> 0
                    spilloverChannelByPart.containsKey(idx) -> spilloverChannelByPart[idx]!!
                    logicalTarget in 1..15 && spilloverChannelByPart.containsValue(logicalTarget) -> {
                        // Another part (such as Part 1) is temporarily using this channel for spillover;
                        // decouple this silent unfocused part until the spillover finishes.
                        127
                    }
                    else -> logicalTarget
                }

                val prevHw = hardwareChannelByPart[idx]
                val prevLogical = currentParts.getOrNull(idx)?.receiveChannel ?: prevHw
                hardwareChannelByPart[idx] = hwTarget

                if (prevHw != hwTarget || prevLogical != logicalTarget) {
                    messages.add(YamahaSysExEncoder.setPartReceiveChannel(idx, hwTarget))
                    // If this outgoing part is sustaining on its spillover channel, keep Hold1 (CC 64) engaged
                    if (idx in spilloverChannelByPart && isSustainPressed && idx in sustainedParts && hwTarget in 1..15) {
                        messages.add(YamahaSysExEncoder.createControlChangeMessage(hwTarget, 64, 127))
                    }
                }
            }

            return RoutingTransition(
                updatedReceiveChannels = updatedLogicalChannels,
                midiMessages = messages
            )
        }
    }

    internal fun getSpilloverChannelForPart(partIndex: Int): Int? {
        synchronized(lock) {
            return spilloverChannelByPart[partIndex]
        }
    }

    private fun handleNoteOnLocked(
        channel: Int,
        note: Int,
        currentParts: List<MultiPartState>,
        focusedPartIndex: Int
    ) {
        val clampedNote = note and 0x7F
        val activeParts = resolveActivePartsForChannelLocked(channel, currentParts, focusedPartIndex)
        if (activeParts.isNotEmpty()) {
            val set = heldNotesByNote.getOrPut(clampedNote) { HashSet() }
            set.addAll(activeParts)
            if (isSustainPressed) {
                sustainedParts.addAll(activeParts)
            }
        }
    }

    private fun handleNoteOffLocked(channel: Int, note: Int): List<ByteArray> {
        val clampedNote = note and 0x7F
        val partsHoldingNote = heldNotesByNote.remove(clampedNote) ?: return emptyList()
        val messages = mutableListOf<ByteArray>()
        val targetChannels = LinkedHashSet<Int>()

        for (partIdx in partsHoldingNote) {
            val hwCh = spilloverChannelByPart[partIdx] ?: hardwareChannelByPart[partIdx]
            if (hwCh in 0..15 && hwCh != channel) {
                targetChannels.add(hwCh)
            }
            if (isSustainPressed) {
                sustainedParts.add(partIdx)
            }
        }

        for (hwCh in targetChannels) {
            messages.add(YamahaSysExEncoder.createNoteOffMessage(hwCh, clampedNote, 0))
        }

        // Clean up any spillover part that no longer has held notes or active sustain
        for (partIdx in partsHoldingNote) {
            if (!isSustainPressed && partIdx !in sustainedParts && !hasAnyHeldNotesLocked(partIdx)) {
                cleanupFinishedSpilloverForPartLocked(partIdx, messages)
            }
        }

        return messages
    }

    private fun handleSustainChangeLocked(
        channel: Int,
        isPressed: Boolean,
        currentParts: List<MultiPartState>,
        focusedPartIndex: Int
    ): List<ByteArray> {
        if (isPressed) {
            isSustainPressed = true
            val activeParts = resolveActivePartsForChannelLocked(channel, currentParts, focusedPartIndex)
            sustainedParts.addAll(activeParts)
            for (partsSet in heldNotesByNote.values) {
                sustainedParts.addAll(partsSet)
            }
            return emptyList()
        } else {
            isSustainPressed = false
            if (sustainedParts.isEmpty() && spilloverChannelByPart.isEmpty()) {
                return emptyList()
            }

            val messages = mutableListOf<ByteArray>()
            val channelsNeedingSustainOff = LinkedHashSet<Int>()
            val partsToCleanup = HashSet<Int>(sustainedParts)
            partsToCleanup.addAll(spilloverChannelByPart.keys)

            for (partIdx in partsToCleanup) {
                val hwCh = spilloverChannelByPart[partIdx] ?: hardwareChannelByPart[partIdx]
                if (hwCh in 0..15 && hwCh != channel) {
                    channelsNeedingSustainOff.add(hwCh)
                }
            }

            sustainedParts.clear()

            for (hwCh in channelsNeedingSustainOff) {
                messages.add(YamahaSysExEncoder.createControlChangeMessage(hwCh, 64, 0))
            }

            for (partIdx in partsToCleanup) {
                if (!hasAnyHeldNotesLocked(partIdx)) {
                    cleanupFinishedSpilloverForPartLocked(partIdx, messages)
                }
            }

            return messages
        }
    }

    private fun resolveActivePartsForChannelLocked(
        channel: Int,
        currentParts: List<MultiPartState>,
        focusedPartIndex: Int
    ): List<Int> {
        val result = mutableListOf<Int>()
        for (part in currentParts) {
            val idx = part.partIndex
            if (idx !in 0..15) continue
            if (channel == 0 && part.receiveChannel == 0) {
                result.add(idx)
            } else if (channel in 1..15 && part.receiveChannel == channel && idx !in spilloverChannelByPart) {
                result.add(idx)
            }
        }
        if (result.isEmpty() && channel == 0) {
            val safeFocused = focusedPartIndex.coerceIn(0, 15)
            result.add(safeFocused)
        }
        return result
    }

    private fun hasAnyHeldNotesLocked(partIndex: Int): Boolean {
        for (partsSet in heldNotesByNote.values) {
            if (partIndex in partsSet) return true
        }
        return false
    }

    private fun allocateSpilloverChannelLocked(partIndex: Int, activeCh0PartIndices: Set<Int>): Int {
        val usedSpillChannels = spilloverChannelByPart.values.toSet()

        // For Parts 2..16 (indices 1..15), prefer their own natural channel (1..15) if not already used by another spillover
        if (partIndex in 1..15 && partIndex !in usedSpillChannels) {
            return partIndex
        }

        // For Part 1 (index 0, whose unfocused resting channel is 127 = Off) or if natural channel was occupied:
        // Prefer a channel (1..15) belonging to a part that is currently focused on Channel 0 (therefore vacant!)
        for (ch in 1..15) {
            if (ch in activeCh0PartIndices && ch !in usedSpillChannels) {
                return ch
            }
        }

        // Fallback: any channel in 15 downTo 1 not currently used by another active spillover
        for (ch in 15 downTo 1) {
            if (ch !in usedSpillChannels) {
                return ch
            }
        }

        return if (partIndex in 1..15) partIndex else 15
    }

    private fun cleanupFinishedSpilloverForPartLocked(partIndex: Int, messages: MutableList<ByteArray>) {
        val freedChannel = spilloverChannelByPart.remove(partIndex) ?: return
        val restingChannel = logicalChannelByPart[partIndex]
        if (hardwareChannelByPart[partIndex] != restingChannel) {
            hardwareChannelByPart[partIndex] = restingChannel
            messages.add(YamahaSysExEncoder.setPartReceiveChannel(partIndex, restingChannel))
        }

        // If another silent part (1..15) had its natural channel borrowed as freedChannel, restore it
        if (freedChannel in 1..15) {
            val ownerLogical = logicalChannelByPart[freedChannel]
            if (ownerLogical == freedChannel &&
                hardwareChannelByPart[freedChannel] != freedChannel &&
                !spilloverChannelByPart.containsValue(freedChannel)
            ) {
                hardwareChannelByPart[freedChannel] = freedChannel
                messages.add(YamahaSysExEncoder.setPartReceiveChannel(freedChannel, freedChannel))
            }
        }
    }
}
