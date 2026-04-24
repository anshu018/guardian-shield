# WebRTC Android Skill

# Load this skill for any task involving screen streaming, MediaProjection, or WebRTC.

---

## What This Skill Covers

Any agent working on L8 (Railway signaling server) or L9 (ScreenCaptureService)
or L11 (parent live screen view) must read this entire file first.
This covers the complete WebRTC pipeline from screen capture to parent display.

---

## How The Streaming Works (Big Picture)

Child phone Railway.app Parent phone
─────────────────────────────────────────────────────────────────────
MediaProjection socket.io server SurfaceViewRenderer
↓ ↓ ↓
ScreenCaptureService ←→ signaling (offer/answer/ICE) ←→ ParentLiveViewModel
↓ ↓
PeerConnection ──────────── WebRTC P2P (direct) ────────────► PeerConnection
↓ ↓
VideoTrack VideoTrack renders

The video never touches the Railway server.
Railway only handles signaling (small JSON messages).
All actual video goes P2P — zero server bandwidth cost.

---

## Dependencies

```kotlin
// In libs.versions.toml
[versions]
webrtc = "114.5735.07"

[libraries]
webrtc = { module = "io.getstream:stream-webrtc-android", version.ref = "webrtc" }
socket-io = { module = "io.socket:socket.io-client", version = "2.1.0" }

// In build.gradle.kts (child-app and parent-app both need these)
implementation(libs.webrtc)
implementation(libs.socket.io) {
    exclude(group = "org.json", module = "json") // avoid conflict with Android's json
}
```

---

## Signaling Server (Railway.app — Node.js)

```javascript
// server.js — deploy this to Railway.app
// This is the COMPLETE signaling server — no changes needed

const express = require("express");
const { createServer } = require("http");
const { Server } = require("socket.io");

const app = express();
const httpServer = createServer(app);
const io = new Server(httpServer, {
  cors: { origin: "*" },
});

// Store active rooms: roomId → { child: socketId, parent: socketId }
const rooms = new Map();

io.on("connection", (socket) => {
  // Child or parent joins a room identified by family_id
  socket.on("join_room", ({ roomId, role }) => {
    socket.join(roomId);
    if (!rooms.has(roomId)) rooms.set(roomId, {});
    rooms.get(roomId)[role] = socket.id;
    // Tell the other side someone joined
    socket.to(roomId).emit("peer_joined", { role });
  });

  // Forward WebRTC offer from child to parent
  socket.on("offer", ({ roomId, sdp }) => {
    socket.to(roomId).emit("offer", { sdp });
  });

  // Forward WebRTC answer from parent to child
  socket.on("answer", ({ roomId, sdp }) => {
    socket.to(roomId).emit("answer", { sdp });
  });

  // Forward ICE candidates both directions
  socket.on("ice_candidate", ({ roomId, candidate }) => {
    socket.to(roomId).emit("ice_candidate", { candidate });
  });

  socket.on("disconnect", () => {
    rooms.forEach((peers, roomId) => {
      if (Object.values(peers).includes(socket.id)) {
        socket.to(roomId).emit("peer_disconnected");
        rooms.delete(roomId);
      }
    });
  });
});

httpServer.listen(process.env.PORT || 3000);
```

package.json for Railway:

```json
{
  "name": "guardian-shield-signaling",
  "version": "1.0.0",
  "main": "server.js",
  "scripts": { "start": "node server.js" },
  "dependencies": {
    "express": "^4.18.2",
    "socket.io": "^4.6.1"
  }
}
```

---

## PeerConnection Setup (Shared — Both Apps)

```kotlin
// WebRTCManager.kt — used by both child-app and parent-app
// Child creates offer, parent creates answer

class WebRTCManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val eglBase = EglBase.create()

    // STUN servers — use Google's free STUN, works in India
    private val iceServers = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
    )

    private val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
        sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
    }

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null

    fun initialize() {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(false) // disable in production
                .createInitializationOptions()
        )
        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(
                DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
            )
            .setVideoDecoderFactory(
                DefaultVideoDecoderFactory(eglBase.eglBaseContext)
            )
            .createPeerConnectionFactory()
    }

    fun createPeerConnection(observer: PeerConnection.Observer): PeerConnection? {
        peerConnection = peerConnectionFactory?.createPeerConnection(rtcConfig, observer)
        return peerConnection
    }

    fun getEglBase(): EglBase = eglBase

    fun dispose() {
        peerConnection?.dispose()
        peerConnectionFactory?.dispose()
        eglBase.release()
    }
}
```

---

## Child App — Screen Capture + Stream

```kotlin
@AndroidEntryPoint
class ScreenCaptureService : Service() {

    @Inject lateinit var webRTCManager: WebRTCManager
    @Inject lateinit var signalingClient: SignalingClient

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var mediaProjection: MediaProjection? = null
    private var videoCapturer: ScreenCapturerAndroid? = null
    private var localVideoTrack: VideoTrack? = null
    private var peerConnection: PeerConnection? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildStealthNotification())

        // MediaProjection data comes from setup wizard result
        val resultCode = intent?.getIntExtra("result_code", Activity.RESULT_CANCELED)
            ?: return START_STICKY
        val data = intent.getParcelableExtra<Intent>("projection_data")
            ?: return START_STICKY

        initializeWebRTC(resultCode, data)
        connectToSignaling()
        return START_STICKY
    }

    private fun initializeWebRTC(resultCode: Int, data: Intent) {
        webRTCManager.initialize()

        // ScreenCapturerAndroid uses MediaProjection to capture screen frames
        videoCapturer = ScreenCapturerAndroid(data, object : MediaProjection.Callback() {
            override fun onStop() {
                // MediaProjection stopped — restart capture
                scheduleRestart()
            }
        })

        val surfaceTextureHelper = SurfaceTextureHelper.create(
            "CaptureThread",
            webRTCManager.getEglBase().eglBaseContext
        )

        val videoSource = webRTCManager.createVideoSource(isScreencast = true)
        videoCapturer?.initialize(surfaceTextureHelper, applicationContext, videoSource.capturerObserver)

        // Adaptive resolution based on network quality
        val (width, height, fps) = getQualityForNetwork()
        videoCapturer?.startCapture(width, height, fps)

        localVideoTrack = webRTCManager.createVideoTrack("screen_track", videoSource)

        peerConnection = webRTCManager.createPeerConnection(peerConnectionObserver)
        peerConnection?.addTrack(localVideoTrack!!, listOf("stream_id"))
    }

    private fun getQualityForNetwork(): Triple<Int, Int, Int> {
        return when (getCurrentNetworkType()) {
            NetworkType.NR   -> Triple(1080, 1920, 20)
            NetworkType.WIFI -> Triple(720,  1280, 15)
            NetworkType.LTE  -> Triple(540,  960,  12)
            NetworkType.UMTS -> Triple(360,  640,  10)
            NetworkType.EDGE -> Triple(240,  426,  8)
        }
    }

    private fun connectToSignaling() {
        serviceScope.launch {
            signalingClient.connect()
            signalingClient.joinRoom(familyId = getFamilyId(), role = "child")

            // When parent joins, create and send offer
            signalingClient.onPeerJoined = {
                createAndSendOffer()
            }

            signalingClient.onAnswerReceived = { sdp ->
                val sessionDescription = SessionDescription(
                    SessionDescription.Type.ANSWER, sdp
                )
                peerConnection?.setRemoteDescription(SimpleSdpObserver(), sessionDescription)
            }

            signalingClient.onIceCandidateReceived = { candidate ->
                peerConnection?.addIceCandidate(
                    IceCandidate(candidate.sdpMid, candidate.sdpMLineIndex, candidate.sdp)
                )
            }
        }
    }

    private fun createAndSendOffer() {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
        }
        peerConnection?.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(sdp: SessionDescription) {
                peerConnection?.setLocalDescription(SimpleSdpObserver(), sdp)
                signalingClient.sendOffer(getFamilyId(), sdp.description)
            }
        }, constraints)
    }

    private val peerConnectionObserver = object : PeerConnection.Observer {
        override fun onIceCandidate(candidate: IceCandidate) {
            signalingClient.sendIceCandidate(getFamilyId(), candidate)
        }
        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
            if (state == PeerConnection.IceConnectionState.DISCONNECTED ||
                state == PeerConnection.IceConnectionState.FAILED) {
                // Auto-reconnect within 3 seconds as per project spec
                serviceScope.launch {
                    delay(3000)
                    reconnect()
                }
            }
        }
        // Implement remaining required overrides with empty bodies
        override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
        override fun onIceConnectionReceivingChange(receiving: Boolean) {}
        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
        override fun onAddStream(stream: MediaStream?) {}
        override fun onRemoveStream(stream: MediaStream?) {}
        override fun onDataChannel(channel: DataChannel?) {}
        override fun onRenegotiationNeeded() {}
        override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {}
    }

    // Adaptive quality — reduce quality on slow networks
    fun adaptBitrate(networkType: NetworkType) {
        val bitrate = when (networkType) {
            NetworkType.NR   -> 1_500_000  // 1500kbps on 5G
            NetworkType.WIFI -> 800_000    // 800kbps on WiFi
            NetworkType.LTE  -> 500_000    // 500kbps on 4G
            NetworkType.UMTS -> 200_000    // 200kbps on 3G
            NetworkType.EDGE -> 100_000    // 100kbps on 2G
        }
        val parameters = peerConnection
            ?.senders
            ?.firstOrNull()
            ?.parameters
            ?: return

        parameters.encodings.firstOrNull()?.maxBitrateBps = bitrate
        peerConnection?.senders?.firstOrNull()?.parameters = parameters
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        scheduleRestart()
    }

    override fun onDestroy() {
        videoCapturer?.stopCapture()
        videoCapturer?.dispose()
        localVideoTrack?.dispose()
        webRTCManager.dispose()
        signalingClient.disconnect()
        serviceScope.cancel()
        scheduleRestart() // always restart — screen capture must survive
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        // Resolution and FPS are now dynamic based on network — see getQualityForNetwork()
        const val NOTIFICATION_ID = 1001
    }
}
```

---

## Parent App — Receive and Display Stream

```kotlin
// In ParentLiveScreenFragment.xml — add this view:
// <org.webrtc.SurfaceViewRenderer android:id="@+id/remoteVideoView" />

@AndroidEntryPoint
class ParentLiveScreenFragment : Fragment() {

    @Inject lateinit var webRTCManager: WebRTCManager
    @Inject lateinit var signalingClient: SignalingClient

    private var peerConnection: PeerConnection? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize the renderer with EGL context
        binding.remoteVideoView.init(webRTCManager.getEglBase().eglBaseContext, null)
        binding.remoteVideoView.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
        binding.remoteVideoView.setMirror(false)

        webRTCManager.initialize()
        peerConnection = webRTCManager.createPeerConnection(peerConnectionObserver)
        connectToSignaling()
    }

    private fun connectToSignaling() {
        viewLifecycleOwner.lifecycleScope.launch {
            signalingClient.connect()
            signalingClient.joinRoom(familyId = getFamilyId(), role = "parent")

            signalingClient.onOfferReceived = { sdp ->
                handleOffer(sdp)
            }

            signalingClient.onIceCandidateReceived = { candidate ->
                peerConnection?.addIceCandidate(
                    IceCandidate(candidate.sdpMid, candidate.sdpMLineIndex, candidate.sdp)
                )
            }
        }
    }

    private fun handleOffer(sdp: String) {
        val sessionDescription = SessionDescription(SessionDescription.Type.OFFER, sdp)
        peerConnection?.setRemoteDescription(SimpleSdpObserver(), sessionDescription)

        peerConnection?.createAnswer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(answer: SessionDescription) {
                peerConnection?.setLocalDescription(SimpleSdpObserver(), answer)
                signalingClient.sendAnswer(getFamilyId(), answer.description)
            }
        }, MediaConstraints())
    }

    private val peerConnectionObserver = object : PeerConnection.Observer {
        override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
            // When video track arrives, attach it to the renderer
            val videoTrack = receiver?.track() as? VideoTrack
            activity?.runOnUiThread {
                videoTrack?.addSink(binding.remoteVideoView)
            }
        }
        override fun onIceCandidate(candidate: IceCandidate) {
            signalingClient.sendIceCandidate(getFamilyId(), candidate)
        }
        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
            if (state == PeerConnection.IceConnectionState.DISCONNECTED) {
                activity?.runOnUiThread { showReconnecting() }
            }
        }
        override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
        override fun onIceConnectionReceivingChange(receiving: Boolean) {}
        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
        override fun onAddStream(stream: MediaStream?) {}
        override fun onRemoveStream(stream: MediaStream?) {}
        override fun onDataChannel(channel: DataChannel?) {}
        override fun onRenegotiationNeeded() {}
    }

    override fun onDestroyView() {
        peerConnection?.dispose()
        webRTCManager.dispose()
        binding.remoteVideoView.release()
        signalingClient.disconnect()
        super.onDestroyView()
    }
}
```

---

## SignalingClient (Shared — Both Apps)

```kotlin
class SignalingClient @Inject constructor() {

    private lateinit var socket: Socket

    var onPeerJoined: (() -> Unit)? = null
    var onOfferReceived: ((String) -> Unit)? = null
    var onAnswerReceived: ((String) -> Unit)? = null
    var onIceCandidateReceived: ((IceCandidateData) -> Unit)? = null
    var onPeerDisconnected: (() -> Unit)? = null

    fun connect() {
        socket = IO.socket(BuildConfig.SIGNALING_SERVER_URL)
        socket.connect()

        socket.on("peer_joined") { onPeerJoined?.invoke() }
        socket.on("offer") { args ->
            val sdp = (args[0] as JSONObject).getString("sdp")
            onOfferReceived?.invoke(sdp)
        }
        socket.on("answer") { args ->
            val sdp = (args[0] as JSONObject).getString("sdp")
            onAnswerReceived?.invoke(sdp)
        }
        socket.on("ice_candidate") { args ->
            val obj = args[0] as JSONObject
            onIceCandidateReceived?.invoke(
                IceCandidateData(
                    sdpMid = obj.getString("sdpMid"),
                    sdpMLineIndex = obj.getInt("sdpMLineIndex"),
                    sdp = obj.getString("sdp")
                )
            )
        }
        socket.on("peer_disconnected") { onPeerDisconnected?.invoke() }
    }

    fun joinRoom(familyId: String, role: String) {
        socket.emit("join_room", JSONObject().apply {
            put("roomId", familyId)
            put("role", role)
        })
    }

    fun sendOffer(familyId: String, sdp: String) {
        socket.emit("offer", JSONObject().apply {
            put("roomId", familyId)
            put("sdp", sdp)
        })
    }

    fun sendAnswer(familyId: String, sdp: String) {
        socket.emit("answer", JSONObject().apply {
            put("roomId", familyId)
            put("sdp", sdp)
        })
    }

    fun sendIceCandidate(familyId: String, candidate: IceCandidate) {
        socket.emit("ice_candidate", JSONObject().apply {
            put("roomId", familyId)
            put("sdpMid", candidate.sdpMid)
            put("sdpMLineIndex", candidate.sdpMLineIndex)
            put("sdp", candidate.sdp)
        })
    }

    fun disconnect() {
        socket.disconnect()
        socket.off()
    }
}

data class IceCandidateData(
    val sdpMid: String,
    val sdpMLineIndex: Int,
    val sdp: String
)
```

---

## Adaptive Quality Rules

Network Resolution FPS Bitrate Expected experience
─────────────────────────────────────────────────────────────
5G 1080x1920 20 1500kbps Excellent, crisp
WiFi 720x1280 15 800kbps Smooth, clear
4G/LTE 540x960 12 500kbps Good, minor lag
3G 360x640 10 200kbps Acceptable, some blur
2G/Edge 240x426 8 100kbps Low quality but visible

Detect network type using ConnectivityManager.getNetworkCapabilities()
Call adaptBitrate() every 30 seconds to adjust for changing conditions.

---

## Auto-Reconnect Rules

- Reconnect attempt: 3 seconds after disconnect (project spec)
- Max reconnect attempts: unlimited — never give up
- On reconnect: re-join room → re-create offer → re-establish stream
- Show "Reconnecting..." in parent UI during gap
- SignalingClient socket.io auto-reconnects by default — do not disable this

---

## What to Never Do

- Never send video through the Railway signaling server — P2P only
- Never use more than 15fps — battery drain is too high for child phone
- Never skip adaptBitrate() — fixed bitrate breaks on 2G/3G
- Never forget to release SurfaceViewRenderer in onDestroyView
- Never create multiple PeerConnections for same stream
- Never skip the auto-reconnect logic — connections drop often in India
- Never use paid TURN servers — STUN is sufficient for most P2P connections
