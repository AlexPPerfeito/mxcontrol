package com.yamahamx.editor.data.repository

import android.content.Context
import com.yamahamx.editor.data.db.UserVoiceDatabaseHelper
import com.yamahamx.editor.data.model.UserVoiceEntity
import com.yamahamx.editor.data.model.YamahaVoice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Repository providing reactive and direct access to the 128 User Voices bank.
 */
class UserVoiceRepository(
    context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {

    private val dbHelper = UserVoiceDatabaseHelper(context.applicationContext)

    private val _userVoicesFlow = MutableStateFlow<List<UserVoiceEntity>>(emptyList())
    val userVoicesFlow: StateFlow<List<UserVoiceEntity>> = _userVoicesFlow.asStateFlow()

    init {
        refreshVoices()
    }

    /**
     * Loads/refreshes user voices from the database into the StateFlow.
     */
    fun refreshVoices() {
        scope.launch {
            val list = withContext(Dispatchers.IO) {
                dbHelper.getAllUserVoices()
            }
            _userVoicesFlow.value = list
        }
    }

    /**
     * Updates the custom name for a specific User Voice slot and updates the Flow.
     */
    fun updateUserVoiceName(slotIndex: Int, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isNotBlank() && slotIndex in 0..127) {
            scope.launch {
                withContext(Dispatchers.IO) {
                    dbHelper.updateUserVoiceName(slotIndex, trimmed)
                }
                refreshVoices()
            }
        }
    }

    /**
     * Synchronous retrieval of all User Voices converted to [YamahaVoice].
     */
    fun getUserVoicesAsYamahaVoices(): List<YamahaVoice> {
        val current = _userVoicesFlow.value
        return if (current.isNotEmpty()) {
            current.map { it.toYamahaVoice() }
        } else {
            dbHelper.getAllUserVoices().map { it.toYamahaVoice() }
        }
    }

    /**
     * Returns a specific User Voice by slot index.
     */
    fun getUserVoice(slotIndex: Int): UserVoiceEntity? {
        return _userVoicesFlow.value.getOrNull(slotIndex) ?: dbHelper.getUserVoice(slotIndex)
    }
}
