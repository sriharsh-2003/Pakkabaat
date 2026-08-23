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

    /**
     * Sent by the host device (Party A) once its own Gemini structuring call finishes,
     * so Party B's device never has to make its own Gemini call for the same
     * conversation — previously BOTH phones independently called Gemini on their own
     * on-device transcript, doubling API usage/credits for every single session and
     * risking two slightly different drafts. Party B still keeps its own audio
     * recording and transcript (each phone records its own copy, per spec 7.1) and
     * builds its own certificate from its own audio hash — only the drafted content
     * itself is shared, not the audio.
     */
    data class DraftReady(
        val agreementType: String,
        val amount: Double?,
        val currency: String,
        val termsJson: String,
        val conditions: String,
        val unclearItemsJson: String,
        val dateOfConversation: Long,
        val modelUsed: String
    ) : SessionMessage()

    /** Sent by Party A to Party B if the host's Gemini call ultimately failed, so
     *  Party B's ProcessingScreen doesn't spin forever waiting for a draft that will
     *  never arrive. */
    data class DraftFailed(val reason: String) : SessionMessage()

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
                    "DraftReady" -> gson.fromJson(envelope.payload, DraftReady::class.java)
                    "DraftFailed" -> gson.fromJson(envelope.payload, DraftFailed::class.java)
                    else -> null
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    private data class Envelope(val type: String, val payload: String)
}
