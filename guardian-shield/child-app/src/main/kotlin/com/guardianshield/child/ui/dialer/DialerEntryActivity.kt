package com.guardianshield.child.ui.dialer

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.guardianshield.child.ui.setup.SetupActivity

class DialerEntryActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scheme = intent?.data?.scheme
        val host = intent?.data?.host

        if (scheme == "tel" && host == "*#1234#") {
            // Secret code entered, launch setup/settings
            startActivity(Intent(this, SetupActivity::class.java))
        }
        finish()
    }
}
