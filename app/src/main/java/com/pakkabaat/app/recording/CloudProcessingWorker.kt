package com.pakkabaat.app.recording

import android.content.Context
import androidx.work.*
import com.google.gson.Gson
import com.pakkabaat.app.data.db.CertificateEntity
import com.pakkabaat.app.data.db.PakkaBaatDatabase
import com.pakkabaat.app.data.db.StructuredDocumentEntity
import com.pakkabaat.app.data.model.SessionStatus
import com.pakkabaat.app.data.model.UploadStatus
import com.pakkabaat.app.data.repository.ApiKeyStore
import com.pakkabaat.app.network.GeminiStructuringService
import com.pakkabaat.app.util.HashUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

/**
 * By the time this worker runs, whisper.cpp has already produced the transcript
 * on-device (see SessionViewModel.finishRecording) and it's sitting in the
 * `transcripts` table. So the ONLY network-dependent step left is Gemini
 * structuring (spec 10.1) — this worker exists purely to queue that one call
 * until a connection is available (spec 6.4/8.7), same as before, just shorter.
 */
class CloudProcessingWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_SESSION_ID = "sessionId"
        const val KEY_RECORDING_ID = "recordingId"
        const val KEY_TRANSCRIPT_ID = "transcriptId"
        const val KEY_LANGUAGE = "language"
        const val KEY_LANGUAGE_NAME = "languageName"
        const val KEY_PARTY_A_NAME = "partyAName"
        const val KEY_PARTY_B_NAME = "partyBName"
        const val KEY_ERROR = "error"

        /** Unique work name for a given session — used by the ViewModel to observe this
         *  worker's WorkInfo (state + output data) so a failure can be surfaced in the UI
         *  instead of leaving ProcessingScreen spinning forever. */
        fun workName(sessionId: String) = "process_session_$sessionId"

        fun enqueue(
            context: Context,
            sessionId: String,
            recordingId: String,
            transcriptId: String,
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
                KEY_TRANSCRIPT_ID to transcriptId,
                KEY_LANGUAGE to language,
                KEY_LANGUAGE_NAME to languageName,
                KEY_PARTY_A_NAME to partyAName,
                KEY_PARTY_B_NAME to partyBName
            )

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
                .enqueueUniqueWork(workName(sessionId), ExistingWorkPolicy.KEEP, request)
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val sessionId = inputData.getString(KEY_SESSION_ID) ?: return@withContext Result.failure()
        val recordingId = inputData.getString(KEY_RECORDING_ID) ?: return@withContext Result.failure()
        val transcriptId = inputData.getString(KEY_TRANSCRIPT_ID) ?: return@withContext Result.failure()
        val language = inputData.getString(KEY_LANGUAGE) ?: "hi"
        val languageName = inputData.getString(KEY_LANGUAGE_NAME) ?: "Hindi"
        val partyAName = inputData.getString(KEY_PARTY_A_NAME) ?: "Party A"
        val partyBName = inputData.getString(KEY_PARTY_B_NAME) ?: "Party B"

        val db = PakkaBaatDatabase.getInstance(applicationContext)

        val transcript = db.transcriptDao().forRecording(recordingId).firstOrNull { it.transcriptId == transcriptId }
            ?: return@withContext Result.failure()
        val recording = db.audioRecordingDao().forSession(sessionId) ?: return@withContext Result.failure()

        return@withContext try {
            db.sessionDao().getById(sessionId)?.let {
                db.sessionDao().upsert(it.copy(status = SessionStatus.PROCESSING))
            }

            // The only step left that needs a connection: structuring via Gemini (spec 10.1).
            val apiKey = ApiKeyStore(applicationContext).getGeminiKey()
            val structured = GeminiStructuringService(apiKey = apiKey)
                .structure(transcript.rawText, language, languageName, partyAName, partyBName)

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

            // Tamper-evidence certificate (spec 8.9/14 — placeholder pending lawyer review).
            // Audio hash was already computed the instant recording stopped (spec 8.9) —
            // reuse it rather than re-reading/re-hashing the file here.
            db.certificateDao().upsert(
                CertificateEntity(
                    certificateId = UUID.randomUUID().toString(),
                    documentId = documentId,
                    audioSha256 = recording.sha256Hash,
                    transcriptSha256 = HashUtil.sha256Text(transcript.rawText),
                    deviceMetadataJson = gson.toJson(
                        mapOf(
                            "device" to android.os.Build.MODEL,
                            "androidVersion" to android.os.Build.VERSION.RELEASE,
                            "processedAt" to System.currentTimeMillis(),
                            "asrEngine" to "whisper.cpp (on-device)"
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
            // Previously this was retried silently up to 5 times and then just
            // Result.failure()'d with no reason attached anywhere — the UI had no way
            // to know processing had died at all, so ProcessingScreen spun forever.
            // Attaching the message to the WorkInfo output lets the ViewModel observe
            // it and actually tell the user what happened.
            val reason = e.message ?: e.javaClass.simpleName
            if (runAttemptCount < 5) {
                Result.retry()
            } else {
                Result.failure(workDataOf(KEY_ERROR to reason))
            }
        }
    }
}
