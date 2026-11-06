package com.yamahamx.editor

import android.app.Application
import com.yamahamx.editor.data.billing.BillingManager
import com.yamahamx.editor.data.midi.MidiConnectionManager
import com.yamahamx.editor.data.repository.MidiMappingRepository
import com.yamahamx.editor.data.repository.PerformanceStorageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class YamahaMxApp : Application() {

    companion object {
        lateinit var instance: YamahaMxApp
            private set
    }

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var midiConnectionManager: MidiConnectionManager
        private set

    lateinit var storageRepository: PerformanceStorageRepository
        private set

    lateinit var userVoiceRepository: com.yamahamx.editor.data.repository.UserVoiceRepository
        private set

    lateinit var billingManager: BillingManager
        private set

    lateinit var midiMappingRepository: MidiMappingRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        storageRepository = PerformanceStorageRepository(this)
        midiMappingRepository = MidiMappingRepository(this)
        userVoiceRepository = com.yamahamx.editor.data.repository.UserVoiceRepository(this, applicationScope)
        midiConnectionManager = MidiConnectionManager(this, applicationScope)
        billingManager = BillingManager(this, storageRepository, applicationScope)
    }
}
