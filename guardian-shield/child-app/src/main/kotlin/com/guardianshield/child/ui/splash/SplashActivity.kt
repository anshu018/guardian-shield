package com.guardianshield.child.ui.splash

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.guardianshield.child.data.local.LocationDataStore
import com.guardianshield.child.ui.setup.SetupActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SplashActivity : AppCompatActivity() {

    @Inject lateinit var dataStore: LocationDataStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        lifecycleScope.launch {
            if (dataStore.isSetupCompleted()) {
                // If setup is done, this shouldn't normally run because the launcher 
                // icon is hidden. But if it does, close immediately.
                finish()
            } else {
                startActivity(Intent(this@SplashActivity, SetupActivity::class.java))
                finish()
            }
        }
    }
}
