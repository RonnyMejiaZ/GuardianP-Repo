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

    // Keywords to detect strictly
    private val FORBIDDEN_KEYWORDS = setOf(
        "Extensions",
        "Add-ons",
        "Extensiones", // Spanish support
        "Complementos",
        "InPrivate",   // Edge Private mode
        "Incognito",   // Chrome/Kiwi Private mode
        "Incógnito"    // Spanish Private mode
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
        val rootNode = rootInActiveWindow ?: return
        
        try {
            // Scan the hierarchy for forbidden keywords
            if (findForbiddenKeywordRecursive(rootNode)) {
                Log.w(TAG, "Forbidden content detected! Performing Back Action.")
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
        } finally {
            // Crucial: Recycle the root node to prevent memory leaks
            rootNode.recycle()
        }
    }

    /**
     * Recursively scans the node hierarchy for forbidden text.
     * Returns true if forbidden text is found.
     * Note: This function does NOT recycle the 'node' passed to it. 
     * The caller is responsible for recycling the 'node' if it obtained it directly.
     * Use this pattern to traverse safely.
     */
    private fun findForbiddenKeywordRecursive(node: AccessibilityNodeInfo): Boolean {
        // 1. Check current node text/desc
        val text = node.text?.toString()
        val desc = node.contentDescription?.toString()
        
        if (containsForbiddenKeyword(text) || containsForbiddenKeyword(desc)) {
            return true
        }

        // 2. Check children
        val childCount = node.childCount
        for (i in 0 until childCount) {
            val child = node.getChild(i)
            if (child != null) {
                try {
                    if (findForbiddenKeywordRecursive(child)) {
                        return true
                    }
                } finally {
                    // Recycle child after we are done checking it and its subtree
                    child.recycle()
                }
            }
        }
        
        return false
    }

    private fun containsForbiddenKeyword(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        return FORBIDDEN_KEYWORDS.any { keyword -> 
            text.contains(keyword, ignoreCase = true) 
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Guardian Accessibility Service Interrupted")
    }
}
