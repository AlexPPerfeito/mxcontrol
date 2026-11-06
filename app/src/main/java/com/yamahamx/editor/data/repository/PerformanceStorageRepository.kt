package com.yamahamx.editor.data.repository

import android.content.Context
import android.util.Log
import com.yamahamx.editor.data.model.SavedPart
import com.yamahamx.editor.data.model.SavedPerformance
import com.yamahamx.editor.data.model.SavedVoice
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Manages local persistence, CRUD and JSON import/export of user Performances in internal storage.
 */
class PerformanceStorageRepository(private val context: Context) {

    companion object {
        private const val TAG = "PerfStorageRepo"
        private const val FILE_NAME = "performances.json"
        private const val PREFS_CATEGORIES_KEY = "custom_setlist_categories"

        val DEFAULT_CATEGORIES = listOf("GERAL", "ROCK", "IGREJA", "POP", "JAZZ", "ENSAIO")
        val PRESET_TAGS: List<String>
            get() = listOf("TODAS") + DEFAULT_CATEGORIES

        // 01/11/2026 00:00:00 BRT (03:00:00 UTC)
        const val PROMO_START_TIMESTAMP = 1793502000000L
        // 08/11/2026 00:00:00 BRT (03:00:00 UTC)
        const val PROMO_END_TIMESTAMP   = 1794106800000L

        /**
         * Parses and validates a JSON string representing a list of SavedPerformances.
         * Returns the list of parsed performances if valid, or null if invalid or empty.
         */
        fun parsePerformancesJson(jsonString: String): List<SavedPerformance>? {
            return try {
                val trimmed = jsonString.trim()
                if (trimmed.isEmpty()) return null
                val jsonArray = JSONArray(trimmed)
                if (jsonArray.length() == 0) return null
                val list = mutableListOf<SavedPerformance>()
                for (i in 0 until jsonArray.length()) {
                    val item = SavedPerformance.fromJson(jsonArray.getJSONObject(i))
                    list.add(item)
                }
                if (list.isEmpty()) null else list
            } catch (e: Exception) {
                null
            }
        }

        /**
         * Pure function to merge or replace performances while ensuring each imported
         * item receives a unique UUID to prevent ID collisions.
         */
        fun mergePerformances(
            existing: List<SavedPerformance>,
            imported: List<SavedPerformance>,
            replaceAll: Boolean
        ): List<SavedPerformance> {
            val processed = imported.map { it.copy(id = UUID.randomUUID().toString()) }
            return if (replaceAll) {
                processed
            } else {
                processed + existing
            }
        }
    }

    private val prefs = context.getSharedPreferences("yamaha_mx_prefs", Context.MODE_PRIVATE)

    @Synchronized
    fun getCategories(): List<String> {
        val jsonStr = prefs.getString(PREFS_CATEGORIES_KEY, null) ?: return DEFAULT_CATEGORIES
        return try {
            val jsonArray = JSONArray(jsonStr)
            val list = mutableListOf<String>()
            for (i in 0 until jsonArray.length()) {
                val cat = jsonArray.optString(i)
                if (cat.isNotBlank()) {
                    list.add(cat)
                }
            }
            if (list.isEmpty()) DEFAULT_CATEGORIES else list
        } catch (e: Exception) {
            Log.e(TAG, "Error loading categories", e)
            DEFAULT_CATEGORIES
        }
    }

    @Synchronized
    fun saveCategories(categories: List<String>) {
        try {
            val jsonArray = JSONArray()
            categories.forEach { jsonArray.put(it) }
            prefs.edit().putString(PREFS_CATEGORIES_KEY, jsonArray.toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving categories", e)
        }
    }

    @Synchronized
    fun renameCategory(oldCategory: String, newCategory: String): Boolean {
        val trimmedNew = newCategory.trim()
        if (trimmedNew.isBlank() || trimmedNew.equals("TODAS", ignoreCase = true)) return false

        val current = getCategories().toMutableList()
        val index = current.indexOfFirst { it.equals(oldCategory, ignoreCase = true) }
        if (index == -1) return false

        // Check if new name already exists in another category
        val isDuplicate = current.any {
            it.equals(trimmedNew, ignoreCase = true) && !it.equals(oldCategory, ignoreCase = true)
        }
        if (isDuplicate) return false

        // 1. Update categories list
        current[index] = trimmedNew
        saveCategories(current)

        // 2. Update all saved performances that had oldCategory
        val allPerformances = getAllPerformances()
        var modified = false
        val updated = allPerformances.map { perf ->
            if (perf.categoryTag.equals(oldCategory, ignoreCase = true)) {
                modified = true
                perf.copy(categoryTag = trimmedNew)
            } else {
                perf
            }
        }
        if (modified) {
            saveAll(updated)
        }

        // 3. Update working performance if applicable
        val working = getWorkingPerformance()
        if (working != null && working.categoryTag.equals(oldCategory, ignoreCase = true)) {
            saveWorkingPerformance(working.copy(categoryTag = trimmedNew), getLastFocusedPartIndex())
        }

        return true
    }

    private val storageFile: File
        get() = File(context.filesDir, FILE_NAME)

    fun saveLastActiveState(performanceId: String?, focusedPartIndex: Int) {
        prefs.edit()
            .putString("last_performance_id", performanceId)
            .putInt("last_focused_part_index", focusedPartIndex)
            .apply()
    }

    fun saveWorkingPerformance(performance: SavedPerformance, focusedPartIndex: Int) {
        try {
            prefs.edit()
                .putString("working_performance_json", performance.toJson().toString())
                .putString("last_performance_id", performance.id)
                .putInt("last_focused_part_index", focusedPartIndex)
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving working performance", e)
        }
    }

    fun getWorkingPerformance(): SavedPerformance? {
        val jsonStr = prefs.getString("working_performance_json", null) ?: return null
        return try {
            SavedPerformance.fromJson(JSONObject(jsonStr))
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing working performance", e)
            null
        }
    }

    fun getLastPerformanceId(): String? {
        return prefs.getString("last_performance_id", null)
    }

    fun getLastFocusedPartIndex(): Int {
        return prefs.getInt("last_focused_part_index", 0)
    }

    val isLicensePurchased: Boolean
        get() = prefs.getBoolean("is_pro_unlocked", false)

    fun isPromoActive(): Boolean {
        val now = System.currentTimeMillis()
        return now in PROMO_START_TIMESTAMP until PROMO_END_TIMESTAMP
    }

    fun isPromoActive(timestamp: Long): Boolean {
        return timestamp in PROMO_START_TIMESTAMP until PROMO_END_TIMESTAMP
    }

    fun isProUnlocked(): Boolean {
        // 1. Licença definitiva comprada pelo usuário (sempre Pro)
        if (isLicensePurchased) return true
        
        // 2. Janela de degustação de 01 a 07 de novembro
        if (isPromoActive()) return true

        return false
    }

    fun isProUnlocked(timestamp: Long): Boolean {
        if (isLicensePurchased) return true
        if (isPromoActive(timestamp)) return true
        return false
    }

    fun setProUnlocked(unlocked: Boolean) {
        prefs.edit().putBoolean("is_pro_unlocked", unlocked).apply()
    }

    @Synchronized
    fun getAllPerformances(): List<SavedPerformance> {
        val file = storageFile
        if (!file.exists()) {
            val initialPresets = createDefaultFactoryPresets()
            saveAll(initialPresets)
            return initialPresets
        }

        return try {
            val jsonString = file.readText(Charsets.UTF_8)
            val jsonArray = JSONArray(jsonString)
            val list = mutableListOf<SavedPerformance>()
            for (i in 0 until jsonArray.length()) {
                val itemJson = jsonArray.getJSONObject(i)
                list.add(SavedPerformance.fromJson(itemJson))
            }
            list.sortedByDescending { it.timestamp }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading performances JSON", e)
            emptyList()
        }
    }

    @Synchronized
    fun savePerformance(performance: SavedPerformance) {
        val current = getAllPerformances().toMutableList()
        val index = current.indexOfFirst { it.id == performance.id }
        if (index != -1) {
            current[index] = performance
        } else {
            current.add(0, performance)
        }
        saveAll(current)
    }

    @Synchronized
    fun updatePerformance(performance: SavedPerformance) {
        savePerformance(performance)
    }

    @Synchronized
    fun deletePerformance(id: String) {
        val current = getAllPerformances().filter { it.id != id }
        saveAll(current)
    }

    @Synchronized
    fun duplicatePerformance(performance: SavedPerformance): SavedPerformance {
        val duplicated = performance.copy(
            id = UUID.randomUUID().toString(),
            name = "${performance.name} (Cópia)",
            timestamp = System.currentTimeMillis()
        )
        savePerformance(duplicated)
        return duplicated
    }

    @Synchronized
    fun renamePerformance(id: String, newName: String, newTag: String) {
        val current = getAllPerformances().map {
            if (it.id == id) {
                it.copy(name = newName, categoryTag = newTag, timestamp = System.currentTimeMillis())
            } else it
        }
        saveAll(current)
    }

    @Synchronized
    fun exportJson(): String {
        val list = getAllPerformances()
        val jsonArray = JSONArray()
        list.forEach { jsonArray.put(it.toJson()) }
        return jsonArray.toString(2)
    }

    @Synchronized
    fun parseJson(jsonString: String): List<SavedPerformance>? = parsePerformancesJson(jsonString)

    @Synchronized
    fun getPerformancesCount(): Int {
        return getAllPerformances().size
    }

    @Synchronized
    fun importPerformances(importedList: List<SavedPerformance>, replaceAll: Boolean = false): Int {
        if (importedList.isEmpty()) return 0
        try {
            val current = if (replaceAll) emptyList() else getAllPerformances()
            val result = mergePerformances(current, importedList, replaceAll)
            saveAll(result)
            return importedList.size
        } catch (e: Exception) {
            Log.e(TAG, "Error saving imported performances", e)
            return -1
        }
    }

    @Synchronized
    fun importJson(jsonString: String, replaceAll: Boolean = false): Int {
        val parsed = parsePerformancesJson(jsonString) ?: return -1
        return importPerformances(parsed, replaceAll)
    }

    private fun saveAll(list: List<SavedPerformance>) {
        try {
            val jsonArray = JSONArray()
            list.forEach { jsonArray.put(it.toJson()) }
            storageFile.writeText(jsonArray.toString(2), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Error writing performances JSON to file", e)
        }
    }

    private fun createDefaultFactoryPresets(): List<SavedPerformance> {
        val allVoices = VoiceRepository.getAllVoices()
        val grand = allVoices.firstOrNull { it.name == "CncrtGrand" } ?: allVoices[0]
        val strings = allVoices.firstOrNull { it.name == "Real Strings" } ?: allVoices[10]
        val pad = allVoices.firstOrNull { it.name == "Analog Warm Pad" } ?: allVoices[20]
        val rhodes = allVoices.firstOrNull { it.name == "Vintage'74" } ?: allVoices[25]
        val organ = allVoices.firstOrNull { it.name == "Jazz Organ 1" } ?: allVoices[50]
        val bass = allVoices.firstOrNull { it.name == "FingerBass" } ?: allVoices[70]

        // 1. Preset: Grand Piano + Warm Strings Layer
        val parts1 = List(16) { idx ->
            when (idx) {
                0 -> SavedPart(0, SavedVoice.fromYamahaVoice(grand), volume = 110, pan = 64, reverbSend = 24)
                1 -> SavedPart(1, SavedVoice.fromYamahaVoice(strings), volume = 85, pan = 64, reverbSend = 40)
                else -> SavedPart(idx, SavedVoice.fromYamahaVoice(VoiceRepository.getDefaultVoiceForPart(idx)), isMuted = true)
            }
        }
        val p1 = SavedPerformance(
            name = "Ballad Piano & Strings",
            categoryTag = "Pop",
            part12Link = true,
            parts = parts1
        )

        // 2. Preset: Worship Pad + Rhodes
        val parts2 = List(16) { idx ->
            when (idx) {
                0 -> SavedPart(0, SavedVoice.fromYamahaVoice(rhodes), volume = 105, pan = 50, reverbSend = 32, chorusSend = 20)
                1 -> SavedPart(1, SavedVoice.fromYamahaVoice(pad), volume = 75, pan = 78, reverbSend = 50)
                else -> SavedPart(idx, SavedVoice.fromYamahaVoice(VoiceRepository.getDefaultVoiceForPart(idx)), isMuted = true)
            }
        }
        val p2 = SavedPerformance(
            name = "Worship Rhodes & Pad",
            categoryTag = "Igreja",
            part12Link = true,
            parts = parts2
        )

        // 3. Preset: Rock Organ & Left Bass Split
        val parts3 = List(16) { idx ->
            when (idx) {
                0 -> SavedPart(0, SavedVoice.fromYamahaVoice(organ), volume = 115, pan = 64, noteLimitLow = 60, noteLimitHigh = 127) // Upper C3..G8
                1 -> SavedPart(1, SavedVoice.fromYamahaVoice(bass), volume = 105, pan = 64, noteLimitLow = 0, noteLimitHigh = 59)   // Lower C-2..B2
                else -> SavedPart(idx, SavedVoice.fromYamahaVoice(VoiceRepository.getDefaultVoiceForPart(idx)), isMuted = true)
            }
        }
        val p3 = SavedPerformance(
            name = "Rock Organ + Bass Split",
            categoryTag = "Rock",
            part12Link = false,
            parts = parts3
        )

        return listOf(p1, p2, p3)
    }
}
