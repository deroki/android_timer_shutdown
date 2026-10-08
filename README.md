# Screen Idle Shutdown (Android App)

[![Website](https://img.shields.io/badge/Website-GitHub%20Pages-38bdf8?logo=github)](https://deroki.github.io/android_timer_shutdown/)
[![Download APK](https://img.shields.io/badge/Download-APK%20(v1.0.0)-10b981?logo=android)](https://deroki.github.io/android_timer_shutdown/)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![Platform](https://img.shields.io/badge/Platform-Android%208.0+-green.svg)](https://developer.android.com)

> 🌐 **App Website & Direct APK Download**: [https://deroki.github.io/android_timer_shutdown/](https://deroki.github.io/android_timer_shutdown/)

An Android application that continuously monitors the device's screen state. When the display turns off, it automatically starts a countdown timer. If the screen remains off for the duration configured in the UI (hours, minutes, seconds), it automatically powers down / shuts off the device to prevent battery drain when left unused.

---

## 📱 Features

1. **Continuous Screen Monitoring**:
   - Runs as a persistent **Foreground Service** with low resource consumption.
   - Listens to `Intent.ACTION_SCREEN_OFF`, `Intent.ACTION_SCREEN_ON`, and `Intent.ACTION_USER_PRESENT`.
2. **Customizable Timeout (Hours, Minutes, Seconds)**:
   - Modern Material 3 UI with stepper counters for Hours (0-72) and Minutes (0-59).
   - Quick preset chips (10s test, 15m, 30m, 1h, 2h, 4h, 8h).
3. **Automatic Countdown & Reset**:
   - When the screen goes **OFF**, the timer starts immediately, and an exact alarm is scheduled via `AlarmManager` (with `setExactAndAllowWhileIdle` for deep Doze mode reliability).
   - When the screen turns back **ON** (or is unlocked), the timer and alarm are **automatically canceled and reset**.
4. **100% Standalone Automated Shutdown**:
   - **Rooted Devices**: Direct hardware poweroff via superuser shell command.
   - **Non-Rooted Devices**: Completely standalone automation via Android **Accessibility Service**. When timeout expires, triggers the global system power dialog and automatically completes the power-off sequence without requiring Shizuku, companion apps, or computers.
   - **OEM Security Advisor**: Built-in detection for Samsung One UI / Xiaomi lock screen security restrictions with direct settings navigation.
5. **System Reliability & Settings**:
   - Auto-start on boot (`RECEIVE_BOOT_COMPLETED`).
   - One-tap button to disable battery optimization restrictions in Android settings.
   - Root detection and Accessibility status indicators with direct shortcuts.

---

## 🛠️ Project Architecture

- **UI Layer**: Jetpack Compose + Material Design 3 (`MainActivity.kt`, `ScreenShutdownApp.kt`, `ScreenShutdownViewModel.kt`)
- **Background Service**: [`ScreenMonitorService.kt`](file:///c:/Users/linxs/Desktop/git/android_app_for_shutdown/app/src/main/java/com/autoshutdown/app/service/ScreenMonitorService.kt)
- **Receivers**:
  - `ShutdownAlarmReceiver.kt`: Exact alarm receiver that wakes up the device from Doze mode to trigger power-off.
  - `BootReceiver.kt`: Restarts the monitoring service when the device boots.
- **Preferences**: DataStore (`PreferencesManager.kt`)
- **Shutdown Engine**: [`ShutdownManager.kt`](file:///c:/Users/linxs/Desktop/git/android_app_for_shutdown/app/src/main/java/com/autoshutdown/app/util/ShutdownManager.kt)

---

## 🚀 Building & Installing the APK

### Build via Gradle
```bash
./gradlew assembleDebug
```
The APK will be generated at:
```
app/build/outputs/apk/debug/app-debug.apk
```

### Install via ADB
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

---

## 🧪 Testing

### Quick 10s Test
1. In the app, select the **`10s (Test)`** chip.
2. Turn ON the **Monitoring** switch.
3. Turn OFF your device screen (press the Power button).
4. Leave the screen OFF for 10 seconds. The app will trigger the automated shutdown sequence.

---

## 📄 License

This project is free software: you can redistribute it and/or modify it under the terms of the **GNU General Public License as published by the Free Software Foundation, version 3 (GPL-3.0)**.

See the [LICENSE](LICENSE) file for the full license text.

Copyright (C) 2026 **deroki**

