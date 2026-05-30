package com.guardianshield.parent.ui.sos

import android.Manifest
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.guardianshield.parent.databinding.ActivityEmergencySosBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import java.util.Locale

@AndroidEntryPoint
class SOSAlertActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEmergencySosBinding
    private val viewModel: SOSViewModel by viewModels()

    private var mediaPlayer: MediaPlayer? = null
    private lateinit var audioManager: AudioManager
    private var originalAlarmVolume: Int = 0

    private var eventId: String? = null
    private var childId: String? = null
    private var latitude: Double = 0.0
    private var longitude: Double = 0.0
    private var childPhoneNumber: String = ""

    private lateinit var historyAdapter: SosHistoryAdapter

    private val requestCallPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            initiateCall()
        } else {
            initiateDialerFallback()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Enforce lockscreen override flags
        setupLockscreenFlags()

        super.onCreate(savedInstanceState)
        binding = ActivityEmergencySosBinding.inflate(layoutInflater)
        setContentView(binding.root)

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // Block the back button using OnBackPressedCallback as mandated by Correction 4
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Do nothing — SOS cannot be dismissed by back button
                // Parent must tap "I'm on my way" to resolve
            }
        })

        // Extract parameters
        eventId = intent.getStringExtra("EXTRA_SOS_EVENT_ID")
        childId = intent.getStringExtra("EXTRA_CHILD_ID")
        latitude = intent.getDoubleExtra("EXTRA_LATITUDE", 0.0)
        longitude = intent.getDoubleExtra("EXTRA_LONGITUDE", 0.0)

        // Initialize Alarm tone loop at full volume
        playEmergencyAlarm()

        // Setup UI components
        setupMapView()
        setupHistoryRecyclerView()
        setupListeners()
        observeViewModel()

        // Fetch details
        childId?.let { id ->
            viewModel.loadChildDetails(id)
            viewModel.loadSosHistory(id)
        }
    }

    private fun setupLockscreenFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
    }

    private fun playEmergencyAlarm() {
        try {
            // Backup original volume
            originalAlarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)

            // Force maximum volume
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0)

            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@SOSAlertActivity, alarmUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopEmergencyAlarm() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
            mediaPlayer = null
            // Restore original volume
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, originalAlarmVolume, 0)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupMapView() {
        binding.mapView.setUseDataConnection(true)
        binding.mapView.controller.setZoom(16.5)

        val childGeoPoint = GeoPoint(latitude, longitude)
        binding.mapView.controller.setCenter(childGeoPoint)

        binding.tvCoordinates.text = String.format(Locale.US, "Coordinates: %.5f, %.5f", latitude, longitude)

        // Drop red map pin marker
        val childMarker = Marker(binding.mapView).apply {
            position = childGeoPoint
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = "Emergency Location"
            subDescription = "Child triggered SOS here"
        }
        binding.mapView.overlays.add(childMarker)
        binding.mapView.invalidate()
    }

    private fun setupHistoryRecyclerView() {
        historyAdapter = SosHistoryAdapter()
        binding.rvSosHistory.apply {
            adapter = historyAdapter
            layoutManager = LinearLayoutManager(this@SOSAlertActivity)
        }
    }

    private fun setupListeners() {
        binding.btnCallFallback.setOnClickListener {
            checkCallPermissionAndInitiate()
        }

        binding.btnAcknowledge.setOnClickListener {
            eventId?.let { id ->
                viewModel.resolveSos(id)
            } ?: run {
                finish()
            }
        }
    }

    private fun checkCallPermissionAndInitiate() {
        if (childPhoneNumber.isEmpty()) {
            Toast.makeText(this, "Child phone number is not available yet.", Toast.LENGTH_SHORT).show()
            return
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            initiateCall()
        } else {
            requestCallPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
        }
    }

    private fun initiateCall() {
        try {
            val callIntent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$childPhoneNumber"))
            startActivity(callIntent)
        } catch (e: SecurityException) {
            initiateDialerFallback()
        }
    }

    private fun initiateDialerFallback() {
        val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$childPhoneNumber"))
        startActivity(dialIntent)
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                if (state is SosUiState.ChildDetails) {
                    binding.tvChildName.text = state.name
                    childPhoneNumber = state.phone
                }
            }
        }

        lifecycleScope.launch {
            viewModel.sosHistory.collectLatest { history ->
                historyAdapter.submitList(history)
            }
        }

        lifecycleScope.launch {
            viewModel.resolveStatus.collectLatest { result ->
                if (result != null) {
                    if (result.isSuccess) {
                        Toast.makeText(this@SOSAlertActivity, "SOS alert resolved successfully", Toast.LENGTH_SHORT).show()
                        finish()
                    } else {
                        Toast.makeText(this@SOSAlertActivity, "Error: ${result.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // OSMDroid map lifecycle overrides as mandated by Addition 1
    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
    }

    override fun onPause() {
        binding.mapView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        stopEmergencyAlarm()
        super.onDestroy()
    }
}
