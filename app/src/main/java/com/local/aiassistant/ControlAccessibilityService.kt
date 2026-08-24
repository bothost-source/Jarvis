package com.local.aiassistant

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent

/**
 * This service is the "hands" of the assistant. Once the user enables it in
 * Settings > Accessibility, it can:
 *  - Launch any installed app by package name
 *  - Perform a tap at (x, y)
 *  - Perform a swipe/drag between two points (used by gesture control)
 *  - Read the text currently visible on screen (used by automation rules
 *    that need to "see" what's happening, e.g. wait until a button appears)
 *
 * Android requires the user to grant this manually — no app can silently
 * self-enable an Accessibility Service. That's a deliberate OS-level
 * safeguard against exactly the kind of silent remote-control abuse this
 * class of permission could otherwise enable.
 */
class ControlAccessibilityService : AccessibilityService() {

    companion object {
        // Simple static reference so other components (gesture tracker,
        // automation engine) can call into the running service instance.
        var instance: ControlAccessibilityService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Automation "watchers" (e.g. "when notification X appears, do Y")
        // would hook in here. Left as an extension point.
    }

    override fun onInterrupt() {}

    /** Launch any installed app by its package name. */
    fun launchApp(packageName: String): Boolean {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName) ?: return false
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(launchIntent)
        return true
    }

    /** Simulate a single tap at screen coordinates (x, y). */
    fun tap(x: Float, y: Float, durationMs: Long = 60L) {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    /** Simulate a swipe/drag from (x1,y1) to (x2,y2) — used for scroll and drag gestures. */
    fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long = 200L) {
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    /** Global system actions: back, home, recents, notifications, quick settings. */
    fun goHome() = performGlobalAction(GLOBAL_ACTION_HOME)
    fun goBack() = performGlobalAction(GLOBAL_ACTION_BACK)
    fun openRecents() = performGlobalAction(GLOBAL_ACTION_RECENTS)
    fun openNotifications() = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
    fun openQuickSettings() = performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)

    /** Reads all visible text currently on screen — used by automation rules that need to "see" state. */
    fun readVisibleText(): List<String> {
        val root = rootInActiveWindow ?: return emptyList()
        val results = mutableListOf<String>()
        fun walk(node: android.view.accessibility.AccessibilityNodeInfo?) {
            node ?: return
            node.text?.toString()?.takeIf { it.isNotBlank() }?.let { results.add(it) }
            for (i in 0 until node.childCount) walk(node.getChild(i))
        }
        walk(root)
        return results
    }

    /** Attempts to find and tap a node whose visible text matches (used for "tap the button that says X"). */
    fun tapNodeWithText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val matches = root.findAccessibilityNodeInfosByText(text)
        val node = matches.firstOrNull() ?: return false
        val rect = android.graphics.Rect()
        node.getBoundsInScreen(rect)
        tap(rect.exactCenterX(), rect.exactCenterY())
        return true
    }
}
