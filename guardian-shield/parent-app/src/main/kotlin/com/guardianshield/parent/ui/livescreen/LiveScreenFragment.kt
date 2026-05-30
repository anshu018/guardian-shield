package com.guardianshield.parent.ui.livescreen

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.guardianshield.parent.R
import com.guardianshield.parent.databinding.FragmentLiveScreenBinding
import com.guardianshield.parent.domain.model.SignalingMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.webrtc.*

@AndroidEntryPoint
class LiveScreenFragment : Fragment() {

    private val tag = "LiveScreenFragment"

    private var _binding: FragmentLiveScreenBinding? = null
    private val binding get() = _binding!!

    private val viewModel: LiveScreenViewModel by viewModels()

    // WebRTC Core
    private var rootEglBase: EglBase? = null
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var videoTrack: VideoTrack? = null

    // FIX 2 — Infinite Exponential Backoff & Connectivity Variables
    private var retryDelayMs = 3000L
    private val maxDelayMs = 30000L
    private var failedAttempts = 0
    private var reconnectJob: Job? = null
    private var connectivityManager: ConnectivityManager? = null

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            Log.d(tag, "Network available. Scheduling reconnection...")
            activity?.runOnUiThread {
                scheduleReconnect()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLiveScreenBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initializeWebRTC()
        setupUI()
        registerNetworkCallback()
        observeViewModel()

        connectToStream()
    }

    private fun initializeWebRTC() {
        try {
            rootEglBase = EglBase.create()

            // Initialize PeerConnectionFactory if not done
            val initOptions = PeerConnectionFactory.InitializationOptions.builder(requireContext().applicationContext)
                .createInitializationOptions()
            PeerConnectionFactory.initialize(initOptions)

            val decoderFactory = DefaultVideoDecoderFactory(rootEglBase!!.eglBaseContext)
            peerConnectionFactory = PeerConnectionFactory.builder()
                .setOptions(PeerConnectionFactory.Options())
                .setVideoDecoderFactory(decoderFactory)
                .createPeerConnectionFactory()

            binding.srvLiveScreen.apply {
                init(rootEglBase!!.eglBaseContext, null)
                setEnableHardwareScaler(true)
                setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
            }
            Log.d(tag, "WebRTC UI components initialized successfully")
        } catch (e: Exception) {
            Log.e(tag, "Failed to initialize parent WebRTC elements: ${e.message}", e)
        }
    }

    private fun setupUI() {
        binding.fabReconnect.setOnClickListener {
            Log.d(tag, "Manual refresh triggered via FAB.")
            connectToStream()
        }
    }

    private fun registerNetworkCallback() {
        try {
            connectivityManager = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager?.registerNetworkCallback(request, networkCallback)
        } catch (e: Exception) {
            Log.e(tag, "Failed to register network callback: ${e.message}", e)
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.connectionState.collect { state ->
                Log.d(tag, "Signaling connection state: $state")
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.incomingSignals.collect { signal ->
                handleIncomingSignal(signal)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.streamState.collect { state ->
                updateUiForState(state)
            }
        }
    }

    private fun connectToStream() {
        reconnectJob?.cancel()
        viewModel.updateStreamState(LiveScreenState.Connecting)
        viewModel.startSignaling()

        // Send a join message to wake up the child capture stream
        viewLifecycleOwner.lifecycleScope.launch {
            delay(1000)
            viewModel.sendSignal(SignalingMessage(type = "join"))
        }
    }

    // FIX 2 — Replace 3-retry reconnection logic with infinite exponential backoff
    private fun scheduleReconnect() {
        if (!isAdded) return
        reconnectJob?.cancel()
        reconnectJob = viewLifecycleOwner.lifecycleScope.launch {
            Log.d(tag, "Scheduling auto-reconnect in ${retryDelayMs}ms")
            delay(retryDelayMs)
            retryDelayMs = minOf(retryDelayMs * 2, maxDelayMs)
            failedAttempts++
            
            // Show FAB after first failed auto-retry attempt
            if (failedAttempts > 0) {
                binding.fabReconnect.visibility = View.VISIBLE
            }
            connectToStream()
        }
    }

    // Reset delay on successful connection
    private fun onConnectionEstablished() {
        retryDelayMs = 3000L
        failedAttempts = 0
        binding.fabReconnect.visibility = View.GONE
    }

    private fun handleIncomingSignal(msg: SignalingMessage) {
        when (msg.type) {
            "offer" -> {
                Log.d(tag, "Received WebRTC SDP offer from child. Applying and generating answer.")
                setupPeerConnection()
                val remoteSdp = SessionDescription(SessionDescription.Type.OFFER, msg.sdp)
                peerConnection?.setRemoteDescription(object : SimpleSdpObserver() {
                    override fun onSetSuccess() {
                        super.onSetSuccess()
                        createAnswer()
                    }
                }, remoteSdp)
            }
            "candidate" -> {
                Log.d(tag, "Received ICE candidate from child.")
                val candidate = IceCandidate(msg.sdpMid!!, msg.sdpMLineIndex!!, msg.candidate!!)
                peerConnection?.addIceCandidate(candidate)
            }
            "leave" -> {
                Log.d(tag, "Child left. Streaming ended.")
                teardownPeerConnection()
                viewModel.updateStreamState(LiveScreenState.Idle)
            }
        }
    }

    private fun setupPeerConnection() {
        if (peerConnection != null) return

        val factory = peerConnectionFactory ?: return

        // FIX 1 — Symmetrical open-relay TURN config to bypass symmetric CGNAT
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302")
                .createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302")
                .createIceServer(),
            PeerConnection.IceServer.builder("turn:openrelay.metered.ca:80")
                .setUsername("openrelayproject")
                .setPassword("openrelayproject")
                .createIceServer(),
            PeerConnection.IceServer.builder("turn:openrelay.metered.ca:443?transport=tcp")
                .setUsername("openrelayproject")
                .setPassword("openrelayproject")
                .createIceServer(),
        )

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }

        peerConnection = factory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                Log.d(tag, "ICE Connection State changed: $state")
                activity?.runOnUiThread {
                    when (state) {
                        PeerConnection.IceConnectionState.CONNECTED,
                        PeerConnection.IceConnectionState.COMPLETED -> {
                            onConnectionEstablished()
                            viewModel.updateStreamState(LiveScreenState.Streaming())
                        }
                        PeerConnection.IceConnectionState.DISCONNECTED -> {
                            viewModel.updateStreamState(LiveScreenState.Buffering)
                            scheduleReconnect()
                        }
                        PeerConnection.IceConnectionState.FAILED -> {
                            viewModel.updateStreamState(LiveScreenState.Error("Connection Failed"))
                            scheduleReconnect()
                        }
                        else -> {}
                    }
                }
            }
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
            
            override fun onIceCandidate(candidate: IceCandidate) {
                // Send local ICE candidate to child
                val signal = SignalingMessage(
                    type = "candidate",
                    sdpMid = candidate.sdpMid,
                    sdpMLineIndex = candidate.sdpMLineIndex,
                    candidate = candidate.sdp
                )
                viewModel.sendSignal(signal)
            }

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
            
            override fun onAddStream(stream: MediaStream?) {
                Log.d(tag, "onAddStream stream received")
                if (stream != null && stream.videoTracks.isNotEmpty()) {
                    val track = stream.videoTracks[0]
                    videoTrack = track
                    activity?.runOnUiThread {
                        track.addSink(binding.srvLiveScreen)
                    }
                }
            }

            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onDataChannel(dc: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            
            override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {
                Log.d(tag, "onAddTrack track received")
                val track = receiver?.track()
                if (track is VideoTrack) {
                    videoTrack = track
                    activity?.runOnUiThread {
                        track.addSink(binding.srvLiveScreen)
                    }
                }
            }
        })
    }

    private fun createAnswer() {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"))
        }

        peerConnection?.createAnswer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(sdp: SessionDescription) {
                peerConnection?.setLocalDescription(object : SimpleSdpObserver() {
                    override fun onSetSuccess() {
                        Log.d(tag, "Local WebRTC answer set. Sending back to child.")
                        val signal = SignalingMessage(
                            type = "answer",
                            sdp = sdp.description
                        )
                        viewModel.sendSignal(signal)
                    }
                }, sdp)
            }
        }, constraints)
    }

    private fun updateUiForState(state: LiveScreenState) {
        if (_binding == null) return
        when (state) {
            is LiveScreenState.Idle -> {
                binding.viewStatusDot.setBackgroundColor(resources.getColor(android.R.color.darker_gray, null))
                binding.tvStatusText.text = "OFFLINE"
                binding.tvStatusText.setTextColor(resources.getColor(android.R.color.darker_gray, null))
                binding.tvLiveStats.text = "Stream not active. Start viewing screen."
            }
            is LiveScreenState.Connecting -> {
                binding.viewStatusDot.setBackgroundColor(resources.getColor(android.R.color.holo_orange_dark, null))
                binding.tvStatusText.text = "CONNECTING"
                binding.tvStatusText.setTextColor(resources.getColor(android.R.color.holo_orange_dark, null))
                binding.tvLiveStats.text = "Gathering connectivity candidates..."
            }
            is LiveScreenState.Buffering -> {
                binding.viewStatusDot.setBackgroundColor(resources.getColor(android.R.color.holo_blue_light, null))
                binding.tvStatusText.text = "BUFFERING"
                binding.tvStatusText.setTextColor(resources.getColor(android.R.color.holo_blue_light, null))
                binding.tvLiveStats.text = "Handshake complete. Waiting for first frame..."
            }
            is LiveScreenState.Streaming -> {
                binding.viewStatusDot.setBackgroundColor(resources.getColor(android.R.color.holo_green_light, null))
                binding.tvStatusText.text = "STREAMING LIVE"
                binding.tvStatusText.setTextColor(resources.getColor(android.R.color.holo_green_light, null))
                binding.tvLiveStats.text = "Direct P2P Link Established (Adaptive quality active)"
            }
            is LiveScreenState.Error -> {
                binding.viewStatusDot.setBackgroundColor(resources.getColor(android.R.color.holo_red_light, null))
                binding.tvStatusText.text = "DISCONNECTED"
                binding.tvStatusText.setTextColor(resources.getColor(android.R.color.holo_red_light, null))
                binding.tvLiveStats.text = "Error: ${state.message}. Attempting reconnect..."
            }
        }
    }

    private fun teardownPeerConnection() {
        try {
            videoTrack?.removeSink(binding.srvLiveScreen)
            videoTrack = null
            peerConnection?.close()
            peerConnection = null
            Log.d(tag, "WebRTC peer connection closed and assets disposed")
        } catch (e: Exception) {
            Log.e(tag, "Error during WebRTC peer connection teardown: ${e.message}", e)
        }
    }

    override fun onPause() {
        // Send leave signal to child to stop accessibility capture immediately
        viewModel.sendSignal(SignalingMessage(type = "leave"))
        teardownPeerConnection()
        viewModel.stopSignaling()
        super.onPause()
    }

    override fun onDestroyView() {
        connectivityManager?.unregisterNetworkCallback(networkCallback)
        reconnectJob?.cancel()
        
        teardownPeerConnection()
        
        try {
            binding.srvLiveScreen.release()
            rootEglBase?.release()
            rootEglBase = null
            peerConnectionFactory?.dispose()
            peerConnectionFactory = null
        } catch (e: Exception) {
            // Ignore
        }
        
        _binding = null
        super.onDestroyView()
    }

    open class SimpleSdpObserver : SdpObserver {
        override fun onCreateSuccess(sdp: SessionDescription) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(error: String?) {
            Log.e("LiveScreenFragment", "SdpObserver onCreateFailure: $error")
        }
        override fun onSetFailure(error: String?) {
            Log.e("LiveScreenFragment", "SdpObserver onSetFailure: $error")
        }
    }
}
