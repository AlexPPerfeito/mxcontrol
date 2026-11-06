package com.yamahamx.editor.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.yamahamx.editor.data.model.MultiPartState
import com.yamahamx.editor.data.model.PerformanceState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Parameters that support interactive MIDI Learn (PadLab-style) per Part (0..15).
 */
enum class MidiLearnableParameter(val defaultCc: Int, val displayName: String) {
    VOLUME(defaultCc = 7, displayName = "Volume"),
    PAN(defaultCc = 10, displayName = "Pan"),
    MODULATION(defaultCc = 1, displayName = "Modulação"),
    CUTOFF(defaultCc = 74, displayName = "Cutoff"),
    RESONANCE(defaultCc = 71, displayName = "Resonance"),
    REVERB_SEND(defaultCc = 91, displayName = "Reverb Send"),
    CHORUS_SEND(defaultCc = 93, displayName = "Chorus Send");

    companion object {
        fun fromDefaultCc(cc: Int): MidiLearnableParameter? {
            return entries.firstOrNull { it.defaultCc == cc }
        }
    }
}

/**
 * Uniquely identifies a learnable parameter on a specific part (0..15).
 */
data class MidiTargetKey(
    val parameter: MidiLearnableParameter,
    val partIndex: Int
)

/**
 * Represents a user-configured MIDI CC binding for a [MidiTargetKey].
 * - If [isDisabled] is true, external MIDI control for this target is completely disabled ("Remover Mapeamento").
 * - Otherwise, [ccNumber] (0..127) and [channel] (0..15) define the custom binding.
 * - Absence of a [MidiMappingConfig] in the repository means Yamaha Default Fallback is active ("Padrão Yamaha").
 */
data class MidiMappingConfig(
    val ccNumber: Int,
    val channel: Int,
    val isDisabled: Boolean = false
)

/**
 * Represents a validated, strictly extracted MIDI Control Change message (status 0xB0..0xBF).
 */
data class StrictControlChangeEvent(
    val statusByte: Int,
    val channel: Int,
    val ccNumber: Int,
    val value: Int
)

/**
 * Repository responsible for storing, resolving, and persisting custom MIDI Learn CC mappings.
 */
class MidiMappingRepository(
    private val prefs: SharedPreferences? = null
) {
    constructor(context: Context) : this(
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    )

    companion object {
        const val PREFS_NAME = "yamaha_mx_midi_learn_mappings"
        private const val KEY_MAPPINGS_JSON = "midi_mappings_v1"

        /**
         * Builds the canonical destination mapping key: `part_${partIndex}_${controlType}`
         * (e.g. "part_4_volume", "part_5_volume", "part_6_volume").
         */
        fun buildMappingKey(partIndex: Int, controlType: String): String {
            return "part_${partIndex}_${controlType.trim().lowercase()}"
        }

        fun buildMappingKey(partIndex: Int, parameter: MidiLearnableParameter): String {
            val controlType = parameter.name.lowercase()
            val mappingKey = "part_${partIndex}_${controlType}"
            return mappingKey
        }

        fun parseMappingKey(mappingKey: String): MidiTargetKey? {
            if (!mappingKey.startsWith("part_")) return null
            val rest = mappingKey.removePrefix("part_")
            val separatorIdx = rest.indexOf('_')
            if (separatorIdx <= 0 || separatorIdx >= rest.length - 1) return null
            val partIndex = rest.substring(0, separatorIdx).toIntOrNull() ?: return null
            if (partIndex !in 0..15) return null
            val controlType = rest.substring(separatorIdx + 1).uppercase()
            val parameter = MidiLearnableParameter.entries.firstOrNull { it.name == controlType } ?: return null
            return MidiTargetKey(parameter, partIndex)
        }

        /**
         * Returns true EXCLUSIVELY for Control Change status bytes (0xB0..0xBF).
         * Summarily rejects SysEx (0xF0..0xF7), Clock (0xF8), Active Sensing (0xFE),
         * and all non-CC status bytes.
         */
        fun isStrictControlChangeStatus(statusByte: Int): Boolean {
            val masked = statusByte and 0xFF
            if (masked in 0xF0..0xF7 || masked == 0xF8 || masked == 0xFE || masked in 0xF9..0xFF) {
                return false
            }
            return masked in 0xB0..0xBF
        }

        /**
         * Strictly parses a raw MIDI byte slice for MIDI Learn / Control Change:
         * - Summarily discards any SysEx (0xF0..0xF7), Active Sensing (0xFE), and Clock (0xF8).
         * - Captures EXCLUSIVELY Control Change messages (status byte 0xB0..0xBF).
         * - Extracts strictly: `ccNumber = byte1` (0..127) and `value = byte2` (0..127).
         */
        fun extractStrictControlChange(
            bytes: ByteArray?,
            offset: Int = 0,
            count: Int = bytes?.size ?: 0
        ): StrictControlChangeEvent? {
            if (bytes == null || count <= 0 || offset < 0 || offset >= bytes.size) return null
            val end = (offset + count).coerceAtMost(bytes.size)
            var inSysEx = false
            var i = offset
            while (i < end) {
                val b = bytes[i].toInt() and 0xFF
                // Summarily discard Clock (0xF8), Active Sensing (0xFE), and Real-Time (0xF8..0xFF)
                if (b == 0xF8 || b == 0xFE || b in 0xF8..0xFF) {
                    i++
                    continue
                }
                // Summarily discard SysEx (0xF0..0xF7)
                if (b == 0xF0) {
                    inSysEx = true
                    i++
                    continue
                }
                if (b == 0xF7) {
                    inSysEx = false
                    i++
                    continue
                }
                if (inSysEx) {
                    i++
                    continue
                }
                if (b in 0xF1..0xF6) {
                    i++
                    continue
                }
                // Exclusively capture Control Change (0xB0..0xBF)
                if (b in 0xB0..0xBF) {
                    var dIdx = i + 1
                    var byte1 = -1
                    var byte2 = -1
                    while (dIdx < end) {
                        val db = bytes[dIdx].toInt() and 0xFF
                        dIdx++
                        if (db in 0xF8..0xFF) continue // skip interleaved Clock/Active Sensing
                        if (db >= 0x80) break // interrupted by another status byte
                        if (byte1 < 0) {
                            byte1 = db and 0x7F
                        } else {
                            byte2 = db and 0x7F
                            break
                        }
                    }
                    if (byte1 in 0..127 && byte2 in 0..127) {
                        return StrictControlChangeEvent(
                            statusByte = b,
                            channel = b and 0x0F,
                            ccNumber = byte1,
                            value = byte2
                        )
                    }
                }
                i++
            }
            return null
        }
    }

    private val _mappingsByStringKey = MutableStateFlow<Map<String, MidiMappingConfig>>(loadStringMapFromPrefs())
    val mappingsByKeyFlow: StateFlow<Map<String, MidiMappingConfig>> = _mappingsByStringKey.asStateFlow()

    private val _mappings = MutableStateFlow<Map<MidiTargetKey, MidiMappingConfig>>(
        toTargetKeyMap(_mappingsByStringKey.value)
    )
    val mappingsFlow: StateFlow<Map<MidiTargetKey, MidiMappingConfig>> = _mappings.asStateFlow()

    /**
     * Binds `"part_${partIndex}_${controlType}"` (and [MidiTargetKey]) -> (ccNumber, channel).
     * Coexists with all other mapped parts without clearing or resetting previous mappings.
     */
    fun assignMapping(
        parameter: MidiLearnableParameter,
        partIndex: Int,
        ccNumber: Int,
        channel: Int
    ) {
        if (partIndex !in 0..15) return
        val clampedCc = ccNumber.coerceIn(0, 127)
        val clampedCh = channel.coerceIn(0, 15)
        val controlType = parameter.name.lowercase()
        val mappingKey = "part_${partIndex}_${controlType}"
        val config = MidiMappingConfig(ccNumber = clampedCc, channel = clampedCh, isDisabled = false)

        val updatedStringMap = LinkedHashMap(_mappingsByStringKey.value)
        updatedStringMap[mappingKey] = config
        _mappingsByStringKey.value = updatedStringMap

        val updatedTargetMap = LinkedHashMap(_mappings.value)
        updatedTargetMap[MidiTargetKey(parameter, partIndex)] = config
        _mappings.value = updatedTargetMap

        saveToPrefsIncremental(mappingKey, config, updatedStringMap)
    }

    /**
     * Restores a parameter on a part to the official Yamaha default CC behavior ("Restaurar Padrão Yamaha").
     */
    fun restoreDefault(parameter: MidiLearnableParameter, partIndex: Int) {
        if (partIndex !in 0..15) return
        val controlType = parameter.name.lowercase()
        val mappingKey = "part_${partIndex}_${controlType}"
        val key = MidiTargetKey(parameter, partIndex)

        val updatedStringMap = LinkedHashMap(_mappingsByStringKey.value)
        val removedByStr = updatedStringMap.remove(mappingKey)
        val updatedTargetMap = LinkedHashMap(_mappings.value)
        val removedByTarget = updatedTargetMap.remove(key)

        if (removedByStr != null || removedByTarget != null) {
            _mappingsByStringKey.value = updatedStringMap
            _mappings.value = updatedTargetMap
            saveToPrefsIncremental(mappingKey, null, updatedStringMap)
        }
    }

    /**
     * Disables any external MIDI control over this parameter on this part ("Remover Mapeamento").
     */
    fun removeMapping(parameter: MidiLearnableParameter, partIndex: Int) {
        if (partIndex !in 0..15) return
        val controlType = parameter.name.lowercase()
        val mappingKey = "part_${partIndex}_${controlType}"
        val config = MidiMappingConfig(ccNumber = -1, channel = -1, isDisabled = true)

        val updatedStringMap = LinkedHashMap(_mappingsByStringKey.value)
        updatedStringMap[mappingKey] = config
        _mappingsByStringKey.value = updatedStringMap

        val updatedTargetMap = LinkedHashMap(_mappings.value)
        updatedTargetMap[MidiTargetKey(parameter, partIndex)] = config
        _mappings.value = updatedTargetMap

        saveToPrefsIncremental(mappingKey, config, updatedStringMap)
    }

    /**
     * Returns the custom/disabled configuration for (parameter, partIndex), or null if using Yamaha Default.
     */
    fun getMapping(parameter: MidiLearnableParameter, partIndex: Int): MidiMappingConfig? {
        val controlType = parameter.name.lowercase()
        val mappingKey = "part_${partIndex}_${controlType}"
        return _mappingsByStringKey.value[mappingKey] ?: _mappings.value[MidiTargetKey(parameter, partIndex)]
    }

    /**
     * Returns the custom/disabled configuration by string destination key (e.g. "part_4_volume").
     */
    fun getMappingByKey(mappingKey: String): MidiMappingConfig? {
        return _mappingsByStringKey.value[mappingKey]
    }

    /**
     * Finds all custom-mapped targets that match the incoming (ccNumber, channel).
     */
    fun findCustomTargets(ccNumber: Int, channel: Int): List<MidiTargetKey> {
        val matched = LinkedHashSet<MidiTargetKey>()
        for ((mappingKey, config) in _mappingsByStringKey.value) {
            if (!config.isDisabled && config.ccNumber == ccNumber && config.channel == channel) {
                parseMappingKey(mappingKey)?.let { matched.add(it) }
            }
        }
        for ((targetKey, config) in _mappings.value) {
            if (!config.isDisabled && config.ccNumber == ccNumber && config.channel == channel) {
                matched.add(targetKey)
            }
        }
        return matched.toList()
    }

    /**
     * Resolves which target parameter(s) and part(s) should be updated for an incoming (ccNumber, channel):
     * 1. Priority: Custom mappings in this repository matching (ccNumber, channel).
     * 2. Fallback: Official Yamaha default CC mapping for the part matching [channel], ONLY if that
     *    parameter on that part has no custom mapping and has not been disabled via [removeMapping].
     */
    fun resolveTargetsForIncomingCc(
        ccNumber: Int,
        channel: Int,
        parts: List<MultiPartState>
    ): List<MidiTargetKey> {
        if (channel !in 0..15 || ccNumber !in 0..127) return emptyList()

        // 1. Custom mapping priority
        val customTargets = findCustomTargets(ccNumber, channel)
        if (customTargets.isNotEmpty()) {
            return customTargets
        }

        // 2. Yamaha Default Fallback
        val defaultParam = MidiLearnableParameter.fromDefaultCc(ccNumber) ?: return emptyList()
        val part = parts.firstOrNull { it.receiveChannel == channel }
            ?: parts.getOrNull(channel)
            ?: return emptyList()

        // If this parameter on this part has a custom mapping (to another CC/Ch) or is disabled, ignore default CC
        if (getMapping(defaultParam, part.partIndex) != null) {
            return emptyList()
        }

        return listOf(MidiTargetKey(defaultParam, part.partIndex))
    }

    /**
     * Applies a MIDI CC value (0..127) to the specified [parameter] on [part].
     */
    fun applyParameterValueToPart(
        part: MultiPartState,
        parameter: MidiLearnableParameter,
        value: Int
    ): MultiPartState {
        return when (parameter) {
            MidiLearnableParameter.VOLUME -> part.copy(volume = value.coerceIn(0, 127), isMuted = if (value > 0) false else part.isMuted)
            MidiLearnableParameter.PAN -> part.copy(pan = value.coerceIn(1, 127))
            MidiLearnableParameter.MODULATION -> part.copy(modulation = value.coerceIn(0, 127))
            MidiLearnableParameter.CUTOFF -> part.copy(cutoff = value.coerceIn(0, 127))
            MidiLearnableParameter.RESONANCE -> part.copy(resonance = value.coerceIn(0, 127))
            MidiLearnableParameter.REVERB_SEND -> part.copy(reverbSend = value.coerceIn(0, 127))
            MidiLearnableParameter.CHORUS_SEND -> part.copy(chorusSend = value.coerceIn(0, 127))
        }
    }

    /**
     * Pure helper that routes an incoming Control Change (0xB0..0xBF) against [state],
     * updating all resolved target parameters.
     */
    fun routeControlChangeToState(
        state: PerformanceState,
        channel: Int,
        ccNumber: Int,
        value: Int
    ): PerformanceState {
        val targets = resolveTargetsForIncomingCc(ccNumber, channel, state.parts)
        if (targets.isEmpty()) return state
        var updatedParts = state.parts
        for (target in targets) {
            updatedParts = updatedParts.mapIndexed { idx, part ->
                if (idx == target.partIndex) applyParameterValueToPart(part, target.parameter, value) else part
            }
        }
        return state.copy(parts = updatedParts)
    }

    /**
     * Reloads mappings from SharedPreferences (useful for verifying persistence across instances).
     */
    fun reload() {
        val loadedStringMap = loadStringMapFromPrefs()
        _mappingsByStringKey.value = loadedStringMap
        _mappings.value = toTargetKeyMap(loadedStringMap)
    }

    private fun toTargetKeyMap(stringMap: Map<String, MidiMappingConfig>): Map<MidiTargetKey, MidiMappingConfig> {
        val result = LinkedHashMap<MidiTargetKey, MidiMappingConfig>()
        for ((mappingKey, config) in stringMap) {
            val targetKey = parseMappingKey(mappingKey) ?: continue
            result[targetKey] = config
        }
        return result
    }

    private fun loadStringMapFromPrefs(): Map<String, MidiMappingConfig> {
        val p = prefs ?: return emptyMap()
        val result = LinkedHashMap<String, MidiMappingConfig>()
        try {
            // 1. Parse combined map stored under KEY_MAPPINGS_JSON
            val raw = p.getString(KEY_MAPPINGS_JSON, null)
            if (!raw.isNullOrBlank()) {
                val entries = raw.split(";")
                for (entry in entries) {
                    if (entry.isBlank()) continue
                    if (entry.contains("=")) {
                        // Format: part_4_volume=cc,channel,isDisabled
                        val kv = entry.split("=", limit = 2)
                        val mappingKey = kv[0].trim()
                        val targetKey = parseMappingKey(mappingKey) ?: continue
                        val fields = kv[1].split(",")
                        if (fields.size < 3) continue
                        val ccNumber = fields[0].trim().toIntOrNull() ?: -1
                        val channel = fields[1].trim().toIntOrNull() ?: -1
                        val isDisabled = fields[2].trim().toBooleanStrictOrNull() ?: false
                        val canonicalKey = buildMappingKey(targetKey.partIndex, targetKey.parameter)
                        result[canonicalKey] = MidiMappingConfig(
                            ccNumber = ccNumber,
                            channel = channel,
                            isDisabled = isDisabled
                        )
                    } else {
                        // Legacy/Standard format: PARAM,partIndex,cc,channel,isDisabled
                        val fields = entry.split(",")
                        if (fields.size < 5) continue
                        val paramName = fields[0].trim()
                        val param = MidiLearnableParameter.entries.firstOrNull {
                            it.name.equals(paramName, ignoreCase = true)
                        } ?: continue
                        val partIndex = fields[1].trim().toIntOrNull() ?: continue
                        if (partIndex !in 0..15) continue
                        val ccNumber = fields[2].trim().toIntOrNull() ?: -1
                        val channel = fields[3].trim().toIntOrNull() ?: -1
                        val isDisabled = fields[4].trim().toBooleanStrictOrNull() ?: false
                        val controlType = param.name.lowercase()
                        val mappingKey = "part_${partIndex}_${controlType}"
                        result[mappingKey] = MidiMappingConfig(
                            ccNumber = ccNumber,
                            channel = channel,
                            isDisabled = isDisabled
                        )
                    }
                }
            }

            // 2. Also merge any individual incremental "part_${partIndex}_${controlType}" keys in SharedPreferences
            val allEntries = try { p.all } catch (_: Throwable) { null }
            if (allEntries != null) {
                for ((k, v) in allEntries) {
                    if (k == null || !k.startsWith("part_")) continue
                    val targetKey = parseMappingKey(k) ?: continue
                    val strVal = v as? String ?: continue
                    val fields = strVal.split(",")
                    if (fields.size >= 3) {
                        val ccNumber = fields[0].trim().toIntOrNull() ?: -1
                        val channel = fields[1].trim().toIntOrNull() ?: -1
                        val isDisabled = fields[2].trim().toBooleanStrictOrNull() ?: false
                        val canonicalKey = buildMappingKey(targetKey.partIndex, targetKey.parameter)
                        result[canonicalKey] = MidiMappingConfig(
                            ccNumber = ccNumber,
                            channel = channel,
                            isDisabled = isDisabled
                        )
                    }
                }
            }
        } catch (_: Throwable) {
            // Fallback to what was parsed so far if corrupted
        }
        return result
    }

    /**
     * Persists the full mapping map incrementally to SharedPreferences without ever calling `clear()`.
     */
    private fun saveToPrefsIncremental(
        changedMappingKey: String,
        changedConfig: MidiMappingConfig?,
        fullStringMap: Map<String, MidiMappingConfig>
    ) {
        val p = prefs ?: return
        try {
            val merged = LinkedHashMap<String, MidiMappingConfig>()
            // Merge any existing persisted entries first so no previous part mapping is ever lost
            merged.putAll(loadStringMapFromPrefs())
            merged.putAll(fullStringMap)
            if (changedConfig == null) {
                merged.remove(changedMappingKey)
            } else {
                merged[changedMappingKey] = changedConfig
            }

            val serialized = merged.entries.mapNotNull { (mappingKey, config) ->
                val targetKey = parseMappingKey(mappingKey) ?: return@mapNotNull null
                "${targetKey.parameter.name},${targetKey.partIndex},${config.ccNumber},${config.channel},${config.isDisabled}"
            }.joinToString(";")

            val editor = p.edit() ?: return
            // Incremental update: never call editor.clear()
            if (changedConfig == null) {
                editor.remove(changedMappingKey)
            } else {
                editor.putString(changedMappingKey, "${changedConfig.ccNumber},${changedConfig.channel},${changedConfig.isDisabled}")
            }
            editor.putString(KEY_MAPPINGS_JSON, serialized)
            editor.apply()
        } catch (_: Throwable) {
            // Ignore in test environments if editor is not mocked
        }
    }
}
