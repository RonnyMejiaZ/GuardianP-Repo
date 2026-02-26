package com.guardianp

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import com.guardianp.receiver.GuardianDeviceAdminReceiver

class MainActivity : Activity() {

    private lateinit var btnEnableService: Button
    private lateinit var btnEnableAdmin: Button
    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var adminComponent: ComponentName

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponent = ComponentName(this, GuardianDeviceAdminReceiver::class.java)

        btnEnableService = findViewById(R.id.btn_enable_service)
        btnEnableAdmin = findViewById(R.id.btn_enable_admin)
        val btnInstallExtension = findViewById<Button>(R.id.btn_install_extension)

        btnEnableService.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            Toast.makeText(this, "Find Guardian Service and Enable it", Toast.LENGTH_LONG).show()
        }

        btnEnableAdmin.setOnClickListener {
            if (!devicePolicyManager.isAdminActive(adminComponent)) {
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Start Guarding against uninstall.")
                startActivity(intent)
            } else {
                Toast.makeText(this, "Already Active!", Toast.LENGTH_SHORT).show()
            }
        }

        btnInstallExtension.setOnClickListener {
            val extensionUrl = "TU_ENLACE_AQUI_AL_ARCHIVO.crx" // <-- ELIMINA ESTO Y PON TU LINK
            if (extensionUrl.startsWith("http")) {
                downloadAndOpenExtension(extensionUrl)
            } else {
                Toast.makeText(this, "Por favor, configura el enlace al .crx", Toast.LENGTH_LONG).show()
            }
        }
        
        // Check for updates on startup
        checkForUpdates()
    }

    private fun downloadAndOpenExtension(url: String) {
        val request = android.app.DownloadManager.Request(android.net.Uri.parse(url))
            .setTitle("Downloading Guardian Extension")
            .setDescription("Installing protector...")
            .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, "guardian_extension.crx")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val dm = getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
        val downloadId = dm.enqueue(request)

        Toast.makeText(this, "Descargando extensión... ábrela con Kiwi cuando termine.", Toast.LENGTH_LONG).show()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun updateStatus() {
        // Check Accessibility Service
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        val isServiceEnabled = enabledServices.any { it.resolveInfo.serviceInfo.packageName == packageName }

        if (isServiceEnabled) {
            btnEnableService.text = "Service Active ✅"
            btnEnableService.isEnabled = false
            btnEnableService.alpha = 0.5f // Visual indication
        } else {
            btnEnableService.text = "Enable Service"
            btnEnableService.isEnabled = true
            btnEnableService.alpha = 1.0f
        }

        // Check Device Admin
        if (devicePolicyManager.isAdminActive(adminComponent)) {
            btnEnableAdmin.text = "Admin Active ✅"
            btnEnableAdmin.isEnabled = false
            btnEnableAdmin.alpha = 0.5f
        } else {
            btnEnableAdmin.text = "Enable Admin"
            btnEnableAdmin.isEnabled = true
            btnEnableAdmin.alpha = 1.0f
        }
    }

    private fun checkForUpdates() {
        // REPLACE THIS URL with your own hosted version.json file
        val updateUrl = "https://raw.githubusercontent.com/RonnyMejiaZ/GuardianP-Repo/refs/heads/main/version.json" 
        
        Thread {
            try {
                val url = java.net.URL(updateUrl)
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "GET"
                connection.connect()

                if (connection.responseCode == 200) {
                    val stream = connection.inputStream
                    val response = stream.bufferedReader().use { it.readText() }
                    val json = org.json.JSONObject(response)
                    
                    val remoteVersionCode = json.getInt("versionCode")
                    val apkUrl = json.getString("apkUrl")
                    
                    val currentVersionCode = packageManager.getPackageInfo(packageName, 0).versionCode
                    
                    if (remoteVersionCode > currentVersionCode) {
                        runOnUiThread {
                            showUpdateDialog(apkUrl)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    private fun showUpdateDialog(apkUrl: String) {
        android.app.AlertDialog.Builder(this)
            .setTitle("New Update Available")
            .setMessage("A new version of GuardianP is ready to install.")
            .setPositiveButton("Update Now") { _, _ ->
                downloadAndInstallApk(apkUrl)
            }
            .setNegativeButton("Later", null)
            .show()
    }

    private fun downloadAndInstallApk(url: String) {
        try {
            val request = android.app.DownloadManager.Request(android.net.Uri.parse(url))
                .setTitle("Downloading Update")
                .setDescription("Updating GuardianP...")
                .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(this, android.os.Environment.DIRECTORY_DOWNLOADS, "guardian_update.apk")
                .setMimeType("application/vnd.android.package-archive")

            val dm = getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
            val downloadId = dm.enqueue(request)

            Toast.makeText(this, "Downloading update...", Toast.LENGTH_SHORT).show()

            // Register receiver for when download is complete
            val onComplete = object : android.content.BroadcastReceiver() {
                override fun onReceive(ctxt: Context, intent: Intent) {
                    val id = intent.getLongExtra(android.app.DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                    if (downloadId == id) {
                        installApk(id)
                        try {
                            unregisterReceiver(this)
                        } catch (e: IllegalArgumentException) {
                            // Receiver not registered
                        }
                    }
                }
            }
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(onComplete, android.content.IntentFilter(android.app.DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(onComplete, android.content.IntentFilter(android.app.DownloadManager.ACTION_DOWNLOAD_COMPLETE))
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error starting download: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    private fun installApk(downloadId: Long) {
        try {
            val file = java.io.File(getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), "guardian_update.apk")
            
            if (file.exists()) {
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    this,
                    "${applicationContext.packageName}.provider",
                    file
                )
                
                val installIntent = Intent(Intent.ACTION_VIEW)
                installIntent.setDataAndType(uri, "application/vnd.android.package-archive")
                installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                installIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(installIntent)
            } else {
                Toast.makeText(this, "Update file not found!", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Install failed: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

}