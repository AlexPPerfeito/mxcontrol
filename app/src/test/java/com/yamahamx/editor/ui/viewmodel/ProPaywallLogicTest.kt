package com.yamahamx.editor.ui.viewmodel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test verifying the reactive state behavior of the Startup Paywall Dialog logic.
 */
class ProPaywallLogicTest {

    private class StartupPaywallStateManager(initialIsPro: Boolean) {
        val isProUser = MutableStateFlow(initialIsPro)
        private var hasDismissedStartupPaywallInSession = false
        private val _showStartupPaywallDialog = MutableStateFlow(false)
        val showStartupPaywallDialog = _showStartupPaywallDialog.asStateFlow()

        init {
            observeProState(isProUser.value)
        }

        fun onProStateUpdated(unlocked: Boolean) {
            isProUser.value = unlocked
            observeProState(unlocked)
        }

        private fun observeProState(unlocked: Boolean) {
            if (unlocked) {
                _showStartupPaywallDialog.value = false
            } else if (!hasDismissedStartupPaywallInSession) {
                _showStartupPaywallDialog.value = true
            }
        }

        fun dismissStartupPaywall() {
            hasDismissedStartupPaywallInSession = true
            _showStartupPaywallDialog.value = false
        }
    }

    @Test
    fun testStartupPaywallTriggeredOnlyForFreeUser() {
        val manager = StartupPaywallStateManager(initialIsPro = false)
        assertTrue("Startup paywall should be shown for Free users on first launch", manager.showStartupPaywallDialog.value)
    }

    @Test
    fun testStartupPaywallNotTriggeredForProUser() {
        val manager = StartupPaywallStateManager(initialIsPro = true)
        assertFalse("Startup paywall should NOT be shown for Pro users", manager.showStartupPaywallDialog.value)
    }

    @Test
    fun testStartupPaywallDismissalPreservedForSession() {
        val manager = StartupPaywallStateManager(initialIsPro = false)
        assertTrue(manager.showStartupPaywallDialog.value)

        // User clicks "Continuar na Versão Gratuita"
        manager.dismissStartupPaywall()
        assertFalse("Startup paywall should be dismissed", manager.showStartupPaywallDialog.value)

        // Any state update during the same session should NOT re-trigger the paywall
        manager.onProStateUpdated(false)
        assertFalse("Startup paywall should NOT re-trigger in the same session after dismissal", manager.showStartupPaywallDialog.value)
    }

    @Test
    fun testStartupPaywallClosesImmediatelyOnSuccessfulPurchase() {
        val manager = StartupPaywallStateManager(initialIsPro = false)
        assertTrue("Paywall is initially open for Free user", manager.showStartupPaywallDialog.value)

        // User completes purchase -> Pro unlocked
        manager.onProStateUpdated(true)
        assertFalse("Startup paywall must close immediately when Pro is unlocked", manager.showStartupPaywallDialog.value)

        // Subsequent checks should never show it
        manager.onProStateUpdated(true)
        assertFalse("Startup paywall must never show for Pro user", manager.showStartupPaywallDialog.value)
    }

    // --- Equalizer & Compressor Freemium Rule Tests ---

    private class EqAndCompPaywallManager(initialIsPro: Boolean) {
        val isProUser = MutableStateFlow(initialIsPro)
        private val _showUpgradeDialog = MutableStateFlow(false)
        val showUpgradeDialog = _showUpgradeDialog.asStateFlow()

        var masterEqGains = mutableListOf(64, 64, 64, 64, 64)
        var compressorThreshold = 72
        var compressorOutputGain = 64
        var compressorRatio = 3
        var compressorAttack = 19
        var compressorRelease = 4

        var showMasterEqGraphDialog = false
        var showCompressorGraphDialog = false

        fun openUpgradeDialog() {
            _showUpgradeDialog.value = true
        }

        fun onEqGraphButtonClicked() {
            if (isProUser.value) {
                showMasterEqGraphDialog = true
            } else {
                openUpgradeDialog()
            }
        }

        fun onCompGraphButtonClicked() {
            if (isProUser.value) {
                showCompressorGraphDialog = true
            } else {
                openUpgradeDialog()
            }
        }

        fun setMasterEqBandGain(bandIdx: Int, gain: Int): Boolean {
            if (bandIdx !in 0..4) return false
            if (!isProUser.value && bandIdx >= 2) {
                openUpgradeDialog()
                return false
            }
            masterEqGains[bandIdx] = gain
            return true
        }

        fun setCompressorThreshold(threshold: Int): Boolean {
            compressorThreshold = threshold
            return true
        }

        fun setCompressorOutputGain(gain: Int): Boolean {
            compressorOutputGain = gain
            return true
        }

        fun setCompressorRatio(ratio: Int): Boolean {
            if (!isProUser.value) {
                openUpgradeDialog()
                return false
            }
            compressorRatio = ratio
            return true
        }

        fun setCompressorAttack(attack: Int): Boolean {
            if (!isProUser.value) {
                openUpgradeDialog()
                return false
            }
            compressorAttack = attack
            return true
        }

        fun setCompressorRelease(release: Int): Boolean {
            if (!isProUser.value) {
                openUpgradeDialog()
                return false
            }
            compressorRelease = release
            return true
        }
    }

    @Test
    fun testFreeUserEqBands1And2AllowedBands3To5Blocked() {
        val manager = EqAndCompPaywallManager(initialIsPro = false)

        // Band 0 (Low) and Band 1 (Low-Mid) should be permitted
        val band0Success = manager.setMasterEqBandGain(0, 70)
        assertTrue("Free user can edit Band 0 (Low)", band0Success)
        assertFalse("Upgrade dialog not shown for Band 0", manager.showUpgradeDialog.value)

        val band1Success = manager.setMasterEqBandGain(1, 75)
        assertTrue("Free user can edit Band 1 (Low-Mid)", band1Success)
        assertFalse("Upgrade dialog not shown for Band 1", manager.showUpgradeDialog.value)

        // Band 2 (Mid), 3 (High-Mid), 4 (High) should be blocked and trigger upgrade dialog
        val band2Success = manager.setMasterEqBandGain(2, 80)
        assertFalse("Free user blocked on Band 2 (Mid)", band2Success)
        assertTrue("Upgrade dialog shown on Band 2", manager.showUpgradeDialog.value)

        val band3Success = manager.setMasterEqBandGain(3, 85)
        assertFalse("Free user blocked on Band 3 (High-Mid)", band3Success)

        val band4Success = manager.setMasterEqBandGain(4, 90)
        assertFalse("Free user blocked on Band 4 (High)", band4Success)
    }

    @Test
    fun testProUserCanAccessAllEqBands() {
        val manager = EqAndCompPaywallManager(initialIsPro = true)

        for (bandIdx in 0..4) {
            val success = manager.setMasterEqBandGain(bandIdx, 60 + bandIdx * 5)
            assertTrue("Pro user should have unrestricted access to Band $bandIdx", success)
            assertFalse("Upgrade dialog must not open for Pro user on Band $bandIdx", manager.showUpgradeDialog.value)
        }
    }

    @Test
    fun testFreeUserCompressorThresholdAndOutputGainAllowedRatioAttackReleaseBlocked() {
        val manager = EqAndCompPaywallManager(initialIsPro = false)

        // Threshold & Output Gain allowed
        val thresholdSuccess = manager.setCompressorThreshold(80)
        assertTrue("Free user can adjust Threshold", thresholdSuccess)
        assertFalse("Upgrade dialog not shown for Threshold", manager.showUpgradeDialog.value)

        val outputGainSuccess = manager.setCompressorOutputGain(70)
        assertTrue("Free user can adjust Output Gain", outputGainSuccess)
        assertFalse("Upgrade dialog not shown for Output Gain", manager.showUpgradeDialog.value)

        // Ratio, Attack, Release blocked
        val ratioSuccess = manager.setCompressorRatio(4)
        assertFalse("Free user blocked from adjusting Ratio", ratioSuccess)
        assertTrue("Upgrade dialog opened when interacting with locked Ratio", manager.showUpgradeDialog.value)

        val attackSuccess = manager.setCompressorAttack(15)
        assertFalse("Free user blocked from adjusting Attack", attackSuccess)

        val releaseSuccess = manager.setCompressorRelease(6)
        assertFalse("Free user blocked from adjusting Release", releaseSuccess)
    }

    @Test
    fun testProUserCanAccessAllCompressorParameters() {
        val manager = EqAndCompPaywallManager(initialIsPro = true)

        assertTrue(manager.setCompressorThreshold(80))
        assertTrue(manager.setCompressorOutputGain(70))
        assertTrue(manager.setCompressorRatio(4))
        assertTrue(manager.setCompressorAttack(15))
        assertTrue(manager.setCompressorRelease(6))
        assertFalse("Upgrade dialog must never open for Pro user", manager.showUpgradeDialog.value)
    }

    @Test
    fun testGraphButtonFreemiumAccess() {
        // Free user
        val freeManager = EqAndCompPaywallManager(initialIsPro = false)
        freeManager.onEqGraphButtonClicked()
        assertFalse("Free user cannot open EQ graph dialog directly", freeManager.showMasterEqGraphDialog)
        assertTrue("Free user triggers upgrade dialog on EQ graph click", freeManager.showUpgradeDialog.value)

        freeManager.onCompGraphButtonClicked()
        assertFalse("Free user cannot open Comp graph dialog directly", freeManager.showCompressorGraphDialog)

        // Pro user
        val proManager = EqAndCompPaywallManager(initialIsPro = true)
        proManager.onEqGraphButtonClicked()
        assertTrue("Pro user opens EQ graph dialog", proManager.showMasterEqGraphDialog)
        assertFalse("Upgrade dialog not triggered for Pro user on EQ graph", proManager.showUpgradeDialog.value)

        proManager.onCompGraphButtonClicked()
        assertTrue("Pro user opens Comp graph dialog", proManager.showCompressorGraphDialog)
        assertFalse("Upgrade dialog not triggered for Pro user on Comp graph", proManager.showUpgradeDialog.value)
    }
}
