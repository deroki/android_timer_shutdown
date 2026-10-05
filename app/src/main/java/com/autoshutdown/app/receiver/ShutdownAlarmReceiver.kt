package com.autoshutdown.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import com.autoshutdown.app.service.ScreenMonitorService
import com.autoshutdown.app.util.PreferencesManager
import com.autoshutdown.app.util.ShutdownManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ShutdownAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_ALARM_SHUTDOWN = "com.autoshutdown.app.ACTION_ALARM_SHUTDOWN"
        private const val TAG = "ShutdownAlarmReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        Log.i(TAG, "Alarm triggered! Intent action: ${intent?.action}")

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "ScreenIdleShutdown:AlarmWakeLock"
        )
        wakeLock?.acquire(30_000L) // 30 seconds max wake lock

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val preferencesManager = PreferencesManager(context)
                val isSimulation = preferencesManager.isSimulationModeFlow.first()

                if (isSimulation) {
                    Log.i(TAG, "Simulation mode active: Bypassing real hardware shutdown.")
                    val serviceIntent = Intent(context, ScreenMonitorService::class.java).apply {
                        action = ScreenMonitorService.ACTION_SHUTDOWN_EXECUTED
                        putExtra("success", true)
                        putExtra("message", "SIMULATION: Shutdown timer reached 0! (Device shutdown simulated)")
                    }
                    context.startService(serviceIntent)
                } else {
                    Log.i(TAG, "Executing real device shutdown from alarm receiver...")
                    val result = ShutdownManager.executeShutdown(context)
                    Log.i(TAG, "Shutdown result: ${result.message}")

                    val serviceIntent = Intent(context, ScreenMonitorService::class.java).apply {
                        action = ScreenMonitorService.ACTION_SHUTDOWN_EXECUTED
                        putExtra("success", result.success)
                        putExtra("message", result.message)
                    }
                    context.startService(serviceIntent)
                }
            } finally {
                wakeLock?.release()
                pendingResult.finish()
            }
        }
    }
}
