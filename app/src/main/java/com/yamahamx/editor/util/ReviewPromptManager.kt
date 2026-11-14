package com.yamahamx.editor.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import com.google.android.play.core.review.ReviewManagerFactory
import java.util.concurrent.TimeUnit

object ReviewPromptManager {
    private const val TAG = "ReviewPromptManager"
    private const val PREFS_NAME = "mxcontrol_review_prefs"

    const val KEY_FIRST_SEEN = "first_seen_timestamp"
    const val KEY_NEXT_REVIEW_PROMPT = "next_review_prompt_timestamp"
    const val KEY_SESSION_COUNT = "session_count"

    const val DAYS_REMIND_LATER = 7L
    const val DAYS_RATED = 30L
    const val DAYS_INITIAL_WAIT = 1L
    const val SESSIONS_INITIAL_WAIT = 2

    fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Registers an app launch / session. Sets the initial installation timestamp
     * and sets the initial prompt timestamp if not previously initialized.
     */
    fun registerAppLaunch(context: Context) {
        val prefs = getPrefs(context)
        val now = System.currentTimeMillis()
        val firstSeen = prefs.getLong(KEY_FIRST_SEEN, 0L)
        val sessionCount = prefs.getInt(KEY_SESSION_COUNT, 0) + 1

        val editor = prefs.edit().putInt(KEY_SESSION_COUNT, sessionCount)

        if (firstSeen == 0L) {
            editor.putLong(KEY_FIRST_SEEN, now)
            val initialNextPrompt = now + TimeUnit.DAYS.toMillis(DAYS_INITIAL_WAIT)
            editor.putLong(KEY_NEXT_REVIEW_PROMPT, initialNextPrompt)
        }
        editor.apply()
    }

    /**
     * Checks whether the in-app review prompt should be displayed.
     * Returns true only if System.currentTimeMillis() >= next_review_prompt_timestamp
     * and the initial period (at least 1 day or 2 sessions) has elapsed.
     */
    fun shouldShowReviewPrompt(context: Context): Boolean {
        val prefs = getPrefs(context)
        val now = System.currentTimeMillis()
        val firstSeen = prefs.getLong(KEY_FIRST_SEEN, 0L)
        val nextPrompt = prefs.getLong(KEY_NEXT_REVIEW_PROMPT, 0L)
        val sessionCount = prefs.getInt(KEY_SESSION_COUNT, 0)

        if (firstSeen == 0L || nextPrompt == 0L) {
            registerAppLaunch(context)
            return false
        }

        return shouldShowPrompt(now, firstSeen, nextPrompt, sessionCount)
    }

    /**
     * Pure logic function determining if prompt should be shown.
     */
    fun shouldShowPrompt(
        now: Long,
        firstSeen: Long,
        nextPrompt: Long,
        sessionCount: Int
    ): Boolean {
        if (firstSeen == 0L || nextPrompt == 0L) return false
        val hasWaitedInitialPeriod = (now - firstSeen >= TimeUnit.DAYS.toMillis(DAYS_INITIAL_WAIT)) || (sessionCount >= SESSIONS_INITIAL_WAIT)
        return hasWaitedInitialPeriod && (now >= nextPrompt)
    }

    /**
     * Calculates the target timestamp given a base time and number of days.
     */
    fun calculateNextPromptTimestamp(baseTime: Long, days: Long): Long {
        return baseTime + TimeUnit.DAYS.toMillis(days)
    }

    /**
     * Handles when user clicks "Avaliar agora".
     * Postpones the next prompt by 30 days and triggers the Google Play In-App Review flow,
     * falling back to the Play Store intent if the review flow fails or is unavailable.
     */
    fun launchReviewFlow(activity: Activity) {
        scheduleNextPrompt(activity, DAYS_RATED)

        try {
            val manager = ReviewManagerFactory.create(activity)
            val request = manager.requestReviewFlow()
            request.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val reviewInfo = task.result
                    val flow = manager.launchReviewFlow(activity, reviewInfo)
                    flow.addOnCompleteListener {
                        Log.d(TAG, "In-App Review flow completed.")
                    }
                } else {
                    Log.w(TAG, "requestReviewFlow failed: ${task.exception?.message}. Falling back to Play Store.")
                    openPlayStoreFallback(activity)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error invoking ReviewManagerFactory: ${e.message}", e)
            openPlayStoreFallback(activity)
        }
    }

    /**
     * Handles "Lembrar na próxima semana".
     * Postpones the next prompt by 7 days.
     */
    fun onRemindLater(context: Context) {
        scheduleNextPrompt(context, DAYS_REMIND_LATER)
    }

    /**
     * Handles onDismissRequest.
     * Postpones the next prompt by 7 days.
     */
    fun onDismiss(context: Context) {
        scheduleNextPrompt(context, DAYS_REMIND_LATER)
    }

    /**
     * Updates next_review_prompt_timestamp in SharedPreferences.
     */
    fun scheduleNextPrompt(context: Context, days: Long) {
        val targetTimestamp = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(days)
        getPrefs(context).edit()
            .putLong(KEY_NEXT_REVIEW_PROMPT, targetTimestamp)
            .apply()
        Log.d(TAG, "Scheduled next review prompt for +$days days (timestamp: $targetTimestamp)")
    }

    /**
     * Opens Google Play Store app page directly or in a browser as fallback.
     */
    fun openPlayStoreFallback(context: Context) {
        val packageName = context.packageName
        try {
            val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(marketIntent)
        } catch (e: Exception) {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
        }
    }
}
