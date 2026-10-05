package com.autoshutdown.app.service

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.autoshutdown.app.MainActivity
import com.autoshutdown.app.R
import com.autoshutdown.app.receiver.ShutdownAlarmReceiver
import com.autoshutdown.app.util.PreferencesManager
import com.autoshutdown.app.util.ShutdownManager
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ScreenMonitorService : Service() {

    companion object {
        const val CHANNEL_ID = "screen_monitor_service_channel"
        const val SIMULATION_CHANNEL_ID = "screen_simulation_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START_MONITORING = "com.autoshutdown.app.ACTION_START"
        const val ACTION_STOP_MONITORING = "com.autoshutdown.app.ACTION_STOP"
        const val ACTION_TRIGGER_SHUTDOWN = "com.autoshutdown.app.ACTION_TRIGGER_SHUTDOWN"
        const val ACTION_SHUTDOWN_EXECUTED = "com.autoshutdown.app.ACTION_SHUTDOWN_EXECUTED"

        private const val TAG = "ScreenMonitorService"

        // Global State for UI
        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning = _isServiceRunning.asStateFlow()

        private val _isScreenOff = MutableStateFlow(false)
        val isScreenOff = _isScreenOff.asStateFlow()

        private val _remainingTimeMillis = MutableStateFlow<Long?>(null)
        val remainingTimeMillis = _remainingTimeMillis.asStateFlow()
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var countdownJob: Job? = null
    private lateinit var preferencesManager: PreferencesManager
    private var isReceiverRegistered = false

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    Log.i(TAG, "Screen went OFF! Initiating shutdown countdown...")
                    onScreenOff()
                }
                Intent.ACTION_SCREEN_ON,
                Intent.ACTION_USER_PRESENT -> {
                    Log.i(TAG, "Screen turned ON / Unlocked! Canceling shutdown countdown.")
                    onScreenOn()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        preferencesManager = PreferencesManager(this)
        createNotificationChannel()
        registerScreenReceiver()
        _isServiceRunning.value = true

        // Check initial screen state
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isInteractive = powerManager?.isInteractive ?: true
        if (!isInteractive) {
            onScreenOff()
        } else {
            onScreenOn()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_MONITORING -> {
                Log.i(TAG, "Stopping service via intent")
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_TRIGGER_SHUTDOWN -> {
                Log.i(TAG, "Direct shutdown trigger requested")
                triggerShutdown()
            }
            ACTION_SHUTDOWN_EXECUTED -> {
                val success = intent.getBooleanExtra("success", false)
                val msg = intent.getStringExtra("message") ?: ""
                Log.i(TAG, "Shutdown result reported: $msg")
                if (msg.contains("SIMULATION")) {
                    showSimulationNotification(msg)
                }
            }
        }

        startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.service_screen_on)))
        return START_STICKY
    }

    private fun registerScreenReceiver() {
        if (!isReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            registerReceiver(screenStateReceiver, filter)
            isReceiverRegistered = true
            Log.d(TAG, "Screen state receiver registered.")
        }
    }

    private fun unregisterScreenReceiver() {
        if (isReceiverRegistered) {
            try {
                unregisterReceiver(screenStateReceiver)
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering receiver: ${e.message}")
            }
            isReceiverRegistered = false
        }
    }

    private fun onScreenOff() {
        _isScreenOff.value = true
        countdownJob?.cancel()

        serviceScope.launch {
            val hours = preferencesManager.hoursFlow.first()
            val minutes = preferencesManager.minutesFlow.first()
            val seconds = preferencesManager.secondsFlow.first()
            val durationMillis = preferencesManager.getTotalDurationMillis(hours, minutes, seconds)
            val targetTime = System.currentTimeMillis() + durationMillis

            preferencesManager.setTargetShutdownMillis(targetTime)
            preferencesManager.setScreenOffTimestamp(System.currentTimeMillis())

            scheduleAlarm(targetTime)
            startCountdown(targetTime)
        }
    }

    private fun onScreenOn() {
        _isScreenOff.value = false
        _remainingTimeMillis.value = null
        countdownJob?.cancel()
        cancelAlarm()

        serviceScope.launch {
            preferencesManager.setTargetShutdownMillis(0L)
            updateNotification(getString(R.string.service_screen_on))
        }
    }

    @SuppressLint("ScheduleExactAlarm")
    private fun scheduleAlarm(triggerAtMillis: Long) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(this, ShutdownAlarmReceiver::class.java).apply {
            action = ShutdownAlarmReceiver.ACTION_ALARM_SHUTDOWN
        }
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
            Log.i(TAG, "Exact alarm scheduled for timestamp: $triggerAtMillis")
        } catch (e: SecurityException) {
            Log.e(TAG, "Cannot schedule exact alarm: ${e.message}. Falling back to standard alarm.")
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }
    }

    private fun cancelAlarm() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(this, ShutdownAlarmReceiver::class.java).apply {
            action = ShutdownAlarmReceiver.ACTION_ALARM_SHUTDOWN
        }
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        Log.d(TAG, "Shutdown alarm canceled.")
    }

    private fun startCountdown(targetTimeMillis: Long) {
        countdownJob = serviceScope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                val remaining = targetTimeMillis - now

                if (remaining <= 0) {
                    _remainingTimeMillis.value = 0L
                    updateNotification("Screen OFF: Timeout reached. Shutting down device...")
                    triggerShutdown()
                    break
                }

                _remainingTimeMillis.value = remaining
                val formatted = formatRemainingTime(remaining)
                val statusText = getString(R.string.service_screen_off_countdown, formatted)
                updateNotification(statusText)

                delay(1000L)
            }
        }
    }

    private fun triggerShutdown() {
        serviceScope.launch(Dispatchers.IO) {
            val isSimulation = preferencesManager.isSimulationModeFlow.first()
            if (isSimulation) {
                Log.i(TAG, "Countdown completed in SIMULATION MODE! Hardware shutdown bypassed.")
                showSimulationNotification("Screen timeout expired! Device shutdown was triggered in Simulation Mode.")
            } else {
                Log.i(TAG, "Countdown completed! Executing device shutdown...")
                val result = ShutdownManager.executeShutdown(applicationContext)
                Log.i(TAG, "Shutdown execution result: ${result.message}")
            }
        }
    }

    private fun showSimulationNotification(detailMessage: String) {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, SIMULATION_CHANNEL_ID)
            .setContentTitle("⚡ Simulation: Shutdown Triggered!")
            .setContentText(detailMessage)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detailMessage))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(Notification.DEFAULT_ALL)
            .build()

        manager.notify(2002, notification)
    }

    private fun formatRemainingTime(millis: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(millis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60

        return if (hours > 0) {
            String.format(Locale.getDefault(), "%dh %02dm %02ds", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%02dm %02ds", minutes, seconds)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val monitorChannel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }

            val simulationChannel = NotificationChannel(
                SIMULATION_CHANNEL_ID,
                "Simulation Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Shows high-priority alert when shutdown is simulated"
                setShowBadge(true)
                enableVibration(true)
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(monitorChannel)
            manager?.createNotificationChannel(simulationChannel)
        }
    }

    private fun buildNotification(contentText: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, ScreenMonitorService::class.java).apply {
            action = ACTION_STOP_MONITORING
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.service_running_title))
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Service", stopPendingIntent)
            .build()
    }

    private fun updateNotification(contentText: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, buildNotification(contentText))
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "ScreenMonitorService destroyed")
        countdownJob?.cancel()
        cancelAlarm()
        unregisterScreenReceiver()
        _isServiceRunning.value = false
        _remainingTimeMillis.value = null
        _isScreenOff.value = false
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
