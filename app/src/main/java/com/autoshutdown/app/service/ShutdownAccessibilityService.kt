package com.autoshutdown.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ShutdownAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "ShutdownAccessibility"
        private var instance: ShutdownAccessibilityService? = null

        fun isAccessibilityEnabled(): Boolean = instance != null

        fun triggerPowerDialog(): Boolean {
            val service = instance ?: run {
                Log.w(TAG, "triggerPowerDialog called but service instance is null.")
                return false
            }
            Log.i(TAG, "Triggering GLOBAL_ACTION_POWER_DIALOG via Accessibility Service...")
            service.wakeUpScreen()
            service.isAwaitingShutdown = true
            service.isGestureActive = false
            val success = service.performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
            if (success) {
                service.startAutomatedSequence()
            } else {
                Log.e(TAG, "performGlobalAction(GLOBAL_ACTION_POWER_DIALOG) failed.")
            }
            return success
        }
    }

    private var isAwaitingShutdown = false
    private var isGestureActive = false
    private var automationJob: Job? = null

    private fun wakeUpScreen() {
        try {
            val powerManager = getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager
            if (powerManager != null && !powerManager.isInteractive) {
                Log.i(TAG, "Waking up display before triggering power dialog...")
                @Suppress("DEPRECATION")
                val wakeLock = powerManager.newWakeLock(
                    android.os.PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                            android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP or
                            android.os.PowerManager.ON_AFTER_RELEASE,
                    "autoshutdown:wake_screen_for_dialog"
                )
                wakeLock.acquire(4000L)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Wakeup error: ${e.message}")
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "ShutdownAccessibilityService connected.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Active polling in startAutomatedSequence handles UI transitions reliably
    }

    private fun startAutomatedSequence() {
        automationJob?.cancel()
        automationJob = CoroutineScope(Dispatchers.Main).launch {
            Log.i(TAG, "Starting automated shutdown pulse sequence...")

            // Initial wait for power dialog animation to open (500ms)
            delay(500L)
            if (!isAwaitingShutdown) return@launch
            attemptAutomatedShutdown()

            // Follow-up check at +700ms (useful for Samsung 2nd tap or slider completion)
            delay(700L)
            if (!isAwaitingShutdown) return@launch
            attemptAutomatedShutdown()

            // Safety check at +900ms
            delay(900L)
            if (!isAwaitingShutdown) return@launch
            attemptAutomatedShutdown()

            // Conclude sequence after 3 seconds
            delay(1500L)
            isAwaitingShutdown = false
        }
    }

    private fun attemptAutomatedShutdown() {
        if (isGestureActive) {
            Log.d(TAG, "Gesture currently in flight, waiting for completion.")
            return
        }

        val allRoots = getAllRootNodes()
        if (allRoots.isEmpty()) {
            Log.d(TAG, "No root accessibility nodes found yet.")
            return
        }

        // =========================================================================
        // CASE 1: OnePlus / OPPO / Realme (ColorOS / OxygenOS Slider UI)
        // =========================================================================
        val oplusShutdownIcon = findNodeByResourceId(allRoots, listOf(
            "com.android.systemui:id/shutdown_animation_view",
            "com.android.systemui:id/oplus_shutdown_view"
        ))

        if (oplusShutdownIcon != null) {
            Log.i(TAG, "ColorOS/OxygenOS power slider detected.")
            if (handleColorOsSlider(allRoots, oplusShutdownIcon)) {
                return
            }
        }

        // =========================================================================
        // CASE 2: Button / Clickable Node (Pixel, Samsung, Stock Android, Xiaomi)
        // =========================================================================
        val clickablePowerButton = findClickablePowerButton(allRoots)
        if (clickablePowerButton != null) {
            Log.i(TAG, "Clickable power button found. Triggering click...")
            val clicked = tryClickNode(clickablePowerButton)
            if (clicked) {
                Log.i(TAG, "Successfully clicked power off button.")
                return
            }
        }

        // =========================================================================
        // CASE 3: Universal Fallback Gesture (Screen Center to Lower Center)
        // =========================================================================
        Log.i(TAG, "No standard click target found. Attempting universal center-down swipe...")
        dispatchUniversalDrag()
    }

    /**
     * Handles the OnePlus / OPPO slider by locating the knob and dragging it into the shutdown icon.
     */
    private fun handleColorOsSlider(
        roots: List<AccessibilityNodeInfo>,
        shutdownIconNode: AccessibilityNodeInfo
    ): Boolean {
        val iconBounds = Rect()
        shutdownIconNode.getBoundsInScreen(iconBounds)

        // Find knob: node that mentions slider instructions or center button
        var knobBounds = Rect()
        var knobFound = false

        for (root in roots) {
            val candidateKnobs = root.findAccessibilityNodeInfosByText("apagar") +
                    root.findAccessibilityNodeInfosByText("desliza") +
                    root.findAccessibilityNodeInfosByText("slide")

            for (candidate in candidateKnobs) {
                val b = Rect()
                candidate.getBoundsInScreen(b)
                // Knob is located above the bottom shutdown icon
                if (b.centerY() > 0 && b.centerY() < iconBounds.top) {
                    knobBounds = b
                    knobFound = true
                    break
                }
            }
            if (knobFound) break
        }

        val dm = resources.displayMetrics
        val width = dm.widthPixels.toFloat()
        val height = dm.heightPixels.toFloat()

        val startX: Float
        val startY: Float
        if (knobFound && knobBounds.centerY() > 0) {
            startX = knobBounds.centerX().toFloat()
            startY = knobBounds.centerY().toFloat()
        } else {
            // Default center of display if knob node wasn't explicitly found
            startX = width * 0.5f
            startY = height * 0.49f
        }

        val endX: Float = if (iconBounds.centerX() > 0) iconBounds.centerX().toFloat() else width * 0.5f
        val endY: Float = if (iconBounds.centerY() > 0) iconBounds.bottom.toFloat() else height * 0.65f

        Log.i(TAG, "Dispatching ColorOS two-finger slider drag: ($startX, $startY) -> ($endX, $endY)")
        dispatchTwoFingerDrag(startX, startY, endX, endY, 400L)
        return true
    }

    private fun dispatchTwoFingerDrag(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long
    ) {
        isGestureActive = true
        // ColorOS accessibility instructs: "Desliza dos dedos hacia abajo para apagarlo"
        val path1 = Path().apply {
            moveTo(startX - 30f, startY)
            lineTo(endX - 30f, endY)
        }
        val stroke1 = GestureDescription.StrokeDescription(path1, 0L, durationMs)

        val path2 = Path().apply {
            moveTo(startX + 30f, startY)
            lineTo(endX + 30f, endY)
        }
        val stroke2 = GestureDescription.StrokeDescription(path2, 0L, durationMs)

        val gesture = GestureDescription.Builder()
            .addStroke(stroke1)
            .addStroke(stroke2)
            .build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.i(TAG, "Two-finger gesture completed successfully: ($startX, $startY) -> ($endX, $endY)")
                isGestureActive = false
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.w(TAG, "Two-finger gesture cancelled, trying single finger drag...")
                dispatchGestureDrag(startX, startY, endX, endY, durationMs)
            }
        }, null)
    }

    private fun findClickablePowerButton(roots: List<AccessibilityNodeInfo>): AccessibilityNodeInfo? {
        val powerKeywords = listOf(
            "power off",
            "apagar",
            "shut down",
            "éteindre",
            "ausschalten",
            "spegni",
            "desligar",
            "pulse de nuevo para apagar",
            "tap again to power off"
        )

        for (root in roots) {
            for (keyword in powerKeywords) {
                val nodes = root.findAccessibilityNodeInfosByText(keyword)
                if (!nodes.isNullOrEmpty()) {
                    for (node in nodes) {
                        // Check if node itself or any parent is clickable
                        if (isNodeOrParentClickable(node)) {
                            return node
                        }
                    }
                }
            }
        }

        val targetIds = listOf(
            "com.android.systemui:id/power_button",
            "com.android.systemui:id/emergency_power_off",
            "com.android.systemui:id/shutdown",
            "android:id/item"
        )
        return findNodeByResourceId(roots, targetIds)
    }

    private fun isNodeOrParentClickable(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            if (current.isClickable) return true
            current = current.parent
        }
        return false
    }

    private fun findNodeByResourceId(
        roots: List<AccessibilityNodeInfo>,
        resourceIds: List<String>
    ): AccessibilityNodeInfo? {
        for (root in roots) {
            for (resId in resourceIds) {
                val nodes = root.findAccessibilityNodeInfosByViewId(resId)
                if (!nodes.isNullOrEmpty()) {
                    for (node in nodes) {
                        return node
                    }
                }
            }
        }
        return null
    }

    private fun tryClickNode(node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable) {
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        var parent = node.parent
        while (parent != null) {
            if (parent.isClickable) {
                return parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            parent = parent.parent
        }
        return false
    }

    private fun dispatchUniversalDrag() {
        val dm = resources.displayMetrics
        val width = dm.widthPixels.toFloat()
        val height = dm.heightPixels.toFloat()

        val startX = width * 0.5f
        val startY = height * 0.5f
        val endX = width * 0.5f
        val endY = height * 0.65f

        dispatchGestureDrag(startX, startY, endX, endY, 350L)
    }

    private fun dispatchGestureDrag(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long
    ) {
        isGestureActive = true
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }

        val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.i(TAG, "Gesture completed successfully: ($startX, $startY) -> ($endX, $endY)")
                isGestureActive = false
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.w(TAG, "Gesture cancelled by system: ($startX, $startY) -> ($endX, $endY)")
                isGestureActive = false
            }
        }, null)
    }

    private fun getAllRootNodes(): List<AccessibilityNodeInfo> {
        val roots = mutableListOf<AccessibilityNodeInfo>()
        rootInActiveWindow?.let { roots.add(it) }

        try {
            val windowList = windows
            if (!windowList.isNullOrEmpty()) {
                for (window in windowList) {
                    val root = window.root
                    if (root != null && !roots.contains(root)) {
                        roots.add(root)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Exception accessing windows: ${e.message}")
        }
        return roots
    }

    override fun onInterrupt() {
        Log.w(TAG, "ShutdownAccessibilityService interrupted.")
        isGestureActive = false
        isAwaitingShutdown = false
    }

    override fun onDestroy() {
        super.onDestroy()
        automationJob?.cancel()
        instance = null
        isGestureActive = false
        isAwaitingShutdown = false
        Log.i(TAG, "ShutdownAccessibilityService destroyed.")
    }
}
