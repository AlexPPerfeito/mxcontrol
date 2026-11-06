package com.yamahamx.editor.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import com.yamahamx.editor.MainActivity
import com.yamahamx.editor.R

/**
 * Foreground Service that keeps the MIDI connection alive and prevents the Android OS
 * from terminating the app or putting the CPU to deep sleep during live performances.
 */
class MidiForegroundService : Service {

    constructor() : super()

    companion object {
        private const val TAG = "MidiForegroundService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "yamaha_mx_live_channel"

        const val ACTION_START = "com.yamahamx.editor.action.START_SERVICE"
        const val ACTION_STOP = "com.yamahamx.editor.action.STOP_SERVICE"
        const val ACTION_UPDATE = "com.yamahamx.editor.action.UPDATE_STATE"

        const val EXTRA_PERF_NAME = "extra_perf_name"
        const val EXTRA_FOCUSED_PART = "extra_focused_part"

        fun start(context: Context) {
            val intent = Intent(context, MidiForegroundService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun updateState(context: Context, performanceName: String, focusedPartText: String) {
            val intent = Intent(context, MidiForegroundService::class.java).apply {
                action = ACTION_UPDATE
                putExtra(EXTRA_PERF_NAME, performanceName)
                putExtra(EXTRA_FOCUSED_PART, focusedPartText)
            }
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, MidiForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var currentPerformanceName: String = ""
    private var currentFocusedPart: String = ""

    override fun onCreate() {
        super.onCreate()
        currentPerformanceName = getString(R.string.notification_default_performance)
        currentFocusedPart = getString(R.string.notification_default_part)
        createNotificationChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForegroundService()
                return START_NOT_STICKY
            }
            ACTION_UPDATE -> {
                val perfName = intent.getStringExtra(EXTRA_PERF_NAME)
                val focusedPart = intent.getStringExtra(EXTRA_FOCUSED_PART)
                if (!perfName.isNullOrBlank()) currentPerformanceName = perfName
                if (!focusedPart.isNullOrBlank()) currentFocusedPart = focusedPart

                val notification = buildNotification(currentPerformanceName, currentFocusedPart)
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.notify(NOTIFICATION_ID, notification)
            }
            ACTION_START, null -> {
                val notification = buildNotification(currentPerformanceName, currentFocusedPart)
                startWithForeground(notification)
            }
        }

        return START_STICKY
    }

    private fun startWithForeground(notification: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            Log.i(TAG, "MidiForegroundService running in foreground")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting foreground service", e)
        }
    }

    private fun buildNotification(performanceName: String, focusedPart: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = getString(R.string.notification_title)
        val content = getString(R.string.notification_content_format, performanceName, focusedPart)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(content)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(pendingIntent)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle(title)
                .setContentText(content)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(Notification.PRIORITY_LOW)
                .setContentIntent(pendingIntent)
                .build()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "MXControl::MidiLiveWakeLock"
            ).apply {
                acquire(10 * 60 * 60 * 1000L) // 10 hours max safety timeout
            }
            Log.i(TAG, "Partial WakeLock acquired for live MIDI execution")
        } catch (e: Exception) {
            Log.e(TAG, "Error acquiring WakeLock", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Log.i(TAG, "Partial WakeLock released")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing WakeLock", e)
        }
        wakeLock = null
    }

    private fun stopForegroundService() {
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseWakeLock()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
