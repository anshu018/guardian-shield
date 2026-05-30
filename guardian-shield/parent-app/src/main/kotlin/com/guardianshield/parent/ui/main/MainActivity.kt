package com.guardianshield.parent.ui.main

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.guardianshield.parent.R
import com.guardianshield.parent.databinding.ActivityMainBinding
import com.guardianshield.parent.ui.auth.AuthActivity
import com.guardianshield.parent.ui.auth.AuthViewModel
import com.guardianshield.parent.ui.controls.ControlsFragment
import com.guardianshield.parent.ui.dashboard.DashboardFragment
import com.guardianshield.parent.ui.livescreen.LiveScreenFragment
import com.guardianshield.parent.ui.location.LocationFragment
import com.guardianshield.parent.ui.monitoring.MonitoringFragment
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val authViewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check session BEFORE setContentView — redirect immediately if not logged in
        if (!authViewModel.isLoggedIn()) {
            startActivity(Intent(this, AuthActivity::class.java))
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Start Foreground Service to monitor Child SOS events in real-time
        val serviceIntent = Intent(this, com.guardianshield.parent.services.ParentSOSService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        setupNavigation()
    }

    private fun setupNavigation() {
        // Load initial Dashboard fragment
        if (supportFragmentManager.findFragmentById(R.id.fragmentContainer) == null) {
            loadFragment(DashboardFragment())
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            val fragment: Fragment = when (item.itemId) {
                R.id.menu_dashboard -> DashboardFragment()
                R.id.menu_live -> LiveScreenFragment()
                R.id.menu_location -> LocationFragment()
                R.id.menu_monitoring -> MonitoringFragment()
                R.id.menu_controls -> ControlsFragment()
                else -> return@setOnItemSelectedListener false
            }
            loadFragment(fragment)
            true
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }
}
