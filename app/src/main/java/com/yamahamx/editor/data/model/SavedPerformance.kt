package com.yamahamx.editor.data.model

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Serialized representation of a Voice inside a saved part.
 */
data class SavedVoice(
    val name: String,
    val category: String,
    val subCategory: String = "",
    val msb: Int = 63,
    val lsb: Int = 0,
    val programChange: Int = 0
) {
    fun toYamahaVoice(id: Int = 1): YamahaVoice {
        return YamahaVoice(
            id = id,
            name = name,
            category = category,
            subCategory = subCategory,
            msb = msb,
            lsb = lsb,
            programChange = programChange
        )
    }

    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("name", name)
            put("category", category)
            put("subCategory", subCategory)
            put("msb", msb)
            put("lsb", lsb)
            put("programChange", programChange)
        }
    }

    companion object {
        fun fromYamahaVoice(voice: YamahaVoice): SavedVoice {
            return SavedVoice(
                name = voice.name,
                category = voice.category,
                subCategory = voice.subCategory,
                msb = voice.msb,
                lsb = voice.lsb,
                programChange = voice.programChange
            )
        }

        fun fromJson(json: JSONObject): SavedVoice {
            return SavedVoice(
                name = json.optString("name", "CncrtGrand"),
                category = json.optString("category", "AP"),
                subCategory = json.optString("subCategory", ""),
                msb = json.optInt("msb", 63),
                lsb = json.optInt("lsb", 0),
                programChange = json.optInt("programChange", 0)
            )
        }
    }
}

/**
 * Serialized representation of a single part (channel).
 */
data class SavedPart(
    val partIndex: Int,
    val voice: SavedVoice,
    val volume: Int = 100,
    val pan: Int = 64,
    val reverbSend: Int = 12,
    val chorusSend: Int = 0,
    val noteShift: Int = 64,
    val noteLimitLow: Int = 0,
    val noteLimitHigh: Int = 127,
    val cutoff: Int = 64,
    val resonance: Int = 64,
    val attack: Int = 64,
    val decay: Int = 64,
    val release: Int = 64,
    val modulation: Int = 0,
    val isMuted: Boolean = false,
    val isSolo: Boolean = false,
    val receiveChannel: Int = partIndex,
    val isModulationEnabled: Boolean = true,
    val isLayerSelected: Boolean = false,
    val isCompressorEnabled: Boolean = false,
    val compressorThreshold: Int = 72,
    val compressorOutputGain: Int = 64,
    val compressorRatio: Int = 3,
    val compressorAttack: Int = 19,
    val compressorRelease: Int = 4
) {
    fun toMultiPartState(): MultiPartState {
        val safeVolume = volume.coerceIn(0, 127)
        return MultiPartState(
            partIndex = partIndex,
            voice = voice.toYamahaVoice(partIndex + 1),
            volume = safeVolume,
            pan = pan,
            reverbSend = reverbSend,
            chorusSend = chorusSend,
            noteShift = noteShift,
            noteLimitLow = noteLimitLow,
            noteLimitHigh = noteLimitHigh,
            cutoff = cutoff,
            resonance = resonance,
            attack = attack.coerceIn(0, 127),
            decay = decay.coerceIn(0, 127),
            release = release.coerceIn(0, 127),
            modulation = modulation,
            isModulationEnabled = isModulationEnabled,
            isMuted = isMuted,
            isSolo = isSolo,
            receiveChannel = if (partIndex == 0 && (receiveChannel == 127 || receiveChannel !in 0..15)) 0 else receiveChannel,
            isLayerSelected = isLayerSelected,
            isCompressorEnabled = isCompressorEnabled,
            compressorThreshold = compressorThreshold,
            compressorOutputGain = compressorOutputGain,
            compressorRatio = compressorRatio,
            compressorAttack = compressorAttack,
            compressorRelease = compressorRelease
        )
    }

    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("partIndex", partIndex)
            put("voice", voice.toJson())
            put("volume", volume)
            put("pan", pan)
            put("reverbSend", reverbSend)
            put("chorusSend", chorusSend)
            put("noteShift", noteShift)
            put("noteLimitLow", noteLimitLow)
            put("noteLimitHigh", noteLimitHigh)
            put("cutoff", cutoff)
            put("resonance", resonance)
            put("attack", attack)
            put("decay", decay)
            put("release", release)
            put("modulation", modulation)
            put("isModulationEnabled", isModulationEnabled)
            put("isMuted", isMuted)
            put("isSolo", isSolo)
            put("receiveChannel", receiveChannel)
            put("isLayerSelected", isLayerSelected)
            put("isCompressorEnabled", isCompressorEnabled)
            put("compressorThreshold", compressorThreshold)
            put("compressorOutputGain", compressorOutputGain)
            put("compressorRatio", compressorRatio)
            put("compressorAttack", compressorAttack)
            put("compressorRelease", compressorRelease)
        }
    }

    companion object {
        fun fromMultiPartState(state: MultiPartState): SavedPart {
            val safeVolume = state.volume.coerceIn(0, 127)
            return SavedPart(
                partIndex = state.partIndex,
                voice = SavedVoice.fromYamahaVoice(state.voice),
                volume = safeVolume,
                pan = state.pan,
                reverbSend = state.reverbSend,
                chorusSend = state.chorusSend,
                noteShift = state.noteShift,
                noteLimitLow = state.noteLimitLow,
                noteLimitHigh = state.noteLimitHigh,
                cutoff = state.cutoff,
                resonance = state.resonance,
                attack = state.attack.coerceIn(0, 127),
                decay = state.decay.coerceIn(0, 127),
                release = state.release.coerceIn(0, 127),
                modulation = state.modulation,
                isModulationEnabled = state.isModulationEnabled,
                isMuted = state.isMuted,
                isSolo = state.isSolo,
                receiveChannel = state.receiveChannel,
                isLayerSelected = state.isLayerSelected,
                isCompressorEnabled = state.isCompressorEnabled,
                compressorThreshold = state.compressorThreshold,
                compressorOutputGain = state.compressorOutputGain,
                compressorRatio = state.compressorRatio,
                compressorAttack = state.compressorAttack,
                compressorRelease = state.compressorRelease
            )
        }

        fun fromJson(json: JSONObject): SavedPart {
            val rawVol = json.optInt("volume", 100)
            val isMuted = json.optBoolean("isMuted", false)
            val safeVol = rawVol.coerceIn(0, 127)
            val partIdx = json.optInt("partIndex", 0)
            val rawRxCh = json.optInt("receiveChannel", partIdx)
            val safeRxCh = if (partIdx == 0 && (rawRxCh == 127 || rawRxCh !in 0..15)) 0 else rawRxCh

            return SavedPart(
                partIndex = partIdx,
                voice = SavedVoice.fromJson(json.getJSONObject("voice")),
                volume = safeVol,
                pan = json.optInt("pan", 64),
                reverbSend = json.optInt("reverbSend", 12),
                chorusSend = json.optInt("chorusSend", 0),
                noteShift = json.optInt("noteShift", 64),
                noteLimitLow = json.optInt("noteLimitLow", 0),
                noteLimitHigh = json.optInt("noteLimitHigh", 127),
                cutoff = json.optInt("cutoff", 64),
                resonance = json.optInt("resonance", 64),
                attack = json.optInt("attack", 64).coerceIn(0, 127),
                decay = json.optInt("decay", 64).coerceIn(0, 127),
                release = json.optInt("release", 64).coerceIn(0, 127),
                modulation = json.optInt("modulation", 0),
                isModulationEnabled = json.optBoolean("isModulationEnabled", true),
                isMuted = isMuted,
                isSolo = json.optBoolean("isSolo", false),
                receiveChannel = safeRxCh,
                isLayerSelected = json.optBoolean("isLayerSelected", false),
                isCompressorEnabled = json.optBoolean("isCompressorEnabled", false),
                compressorThreshold = json.optInt("compressorThreshold", 72),
                compressorOutputGain = json.optInt("compressorOutputGain", 64),
                compressorRatio = json.optInt("compressorRatio", 3),
                compressorAttack = json.optInt("compressorAttack", 19),
                compressorRelease = json.optInt("compressorRelease", 4)
            )
        }
    }
}

/**
 * Complete Performance entity containing settings for all 16 parts and global parameters.
 */
data class SavedPerformance(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val categoryTag: String = "Geral",
    val timestamp: Long = System.currentTimeMillis(),
    val part12Link: Boolean = false,
    val masterVolume: Int = 127,
    val masterEqGains: List<Int> = List(5) { 64 },
    val parts: List<SavedPart>
) {
    val formattedDate: String
        get() {
            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }

    val primaryVoicesSummary: String
        get() {
            val audibleParts = parts.filter { !it.isMuted && it.volume > 0 }
            val active = if (audibleParts.isNotEmpty()) audibleParts else parts.take(2)
            return active.take(3).joinToString(" + ") { "P${it.partIndex + 1}: ${it.voice.name}" }
        }

    fun toJson(): JSONObject {
        val partsArray = JSONArray()
        parts.forEach { partsArray.put(it.toJson()) }

        return JSONObject().apply {
            put("id", id)
            put("name", name)
            put("categoryTag", categoryTag)
            put("timestamp", timestamp)
            put("part12Link", part12Link)
            put("masterVolume", masterVolume)
            val eqArray = JSONArray()
            masterEqGains.forEach { eqArray.put(it.coerceIn(0, 127)) }
            put("masterEqGains", eqArray)
            put("parts", partsArray)
        }
    }

    companion object {
        fun fromState(
            name: String,
            categoryTag: String = "Geral",
            state: PerformanceState,
            id: String = UUID.randomUUID().toString()
        ): SavedPerformance {
            return SavedPerformance(
                id = id,
                name = name,
                categoryTag = categoryTag,
                timestamp = System.currentTimeMillis(),
                part12Link = state.common.part12Link,
                masterVolume = state.common.masterVolume,
                masterEqGains = state.common.masterEqGains,
                parts = state.parts.map { SavedPart.fromMultiPartState(it) }
            )
        }

        fun fromJson(json: JSONObject): SavedPerformance {
            val partsList = mutableListOf<SavedPart>()
            val partsArray = json.optJSONArray("parts")
            if (partsArray != null) {
                for (i in 0 until partsArray.length()) {
                    partsList.add(SavedPart.fromJson(partsArray.getJSONObject(i)))
                }
            }

            return SavedPerformance(
                id = json.optString("id", UUID.randomUUID().toString()),
                name = json.optString("name", "Sem Nome"),
                categoryTag = json.optString("categoryTag", "Geral"),
                timestamp = json.optLong("timestamp", System.currentTimeMillis()),
                part12Link = json.optBoolean("part12Link", false),
                masterVolume = json.optInt("masterVolume", 127),
                masterEqGains = run {
                    val arr = json.optJSONArray("masterEqGains")
                    if (arr != null && arr.length() == 5) {
                        List(5) { i -> arr.optInt(i, 64).coerceIn(0, 127) }
                    } else {
                        List(5) { 64 }
                    }
                },
                parts = partsList
            )
        }
    }
}
