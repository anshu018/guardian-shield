package com.guardianshield.child.services

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class GuardianAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: GuardianAccessibilityService? = null
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Unused for screen capture, can be extended for package monitoring later.
    }

    override fun onInterrupt() {
        // No-op
    }
}
