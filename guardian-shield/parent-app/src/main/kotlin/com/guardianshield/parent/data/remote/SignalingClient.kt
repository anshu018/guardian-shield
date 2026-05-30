package com.guardianshield.parent.data.remote

import android.util.Log
import com.guardianshield.parent.BuildConfig
import com.guardianshield.parent.domain.model.SignalingMessage
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SignalingClient @Inject constructor() {

    private val tag = "SignalingClient"
    private var socket: Socket? = null
    private val json = Json { ignoreUnknownKeys = true }
    private val clientScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _incomingSignals = MutableSharedFlow<SignalingMessage>(extraBufferCapacity = 64)
    val incomingSignals: SharedFlow<SignalingMessage> = _incomingSignals.asSharedFlow()

    private var currentFamilyCode: String? = null

    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        ERROR
    }

    fun connect(familyCode: String) {
        if (socket != null && socket!!.connected() && currentFamilyCode == familyCode) {
            Log.d(tag, "Already connected to signaling server for room: $familyCode")
            return
        }

        disconnect()
        currentFamilyCode = familyCode
        _connectionState.value = ConnectionState.CONNECTING

        try {
            val opts = IO.Options().apply {
                forceNew = true
                reconnection = true
                reconnectionAttempts = 5
                reconnectionDelay = 1000
            }

            val serverUrl = BuildConfig.SIGNALING_SERVER_URL
            Log.d(tag, "Connecting to signaling server: $serverUrl")
            socket = IO.socket(serverUrl, opts)

            socket?.on(Socket.EVENT_CONNECT) {
                Log.d(tag, "Successfully connected to signaling server")
                _connectionState.value = ConnectionState.CONNECTED
                joinRoom(familyCode)
            }

            socket?.on(Socket.EVENT_DISCONNECT) {
                Log.d(tag, "Disconnected from signaling server")
                _connectionState.value = ConnectionState.DISCONNECTED
            }

            socket?.on(Socket.EVENT_CONNECT_ERROR) { args ->
                val error = if (args.isNotEmpty()) args[0].toString() else "Unknown connection error"
                Log.e(tag, "Connection error: $error")
                _connectionState.value = ConnectionState.ERROR
            }

            socket?.on("message") { args ->
                if (args.isNotEmpty()) {
                    val rawPayload = args[0]
                    Log.d(tag, "Received raw signal payload: $rawPayload")
                    val jsonStr = when (rawPayload) {
                        is JSONObject -> rawPayload.toString()
                        is String -> rawPayload
                        else -> rawPayload.toString()
                    }
                    try {
                        val message = json.decodeFromString<SignalingMessage>(jsonStr)
                        // Ignore message if sent by parent (self)
                        if (message.sender != "parent") {
                            clientScope.launch {
                                _incomingSignals.emit(message)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(tag, "Failed to parse incoming signaling message: ${e.message}", e)
                    }
                }
            }

            socket?.connect()
        } catch (e: Exception) {
            Log.e(tag, "Initialization error: ${e.message}", e)
            _connectionState.value = ConnectionState.ERROR
        }
    }

    private fun joinRoom(familyCode: String) {
        Log.d(tag, "Joining room room-$familyCode")
        val joinPayload = JSONObject().apply {
            put("room", "room-$familyCode")
            put("role", "parent")
        }
        socket?.emit("join", joinPayload)
    }

    fun sendSignal(message: SignalingMessage) {
        val socketInstance = socket
        if (socketInstance == null || !socketInstance.connected()) {
            Log.w(tag, "Cannot send signal. Socket not connected.")
            return
        }

        try {
            val messageWithSender = message.copy(
                sender = "parent",
                familyCode = currentFamilyCode
            )
            val jsonStr = json.encodeToString(messageWithSender)
            Log.d(tag, "Sending signal payload: $jsonStr")
            socketInstance.emit("message", JSONObject(jsonStr))
        } catch (e: Exception) {
            Log.e(tag, "Failed to send signaling message: ${e.message}", e)
        }
    }

    fun disconnect() {
        Log.d(tag, "Disconnecting signaling client")
        socket?.off()
        socket?.disconnect()
        socket = null
        currentFamilyCode = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }
}
