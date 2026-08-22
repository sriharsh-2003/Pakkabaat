package com.pakkabaat.app.pairing

import android.content.Context
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Wraps Google Play Services Nearby Connections to pair two phones that are physically
 * together, over Bluetooth/Wi-Fi Direct, with zero server involved (spec sections 7.4, 8.2).
 *
 * The QR code (see QrCodeUtil) carries the session token that Party A generates; Party B
 * uses it purely to confirm it is connecting to the right nearby endpoint (there could be
 * several PakkaBaat users in range), never to reach any server.
 */
sealed class PairingEvent {
    data class Connected(val endpointId: String) : PairingEvent()
    data class MessageReceived(val message: SessionMessage) : PairingEvent()
    data class Disconnected(val endpointId: String) : PairingEvent()
    data class Error(val reason: String) : PairingEvent()
}

class NearbyPairingManager(private val context: Context) {

    companion object {
        private const val SERVICE_ID = "com.pakkabaat.app.SERVICE_ID"
        private val STRATEGY = Strategy.P2P_POINT_TO_POINT
    }

    private val client = Nearby.getConnectionsClient(context)
    private var connectedEndpointId: String? = null

    /** Party A: advertise this device under [sessionToken] so Party B's scan can find it. */
    fun events(sessionToken: String, isAdvertiser: Boolean): Flow<PairingEvent> = callbackFlow {
        val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
            override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
                // Auto-accept: identity is already established via the QR token match,
                // and the human-facing mutual consent screen is a separate, explicit step.
                client.acceptConnection(endpointId, object : PayloadCallback() {
                    override fun onPayloadReceived(fromEndpointId: String, payload: Payload) {
                        payload.asBytes()?.let { bytes ->
                            SessionMessage.decode(bytes)?.let { trySend(PairingEvent.MessageReceived(it)) }
                        }
                    }
                    override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {}
                })
            }

            override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
                if (result.status.isSuccess) {
                    connectedEndpointId = endpointId
                    trySend(PairingEvent.Connected(endpointId))
                } else {
                    trySend(PairingEvent.Error("Connection failed: ${result.status.statusMessage}"))
                }
            }

            override fun onDisconnected(endpointId: String) {
                connectedEndpointId = null
                trySend(PairingEvent.Disconnected(endpointId))
            }
        }

        if (isAdvertiser) {
            val options = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
            client.startAdvertising(sessionToken, SERVICE_ID, connectionLifecycleCallback, options)
                .addOnFailureListener { trySend(PairingEvent.Error("Could not start advertising: ${it.message}")) }
        } else {
            val discoveryCallback = object : EndpointDiscoveryCallback() {
                override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
                    if (info.endpointName == sessionToken && info.serviceId == SERVICE_ID) {
                        client.requestConnection(sessionToken, endpointId, connectionLifecycleCallback)
                    }
                }
                override fun onEndpointLost(endpointId: String) {}
            }
            val options = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
            client.startDiscovery(SERVICE_ID, discoveryCallback, options)
                .addOnFailureListener { trySend(PairingEvent.Error("Could not start discovery: ${it.message}")) }
        }

        awaitClose {
            client.stopAdvertising()
            client.stopDiscovery()
            client.stopAllEndpoints()
        }
    }

    fun send(message: SessionMessage) {
        val endpointId = connectedEndpointId ?: return
        client.sendPayload(endpointId, Payload.fromBytes(SessionMessage.encode(message)))
    }

    fun teardown() {
        client.stopAdvertising()
        client.stopDiscovery()
        client.stopAllEndpoints()
        connectedEndpointId = null
    }
}
