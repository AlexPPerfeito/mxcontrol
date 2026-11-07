package com.yamahamx.editor

import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import com.yamahamx.editor.ui.screens.PerformanceScreen
import com.yamahamx.editor.ui.theme.SynthDarkBackground
import com.yamahamx.editor.ui.theme.YamahaMXEditorTheme
import com.yamahamx.editor.ui.viewmodel.PerformanceViewModel

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: PerformanceViewModel
    private lateinit var appUpdateManager: AppUpdateManager

    private val updateResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) {
            Log.w(TAG, "Update flow failed or was cancelled by user. ResultCode: ${result.resultCode}")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Prevent screen from turning off automatically during live stage performances
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val app = application as YamahaMxApp
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return PerformanceViewModel(
                    app.midiConnectionManager,
                    app.storageRepository,
                    app.billingManager,
                    app.userVoiceRepository,
                    app.midiMappingRepository
                ) as T
            }
        }

        viewModel = ViewModelProvider(this, factory)[PerformanceViewModel::class.java]

        appUpdateManager = AppUpdateManagerFactory.create(this)
        checkForAppUpdate()

        handleUsbIntent(intent)

        setContent {
            val currentDensity = androidx.compose.ui.platform.LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(
                    density = currentDensity.density,
                    fontScale = 1f
                )
            ) {
                YamahaMXEditorTheme {
                    val state by viewModel.uiState.collectAsState()
                    val showUpgradeDialog by viewModel.showUpgradeDialog.collectAsState()
                    val showStartupPaywallDialog by viewModel.showStartupPaywallDialog.collectAsState()
                    val isProUser by viewModel.isProUser.collectAsState()
                    val productPrice by viewModel.productPrice.collectAsState()

                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding(),
                        color = SynthDarkBackground
                    ) {
                    PerformanceScreen(
                        state = state,
                        viewModel = viewModel,
                        onSelectPart = { idx -> viewModel.selectPart(idx) },
                        onPart12LinkToggle = { link -> viewModel.setPart12Link(link) },
                        onDeviceSelected = { dev -> viewModel.connectDevice(dev) },
                        onScanRequested = { viewModel.scanDevices() },
                        onPanicClicked = { viewModel.sendPanic() },
                        onResetPerformance = { viewModel.resetAllPartsToDefault() },
                        onVoiceChange = { idx, voice -> viewModel.confirmVoiceSelection(idx, voice) },
                        onVolumeChange = { idx, vol -> viewModel.setPartVolume(idx, vol) },
                        onPanChange = { idx, pan -> viewModel.setPartPan(idx, pan) },
                        onReverbSendChange = { idx, rev -> viewModel.setPartReverbSend(idx, rev) },
                        onChorusSendChange = { idx, cho -> viewModel.setPartChorusSend(idx, cho) },
                        onNoteShiftChange = { idx, shift -> viewModel.setPartNoteShift(idx, shift) },
                        onNoteRangeChange = { idx, low, high -> viewModel.setPartNoteLimits(idx, low, high) },
                        onCutoffChange = { idx, cut -> viewModel.setPartCutoff(idx, cut) },
                        onResonanceChange = { idx, res -> viewModel.setPartResonance(idx, res) },
                        onAttackChange = { idx, att -> viewModel.setPartAttack(idx, att) },
                        onDecayChange = { idx, dec -> viewModel.setPartDecay(idx, dec) },
                        onReleaseChange = { idx, rel -> viewModel.setPartRelease(idx, rel) },
                        onModulationChange = { idx, mod -> viewModel.sendModulation(idx, mod) },
                        onToggleMute = { idx -> viewModel.togglePartMute(idx) },
                        onToggleSolo = { idx -> viewModel.togglePartSolo(idx) }
                    )

                    if (showUpgradeDialog) {
                        com.yamahamx.editor.ui.components.UpgradeToProDialog(
                            price = productPrice,
                            onBuyClicked = { viewModel.purchaseProLicense(this@MainActivity) },
                            onRestoreClicked = { viewModel.restoreProPurchases() },
                            onDismissRequest = { viewModel.closeUpgradeDialog() }
                        )
                    }

                    if (showStartupPaywallDialog && !isProUser) {
                        com.yamahamx.editor.ui.components.ProPaywallDialog(
                            price = productPrice,
                            onUnlockProClicked = { viewModel.purchaseProLicense(this@MainActivity) },
                            onContinueFreeClicked = { viewModel.dismissStartupPaywall() },
                            onRestorePurchasesClicked = { viewModel.restoreProPurchases() }
                        )
                    }
                }
            }
        }
    }
}

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleUsbIntent(intent)
    }

    private fun handleUsbIntent(intent: Intent?) {
        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(UsbManager.EXTRA_DEVICE)
        }
        viewModel.onUsbDeviceAttached(device)
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshProStatus()
        if (::appUpdateManager.isInitialized) {
            try {
                appUpdateManager.appUpdateInfo
                    .addOnSuccessListener { appUpdateInfo ->
                        if (appUpdateInfo.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                            try {
                                appUpdateManager.startUpdateFlowForResult(
                                    appUpdateInfo,
                                    updateResultLauncher,
                                    AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build()
                                )
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to resume immediate update flow", e)
                            }
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.d(TAG, "Resume update check failed: ${e.message}")
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error checking update on onResume", e)
            }
        }
    }

    private fun checkForAppUpdate() {
        try {
            appUpdateManager.appUpdateInfo
                .addOnSuccessListener { appUpdateInfo ->
                    if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                        && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
                    ) {
                        try {
                            appUpdateManager.startUpdateFlowForResult(
                                appUpdateInfo,
                                updateResultLauncher,
                                AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build()
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to start immediate update flow", e)
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Log.d(TAG, "Check for update failed (expected if offline or in debug): ${e.message}")
                }
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error in checkForAppUpdate", e)
        }
    }

    companion object {
        private const val TAG = "InAppUpdate"
    }
}
