package com.guardianp.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.util.Log

class GuardianAccessibilityService : AccessibilityService() {

    private val TAG = "GuardianAccessService"
    
    // Target packages to monitor
    private val TARGET_PACKAGES = setOf(
        "com.microsoft.emmx.canary", // Edge Canary
        "com.microsoft.emmx",        // Edge Stable
        "com.kiwibrowser.browser",   // Kiwi Browser
        "org.mozilla.firefox",       // Firefox
        "com.android.settings"       // Settings (for admin checks)
    )

    // Keywords to detect strictly during clicks (e.g. menu items)
    private val FORBIDDEN_KEYWORDS = setOf(
        "Extensions",
        "Add-ons",
        "Extensiones", // Spanish support
        "Complementos",
        "InPrivate",   // Edge Private mode
        "Incognito",   // Chrome/Kiwi Private mode
        "Incógnito"    // Spanish Private mode
    )

    // Keywords that identify when we ARE actually inside the extensions/incognito page
    // but are NOT typically in the menu itself.
    private val PAGE_MARKERS = setOf(
        "Developer mode", "Modo de desarrollador",
        "Keyboard shortcuts", "Atajos de teclado",
        "Get extensions", "Obtener extensiones",
        "Chrome Web Store", "Personalizar Edge"
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Guardian Accessibility Service Connected")
        
        // Start as Foreground Service for persistence
        startForegroundService()
    }

    private fun startForegroundService() {
        val channelId = "guardian_service_channel"
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channelId,
                "Guardian Protection Service",
                android.app.NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(android.app.NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        val notificationBuilder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            android.app.Notification.Builder(this, channelId)
        } else {
            android.app.Notification.Builder(this)
        }

        val notification = notificationBuilder
            .setContentTitle("GuardianP Active")
            .setContentText("Monitoring for distractions...")
            .setSmallIcon(android.R.drawable.ic_secure)
            .setOngoing(true)
            .build()

        startForeground(1001, notification)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        
        val packageName = event.packageName?.toString() ?: return
        
        // Only process if it's one of our target apps
        if (TARGET_PACKAGES.contains(packageName)) {
            handleBrowserEvent(event)
        }
    }

    private fun handleBrowserEvent(event: AccessibilityEvent) {
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                val source = event.source ?: return
                try {
                    if (findKeywordRecursive(source, FORBIDDEN_KEYWORDS)) {
                        Log.w(TAG, "Forbidden item clicked! Closing app.")
                        closeApp()
                    }
                } finally {
                    source.recycle()
                }
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val rootNode = rootInActiveWindow ?: return
                try {
                    // Check if we are physically on a forbidden page
                    // We use specific page markers to avoid blocking menus that just list the keywords
                    if (findKeywordRecursive(rootNode, PAGE_MARKERS)) {
                        Log.w(TAG, "Forbidden page detected! Closing app.")
                        closeApp()
                    }
                } finally {
                    rootNode.recycle()
                }
            }
        }
    }

    /**
     * Recursively scans the node hierarchy for any keyword in the provided set.
     */
    private fun findKeywordRecursive(node: AccessibilityNodeInfo, keywords: Set<String>): Boolean {
        // 1. Check current node text/desc
        val text = node.text?.toString()
        val desc = node.contentDescription?.toString()
        
        if (containsAnyKeyword(text, keywords) || containsAnyKeyword(desc, keywords)) {
            return true
        }

        // 2. Check children
        val childCount = node.childCount
        for (i in 0 until childCount) {
            val child = node.getChild(i)
            if (child != null) {
                try {
                    if (findKeywordRecursive(child, keywords)) {
                        return true
                    }
                } finally {
                    child.recycle()
                }
            }
        }
        
        return false
    }

    private fun containsAnyKeyword(text: String?, keywords: Set<String>): Boolean {
        if (text.isNullOrBlank()) return false
        return keywords.any { keyword -> 
            text.contains(keyword, ignoreCase = true) 
        }
    }

    private fun closeApp() {
        // Use GLOBAL_ACTION_HOME to "close" the app effectively
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    override fun onInterrupt() {
        Log.d(TAG, "Guardian Accessibility Service Interrupted")
    }
}
