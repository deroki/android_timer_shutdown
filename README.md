# Screen Idle Shutdown (Android App)

An Android application that continuously monitors the device's screen state. When the display turns off, it automatically starts a countdown timer. If the screen remains off for the duration configured in the UI (hours and minutes), it automatically powers down / shuts off the device to prevent battery drain when left unused.

---

## 📱 Features

1. **Continuous Screen Monitoring**:
   - Runs as a persistent **Foreground Service** with low resource consumption.
   - Listens to `Intent.ACTION_SCREEN_OFF`, `Intent.ACTION_SCREEN_ON`, and `Intent.ACTION_USER_PRESENT`.
2. **Customizable Timeout (Hours & Minutes)**:
   - Modern Material 3 UI with stepper counters for Hours (0-72) and Minutes (0-59).
   - Quick preset chips (15m, 30m, 1h, 2h, 4h, 8h).
3. **Automatic Countdown & Reset**:
   - When the screen goes **OFF**, the timer starts immediately, and an exact alarm is scheduled via `AlarmManager` (with `setExactAndAllowWhileIdle` for deep Doze mode reliability).
   - When the screen turns back **ON** (or is unlocked), the timer and alarm are **automatically canceled and reset**.
4. **Automated Device Shutdown**:
   - Executes hardware shutdown when the timer reaches 0 via root shell commands (`su -c "svc power shutdown"` / `reboot -p` / `poweroff`).
5. **System Reliability & Settings**:
   - Auto-start on boot (`RECEIVE_BOOT_COMPLETED`).
   - One-tap button to disable battery optimization restrictions in Android settings.
   - Root detection badge with direct feedback.
   - "Test Immediate Shutdown" button to verify root poweroff functionality.

---

## 🔒 Important Note on Android Permissions & Root Access

Android's standard security model prohibits standard 3rd-party user applications from turning off the physical device hardware without elevated privileges. 

To execute the final **power-off/shutdown command**, this app uses standard root commands (`su`). 
- **Rooted Devices (Magisk / KernelSU / APatch / LineageOS SU)**: The app works out of the box. The first time the timer expires or when you click "Test Immediate Shutdown", grant superuser access in your root manager.
- **Non-Rooted Devices**: The background screen monitor and countdown notification timer function completely, but when the timer expires, standard Android security restricts the hardware power-down unless elevated via Root or System App Privileges.

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

## 🧪 How to Simulate & Test

### Option 1: In-App Simulation Mode (No Root & No Phone Shutdown Required)
1. In the app UI, turn on **"Simulation / Test Mode"**.
2. Tap the preset **`⚡ 30s (Test)`** chip or **`⚡ Set Quick 30s Simulation Test`** button.
3. Turn on the main **Monitoring** switch.
4. Turn off your screen (or press Power button).
5. Watch the timer:
   - When the screen is OFF: The countdown runs in the background.
   - If you turn the screen back ON before 30s: The timer cancels and resets.
   - If you leave the screen OFF for 30s: A high-priority heads-up simulation notification triggers (`⚡ Simulation: Shutdown Triggered!`), verifying the entire pipeline safely.

### Option 2: Testing on Android Emulator
You have an existing emulator (`Medium_Phone_API_35`) installed:
```bash
# 1. Start the emulator
& "$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe" -avd Medium_Phone_API_35

# 2. Install the APK
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r app/build/outputs/apk/debug/app-debug.apk

# 3. Simulate turning the screen OFF via ADB
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" shell input keyevent 26

# 4. View live logs in real time
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" logcat -s ScreenMonitorService ShutdownAlarmReceiver ShutdownManager

# 5. Simulate turning the screen back ON (to test cancellation)
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" shell input keyevent 26
```

