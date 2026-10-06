package com.autoshutdown.app.util

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

data class OemAdvice(
    val brand: String,
    val title: String,
    val description: String,
    val steps: List<String>,
    val settingsActionLabel: String = "Open Lock Screen Settings"
)

object OemSecurityAdvisor {

    fun getAdvice(context: Context): OemAdvice? {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        val isSamsung = manufacturer.contains("samsung") || brand.contains("samsung")
        val isXiaomi = manufacturer.contains("xiaomi") || brand.contains("xiaomi") ||
                manufacturer.contains("redmi") || brand.contains("redmi") ||
                manufacturer.contains("poco") || brand.contains("poco")

        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val isDeviceSecure = keyguardManager?.isDeviceSecure ?: false

        if (isSamsung) {
            // Check if Samsung PIN / Pattern lock is configured
            if (isDeviceSecure) {
                val isLockSecurityEnabled = try {
                    Settings.Secure.getInt(context.contentResolver, "lock_network_and_security", 1) != 0
                } catch (e: Exception) {
                    true
                }

                if (isLockSecurityEnabled) {
                    return OemAdvice(
                        brand = "Samsung (One UI)",
                        title = "Samsung PIN Lock Notice",
                        description = "Samsung One UI blocks automatic shutdown while the tablet or phone is locked to prevent anti-theft tampering. To allow the shutdown timer to power off your device:",
                        steps = listOf(
                            "Open Settings > Lock screen (Pantalla de bloqueo)",
                            "Tap 'Secure lock settings' (Ajustes de bloqueo seguro) and enter your PIN",
                            "Turn OFF 'Lock network and security' (Bloquear red y seguridad)"
                        ),
                        settingsActionLabel = "Open Lock Screen Settings"
                    )
                }
            }
        } else if (isXiaomi) {
            if (isDeviceSecure) {
                return OemAdvice(
                    brand = "Xiaomi (HyperOS / MIUI)",
                    title = "Xiaomi Lock Screen Power Menu",
                    description = "Xiaomi devices may restrict power menu access on the lock screen. To ensure automated shutdown works:",
                    steps = listOf(
                        "Open Settings > Lock screen",
                        "Ensure 'Open Power menu on lock screen' is enabled"
                    ),
                    settingsActionLabel = "Open Lock Screen Settings"
                )
            }
        }

        return null
    }

    fun openLockScreenSettings(context: Context) {
        val candidateIntents = listOf(
            Intent("com.samsung.android.settings.lockscreen.LockScreenSettings"),
            Intent("android.settings.LOCK_SCREEN_SETTINGS"),
            Intent(Settings.ACTION_SECURITY_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )

        for (intent in candidateIntents) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(context.packageManager) != null) {
                try {
                    context.startActivity(intent)
                    return
                } catch (e: Exception) {
                    // Try next intent
                }
            }
        }

        try {
            context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            // Ignored
        }
    }
}
