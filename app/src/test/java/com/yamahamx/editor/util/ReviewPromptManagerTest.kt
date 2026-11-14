package com.yamahamx.editor.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class ReviewPromptManagerTest {

    private val baseNow = 1700000000000L

    @Test
    fun testFirstTimeSeen_shouldNotShow() {
        val shouldShow = ReviewPromptManager.shouldShowPrompt(
            now = baseNow,
            firstSeen = 0L,
            nextPrompt = 0L,
            sessionCount = 0
        )
        assertFalse("Prompt should not show if firstSeen is 0", shouldShow)
    }

    @Test
    fun testImmediateFirstSession_shouldNotShow() {
        // App just installed 10 seconds ago, session count = 1, nextPrompt scheduled for +1 day
        val firstSeen = baseNow
        val nextPrompt = baseNow + TimeUnit.DAYS.toMillis(1)
        val shouldShow = ReviewPromptManager.shouldShowPrompt(
            now = baseNow + 10_000L,
            firstSeen = firstSeen,
            nextPrompt = nextPrompt,
            sessionCount = 1
        )
        assertFalse("Prompt should not show immediately on first install", shouldShow)
    }

    @Test
    fun testAfterOneDay_shouldShowIfTimeReached() {
        val firstSeen = baseNow
        val nextPrompt = baseNow + TimeUnit.DAYS.toMillis(1)
        // Exactly 1 day + 1 second later
        val now = baseNow + TimeUnit.DAYS.toMillis(1) + 1000L

        val shouldShow = ReviewPromptManager.shouldShowPrompt(
            now = now,
            firstSeen = firstSeen,
            nextPrompt = nextPrompt,
            sessionCount = 1
        )
        assertTrue("Prompt should show after 1 day when nextPrompt is reached", shouldShow)
    }

    @Test
    fun testTwoSessions_shouldShowWhenNextPromptReached() {
        val firstSeen = baseNow
        val nextPrompt = baseNow // Already reached
        val shouldShow = ReviewPromptManager.shouldShowPrompt(
            now = baseNow + 1000L,
            firstSeen = firstSeen,
            nextPrompt = nextPrompt,
            sessionCount = 2
        )
        assertTrue("Prompt should show on 2nd session if nextPrompt is reached", shouldShow)
    }

    @Test
    fun testRemindLater_postponesSevenDays() {
        val target = ReviewPromptManager.calculateNextPromptTimestamp(baseNow, ReviewPromptManager.DAYS_REMIND_LATER)
        val expected = baseNow + TimeUnit.DAYS.toMillis(7)
        assertEquals(expected, target)

        // 3 days later, should not show
        val nowThreeDaysLater = baseNow + TimeUnit.DAYS.toMillis(3)
        val shouldShow = ReviewPromptManager.shouldShowPrompt(
            now = nowThreeDaysLater,
            firstSeen = baseNow - TimeUnit.DAYS.toMillis(10),
            nextPrompt = target,
            sessionCount = 5
        )
        assertFalse("Prompt should not show before 7 days have passed", shouldShow)

        // 8 days later, should show
        val nowEightDaysLater = baseNow + TimeUnit.DAYS.toMillis(8)
        val shouldShowLater = ReviewPromptManager.shouldShowPrompt(
            now = nowEightDaysLater,
            firstSeen = baseNow - TimeUnit.DAYS.toMillis(10),
            nextPrompt = target,
            sessionCount = 5
        )
        assertTrue("Prompt should show after 7 days have passed", shouldShowLater)
    }

    @Test
    fun testRateNow_postponesThirtyDays() {
        val target = ReviewPromptManager.calculateNextPromptTimestamp(baseNow, ReviewPromptManager.DAYS_RATED)
        val expected = baseNow + TimeUnit.DAYS.toMillis(30)
        assertEquals(expected, target)

        // 20 days later, should not show
        val nowTwentyDaysLater = baseNow + TimeUnit.DAYS.toMillis(20)
        val shouldShow = ReviewPromptManager.shouldShowPrompt(
            now = nowTwentyDaysLater,
            firstSeen = baseNow - TimeUnit.DAYS.toMillis(40),
            nextPrompt = target,
            sessionCount = 10
        )
        assertFalse("Prompt should not show before 30 days have passed", shouldShow)

        // 31 days later, should show
        val nowThirtyOneDaysLater = baseNow + TimeUnit.DAYS.toMillis(31)
        val shouldShowLater = ReviewPromptManager.shouldShowPrompt(
            now = nowThirtyOneDaysLater,
            firstSeen = baseNow - TimeUnit.DAYS.toMillis(40),
            nextPrompt = target,
            sessionCount = 10
        )
        assertTrue("Prompt should show after 30 days have passed", shouldShowLater)
    }
}
