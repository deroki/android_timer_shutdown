package com.autoshutdown.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.autoshutdown.app.service.ScreenMonitorService
import com.autoshutdown.app.util.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED || intent?.action == "android.intent.action.QUICKBOOT_POWERON") {
            Log.i(TAG, "Boot completed broadcast received.")
            val preferencesManager = PreferencesManager(context)

            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val isEnabled = preferencesManager.isServiceEnabledFlow.first()
                    val autoStart = preferencesManager.autoStartOnBootFlow.first()

                    if (isEnabled && autoStart) {
                        Log.i(TAG, "Starting ScreenMonitorService after reboot...")
                        val serviceIntent = Intent(context, ScreenMonitorService::class.java).apply {
                            action = ScreenMonitorService.ACTION_START_MONITORING
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(serviceIntent)
                        } else {
                            context.startService(serviceIntent)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start service on boot: ${e.message}")
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
