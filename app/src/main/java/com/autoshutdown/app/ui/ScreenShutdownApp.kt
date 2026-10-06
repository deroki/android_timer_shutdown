package com.autoshutdown.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autoshutdown.app.ui.theme.AccentAmber
import com.autoshutdown.app.ui.theme.AccentCyan
import com.autoshutdown.app.ui.theme.AccentGreen
import com.autoshutdown.app.ui.theme.AccentRed
import com.autoshutdown.app.ui.theme.AccentTeal
import com.autoshutdown.app.ui.theme.DarkBackground
import com.autoshutdown.app.ui.theme.DarkSurface
import com.autoshutdown.app.ui.theme.DarkSurfaceVariant
import com.autoshutdown.app.ui.theme.TextMuted
import com.autoshutdown.app.ui.theme.TextPrimary
import com.autoshutdown.app.ui.theme.TextSecondary
import com.autoshutdown.app.util.ShutdownManager
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun ScreenShutdownApp(
    viewModel: ScreenShutdownViewModel,
    onRequestNotificationPermission: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }

    val hours by viewModel.hours.collectAsState()
    val minutes by viewModel.minutes.collectAsState()
    val seconds by viewModel.seconds.collectAsState()
    val isServiceEnabledPref by viewModel.isServiceEnabledPref.collectAsState()
    val isServiceRunning by viewModel.isServiceRunning.collectAsState()
    val isScreenOff by viewModel.isScreenOff.collectAsState()
    val remainingTimeMillis by viewModel.remainingTimeMillis.collectAsState()
    val autoStartOnBoot by viewModel.autoStartOnBoot.collectAsState()
    val rootStatus by viewModel.rootStatus.collectAsState()
    val isAccessibilityEnabled by viewModel.isAccessibilityEnabled.collectAsState()
    val actionMessage by viewModel.actionMessage.collectAsState()
    val oemAdvice by viewModel.oemAdvice.collectAsState()
    val isOemAdviceDismissed by viewModel.isOemAdviceDismissed.collectAsState()

    var showTestShutdownDialog by remember { mutableStateOf(false) }

    LaunchedEffect(actionMessage) {
        actionMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearActionMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = DarkBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header
            AppHeader(isServiceRunning = isServiceRunning)

            // Privilege Status Banner (Root or Accessibility)
            PrivilegeStatusBanner(
                rootStatus = rootStatus,
                isAccessibilityEnabled = isAccessibilityEnabled,
                onOpenAccessibility = { openAccessibilitySettings(context) },
                onRecheck = { viewModel.checkPermissions() }
            )

            // OEM Security Guidance Banner (Samsung PIN lock prevention)
            if (oemAdvice != null && !isOemAdviceDismissed) {
                OemAdviceCard(
                    advice = oemAdvice!!,
                    onOpenSettings = {
                        com.autoshutdown.app.util.OemSecurityAdvisor.openLockScreenSettings(context)
                    },
                    onDismiss = {
                        viewModel.dismissOemAdvice()
                    }
                )
            }

            // Main Service Activation Switch Card
            MainSwitchCard(
                isServiceRunning = isServiceRunning,
                isServiceEnabledPref = isServiceEnabledPref,
                onToggle = { enable ->
                    onRequestNotificationPermission()
                    viewModel.toggleService(enable)
                }
            )

            // Timer Picker Card (Hours and Minutes)
            TimeConfigurationCard(
                hours = hours,
                minutes = minutes,
                seconds = seconds,
                onHoursChanged = {
                    viewModel.setSeconds(0)
                    viewModel.setHours(it)
                },
                onMinutesChanged = {
                    viewModel.setSeconds(0)
                    viewModel.setMinutes(it)
                },
                onQuick10sRealTest = { viewModel.setQuickRealTest10Seconds() }
            )

            // Live State / Countdown Card
            LiveStatusCard(
                isServiceRunning = isServiceRunning,
                isScreenOff = isScreenOff,
                remainingTimeMillis = remainingTimeMillis,
                configuredHours = hours,
                configuredMinutes = minutes,
                configuredSeconds = seconds
            )

            // Settings & Permissions Section
            SettingsAndPermissionsSection(
                autoStartOnBoot = autoStartOnBoot,
                onAutoStartToggle = { viewModel.setAutoStartOnBoot(it) },
                onIgnoreBatteryOptimization = { openBatteryOptimizationSettings(context) },
                onTestShutdownClick = { showTestShutdownDialog = true },
                hasOemAdvice = oemAdvice != null,
                isOemAdviceDismissed = isOemAdviceDismissed,
                onRestoreOemAdvice = { viewModel.restoreOemAdvice() }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Confirmation Dialog for Test Shutdown
    if (showTestShutdownDialog) {
        AlertDialog(
            onDismissRequest = { showTestShutdownDialog = false },
            icon = { Icon(Icons.Default.PowerSettingsNew, contentDescription = null, tint = AccentRed) },
            title = { Text("Test Immediate Shutdown", color = TextPrimary) },
            text = {
                Text(
                    "Are you sure you want to test shutting down your device right now? This requires root access.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showTestShutdownDialog = false
                        viewModel.testShutdownNow()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRed)
                ) {
                    Text("Shutdown Now", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTestShutdownDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = DarkSurface
        )
    }
}

@Composable
private fun AppHeader(isServiceRunning: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Screen Idle Shutdown",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Auto power-off when device is unused",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }

        // Status Badge
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(if (isServiceRunning) AccentGreen.copy(alpha = 0.2f) else DarkSurfaceVariant)
                .border(
                    1.dp,
                    if (isServiceRunning) AccentGreen else Color.Transparent,
                    RoundedCornerShape(20.dp)
                )
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isServiceRunning) AccentGreen else TextMuted)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isServiceRunning) "ACTIVE" else "STOPPED",
                    color = if (isServiceRunning) AccentGreen else TextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun PrivilegeStatusBanner(
    rootStatus: ShutdownManager.RootStatus,
    isAccessibilityEnabled: Boolean,
    onOpenAccessibility: () -> Unit,
    onRecheck: () -> Unit
) {
    if (rootStatus == ShutdownManager.RootStatus.AVAILABLE) {
        Card(
            colors = CardDefaults.cardColors(containerColor = AccentGreen.copy(alpha = 0.12f)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AccentGreen)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Root Mode Active (su)",
                        color = AccentGreen,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Silent shutdown active! Device will power off directly via root shell commands.",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }
    } else if (isAccessibilityEnabled) {
        Card(
            colors = CardDefaults.cardColors(containerColor = AccentCyan.copy(alpha = 0.15f)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AccentCyan)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Non-Root Mode Ready (100% Standalone)",
                        color = AccentCyan,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Accessibility service is active! When timeout expires, device triggers the system power menu and automates shutdown with no extra apps required.",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }
    } else {
        Card(
            colors = CardDefaults.cardColors(containerColor = AccentAmber.copy(alpha = 0.15f)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = AccentAmber,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Accessibility Service Required",
                        color = AccentAmber,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "To power off automatically without root and without extra apps, enable Screen Idle Shutdown in Accessibility Settings.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onOpenAccessibility,
                            colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("Enable in Settings", fontSize = 12.sp, color = DarkBackground, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = onRecheck,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentAmber),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("Recheck", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MainSwitchCard(
    isServiceRunning: Boolean,
    isServiceEnabledPref: Boolean,
    onToggle: (Boolean) -> Unit
) {
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isServiceRunning) AccentCyan.copy(alpha = 0.15f) else DarkSurface
        ),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (isServiceRunning) AccentCyan.copy(alpha = 0.4f) else DarkSurfaceVariant,
                RoundedCornerShape(20.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (isServiceRunning) AccentCyan else DarkSurfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PowerSettingsNew,
                        contentDescription = null,
                        tint = if (isServiceRunning) DarkBackground else TextSecondary,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = if (isServiceRunning) "Monitoring Running" else "Monitoring Stopped",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isServiceRunning) "Tracking screen on/off events" else "Toggle on to start monitoring",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }

            Switch(
                checked = isServiceEnabledPref || isServiceRunning,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = AccentCyan,
                    uncheckedThumbColor = TextMuted,
                    uncheckedTrackColor = DarkSurfaceVariant
                )
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimeConfigurationCard(
    hours: Int,
    minutes: Int,
    seconds: Int,
    onHoursChanged: (Int) -> Unit,
    onMinutesChanged: (Int) -> Unit,
    onQuick10sRealTest: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.HourglassBottom, contentDescription = null, tint = AccentCyan)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Idle Timeout Duration",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = "Set how long the device screen must stay OFF before triggering a shutdown.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )

            // Hour and Minute Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Hours Counter
                TimeUnitCounter(
                    label = "HOURS",
                    value = hours,
                    minValue = 0,
                    maxValue = 72,
                    onValueChange = onHoursChanged,
                    modifier = Modifier.weight(1f)
                )

                // Minutes Counter
                TimeUnitCounter(
                    label = "MINUTES",
                    value = minutes,
                    minValue = if (hours == 0 && seconds == 0) 1 else 0,
                    maxValue = 59,
                    onValueChange = onMinutesChanged,
                    modifier = Modifier.weight(1f)
                )
            }

            // Quick Preset Chips
            Text(
                text = "Quick Presets:",
                style = MaterialTheme.typography.labelLarge,
                color = TextMuted
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PresetChip(
                    label = "10s (Test)",
                    isSelected = hours == 0 && minutes == 0 && seconds == 10
                ) {
                    onQuick10sRealTest()
                }
                PresetChip(label = "15m", isSelected = hours == 0 && minutes == 15 && seconds == 0) {
                    onHoursChanged(0)
                    onMinutesChanged(15)
                }
                PresetChip(label = "30m", isSelected = hours == 0 && minutes == 30 && seconds == 0) {
                    onHoursChanged(0)
                    onMinutesChanged(30)
                }
                PresetChip(label = "1h", isSelected = hours == 1 && minutes == 0 && seconds == 0) {
                    onHoursChanged(1)
                    onMinutesChanged(0)
                }
                PresetChip(label = "2h", isSelected = hours == 2 && minutes == 0 && seconds == 0) {
                    onHoursChanged(2)
                    onMinutesChanged(0)
                }
                PresetChip(label = "4h", isSelected = hours == 4 && minutes == 0 && seconds == 0) {
                    onHoursChanged(4)
                    onMinutesChanged(0)
                }
                PresetChip(label = "8h", isSelected = hours == 8 && minutes == 0 && seconds == 0) {
                    onHoursChanged(8)
                    onMinutesChanged(0)
                }
            }

            // Target Summary
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkSurfaceVariant.copy(alpha = 0.5f))
                    .padding(14.dp)
            ) {
                Text(
                    text = "Summary: If the screen turns off and is not turned back on within ${formatDurationDescription(hours, minutes, seconds)}, the device will trigger shutdown.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun TimeUnitCounter(
    label: String,
    value: Int,
    minValue: Int,
    maxValue: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkBackground),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = AccentCyan,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = String.format(Locale.getDefault(), "%02d", value),
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                IconButton(
                    onClick = { if (value > minValue) onValueChange(value - 1) },
                    enabled = value > minValue,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(DarkSurfaceVariant)
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Decrease", tint = TextPrimary)
                }

                IconButton(
                    onClick = { if (value < maxValue) onValueChange(value + 1) },
                    enabled = value < maxValue,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(DarkSurfaceVariant)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Increase", tint = TextPrimary)
                }
            }
        }
    }
}

@Composable
private fun PresetChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = { Text(label, fontWeight = FontWeight.SemiBold) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = AccentCyan,
            selectedLabelColor = DarkBackground,
            containerColor = DarkBackground,
            labelColor = TextSecondary
        ),
        shape = RoundedCornerShape(10.dp)
    )
}

@Composable
private fun LiveStatusCard(
    isServiceRunning: Boolean,
    isScreenOff: Boolean,
    remainingTimeMillis: Long?,
    configuredHours: Int,
    configuredMinutes: Int,
    configuredSeconds: Int
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LockClock, contentDescription = null, tint = AccentTeal)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Live Monitor Status",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (!isServiceRunning) {
                Text(
                    text = "Service is not active. Enable the switch above to start screen state monitoring.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Current Screen State:",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = if (isScreenOff) "OFF (Timer Running)" else "ON (Monitoring)",
                        color = if (isScreenOff) AccentAmber else AccentGreen,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                if (isScreenOff && remainingTimeMillis != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(AccentAmber.copy(alpha = 0.15f))
                            .border(1.dp, AccentAmber.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "SHUTTING DOWN IN",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentAmber,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = formatRemainingTime(remainingTimeMillis),
                                fontSize = 28.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Turning the screen ON will reset and cancel this timer.",
                                fontSize = 11.sp,
                                color = TextSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(DarkSurfaceVariant.copy(alpha = 0.3f))
                            .padding(14.dp)
                    ) {
                        Text(
                            text = "When you turn off or lock the screen, the ${formatDurationDescription(configuredHours, configuredMinutes, configuredSeconds)} countdown will immediately begin.",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsAndPermissionsSection(
    autoStartOnBoot: Boolean,
    onAutoStartToggle: (Boolean) -> Unit,
    onIgnoreBatteryOptimization: () -> Unit,
    onTestShutdownClick: () -> Unit,
    hasOemAdvice: Boolean = false,
    isOemAdviceDismissed: Boolean = false,
    onRestoreOemAdvice: () -> Unit = {}
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Security, contentDescription = null, tint = AccentCyan)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "System Settings & Reliability",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }

            // Auto-start on boot
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Auto-Start on Boot",
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Automatically resume monitoring when device powers on",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Switch(
                    checked = autoStartOnBoot,
                    onCheckedChange = onAutoStartToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = AccentCyan,
                        uncheckedThumbColor = TextMuted,
                        uncheckedTrackColor = DarkSurfaceVariant
                    )
                )
            }

            // Battery Optimization Whitelist Button
            FilledTonalButton(
                onClick = onIgnoreBatteryOptimization,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = DarkSurfaceVariant)
            ) {
                Icon(Icons.Default.BatteryChargingFull, contentDescription = null, tint = AccentCyan)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Disable Battery Restrictions", color = TextPrimary)
            }

            // Re-show OEM advice if dismissed
            if (hasOemAdvice && isOemAdviceDismissed) {
                OutlinedButton(
                    onClick = onRestoreOemAdvice,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentAmber),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                        brush = androidx.compose.ui.graphics.SolidColor(AccentAmber.copy(alpha = 0.5f))
                    )
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = AccentAmber)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Show Lock Screen PIN Advisory", color = AccentAmber)
                }
            }

            // Test Shutdown Button
            OutlinedButton(
                onClick = onTestShutdownClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentRed),
                border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(brush = androidx.compose.ui.graphics.SolidColor(AccentRed.copy(alpha = 0.5f)))
            ) {
                Icon(Icons.Default.PowerSettingsNew, contentDescription = null, tint = AccentRed)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Test Immediate Shutdown (Root)", color = AccentRed)
            }
        }
    }
}

@Composable
private fun OemAdviceCard(
    advice: com.autoshutdown.app.util.OemAdvice,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, AccentAmber.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header: Warning Icon + Brand Badge + Title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(AccentAmber.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = AccentAmber,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = advice.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = advice.brand,
                        style = MaterialTheme.typography.labelSmall,
                        color = AccentAmber,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Description
            Text(
                text = advice.description,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )

            // Step List
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkSurfaceVariant.copy(alpha = 0.6f))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                advice.steps.forEachIndexed { index, step ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(AccentAmber.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${index + 1}",
                                color = AccentAmber,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = step,
                            color = TextPrimary,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }

            // Actions: Open Settings & Dismiss
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onOpenSettings,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentAmber),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(advice.settingsActionLabel, color = Color.Black, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                ) {
                    Text("Dismiss", fontSize = 12.sp)
                }
            }
        }
    }
}

private fun formatDurationDescription(hours: Int, minutes: Int, seconds: Int = 0): String {
    return when {
        hours > 0 && minutes > 0 -> "$hours hr $minutes min"
        hours > 0 -> "$hours hour${if (hours > 1) "s" else ""}"
        minutes > 0 -> "$minutes minute${if (minutes > 1) "s" else ""}"
        else -> "$seconds second${if (seconds != 1) "s" else ""}"
    }
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

private fun openBatteryOptimizationSettings(context: Context) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            context.startActivity(intent)
        }
    } catch (e: Exception) {
        try {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            context.startActivity(intent)
        } catch (ex: Exception) {
            Toast.makeText(context, "Could not open battery settings", Toast.LENGTH_SHORT).show()
        }
    }
}

private fun openAccessibilitySettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Could not open accessibility settings", Toast.LENGTH_SHORT).show()
    }
}
