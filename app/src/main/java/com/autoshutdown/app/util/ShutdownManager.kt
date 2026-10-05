package com.autoshutdown.app.util

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ShutdownManager {
    private const val TAG = "ShutdownManager"

    enum class RootStatus {
        AVAILABLE,
        NOT_AVAILABLE,
        UNKNOWN
    }

    suspend fun isRootAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val line = reader.readLine()
            val exitCode = process.waitFor()
            exitCode == 0 && (line != null && line.contains("uid=0"))
        } catch (e: Exception) {
            Log.d(TAG, "Root check failed: ${e.message}")
            false
        }
    }

    suspend fun executeShutdown(context: Context): ShutdownResult = withContext(Dispatchers.IO) {
        Log.i(TAG, "Initiating universal device shutdown sequence...")

        // =========================================================================
        // PRIORITY 1: Root (su) if device is rooted
        // =========================================================================
        val commands = listOf(
            "svc power shutdown",
            "reboot -p",
            "poweroff",
            "setprop sys.powerctl shutdown"
        )

        for (cmd in commands) {
            try {
                Log.d(TAG, "Trying root command: su -c '$cmd'")
                val process = Runtime.getRuntime().exec("su")
                val os = DataOutputStream(process.outputStream)
                os.writeBytes("$cmd\n")
                os.writeBytes("exit\n")
                os.flush()

                val exitCode = process.waitFor()
                if (exitCode == 0) {
                    return@withContext ShutdownResult(
                        success = true,
                        message = "Shutdown command executed successfully via Root: $cmd"
                    )
                }
            } catch (e: Exception) {
                Log.d(TAG, "Root attempt skipped: ${e.message}")
            }
        }

        // =========================================================================
        // PRIORITY 2: Standalone Accessibility Service (Non-Root Universal Automation)
        // =========================================================================
        if (com.autoshutdown.app.service.ShutdownAccessibilityService.isAccessibilityEnabled()) {
            Log.i(TAG, "Triggering shutdown via Accessibility Service...")
            val triggered = com.autoshutdown.app.service.ShutdownAccessibilityService.triggerPowerDialog()
            if (triggered) {
                return@withContext ShutdownResult(
                    success = true,
                    message = "Automated shutdown triggered via Accessibility Service."
                )
            }
        }

        ShutdownResult(
            success = false,
            message = "Shutdown requires Root access or Accessibility Service enabled in Settings."
        )
    }

    fun isAccessibilityEnabled(): Boolean {
        return com.autoshutdown.app.service.ShutdownAccessibilityService.isAccessibilityEnabled()
    }

    data class ShutdownResult(
        val success: Boolean,
        val message: String
    )
}
