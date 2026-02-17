package com.guardianp.receiver

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class GuardianDeviceAdminReceiver : DeviceAdminReceiver() {
    
    private val TAG = "GuardianAdminReceiver"

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.d(TAG, "Guardian Device Admin Enabled")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.d(TAG, "Guardian Device Admin Disabled")
    }
}
