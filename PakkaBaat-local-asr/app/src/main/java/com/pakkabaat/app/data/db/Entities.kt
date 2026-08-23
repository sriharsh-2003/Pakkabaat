package com.pakkabaat.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.pakkabaat.app.data.model.*

/**
 * Every table here is a LOCAL, on-device table only (spec section 7.1 and section 9).
 * There is no server-side mirror of any of these entities. Deleting the app or a
 * session on this device deletes this device's copy of the record (spec section 13).
 */

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val userId: String,
    val phoneNumber: String,          // OTP-verified
    val name: String,
    val preferredLanguage: String,    // e.g. "hi", "en", "mr"
    val createdAt: Long
)

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val sessionId: String,
    val partyAUserId: String,
    val partyBUserId: String?,        // null until B accepts / pairs
    val partyBPhone: String?,
    val partyAName: String,
    val partyBName: String?,
    val mode: SessionMode,
    val status: SessionStatus,
    val startedAt: Long?,
    val endedAt: Long?,
    val stopRequestedBy: String?,
    val stopConfirmedBy: String?,
    val stopViaTimeout: Boolean = false,
    val myRole: PartyRole             // which party THIS device is, for local UI logic
)

@Entity(tableName = "consent_events")
data class ConsentEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val userId: String,
    val action: ConsentAction,
    val timestamp: Long
)

@Entity(tableName = "audio_recordings")
data class AudioRecordingEntity(
    @PrimaryKey val recordingId: String,
    val sessionId: String,
    val storagePath: String,
    val durationSeconds: Int,
    val sha256Hash: String,           // generated immediately on save, before any upload (spec 8.9)
    val languageDetected: String?,
    val uploadStatus: UploadStatus
)

@Entity(tableName = "transcripts")
data class TranscriptEntity(
    @PrimaryKey val transcriptId: String,
    val recordingId: String,
    val rawText: String,
    val language: String,
    val source: TranscriptSource,
    val confidence: Float?
)

@Entity(tableName = "structured_documents")
data class StructuredDocumentEntity(
    @PrimaryKey val documentId: String,
    val sessionId: String,
    val transcriptId: String,
    val partyAName: String,
    val partyBName: String,
    val agreementType: AgreementType,
    val amount: Double?,
    val currency: String,
    val termsJson: String,            // JSON-encoded List<String>
    val conditions: String,
    val unclearItemsJson: String,     // JSON-encoded List<String>
    val dateOfConversation: Long,
    val generatedAt: Long,
    val modelUsed: String
)

@Entity(tableName = "certificates")
data class CertificateEntity(
    @PrimaryKey val certificateId: String,
    val documentId: String,
    val audioSha256: String,
    val transcriptSha256: String,
    val deviceMetadataJson: String,
    val generatedAt: Long,
    // ⚠️ placeholder language only — needs BSA Section 63 lawyer review, spec section 6.2 / 14
    val certificateStatement: String
)
