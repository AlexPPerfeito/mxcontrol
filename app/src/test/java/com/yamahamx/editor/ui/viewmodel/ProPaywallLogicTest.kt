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
}
