package com.guardianshield.parent.ui.dashboard

import android.content.ClipboardManager
import android.content.ClipData
import android.content.Context
import android.graphics.Color
import android.graphics.ColorMatrixColorFilter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.guardianshield.parent.databinding.FragmentDashboardBinding
import com.guardianshield.parent.domain.models.Child
import com.guardianshield.parent.domain.models.ChildLocation
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

@AndroidEntryPoint
class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DashboardViewModel by viewModels()
    
    private var childMarker: Marker? = null
    private var historyPolyline: Polyline? = null
    private val historyMarkers = mutableListOf<Marker>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupMapView()
        observeViewModel()
    }

    private fun setupMapView() {
        binding.mapView.apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(16.0)

            // Dynamic Grayscale Dark Filter for Sleek Map Aesthetics
            val inverseMatrix = floatArrayOf(
                -0.8f, 0f, 0f, 0f, 255f, // Red
                0f, -0.8f, 0f, 0f, 255f, // Green
                0f, 0f, -0.8f, 0f, 255f, // Blue
                0f, 0f, 0f, 1f, 0f       // Alpha
            )
            overlayManager.tilesOverlay.setColorFilter(ColorMatrixColorFilter(inverseMatrix))
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                when (state) {
                    is DashboardUiState.Loading -> {
                        binding.tvChildName.text = "Loading..."
                    }
                    is DashboardUiState.Empty -> {
                        binding.cardChildStatus.visibility = View.GONE
                        binding.cardLinkingPIN.visibility = View.VISIBLE
                        binding.tvLinkingCode.text = state.familyCode
                        binding.btnCopyCode.setOnClickListener {
                            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Family PIN", state.familyCode)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(requireContext(), "Family PIN copied to clipboard!", Toast.LENGTH_SHORT).show()
                        }
                    }
                    is DashboardUiState.Success -> {
                        binding.cardLinkingPIN.visibility = View.GONE
                        binding.cardChildStatus.visibility = View.VISIBLE
                        updateStatusPanel(state.selectedChild)
                        updateChildLocationOnMap(state.selectedChild.lastLocation)
                    }
                    is DashboardUiState.Error -> {
                        Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.childHistory.collectLatest { history ->
                updateMovementHistoryTail(history)
            }
        }
    }

    private fun updateStatusPanel(child: Child) {
        binding.tvChildName.text = child.name
        
        if (child.isOnline && child.lastLocation != null) {
            binding.tvStatusText.text = "ONLINE"
            binding.tvStatusText.setTextColor(Color.parseColor("#10B981"))
            binding.viewStatusDot.setBackgroundColor(Color.parseColor("#10B981"))
            binding.tvBatteryPercent.text = "${child.lastLocation.battery}%"
            binding.tvLastSeen.text = "Just now"
        } else {
            binding.tvStatusText.text = "OFFLINE"
            binding.tvStatusText.setTextColor(Color.parseColor("#94A3B8"))
            binding.viewStatusDot.setBackgroundColor(Color.parseColor("#94A3B8"))
            binding.tvBatteryPercent.text = child.lastLocation?.let { "${it.battery}%" } ?: "0%"
            binding.tvLastSeen.text = child.lastLocation?.let { 
                val elapsedMin = (System.currentTimeMillis() - it.timestamp) / 60_000
                if (elapsedMin <= 0) "Seconds ago" else "$elapsedMin m ago"
            } ?: "Never"
        }
        
        // Mock current app state - Layer 12 App Monitoring pulls live open app data
        binding.tvActiveApp.text = if (child.isOnline) "System Core" else "None"
    }

    private fun updateChildLocationOnMap(loc: ChildLocation?) {
        val map = binding.mapView
        if (loc == null) return

        val point = GeoPoint(loc.lat, loc.lng)
        
        if (childMarker == null) {
            childMarker = Marker(map).apply {
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                title = "Child Current Location"
            }
            map.overlays.add(childMarker)
        }

        childMarker?.position = point
        map.controller.animateTo(point)
        map.invalidate()
    }

    private fun updateMovementHistoryTail(history: List<ChildLocation>) {
        val map = binding.mapView
        
        // 1. Clear previous history lines and dots
        historyPolyline?.let { map.overlays.remove(it) }
        historyMarkers.forEach { map.overlays.remove(it) }
        historyMarkers.clear()

        if (history.size < 2) return

        // 2. Draw faded history line
        historyPolyline = Polyline(map).apply {
            outlinePaint.color = Color.parseColor("#403B82F6") // Faded Blue Glow
            outlinePaint.strokeWidth = 6f
            setPoints(history.map { GeoPoint(it.lat, it.lng) })
        }
        map.overlays.add(historyPolyline)

        // 3. Draw dots representing historical coordinates
        for (i in 0 until history.size - 1) {
            val loc = history[i]
            val marker = Marker(map).apply {
                position = GeoPoint(loc.lat, loc.lng)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                alpha = 0.3f + (i.toFloat() / history.size) * 0.4f // Fade older points
            }
            historyMarkers.add(marker)
            map.overlays.add(marker)
        }

        map.invalidate()
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
    }

    override fun onPause() {
        binding.mapView.onPause()
        super.onPause()
    }

    override fun onDestroyView() {
        childMarker = null
        historyPolyline = null
        historyMarkers.clear()
        _binding = null
        super.onDestroyView()
    }
}
