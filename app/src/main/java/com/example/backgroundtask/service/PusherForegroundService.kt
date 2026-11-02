package com.example.backgroundtask.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.backgroundtask.data.model.PushData
import com.example.backgroundtask.data.repository.TripRepository
import com.example.backgroundtask.ui.TripAlertActivity
import com.pusher.client.connection.ConnectionState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class PusherForegroundService : Service() {

    @Inject
    lateinit var repository: TripRepository

    // Dynamic Coroutine Scope management
    private var serviceJob: CompletableJob? = null
    private var serviceScope: CoroutineScope? = null

    private var isRunning = false

    companion object {
        const val ACTION_START = "com.example.pusherdemo.ACTION_START"
        const val ACTION_STOP = "com.example.pusherdemo.ACTION_STOP"
        const val ONGOING_CHANNEL_ID = "pusher_ongoing_channel"
        const val ALERT_CHANNEL_ID = "pusher_alert_channel"
        const val ONGOING_NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, PusherForegroundService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, PusherForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                shutdownService()
                return START_NOT_STICKY
            }
            ACTION_START, null -> {
                startForegroundWork()
            }
        }
        return START_STICKY
    }

    private fun startForegroundWork() {
        // 1. Avoid redundant work if already actively running
        if (isRunning) return
        isRunning = true

        // 2. Always create a fresh SupervisorJob so cancelled jobs don't block new coroutines
        serviceJob?.cancel()
        val job = SupervisorJob()
        serviceJob = job
        val scope = CoroutineScope(Dispatchers.Main + job)
        serviceScope = scope

        // 3. Post ongoing notification immediately
        val notification = buildPersistentNotification("Connecting to dispatch...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                ONGOING_NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING
            )
        } else {
            startForeground(ONGOING_NOTIFICATION_ID, notification)
        }

        // 4. Connect Pusher Socket
        repository.connectSocket()

        // 5. Update notification on connection state changes
        scope.launch {
            repository.connectionState.collect { state ->
                val statusText = when (state) {
                    ConnectionState.CONNECTED -> "🟢 Connected & listening for trips"
                    ConnectionState.CONNECTING -> "🟡 Connecting to dispatch..."
                    ConnectionState.DISCONNECTED -> "🔴 Disconnected"
                    ConnectionState.RECONNECTING -> "🟠 Reconnecting..."
                    else -> "Status: $state"
                }
                val manager = getSystemService(NotificationManager::class.java)
                manager?.notify(ONGOING_NOTIFICATION_ID, buildPersistentNotification(statusText))
            }
        }

        // 6. Observe incoming trip events
        scope.launch {
            repository.inComingTrips.collect { data ->
                showFullScreenTripAlert(data)
            }
        }
    }

    private fun shutdownService() {
        if (!isRunning) return
        isRunning = false

        // 1. Cancel coroutines
        serviceJob?.cancel()
        serviceJob = null
        serviceScope = null

        // 2. Disconnect socket
        repository.disconnectSocket()

        // 3. Remove ongoing notification
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }

        // 4. Terminate service instance
        stopSelf()
    }

    private fun buildPersistentNotification(statusText: String): Notification {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, ONGOING_CHANNEL_ID)
            .setContentTitle("Trip Dispatch Service")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun showFullScreenTripAlert(data: PushData) {
        val tripId = data.a ?: System.currentTimeMillis().toInt()
        val intent = Intent(this, TripAlertActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            putExtra("trip_id", tripId)
            putExtra("pickup_location", data.b ?: "New Trip Location")
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            tripId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alertNotification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("New Trip Available!")
            .setContentText(data.b ?: "Tap to accept or decline")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(pendingIntent, true)
            .setAutoCancel(true)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))
            .build()

        getSystemService(NotificationManager::class.java)?.notify(tripId, alertNotification)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            val ongoingChannel = NotificationChannel(
                ONGOING_CHANNEL_ID,
                "Connection Status",
                NotificationManager.IMPORTANCE_LOW
            )

            val alertChannel = NotificationChannel(
                ALERT_CHANNEL_ID,
                "Urgent Trip Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                enableVibration(true)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }

            manager?.createNotificationChannel(ongoingChannel)
            manager?.createNotificationChannel(alertChannel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        shutdownService()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}