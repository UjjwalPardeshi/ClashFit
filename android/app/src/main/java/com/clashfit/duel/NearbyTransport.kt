package com.clashfit.duel

import android.content.Context
import android.util.Log
import com.clashfit.core.model.DuelMessage
import com.clashfit.core.model.LinkState
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Transport over Google Nearby Connections (P2P_STAR).
 * Host advertises with a room name; guests discover and connect.
 * Auto-accepts connections; payloads are JSON-serialized DuelMessage.
 */
class NearbyTransport(
    private val context: Context,
    private val json: Json,
    private val scope: CoroutineScope,
) : DuelTransport {

    private companion object {
        const val TAG = "ClashFit/Nearby"
        const val SERVICE_ID = "com.clashfit.duel"
    }

    private val client: ConnectionsClient = Nearby.getConnectionsClient(context)

    private val _state = MutableStateFlow(LinkState.IDLE)
    private val _incoming = MutableSharedFlow<DuelMessage>(extraBufferCapacity = 64)

    override val state: StateFlow<LinkState> = _state.asStateFlow()
    override val incoming: SharedFlow<DuelMessage> = _incoming.asSharedFlow()

    // Track connected endpoints and their IDs
    private val connectedEndpoints = mutableSetOf<String>()
    /** Catch-up for a guest who joins after the host has already announced something. */
    private val outgoingQueue = mutableListOf<DuelMessage>()
    private var roomName = ""
    private var isClosed = false

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type != Payload.Type.BYTES) return

            val bytes = payload.asBytes() ?: return
            try {
                val text = String(bytes, Charsets.UTF_8)
                val msg = json.decodeFromString<DuelMessage>(text)
                scope.launch {
                    _incoming.emit(msg)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to decode message: ${e.message}")
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // No-op; we don't track payload completion
        }
    }

    private val connectionCallback = object : com.google.android.gms.nearby.connection.ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            Log.d(TAG, "Connection initiated from $endpointId")
            // Auto-accept
            client.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            when {
                result.status.isSuccess -> {
                    Log.d(TAG, "Connected to $endpointId")
                    connectedEndpoints.add(endpointId)
                    updateState()
                    // Flush the backlog to whoever just arrived, and keep it.
                    //
                    // This used to clear the queue after the first endpoint connected. In a star
                    // topology the host can have several guests, and every guest after the first
                    // joined an empty backlog — they missed the mode, the exercise and the clock
                    // the host had already announced, and started a different match from everybody
                    // else. The queue is a joining player's catch-up, so it belongs to the session
                    // rather than to the first connection.
                    for (msg in outgoingQueue) {
                        sendRaw(endpointId, msg)
                    }
                }

                else -> {
                    Log.w(TAG, "Connection failed to $endpointId: ${result.status.statusMessage}")
                }
            }
        }

        override fun onDisconnected(endpointId: String) {
            Log.d(TAG, "Disconnected from $endpointId")
            connectedEndpoints.remove(endpointId)
            updateState()
        }
    }

    override suspend fun host(roomName: String): Result<Unit> {
        this.roomName = roomName

        val opts = com.google.android.gms.nearby.connection.AdvertisingOptions.Builder()
            .setStrategy(Strategy.P2P_STAR)
            .build()

        client.startAdvertising(roomName, SERVICE_ID, connectionCallback, opts)
            .addOnSuccessListener {
                Log.d(TAG, "Advertising started for $roomName")
                _state.value = LinkState.ADVERTISING
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Advertising failed: ${e.message}")
                _state.value = LinkState.IDLE
            }

        return Result.success(Unit)
    }

    override suspend fun join(): Result<Unit> {
        val opts = com.google.android.gms.nearby.connection.DiscoveryOptions.Builder()
            .setStrategy(Strategy.P2P_STAR)
            .build()

        val discoveryCallback = object : EndpointDiscoveryCallback() {
            override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
                Log.d(TAG, "Found endpoint: $endpointId (${info.endpointName})")
                client.requestConnection(info.endpointName, endpointId, connectionCallback)
            }

            override fun onEndpointLost(endpointId: String) {
                Log.d(TAG, "Lost endpoint: $endpointId")
            }
        }

        client.startDiscovery(SERVICE_ID, discoveryCallback, opts)
            .addOnSuccessListener {
                Log.d(TAG, "Discovery started")
                _state.value = LinkState.SEARCHING
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Discovery failed: ${e.message}")
                _state.value = LinkState.IDLE
            }

        return Result.success(Unit)
    }

    override fun send(msg: DuelMessage) {
        if (connectedEndpoints.isEmpty()) {
            // Bounded, oldest dropped first. The queue exists so a guest who joins late catches
            // up, and a catch-up only needs the recent past; without a cap a lobby left open with
            // nobody in it would grow it forever.
            outgoingQueue.add(msg)
            while (outgoingQueue.size > MAX_QUEUED) outgoingQueue.removeAt(0)
            return
        }

        for (endpointId in connectedEndpoints) {
            sendRaw(endpointId, msg)
        }
    }

    override fun close() {
        if (isClosed) return
        outgoingQueue.clear()
        isClosed = true

        client.stopAdvertising()
        client.stopDiscovery()
        client.stopAllEndpoints()
        _state.value = LinkState.IDLE
    }

    // Private implementation

    private fun sendRaw(endpointId: String, msg: DuelMessage) {
        try {
            val text = json.encodeToString(msg)
            val bytes = text.toByteArray(Charsets.UTF_8)
            val payload = Payload.fromBytes(bytes)
            client.sendPayload(endpointId, payload)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send message: ${e.message}")
        }
    }

    private fun updateState() {
        val next = when {
            connectedEndpoints.isNotEmpty() -> LinkState.LINKED
            _state.value == LinkState.ADVERTISING -> LinkState.ADVERTISING
            _state.value == LinkState.SEARCHING -> LinkState.SEARCHING
            else -> LinkState.IDLE
        }
        if (next != _state.value) {
            _state.value = next
        }
    }
}

/** How much backlog a late guest is worth. Beyond this the oldest is dropped. */
private const val MAX_QUEUED = 64
