package com.pakkabaat.app.recording

import android.content.Context
import androidx.work.*
import com.google.gson.Gson
import com.pakkabaat.app.data.db.CertificateEntity
import com.pakkabaat.app.data.db.PakkaBaatDatabase
import com.pakkabaat.app.data.db.StructuredDocumentEntity
import com.pakkabaat.app.data.db.TranscriptEntity
import com.pakkabaat.app.data.model.SessionStatus
import com.pakkabaat.app.data.model.TranscriptSource
import com.pakkabaat.app.data.model.UploadStatus
import com.pakkabaat.app.network.BhashiniService
import com.pakkabaat.app.network.GeminiStructuringService
import com.pakkabaat.app.util.HashUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.*

class CloudProcessingWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_SESSION_ID = "sessionId"
        const val KEY_RECORDING_ID = "recordingId"
        const val KEY_AUDIO_PATH = "audioPath"
        const val KEY_LANGUAGE = "language"
        const val KEY_LANGUAGE_NAME = "languageName"
        const val KEY_PARTY_A_NAME = "partyAName"
        const val KEY_PARTY_B_NAME = "partyBName"

        fun enqueue(
            context: Context,
            sessionId: String,
            recordingId: String,
            audioPath: String,
            language: String,
            languageName: String,
            partyAName: String,
            partyBName: String
        ) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val data = workDataOf(
                KEY_SESSION_ID to sessionId,
                KEY_RECORDING_ID to recordingId,
                KEY_AUDIO_PATH to audioPath,
                KEY_LANGUAGE to language,
                KEY_LANGUAGE_NAME to languageName,
                KEY_PARTY_A_NAME to partyAName,
                KEY_PARTY_B_NAME to partyBName
            )

            // Unique per session: if this device already queued processing for this
            // session, don't duplicate it (e.g. app restarted while offline and waiting).
            val request = OneTimeWorkRequestBuilder<CloudProcessingWorker>()
                .setConstraints(constraints)
                .setInputData(data)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    java.util.concurrent.TimeUnit.MILLISECONDS
                )
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork("process_session_$sessionId", ExistingWorkPolicy.KEEP, request)
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val sessionId = inputData.getString(KEY_SESSION_ID) ?: return@withContext Result.failure()
        val recordingId = inputData.getString(KEY_RECORDING_ID) ?: return@withContext Result.failure()
        val audioPath = inputData.getString(KEY_AUDIO_PATH) ?: return@withContext Result.failure()
        val language = inputData.getString(KEY_LANGUAGE) ?: "hi"
        val languageName = inputData.getString(KEY_LANGUAGE_NAME) ?: "Hindi"
        val partyAName = inputData.getString(KEY_PARTY_A_NAME) ?: "Party A"
        val partyBName = inputData.getString(KEY_PARTY_B_NAME) ?: "Party B"

        val db = PakkaBaatDatabase.getInstance(applicationContext)
        val audioFile = File(audioPath)
        if (!audioFile.exists()) return@withContext Result.failure()

        return@withContext try {
            db.sessionDao().getById(sessionId)?.let {
                db.sessionDao().upsert(it.copy(status = SessionStatus.PROCESSING))
            }

            // Step 1: Bhashini ASR -> final, high-quality transcript (spec 8.7)
            val transcriptText = BhashiniService().transcribe(audioFile, language)

            val transcriptId = UUID.randomUUID().toString()
            db.transcriptDao().upsert(
                TranscriptEntity(
                    transcriptId = transcriptId,
                    recordingId = recordingId,
                    rawText = transcriptText,
                    language = language,
                    source = TranscriptSource.CLOUD_FINAL,
                    confidence = null
                )
            )

            // Step 2: structuring prompt (spec 10.1) -> structured document, via the free Gemini API
            val structured = GeminiStructuringService().structure(transcriptText, language, languageName)

            val documentId = UUID.randomUUID().toString()
            val gson = Gson()
            db.structuredDocumentDao().upsert(
                StructuredDocumentEntity(
                    documentId = documentId,
                    sessionId = sessionId,
                    transcriptId = transcriptId,
                    partyAName = partyAName,
                    partyBName = partyBName,
                    agreementType = structured.agreementType,
                    amount = structured.amount,
                    currency = structured.currency,
                    termsJson = gson.toJson(structured.terms),
                    conditions = structured.humanReadableText,
                    unclearItemsJson = gson.toJson(structured.unclearItems),
                    dateOfConversation = System.currentTimeMillis(),
                    generatedAt = System.currentTimeMillis(),
                    modelUsed = structured.modelUsed
                )
            )

            // Step 3: tamper-evidence certificate (spec 8.9 / 14 — placeholder pending lawyer review)
            val transcriptHash = HashUtil.sha256Text(transcriptText)
            val audioHash = HashUtil.sha256File(audioFile)
            db.certificateDao().upsert(
                CertificateEntity(
                    certificateId = UUID.randomUUID().toString(),
                    documentId = documentId,
                    audioSha256 = audioHash,
                    transcriptSha256 = transcriptHash,
                    deviceMetadataJson = gson.toJson(
                        mapOf(
                            "device" to android.os.Build.MODEL,
                            "androidVersion" to android.os.Build.VERSION.RELEASE,
                            "processedAt" to System.currentTimeMillis()
                        )
                    ),
                    generatedAt = System.currentTimeMillis(),
                    // ⚠️ Placeholder only — needs BSA Section 63 lawyer-reviewed wording (spec 6.2/14)
                    certificateStatement = "This recording was made with the informed, verified " +
                        "consent of both named parties, captured via OTP-authenticated devices at " +
                        "the timestamps recorded. The audio file's SHA-256 hash is provided to " +
                        "verify it has not been altered since creation. This statement is a draft " +
                        "placeholder and has not been reviewed by a lawyer for BSA Section 63 " +
                        "compliance."
                )
            )

            db.audioRecordingDao().setUploadStatus(recordingId, UploadStatus.UPLOADED)
            db.sessionDao().getById(sessionId)?.let {
                db.sessionDao().upsert(it.copy(status = SessionStatus.COMPLETED))
            }

            Result.success()
        } catch (e: Exception) {
            db.sessionDao().getById(sessionId)?.let {
                db.sessionDao().upsert(it.copy(status = SessionStatus.FAILED))
            }
            // Let WorkManager retry with backoff next time connectivity is confirmed good,
            // rather than surfacing a hard failure to the user (spec 8.7: delay ≠ failure state).
            if (runAttemptCount < 5) Result.retry() else Result.failure()
        }
    }
}
