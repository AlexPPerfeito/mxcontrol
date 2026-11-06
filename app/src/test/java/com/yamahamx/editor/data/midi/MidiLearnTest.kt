package com.yamahamx.editor.data.midi

import android.content.SharedPreferences
import com.yamahamx.editor.data.model.PerformanceState
import com.yamahamx.editor.data.repository.MidiLearnableParameter
import com.yamahamx.editor.data.repository.MidiMappingRepository
import com.yamahamx.editor.data.repository.MidiTargetKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MidiLearnTest {

    /**
     * In-memory SharedPreferences implementation for deterministic unit testing of persistence.
     */
    private class FakeSharedPreferences : SharedPreferences {
        private val storage = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = storage.toMutableMap()
        override fun getString(key: String?, defValue: String?): String? =
            (storage[key] as? String) ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = defValues
        override fun getInt(key: String?, defValue: Int): Int = (storage[key] as? Int) ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = (storage[key] as? Long) ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = (storage[key] as? Float) ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = (storage[key] as? Boolean) ?: defValue
        override fun contains(key: String?): Boolean = storage.containsKey(key)

        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            private var clearAll = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = this
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor = this
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = this
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = this
            override fun remove(key: String?): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = null
            }
            override fun clear(): SharedPreferences.Editor = apply { clearAll = true }
            override fun commit(): Boolean {
                apply()
                return true
            }
            override fun apply() {
                if (clearAll) storage.clear()
                for ((k, v) in pending) {
                    if (v == null) storage.remove(k) else storage[k] = v
                }
            }
        }

        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    }

    @Test
    fun `capture and bind CC and Channel to target parameter and part`() {
        val prefs = FakeSharedPreferences()
        val repository = MidiMappingRepository(prefs)
        var state = PerformanceState()

        // Simulate user long-pressing Cutoff on Part 2 (partIndex = 1) and receiving CC 21 on Channel 4 (index 3)
        val targetParam = MidiLearnableParameter.CUTOFF
        val targetPartIndex = 1
        repository.assignMapping(
            parameter = targetParam,
            partIndex = targetPartIndex,
            ccNumber = 21,
            channel = 3
        )

        val config = repository.getMapping(targetParam, targetPartIndex)
        assertNotNull(config)
        assertEquals(21, config!!.ccNumber)
        assertEquals(3, config.channel)
        assertFalse(config.isDisabled)

        // Incoming CC 21 on Channel 4 (index 3) with value 110 must update Cutoff on Part 2
        state = repository.routeControlChangeToState(state, channel = 3, ccNumber = 21, value = 110)
        assertEquals(110, state.parts[1].cutoff)
        // Other parts remain at default cutoff (64)
        assertEquals(64, state.parts[0].cutoff)
        assertEquals(64, state.parts[2].cutoff)
    }

    @Test
    fun `custom mapping takes strict priority over Yamaha default fallback`() {
        val repository = MidiMappingRepository()
        var state = PerformanceState()

        // By default, CC 7 on Channel 1 (index 0) controls Volume of Part 1 (partIndex = 0)
        state = repository.routeControlChangeToState(state, channel = 0, ccNumber = 7, value = 95)
        assertEquals(95, state.parts[0].volume)

        // Now user maps CC 7 on Channel 1 (index 0) to RESONANCE of Part 3 (partIndex = 2)
        repository.assignMapping(
            parameter = MidiLearnableParameter.RESONANCE,
            partIndex = 2,
            ccNumber = 7,
            channel = 0
        )

        // Incoming CC 7 on Channel 1 (index 0) with value 120 must update Part 3 Resonance (priority)
        // and MUST NOT change Part 1 Volume
        state = repository.routeControlChangeToState(state, channel = 0, ccNumber = 7, value = 120)
        assertEquals(120, state.parts[2].resonance)
        assertEquals(95, state.parts[0].volume)

        // Furthermore, if Part 1 Volume is mapped to CC 16 on Channel 2 (index 1),
        // default CC 7 on Channel 1 (index 0) must no longer affect Part 1 Volume even if Part 3 mapping is cleared
        repository.restoreDefault(MidiLearnableParameter.RESONANCE, 2)
        repository.assignMapping(
            parameter = MidiLearnableParameter.VOLUME,
            partIndex = 0,
            ccNumber = 16,
            channel = 1
        )

        // Default CC 7 on Channel 0 is ignored because Part 1 Volume has a custom mapping
        state = repository.routeControlChangeToState(state, channel = 0, ccNumber = 7, value = 40)
        assertEquals(95, state.parts[0].volume)

        // Custom CC 16 on Channel 1 updates Part 1 Volume
        state = repository.routeControlChangeToState(state, channel = 1, ccNumber = 16, value = 40)
        assertEquals(40, state.parts[0].volume)
    }

    @Test
    fun `persistence across repository instances and restoration to Yamaha factory default`() {
        val prefs = FakeSharedPreferences()
        val repo1 = MidiMappingRepository(prefs)

        // Assign custom mappings for Volume (Part 1) and Reverb Send (Part 4)
        repo1.assignMapping(MidiLearnableParameter.VOLUME, partIndex = 0, ccNumber = 14, channel = 2)
        repo1.assignMapping(MidiLearnableParameter.REVERB_SEND, partIndex = 3, ccNumber = 55, channel = 0)

        // Instantiate a second repository backed by the same SharedPreferences to verify persistence
        val repo2 = MidiMappingRepository(prefs)
        val persistedVol = repo2.getMapping(MidiLearnableParameter.VOLUME, 0)
        val persistedRev = repo2.getMapping(MidiLearnableParameter.REVERB_SEND, 3)

        assertNotNull(persistedVol)
        assertEquals(14, persistedVol!!.ccNumber)
        assertEquals(2, persistedVol.channel)

        assertNotNull(persistedRev)
        assertEquals(55, persistedRev!!.ccNumber)
        assertEquals(0, persistedRev.channel)

        // Restore Part 1 Volume to Yamaha Factory Default ("Restaurar Padrão Yamaha")
        repo2.restoreDefault(MidiLearnableParameter.VOLUME, 0)
        assertNull(repo2.getMapping(MidiLearnableParameter.VOLUME, 0))

        // Verify persistence after restoration in a third repository instance
        val repo3 = MidiMappingRepository(prefs)
        assertNull(repo3.getMapping(MidiLearnableParameter.VOLUME, 0))
        assertNotNull(repo3.getMapping(MidiLearnableParameter.REVERB_SEND, 3))

        // Verify Part 1 Volume now responds again to official Yamaha Default CC 7 on Channel 0
        var state = PerformanceState()
        state = repo3.routeControlChangeToState(state, channel = 0, ccNumber = 7, value = 88)
        assertEquals(88, state.parts[0].volume)
    }

    @Test
    fun `removeMapping disables external MIDI control and default Yamaha fallback for all parameters`() {
        val prefs = FakeSharedPreferences()
        val repository = MidiMappingRepository(prefs)
        var state = PerformanceState()

        // Verify all 7 default Yamaha CCs work initially on Part 1 (channel 0)
        state = repository.routeControlChangeToState(state, channel = 0, ccNumber = 7, value = 101)  // Volume
        state = repository.routeControlChangeToState(state, channel = 0, ccNumber = 10, value = 32)  // Pan
        state = repository.routeControlChangeToState(state, channel = 0, ccNumber = 1, value = 77)   // Modulation
        state = repository.routeControlChangeToState(state, channel = 0, ccNumber = 74, value = 90)  // Cutoff
        state = repository.routeControlChangeToState(state, channel = 0, ccNumber = 71, value = 85)  // Resonance
        state = repository.routeControlChangeToState(state, channel = 0, ccNumber = 91, value = 60)  // Reverb Send
        state = repository.routeControlChangeToState(state, channel = 0, ccNumber = 93, value = 45)  // Chorus Send

        assertEquals(101, state.parts[0].volume)
        assertEquals(32, state.parts[0].pan)
        assertEquals(77, state.parts[0].modulation)
        assertEquals(90, state.parts[0].cutoff)
        assertEquals(85, state.parts[0].resonance)
        assertEquals(60, state.parts[0].reverbSend)
        assertEquals(45, state.parts[0].chorusSend)

        // Now user selects "Remover Mapeamento" on Cutoff of Part 1
        repository.removeMapping(MidiLearnableParameter.CUTOFF, partIndex = 0)
        val disabledConfig = repository.getMapping(MidiLearnableParameter.CUTOFF, 0)
        assertNotNull(disabledConfig)
        assertTrue(disabledConfig!!.isDisabled)

        // Incoming CC 74 on Channel 0 must now be ignored for Part 1 Cutoff
        state = repository.routeControlChangeToState(state, channel = 0, ccNumber = 74, value = 10)
        assertEquals(90, state.parts[0].cutoff)

        // Verify disabled state also persists across SharedPreferences reload
        val reloadedRepo = MidiMappingRepository(prefs)
        assertTrue(reloadedRepo.getMapping(MidiLearnableParameter.CUTOFF, 0)!!.isDisabled)
        assertEquals(
            emptyList<MidiTargetKey>(),
            reloadedRepo.resolveTargetsForIncomingCc(ccNumber = 74, channel = 0, parts = state.parts)
        )
    }

    @Test
    fun `strict MIDI Learn filter discards SysEx 0xF0_0xF7 ActiveSensing 0xFE Clock 0xF8 and captures only 0xB0_0xBF`() {
        // Packet containing Yamaha display SysEx (0xF0..0xF7), Clock (0xF8), Active Sensing (0xFE),
        // followed by physical Knob 1 Control Change (0xB0, CC 74, value 127)
        val mixedStream = byteArrayOf(
            0xF8.toByte(),                                                       // Clock
            0xFE.toByte(),                                                       // Active Sensing
            0xF0.toByte(), 0x43, 0x10, 0x7F, 0x17, 0x37, 0x00, 0x0E, 0x25, 0xF7.toByte(), // SysEx
            0xFE.toByte(),                                                       // Active Sensing
            0xB0.toByte(), 74, 127                                               // Real Control Change on Ch 1
        )

        val extracted = MidiMappingRepository.extractStrictControlChange(mixedStream)
        assertNotNull(extracted)
        assertEquals(0xB0, extracted!!.statusByte)
        assertEquals(0, extracted.channel)
        assertEquals(74, extracted.ccNumber)
        assertEquals(127, extracted.value)

        // Stream with only SysEx, Clock, Active Sensing, and Note On (no 0xB0..0xBF) must return null
        val nonCcStream = byteArrayOf(
            0xF8.toByte(),
            0xFE.toByte(),
            0xF0.toByte(), 0x43, 0x10, 0x7F, 0x17, 0x00, 0xF7.toByte(),
            0x90.toByte(), 60, 100
        )
        assertNull(MidiMappingRepository.extractStrictControlChange(nonCcStream))
    }

    @Test
    fun `multi-part MIDI Learn persistence keeps Parts 5 6 and 7 coexisting without overwriting`() {
        val prefs = FakeSharedPreferences()
        val repository = MidiMappingRepository(prefs)
        var state = PerformanceState()

        // Map Part 5 (index 4), Part 6 (index 5), and Part 7 (index 6) sequentially
        repository.assignMapping(MidiLearnableParameter.VOLUME, partIndex = 4, ccNumber = 21, channel = 0)
        repository.assignMapping(MidiLearnableParameter.VOLUME, partIndex = 5, ccNumber = 22, channel = 0)
        repository.assignMapping(MidiLearnableParameter.VOLUME, partIndex = 6, ccNumber = 23, channel = 0)

        // Verify string keys "part_4_volume", "part_5_volume", "part_6_volume" coexist
        assertNotNull(repository.getMappingByKey("part_4_volume"))
        assertNotNull(repository.getMappingByKey("part_5_volume"))
        assertNotNull(repository.getMappingByKey("part_6_volume"))
        assertEquals(21, repository.getMappingByKey("part_4_volume")!!.ccNumber)
        assertEquals(22, repository.getMappingByKey("part_5_volume")!!.ccNumber)
        assertEquals(23, repository.getMappingByKey("part_6_volume")!!.ccNumber)

        // Verify reload from SharedPreferences preserves all 3 parts incrementally
        val reloaded = MidiMappingRepository(prefs)
        assertEquals(21, reloaded.getMapping(MidiLearnableParameter.VOLUME, 4)?.ccNumber)
        assertEquals(22, reloaded.getMapping(MidiLearnableParameter.VOLUME, 5)?.ccNumber)
        assertEquals(23, reloaded.getMapping(MidiLearnableParameter.VOLUME, 6)?.ccNumber)

        // Route fader values to Parts 5, 6, 7 and verify independent absolute control
        state = reloaded.routeControlChangeToState(state, channel = 0, ccNumber = 21, value = 45)
        state = reloaded.routeControlChangeToState(state, channel = 0, ccNumber = 22, value = 80)
        state = reloaded.routeControlChangeToState(state, channel = 0, ccNumber = 23, value = 127)
        assertEquals(45, state.parts[4].volume)
        assertEquals(80, state.parts[5].volume)
        assertEquals(127, state.parts[6].volume)
    }

    @Test
    fun `zero volume unlock and Part 1 direct absolute fader resolution from 0 to 127`() {
        val repository = MidiMappingRepository()
        var state = PerformanceState()

        // Map Fader (CC 19 on Ch 1) to Part 1 (partIndex = 0) Volume
        repository.assignMapping(MidiLearnableParameter.VOLUME, partIndex = 0, ccNumber = 19, channel = 0)

        // Zero the volume via fader (0)
        state = repository.routeControlChangeToState(state, channel = 0, ccNumber = 19, value = 0)
        assertEquals(0, state.parts[0].volume)

        // Even if muted, raising fader > 0 immediately unlocks mute and applies full 0..127 absolute value
        state = state.copy(
            parts = state.parts.mapIndexed { idx, p -> if (idx == 0) p.copy(isMuted = true) else p }
        )
        state = repository.routeControlChangeToState(state, channel = 0, ccNumber = 19, value = 64)
        assertEquals(64, state.parts[0].volume)
        assertFalse(state.parts[0].isMuted)

        state = repository.routeControlChangeToState(state, channel = 0, ccNumber = 19, value = 127)
        assertEquals(127, state.parts[0].volume)
    }
}
