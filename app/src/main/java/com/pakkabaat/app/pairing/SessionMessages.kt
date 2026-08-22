package com.pakkabaat.app.pairing

import com.google.gson.Gson

/**
 * Every message here is small JSON sent directly device-to-device over the Nearby
 * Connections link (Bluetooth/Wi-Fi Direct). Nothing here ever touches a server, and
 * audio itself is never sent over this channel — each phone independently records its
 * own copy of the conversation via its own microphone (spec section 7.1: "each party
 * gets their own copy"). This channel only coordinates consent and stop timing.
 */
sealed class SessionMessage {
    data class Hello(val sessionId: String, val name: String, val language: String) : SessionMessage()
    data class ConsentStart(val userId: String, val timestamp: Long) : SessionMessage()
    data class StopRequest(val userId: String, val timestamp: Long) : SessionMessage()
    data class StopConfirm(val userId: String, val timestamp: Long) : SessionMessage()
    data class StopTimeoutOverride(val userId: String, val timestamp: Long) : SessionMessage()

    companion object {
        private val gson = Gson()

        fun encode(message: SessionMessage): ByteArray {
            val type = message::class.simpleName!!
            val envelope = Envelope(type, gson.toJson(message))
            return gson.toJson(envelope).toByteArray(Charsets.UTF_8)
        }

        fun decode(bytes: ByteArray): SessionMessage? {
            return try {
                val envelope = gson.fromJson(String(bytes, Charsets.UTF_8), Envelope::class.java)
                when (envelope.type) {
                    "Hello" -> gson.fromJson(envelope.payload, Hello::class.java)
                    "ConsentStart" -> gson.fromJson(envelope.payload, ConsentStart::class.java)
                    "StopRequest" -> gson.fromJson(envelope.payload, StopRequest::class.java)
                    "StopConfirm" -> gson.fromJson(envelope.payload, StopConfirm::class.java)
                    "StopTimeoutOverride" -> gson.fromJson(envelope.payload, StopTimeoutOverride::class.java)
                    else -> null
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    private data class Envelope(val type: String, val payload: String)
}
