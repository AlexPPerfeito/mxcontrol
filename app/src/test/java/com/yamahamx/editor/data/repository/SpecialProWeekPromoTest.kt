package com.yamahamx.editor.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying the automatic activation and deactivation
 * of the "Semana Especial Pro" promotion (Degustação de Black Friday).
 */
class SpecialProWeekPromoTest {

    // Helper model emulating the license and promo validation logic
    private class MockLicenseState(var isLicensePurchased: Boolean = false) {
        companion object {
            // 01/11/2026 00:00:00 BRT (03:00:00 UTC)
            const val PROMO_START_TIMESTAMP = PerformanceStorageRepository.PROMO_START_TIMESTAMP
            // 08/11/2026 00:00:00 BRT (03:00:00 UTC)
            const val PROMO_END_TIMESTAMP   = PerformanceStorageRepository.PROMO_END_TIMESTAMP
        }

        fun isPromoActive(now: Long): Boolean {
            return now in PROMO_START_TIMESTAMP until PROMO_END_TIMESTAMP
        }

        fun isProUnlocked(now: Long): Boolean {
            // 1. Licença definitiva comprada pelo usuário (sempre Pro)
            if (isLicensePurchased) return true

            // 2. Janela de degustação de 01 a 07 de novembro
            if (isPromoActive(now)) return true

            return false
        }
    }

    @Test
    fun testPromoConstantsMatchSpecification() {
        assertEquals(1793502000000L, PerformanceStorageRepository.PROMO_START_TIMESTAMP)
        assertEquals(1794106800000L, PerformanceStorageRepository.PROMO_END_TIMESTAMP)

        val durationDays = (PerformanceStorageRepository.PROMO_END_TIMESTAMP - PerformanceStorageRepository.PROMO_START_TIMESTAMP) / (1000 * 60 * 60 * 24)
        assertEquals(7L, durationDays)
    }

    @Test
    fun testPromoActiveBoundaryConditions() {
        val licenseState = MockLicenseState(isLicensePurchased = false)

        val start = PerformanceStorageRepository.PROMO_START_TIMESTAMP
        val end = PerformanceStorageRepository.PROMO_END_TIMESTAMP

        // 1 millisecond before promo start
        assertFalse("Promo should NOT be active 1ms before start", licenseState.isPromoActive(start - 1))

        // Exactly at promo start (01/11/2026 00:00:00 BRT)
        assertTrue("Promo MUST be active at exact start timestamp", licenseState.isPromoActive(start))

        // Mid-week during promo (e.g. 04/11/2026)
        val midPromo = start + (3 * 24 * 60 * 60 * 1000L)
        assertTrue("Promo MUST be active during the week", licenseState.isPromoActive(midPromo))

        // Last millisecond before promo end (07/11/2026 23:59:59.999 BRT)
        assertTrue("Promo MUST be active at 23:59:59.999 of last day", licenseState.isPromoActive(end - 1))

        // Exactly at promo end (08/11/2026 00:00:00 BRT)
        assertFalse("Promo should NOT be active at exact end timestamp", licenseState.isPromoActive(end))

        // After promo has ended
        assertFalse("Promo should NOT be active after end timestamp", licenseState.isPromoActive(end + 1000))
    }

    @Test
    fun testProUnlockedWithoutPermanentLicense() {
        val freeUser = MockLicenseState(isLicensePurchased = false)

        val start = PerformanceStorageRepository.PROMO_START_TIMESTAMP
        val end = PerformanceStorageRepository.PROMO_END_TIMESTAMP

        // Before promo: user is locked to free tier
        assertFalse("Unpaid user before promo is not Pro", freeUser.isProUnlocked(start - 1))

        // During promo: automatically unlocked
        assertTrue("Unpaid user during promo is automatically Pro", freeUser.isProUnlocked(start))
        assertTrue("Unpaid user during mid-promo is automatically Pro", freeUser.isProUnlocked(start + 5000))
        assertTrue("Unpaid user at end-1ms is automatically Pro", freeUser.isProUnlocked(end - 1))

        // After promo: reverts to free tier automatically
        assertFalse("Unpaid user after promo reverts to free tier", freeUser.isProUnlocked(end))
        assertFalse("Unpaid user after promo remains in free tier", freeUser.isProUnlocked(end + 10000))
    }

    @Test
    fun testProUnlockedWithPermanentLicense() {
        val paidUser = MockLicenseState(isLicensePurchased = true)

        val start = PerformanceStorageRepository.PROMO_START_TIMESTAMP
        val end = PerformanceStorageRepository.PROMO_END_TIMESTAMP

        // Permanent license: always unlocked regardless of promo dates
        assertTrue("Paid user is always Pro before promo", paidUser.isProUnlocked(start - 100000))
        assertTrue("Paid user is Pro during promo", paidUser.isProUnlocked(start + 1000))
        assertTrue("Paid user is always Pro after promo ends", paidUser.isProUnlocked(end + 100000))
    }

    @Test
    fun testUserPurchasesLicenseDuringPromo() {
        val user = MockLicenseState(isLicensePurchased = false)
        val midPromo = PerformanceStorageRepository.PROMO_START_TIMESTAMP + 100000
        val afterPromo = PerformanceStorageRepository.PROMO_END_TIMESTAMP + 100000

        // In promo without purchase -> Pro via promo
        assertTrue(user.isProUnlocked(midPromo))

        // User purchases license during promo
        user.isLicensePurchased = true

        // After promo ends -> remains Pro because license is purchased
        assertTrue("User who bought during promo remains Pro permanently", user.isProUnlocked(afterPromo))
    }
}
