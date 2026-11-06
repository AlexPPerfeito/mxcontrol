package com.yamahamx.editor.data.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.yamahamx.editor.data.model.UserVoiceEntity

/**
 * SQLite Database Helper managing local persistence of the 128 User Voices (MSB 63, LSB 8).
 */
class UserVoiceDatabaseHelper(context: Context) : SQLiteOpenHelper(
    context,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {

    companion object {
        const val DATABASE_NAME = "mx_user_voices.db"
        const val DATABASE_VERSION = 1

        const val TABLE_USER_VOICES = "user_voices"
        const val COLUMN_SLOT_INDEX = "slot_index"
        const val COLUMN_CUSTOM_NAME = "custom_name"
        const val COLUMN_MSB = "msb"
        const val COLUMN_LSB = "lsb"
        const val COLUMN_PROGRAM_CHANGE = "program_change"
        const val COLUMN_CATEGORY_TAG = "category_tag"

        private val DEFAULT_VOICE_NAMES = listOf(
            "US001: ConcertGrand",
            "US002: BriteGrand",
            "US003: StudioGrand",
            "US004: Vintage'74",
            "US005: Jazz Organ 1",
            "US006: Real Strings",
            "US007: Power Brass",
            "US008: Classical Gtr",
            "US009: Finger Bass",
            "US010: Sweet DX",
            "US011: Analog Sweep",
            "US012: 80s Layer",
            "US013: Piano & Strings",
            "US014: Rock Grand",
            "US015: Old Blues",
            "US016: Neo Soul"
        )
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTableSql = """
            CREATE TABLE $TABLE_USER_VOICES (
                $COLUMN_SLOT_INDEX INTEGER PRIMARY KEY,
                $COLUMN_CUSTOM_NAME TEXT NOT NULL,
                $COLUMN_MSB INTEGER NOT NULL DEFAULT 63,
                $COLUMN_LSB INTEGER NOT NULL DEFAULT 8,
                $COLUMN_PROGRAM_CHANGE INTEGER NOT NULL,
                $COLUMN_CATEGORY_TAG TEXT NOT NULL DEFAULT 'USR'
            )
        """.trimIndent()
        db.execSQL(createTableSql)

        // Seed initial 128 User Voice slots
        seedInitialUserVoices(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Future schema migrations if needed
    }

    private fun seedInitialUserVoices(db: SQLiteDatabase) {
        db.beginTransaction()
        try {
            for (slot in 0..127) {
                val defaultName = if (slot < DEFAULT_VOICE_NAMES.size) {
                    DEFAULT_VOICE_NAMES[slot]
                } else {
                    "US${(slot + 1).toString().padStart(3, '0')}: User Voice ${slot + 1}"
                }

                val values = ContentValues().apply {
                    put(COLUMN_SLOT_INDEX, slot)
                    put(COLUMN_CUSTOM_NAME, defaultName)
                    put(COLUMN_MSB, 63)
                    put(COLUMN_LSB, 8)
                    put(COLUMN_PROGRAM_CHANGE, slot)
                    put(COLUMN_CATEGORY_TAG, "USR")
                }
                db.insertWithOnConflict(TABLE_USER_VOICES, null, values, SQLiteDatabase.CONFLICT_IGNORE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Retrieves all 128 User Voices ordered by slot index (0..127).
     */
    @Synchronized
    fun getAllUserVoices(): List<UserVoiceEntity> {
        val list = mutableListOf<UserVoiceEntity>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_USER_VOICES,
            null,
            null,
            null,
            null,
            null,
            "$COLUMN_SLOT_INDEX ASC"
        )

        cursor.use { c ->
            val slotIdxCol = c.getColumnIndexOrThrow(COLUMN_SLOT_INDEX)
            val nameCol = c.getColumnIndexOrThrow(COLUMN_CUSTOM_NAME)
            val msbCol = c.getColumnIndexOrThrow(COLUMN_MSB)
            val lsbCol = c.getColumnIndexOrThrow(COLUMN_LSB)
            val pcCol = c.getColumnIndexOrThrow(COLUMN_PROGRAM_CHANGE)
            val catCol = c.getColumnIndexOrThrow(COLUMN_CATEGORY_TAG)

            while (c.moveToNext()) {
                list.add(
                    UserVoiceEntity(
                        slotIndex = c.getInt(slotIdxCol),
                        customName = c.getString(nameCol),
                        msb = c.getInt(msbCol),
                        lsb = c.getInt(lsbCol),
                        programChange = c.getInt(pcCol),
                        categoryTag = c.getString(catCol)
                    )
                )
            }
        }

        // If table was empty for any reason, re-seed and return
        if (list.size < 128) {
            val writable = writableDatabase
            seedInitialUserVoices(writable)
            return getAllUserVoices()
        }

        return list
    }

    /**
     * Updates the custom name of a specific User Voice slot.
     */
    @Synchronized
    fun updateUserVoiceName(slotIndex: Int, newName: String): Boolean {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_CUSTOM_NAME, newName.trim())
        }
        val rowsAffected = db.update(
            TABLE_USER_VOICES,
            values,
            "$COLUMN_SLOT_INDEX = ?",
            arrayOf(slotIndex.toString())
        )
        return rowsAffected > 0
    }

    /**
     * Gets a single User Voice slot by its index (0..127).
     */
    @Synchronized
    fun getUserVoice(slotIndex: Int): UserVoiceEntity? {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_USER_VOICES,
            null,
            "$COLUMN_SLOT_INDEX = ?",
            arrayOf(slotIndex.toString()),
            null,
            null,
            null
        )

        cursor.use { c ->
            if (c.moveToFirst()) {
                return UserVoiceEntity(
                    slotIndex = c.getInt(c.getColumnIndexOrThrow(COLUMN_SLOT_INDEX)),
                    customName = c.getString(c.getColumnIndexOrThrow(COLUMN_CUSTOM_NAME)),
                    msb = c.getInt(c.getColumnIndexOrThrow(COLUMN_MSB)),
                    lsb = c.getInt(c.getColumnIndexOrThrow(COLUMN_LSB)),
                    programChange = c.getInt(c.getColumnIndexOrThrow(COLUMN_PROGRAM_CHANGE)),
                    categoryTag = c.getString(c.getColumnIndexOrThrow(COLUMN_CATEGORY_TAG))
                )
            }
        }
        return null
    }
}
