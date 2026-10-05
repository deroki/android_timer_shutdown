package com.autoshutdown.app.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.autoshutdown.app.service.ScreenMonitorService
import com.autoshutdown.app.util.PreferencesManager
import com.autoshutdown.app.util.ShutdownManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ScreenShutdownViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context get() = getApplication<Application>().applicationContext
    private val preferencesManager = PreferencesManager(context)

    val hours: StateFlow<Int> = preferencesManager.hoursFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1)

    val minutes: StateFlow<Int> = preferencesManager.minutesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val seconds: StateFlow<Int> = preferencesManager.secondsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val isSimulationMode: StateFlow<Boolean> = preferencesManager.isSimulationModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isServiceEnabledPref: StateFlow<Boolean> = preferencesManager.isServiceEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val autoStartOnBoot: StateFlow<Boolean> = preferencesManager.autoStartOnBootFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    // Service Live State
    val isServiceRunning: StateFlow<Boolean> = ScreenMonitorService.isServiceRunning
    val isScreenOff: StateFlow<Boolean> = ScreenMonitorService.isScreenOff
    val remainingTimeMillis: StateFlow<Long?> = ScreenMonitorService.remainingTimeMillis

    private val _rootStatus = MutableStateFlow(ShutdownManager.RootStatus.UNKNOWN)
    val rootStatus: StateFlow<ShutdownManager.RootStatus> = _rootStatus.asStateFlow()

    private val _isAccessibilityEnabled = MutableStateFlow(false)
    val isAccessibilityEnabled: StateFlow<Boolean> = _isAccessibilityEnabled.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    init {
        checkPermissions()
    }

    fun checkPermissions() {
        viewModelScope.launch {
            val hasRoot = ShutdownManager.isRootAvailable()
            _rootStatus.value = if (hasRoot) ShutdownManager.RootStatus.AVAILABLE else ShutdownManager.RootStatus.NOT_AVAILABLE
            _isAccessibilityEnabled.value = ShutdownManager.isAccessibilityEnabled()
        }
    }

    fun checkRootAccess() {
        checkPermissions()
    }

    fun setHours(newHours: Int) {
        viewModelScope.launch {
            preferencesManager.setHours(newHours)
        }
    }

    fun setMinutes(newMinutes: Int) {
        viewModelScope.launch {
            preferencesManager.setMinutes(newMinutes)
        }
    }

    fun setSeconds(newSeconds: Int) {
        viewModelScope.launch {
            preferencesManager.setSeconds(newSeconds)
        }
    }

    fun setSimulationMode(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setSimulationMode(enabled)
            _actionMessage.value = if (enabled) "Simulation Mode Enabled (Hardware shutdown will be bypassed)" else "Simulation Mode Disabled (Hardware shutdown armed)"
        }
    }

    fun setQuickRealTest10Seconds() {
        viewModelScope.launch {
            preferencesManager.setHours(0)
            preferencesManager.setMinutes(0)
            preferencesManager.setSeconds(10)
            preferencesManager.setSimulationMode(false)
            toggleService(true)
            _actionMessage.value = "10s Real Test ACTIVE! Turn your screen OFF now to test."
        }
    }

    fun setQuickTest30Seconds() {
        viewModelScope.launch {
            preferencesManager.setHours(0)
            preferencesManager.setMinutes(0)
            preferencesManager.setSeconds(30)
            preferencesManager.setSimulationMode(true)
            toggleService(true)
            _actionMessage.value = "30s Sim Test ACTIVE! Turn your screen OFF now to test."
        }
    }

    fun setAutoStartOnBoot(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setAutoStartOnBoot(enabled)
        }
    }

    fun toggleService(enable: Boolean) {
        viewModelScope.launch {
            preferencesManager.setServiceEnabled(enable)
            if (enable) {
                startMonitoringService()
            } else {
                stopMonitoringService()
            }
        }
    }

    fun startMonitoringService() {
        val intent = Intent(context, ScreenMonitorService::class.java).apply {
            action = ScreenMonitorService.ACTION_START_MONITORING
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun stopMonitoringService() {
        val intent = Intent(context, ScreenMonitorService::class.java).apply {
            action = ScreenMonitorService.ACTION_STOP_MONITORING
        }
        context.startService(intent)
    }

    fun testShutdownNow() {
        viewModelScope.launch {
            val result = ShutdownManager.executeShutdown(context)
            _actionMessage.value = result.message
        }
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }
}
