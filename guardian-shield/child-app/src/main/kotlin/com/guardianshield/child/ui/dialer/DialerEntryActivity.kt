package com.guardianshield.child.ui.dialer

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.guardianshield.child.ui.setup.SetupActivity
import com.guardianshield.child.utils.ServiceHealthChecker

class DialerEntryActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scheme = intent?.data?.scheme
        val host = intent?.data?.host

        if (scheme == "tel" && host == "*#1234#") {
            // Self-heal: restart any dead services
            ServiceHealthChecker.ensureServicesRunning(this)

            // Check Accessibility Service - critical for app monitoring and screen capture
            if (!ServiceHealthChecker.isAccessibilityServiceEnabled(this)) {
                // Show a dialog guiding parent to re-enable it
                AlertDialog.Builder(this)
                    .setTitle("Action Required")
                    .setMessage(
                        "The accessibility service was disabled by your phone's " +
                        "battery manager. Please re-enable it:\n\n" +
                        "Settings → Accessibility → System Services → Enable\n\n" +
                        "This is required for app monitoring and screen sharing."
                    )
                    .setPositiveButton("Open Settings") { _, _ ->
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        finish()
                    }
                    .setNegativeButton("Later") { _, _ ->
                        startActivity(Intent(this, SetupActivity::class.java))
                        finish()
                    }
                    .setOnCancelListener {
                        startActivity(Intent(this, SetupActivity::class.java))
                        finish()
                    }
                    .show()
            } else {
                startActivity(Intent(this, SetupActivity::class.java))
                finish()
            }
        } else {
            finish()
        }
    }
}

