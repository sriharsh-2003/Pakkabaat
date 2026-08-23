package com.pakkabaat.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.gson.Gson
import com.pakkabaat.app.consent.stopTimeoutCountdown
import com.pakkabaat.app.data.db.*
import com.pakkabaat.app.data.model.*
import com.pakkabaat.app.data.repository.ApiKeyStore
import com.pakkabaat.app.data.repository.IdentityStore
import com.pakkabaat.app.pairing.NearbyPairingManager
import com.pakkabaat.app.pairing.PairingEvent
import com.pakkabaat.app.pairing.SessionMessage
import com.pakkabaat.app.recording.AudioRecorderManager
import com.pakkabaat.app.recording.CloudProcessingWorker
import com.pakkabaat.app.recording.OnDeviceTranscriber
import com.pakkabaat.app.recording.WhisperCppTranscriber
import com.pakkabaat.app.util.ConnectivityUtil
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.util.*

data class SessionUiState(
    val myUserId: String = "",
    val myName: String = "",
    val myLanguage: String = "hi",
    val sessionId: String = "",
    val role: PartyRole = PartyRole.PARTY_A,
    val qrToken: String? = null,               // shown as QR when I'm Party A
    val pairingConnected: Boolean = false,
    val partnerName: String? = null,
    // Single-phone mode: both people are physically present at one device, so there's
    // no pairing/QR step at all — the host just types in the other person's name.
    val singlePhoneMode: Boolean = false,
    val myConsent: Boolean = false,
    val otherConsent: Boolean = false,
    val isRecording: Boolean = false,
    val elapsedSeconds: Int = 0,
    val stopRequestedByMe: Boolean = false,
    val stopRequestedByOtherName: String? = null,
    val stopTimeoutRemaining: Int? = null,
    val stopViaTimeout: Boolean = false,
    val draftTranscript: String? = null,
    val draftIsPlaceholder: Boolean = false,
    val isOnline: Boolean = true,
    val processing: Boolean = false,
    val processingStage: ProcessingStage = ProcessingStage.TRANSCRIBING,
    // Which phone is actually doing the Gemini/whisper work, for ProcessingScreen —
    // only the host ever processes; the other phone just waits for DraftReady.
    val processingDeviceLabel: String? = null,
    val document: StructuredDocumentEntity? = null,
    val certificate: CertificateEntity? = null,
    val audioRecording: AudioRecordingEntity? = null,
    val error: String? = null
)

class SessionViewModel(application: Application) : AndroidViewModel(application) {

    private val db = PakkaBaatDatabase.getInstance(application)
    private val pairing = NearbyPairingManager(application)
    private val recorder = AudioRecorderManager(application)
    private val onDeviceTranscriber: OnDeviceTranscriber = WhisperCppTranscriber(application)
    val apiKeyStore = ApiKeyStore(application)
    val identityStore = IdentityStore(application)

    private val _uiState = MutableStateFlow(SessionUiState())
    val uiState: StateFlow<SessionUiState> = _uiState.asStateFlow()

    init {
        // Load any previously-saved identity synchronously, before NavGraph reads
        // uiState.value to pick a start destination — this is what actually fixes
        // "no persistence for name/language": without it, every process restart
        // wiped myName/myLanguage back to blank/"hi" and forced onboarding again.
        if (identityStore.hasIdentity()) {
            _uiState.update {
                it.copy(
                    myUserId = identityStore.getUserId(),
                    myName = identityStore.getName().orEmpty(),
                    myLanguage = identityStore.getLanguage()
                )
            }
        }
    }

    val pastSessions: Flow<List<SessionEntity>> = db.sessionDao().observeAll()

    private var pairingJob: Job? = null
    private var tickerJob: Job? = null
    private var stopTimeoutJob: Job? = null
    private var documentObserverJob: Job? = null
    private var currentRecordingId: String? = null

    // ---------- Onboarding (spec 8.1) ----------

    fun setMyIdentity(name: String, language: String) {
        identityStore.saveIdentity(name, language)
        // Reuse the userId IdentityStore persists (generating it once, lazily, on first
        // call) rather than a fresh UUID per call — otherwise "myUserId" would silently
        // change on every process restart even after name/language start persisting.
        _uiState.update { it.copy(myUserId = identityStore.getUserId(), myName = name, myLanguage = language) }
    }

    /** Settings screen: edit name/language after onboarding. Keeps the same userId so
     *  past sessions in the DB (keyed by userId) still resolve to this identity. */
    fun updateMyProfile(name: String, language: String) {
        val trimmed = name.trim()
        identityStore.updateProfile(trimmed, language)
        _uiState.update { it.copy(myName = trimmed, myLanguage = language) }
    }

    // ---------- Starting a session, in-person mode (spec 8.2) ----------

    /**
     * Clears everything left over from a previous session before starting the next one.
     * Without this, a second "Record a new agreement" run reused the prior session's
     * document/draftTranscript/stopRequestedByOtherName/etc — which caused two real bugs:
     * the second recording's ProcessingScreen jumping straight to the FIRST recording's
     * draft (because state.document was still non-null from before, so the "document
     * ready" LaunchedEffect fired immediately), and RecordingScreen showing a stale
     * "the other person wants to stop" banner left over from how the previous session
     * ended, even though nobody pressed Stop this time.
     */
    private fun resetForNewSession() {
        documentObserverJob?.cancel()
        tickerJob?.cancel()
        stopTimeoutJob?.cancel()
        currentRecordingId = null
        draftSentForSession = null
        _uiState.update {
            it.copy(
                sessionId = "",
                qrToken = null,
                pairingConnected = false,
                partnerName = null,
                singlePhoneMode = false,
                myConsent = false,
                otherConsent = false,
                isRecording = false,
                elapsedSeconds = 0,
                stopRequestedByMe = false,
                stopRequestedByOtherName = null,
                stopTimeoutRemaining = null,
                stopViaTimeout = false,
                draftTranscript = null,
                draftIsPlaceholder = false,
                processing = false,
                processingStage = ProcessingStage.TRANSCRIBING,
                processingDeviceLabel = null,
                document = null,
                certificate = null,
                audioRecording = null,
                error = null
            )
        }
    }

    /** Party A: start advertising nearby for Party B to scan+join. No DB row is created
     *  yet — only once a real connection happens (see listenOnPairingChannel's Connected
     *  branch) so backing out here never leaves a phantom "session" behind. */
    fun startAsInitiator() {
        resetForNewSession()
        val sessionId = UUID.randomUUID().toString()
        _uiState.update {
            it.copy(sessionId = sessionId, role = PartyRole.PARTY_A, qrToken = sessionId, pairingConnected = false)
        }
        listenOnPairingChannel(sessionId, isAdvertiser = true)
    }

    /** Party B: after scanning Party A's QR code, try to join that specific session.
     *  Same as above — nothing is written to the DB until a connection actually happens. */
    fun joinAsScanner(scannedSessionToken: String) {
        resetForNewSession()
        _uiState.update {
            it.copy(sessionId = scannedSessionToken, role = PartyRole.PARTY_B, qrToken = null, pairingConnected = false)
        }
        listenOnPairingChannel(scannedSessionToken, isAdvertiser = false)
    }

    /** Single-phone mode: both parties are physically present at this one device, so
     *  there's no pairing/QR step, no second device, and no partner Gemini call to wait
     *  on. The host (this device) is always the "party A" role and is always the one
     *  that runs Gemini structuring, exactly as the paired host flow does. Consent is
     *  a single tap here (see maybeBeginRecording — otherConsent is set true up-front
     *  since a second physical device confirming isn't part of this mode), which then
     *  takes the same ConsentScreen -> RecordingScreen -> ProcessingScreen path as the
     *  two-phone flow. */
    fun startSinglePhoneMode(otherPersonName: String) {
        resetForNewSession()
        val sessionId = UUID.randomUUID().toString()
        _uiState.update {
            it.copy(
                sessionId = sessionId,
                role = PartyRole.PARTY_A,
                qrToken = null,
                singlePhoneMode = true,
                pairingConnected = true,
                partnerName = otherPersonName.trim(),
                myConsent = false,
                otherConsent = true
            )
        }
        viewModelScope.launch {
            db.sessionDao().upsert(
                SessionEntity(
                    sessionId = sessionId,
                    partyAUserId = _uiState.value.myUserId,
                    partyBUserId = null,
                    partyBPhone = null,
                    partyAName = _uiState.value.myName,
                    partyBName = otherPersonName.trim(),
                    mode = SessionMode.IN_PERSON,
                    status = SessionStatus.PENDING_CONSENT,
                    startedAt = null,
                    endedAt = null,
                    stopRequestedBy = null,
                    stopConfirmedBy = null,
                    myRole = PartyRole.PARTY_A
                )
            )
        }
    }

    /** Call this when the user backs out of pairing/consent before a session is recorded
     *  (e.g. taps back from the QR screen, or from the consent screen before agreeing).
     *  Tears down any in-progress advertising/discovery and, if a session row was already
     *  created (because a connection did happen), deletes it — there's nothing worth
     *  keeping if no recording ever started. */
    fun cancelSessionSetup() {
        pairingJob?.cancel()
        pairing.teardown()
        val sessionId = _uiState.value.sessionId
        if (sessionId.isNotBlank()) {
            viewModelScope.launch {
                db.sessionDao().getById(sessionId)?.let { existing ->
                    if (existing.status == SessionStatus.PENDING_CONSENT) {
                        deleteSessionInternal(sessionId, existing)
                    }
                }
            }
        }
        _uiState.update {
            it.copy(
                sessionId = "", qrToken = null, pairingConnected = false, partnerName = null,
                myConsent = false, otherConsent = false, singlePhoneMode = false
            )
        }
    }

    fun clearPairingError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun listenOnPairingChannel(sessionToken: String, isAdvertiser: Boolean) {
        pairingJob?.cancel()
        pairingJob = viewModelScope.launch {
            pairing.events(sessionToken, isAdvertiser).collect { event ->
                when (event) {
                    is PairingEvent.Connected -> {
                        _uiState.update { it.copy(pairingConnected = true) }
                        persistNewSessionOnConnect()
                        pairing.send(SessionMessage.Hello(sessionToken, _uiState.value.myName, _uiState.value.myLanguage))
                    }
                    is PairingEvent.MessageReceived -> handleIncoming(event.message)
                    is PairingEvent.Disconnected -> _uiState.update { it.copy(pairingConnected = false) }
                    is PairingEvent.Error -> _uiState.update { it.copy(error = event.reason) }
                }
            }
        }
    }

    /** Writes the session row for the first time — only once a real Nearby connection
     *  has actually happened, so nothing is ever saved for an abandoned pairing attempt. */
    private fun persistNewSessionOnConnect() {
        viewModelScope.launch {
            val s = _uiState.value
            val entity = if (s.role == PartyRole.PARTY_A) {
                SessionEntity(
                    sessionId = s.sessionId,
                    partyAUserId = s.myUserId,
                    partyBUserId = null,
                    partyBPhone = null,
                    partyAName = s.myName,
                    partyBName = s.partnerName,
                    mode = SessionMode.IN_PERSON,
                    status = SessionStatus.PENDING_CONSENT,
                    startedAt = null,
                    endedAt = null,
                    stopRequestedBy = null,
                    stopConfirmedBy = null,
                    myRole = PartyRole.PARTY_A
                )
            } else {
                SessionEntity(
                    sessionId = s.sessionId,
                    partyAUserId = "",
                    partyBUserId = s.myUserId,
                    partyBPhone = null,
                    partyAName = s.partnerName ?: "",
                    partyBName = s.myName,
                    mode = SessionMode.IN_PERSON,
                    status = SessionStatus.PENDING_CONSENT,
                    startedAt = null,
                    endedAt = null,
                    stopRequestedBy = null,
                    stopConfirmedBy = null,
                    myRole = PartyRole.PARTY_B
                )
            }
            db.sessionDao().upsert(entity)
        }
    }

    private fun handleIncoming(message: SessionMessage) {
        when (message) {
            is SessionMessage.Hello -> {
                _uiState.update { it.copy(partnerName = message.name) }
                // Backfill the name into the DB row created at Connected time, in case
                // this Hello arrives after that row was written (it usually does).
                viewModelScope.launch {
                    db.sessionDao().getById(_uiState.value.sessionId)?.let { existing ->
                        val updated = if (_uiState.value.role == PartyRole.PARTY_A)
                            existing.copy(partyBName = message.name)
                        else
                            existing.copy(partyAName = message.name)
                        db.sessionDao().upsert(updated)
                    }
                }
            }
            is SessionMessage.ConsentStart -> {
                _uiState.update { it.copy(otherConsent = true) }
                maybeBeginRecording()
                logConsentEvent(ConsentAction.CONSENT_START, message.userId, message.timestamp)
            }
            is SessionMessage.StopRequest -> {
                _uiState.update { it.copy(stopRequestedByOtherName = _uiState.value.partnerName ?: "The other person") }
            }
            is SessionMessage.StopConfirm -> {
                stopTimeoutJob?.cancel()
                finishRecording(viaTimeout = false)
            }
            is SessionMessage.StopTimeoutOverride -> {
                // The other device already stopped unilaterally after 60s of silence from us.
                stopTimeoutJob?.cancel()
                finishRecording(viaTimeout = true)
            }
            is SessionMessage.DraftReady -> applyReceivedDraft(message)
            is SessionMessage.DraftFailed -> {
                _uiState.update { it.copy(processing = false, error = "The host's processing failed: ${message.reason}") }
            }
        }
    }

    /** Non-host (Party B) side of the single-Gemini-call flow: build this device's own
     *  StructuredDocumentEntity + CertificateEntity from the content the host already
     *  extracted, without ever calling Gemini itself. Uses this device's OWN transcript
     *  id, recording, and audio hash — only the drafted terms/text are shared, per
     *  spec 7.1 (each party keeps their own recording). */
    private fun applyReceivedDraft(message: SessionMessage.DraftReady) {
        viewModelScope.launch {
            val sessionId = _uiState.value.sessionId
            val recordingId = currentRecordingId ?: db.audioRecordingDao().forSession(sessionId)?.recordingId
            val transcript = recordingId?.let { db.transcriptDao().forRecording(it).firstOrNull() }
            val recording = recordingId?.let { db.audioRecordingDao().forSession(sessionId) }
            if (recordingId == null || transcript == null || recording == null) {
                _uiState.update { it.copy(processing = false, error = "Could not save the received draft — local recording is missing.") }
                return@launch
            }

            val s = _uiState.value
            val documentId = UUID.randomUUID().toString()
            db.structuredDocumentDao().upsert(
                StructuredDocumentEntity(
                    documentId = documentId,
                    sessionId = sessionId,
                    transcriptId = transcript.transcriptId,
                    partyAName = s.partnerName ?: "Party A",
                    partyBName = s.myName,
                    agreementType = runCatching { AgreementType.valueOf(message.agreementType.uppercase()) }.getOrDefault(AgreementType.OTHER),
                    amount = message.amount,
                    currency = message.currency,
                    termsJson = message.termsJson,
                    conditions = message.conditions,
                    unclearItemsJson = message.unclearItemsJson,
                    dateOfConversation = message.dateOfConversation,
                    generatedAt = System.currentTimeMillis(),
                    modelUsed = message.modelUsed
                )
            )
            db.certificateDao().upsert(
                CertificateEntity(
                    certificateId = UUID.randomUUID().toString(),
                    documentId = documentId,
                    audioSha256 = recording.sha256Hash,
                    transcriptSha256 = com.pakkabaat.app.util.HashUtil.sha256Text(transcript.rawText),
                    deviceMetadataJson = Gson().toJson(
                        mapOf(
                            "device" to android.os.Build.MODEL,
                            "androidVersion" to android.os.Build.VERSION.RELEASE,
                            "processedAt" to System.currentTimeMillis(),
                            "asrEngine" to "whisper.cpp (on-device)",
                            "structuring" to "received from host device"
                        )
                    ),
                    generatedAt = System.currentTimeMillis(),
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
            // observeDocumentReady (already running from finishRecording) picks this up
            // via the DB flow and flips processing=false itself.
        }
    }

    // ---------- Mutual consent to start (spec 8.3) ----------

    fun giveConsentToStart() {
        _uiState.update { it.copy(myConsent = true) }
        val ts = System.currentTimeMillis()
        pairing.send(SessionMessage.ConsentStart(_uiState.value.myUserId, ts))
        logConsentEvent(ConsentAction.CONSENT_START, _uiState.value.myUserId, ts)
        maybeBeginRecording()
    }

    private fun maybeBeginRecording() {
        val s = _uiState.value
        if (s.myConsent && s.otherConsent && !s.isRecording) {
            beginRecording()
        }
    }

    private fun logConsentEvent(action: ConsentAction, userId: String, ts: Long) {
        viewModelScope.launch {
            db.consentEventDao().insert(ConsentEventEntity(sessionId = _uiState.value.sessionId, userId = userId, action = action, timestamp = ts))
        }
    }

    // ---------- Recording (spec 8.4) ----------

    private fun beginRecording() {
        recorder.start(_uiState.value.sessionId)
        _uiState.update { it.copy(isRecording = true, elapsedSeconds = 0) }
        viewModelScope.launch {
            db.sessionDao().getById(_uiState.value.sessionId)?.let {
                db.sessionDao().upsert(it.copy(status = SessionStatus.RECORDING, startedAt = System.currentTimeMillis()))
            }
        }
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(1000)
                _uiState.update { it.copy(elapsedSeconds = it.elapsedSeconds + 1) }
            }
        }
    }

    // ---------- Mutual consent to stop, with 60s timeout (spec 8.5) ----------

    fun requestStop() {
        val ts = System.currentTimeMillis()
        // Single-phone mode has no second device to ever send back a StopConfirm, so the
        // normal mutual-consent handshake below would just wait the full 60s and then
        // auto-stop via the timeout path every single time — pressing Stop should end the
        // recording immediately instead, same as the back-press "End recording" action.
        if (_uiState.value.singlePhoneMode) {
            logConsentEvent(ConsentAction.CONSENT_STOP, _uiState.value.myUserId, ts)
            finishRecording(viaTimeout = false)
            return
        }

        _uiState.update { it.copy(stopRequestedByMe = true) }
        pairing.send(SessionMessage.StopRequest(_uiState.value.myUserId, ts))
        logConsentEvent(ConsentAction.CONSENT_STOP, _uiState.value.myUserId, ts)

        stopTimeoutJob?.cancel()
        stopTimeoutJob = viewModelScope.launch {
            stopTimeoutCountdown().collect { remaining ->
                _uiState.update { it.copy(stopTimeoutRemaining = remaining) }
                if (remaining == 0) {
                    val overrideTs = System.currentTimeMillis()
                    logConsentEvent(ConsentAction.TIMEOUT_OVERRIDE, _uiState.value.myUserId, overrideTs)
                    pairing.send(SessionMessage.StopTimeoutOverride(_uiState.value.myUserId, overrideTs))
                    finishRecording(viaTimeout = true)
                }
            }
        }
    }

    /** The other party requested stop, and I'm confirming it. */
    fun confirmStopRequestedByOther() {
        val ts = System.currentTimeMillis()
        pairing.send(SessionMessage.StopConfirm(_uiState.value.myUserId, ts))
        logConsentEvent(ConsentAction.CONSENT_STOP, _uiState.value.myUserId, ts)
        finishRecording(viaTimeout = false)
    }

    private fun finishRecording(viaTimeout: Boolean) {
        if (!_uiState.value.isRecording) return
        tickerJob?.cancel()
        stopTimeoutJob?.cancel()
        _uiState.update { it.copy(isRecording = false, stopViaTimeout = viaTimeout, stopTimeoutRemaining = null) }

        viewModelScope.launch {
            val result = recorder.stop()
            val recordingId = UUID.randomUUID().toString()
            currentRecordingId = recordingId
            val sessionId = _uiState.value.sessionId
            val language = _uiState.value.myLanguage

            val recordingEntity = AudioRecordingEntity(
                recordingId = recordingId,
                sessionId = sessionId,
                storagePath = result.file.absolutePath,
                durationSeconds = result.durationSeconds,
                sha256Hash = result.sha256,
                languageDetected = language,
                uploadStatus = UploadStatus.LOCAL_ONLY
            )
            db.audioRecordingDao().upsert(recordingEntity)
            // Kept on disk (not deleted) as the proof-of-recording backing this draft —
            // see DocumentScreen's "Proof details", which now offers it for playback/share
            // alongside the hash, not just the hash on its own.
            _uiState.update { it.copy(audioRecording = recordingEntity) }
            db.sessionDao().getById(sessionId)?.let {
                db.sessionDao().upsert(
                    it.copy(
                        status = SessionStatus.STOPPED,
                        endedAt = System.currentTimeMillis(),
                        stopViaTimeout = viaTimeout
                    )
                )
            }

            // Speech-to-text, fully on-device via whisper.cpp — no signal needed (spec 6.4/8.6).
            // This is now the ONE and only transcript; there's no separate cloud ASR pass anymore.
            _uiState.update { it.copy(processing = true, processingStage = ProcessingStage.TRANSCRIBING) }
            val transcriptResult = onDeviceTranscriber.transcribe(result.file, language)
            val transcriptId = UUID.randomUUID().toString()
            db.transcriptDao().upsert(
                TranscriptEntity(
                    transcriptId = transcriptId,
                    recordingId = recordingId,
                    rawText = transcriptResult.text,
                    language = language,
                    source = TranscriptSource.ON_DEVICE_DRAFT,
                    confidence = null
                )
            )
            _uiState.update { it.copy(draftTranscript = transcriptResult.text, draftIsPlaceholder = transcriptResult.isPlaceholder) }

            val online = ConnectivityUtil.isOnline(getApplication())
            _uiState.update { it.copy(isOnline = online) }
            val s = _uiState.value
            val isHost = s.singlePhoneMode || s.role == PartyRole.PARTY_A
            val partyAName = if (s.role == PartyRole.PARTY_A) s.myName else (s.partnerName ?: "Party A")
            val partyBName = if (s.role == PartyRole.PARTY_B) s.myName else (s.partnerName ?: "Party B")

            // Only ever describes THIS device — the host is always Party A's phone, so
            // label it by who's holding it (their name), which is what actually
            // answers "which phone is doing this" for the person staring at the screen.
            val hostLabel = if (s.singlePhoneMode) "${s.myName}'s phone (this device)" else "${partyAName}'s phone"
            _uiState.update {
                it.copy(
                    processingDeviceLabel = hostLabel,
                    processingStage = if (isHost) ProcessingStage.STRUCTURING else ProcessingStage.WAITING_FOR_HOST
                )
            }

            if (isHost) {
                // Only the host calls Gemini. Previously BOTH phones independently
                // structured the same transcript, doubling Gemini API usage/credits for
                // every session for no benefit — the host now does the one call and
                // hands the finished draft to the other phone over the same Nearby
                // link once it's done (see observeDocumentReady below).
                CloudProcessingWorker.enqueue(
                    context = getApplication(),
                    sessionId = sessionId,
                    recordingId = recordingId,
                    transcriptId = transcriptId,
                    language = language,
                    languageName = LanguageCatalog.nameFor(language),
                    partyAName = partyAName,
                    partyBName = partyBName
                )
                observeHostWorkFailure(sessionId)
            }
            // Non-host: nothing to enqueue — just wait. Either the DB flow below picks
            // up a document (shouldn't normally happen on this side, but loadExistingSession
            // reuses the same path) or applyReceivedDraft() writes one in as soon as the
            // host's SessionMessage.DraftReady arrives (see handleIncoming).

            observeDocumentReady(sessionId)
        }
    }

    /** Host-only: if the Gemini call ultimately fails (bad/missing key, no signal after
     *  retries, parse failure, etc.), surface it in this device's UI AND tell the
     *  partner device so its ProcessingScreen doesn't spin forever waiting for a draft
     *  that's never coming. */
    private fun observeHostWorkFailure(sessionId: String) {
        viewModelScope.launch {
            WorkManager.getInstance(getApplication<Application>())
                .getWorkInfosForUniqueWorkFlow(CloudProcessingWorker.workName(sessionId))
                .collect { infos ->
                    val info = infos.firstOrNull() ?: return@collect
                    if (info.state == WorkInfo.State.FAILED) {
                        val reason = info.outputData.getString(CloudProcessingWorker.KEY_ERROR)
                            ?: "Processing failed after several attempts."
                        _uiState.update { it.copy(processing = false, error = reason) }
                        pairing.send(SessionMessage.DraftFailed(reason))
                    }
                }
        }
    }

    private var draftSentForSession: String? = null

    private fun observeDocumentReady(sessionId: String) {
        documentObserverJob?.cancel()
        documentObserverJob = viewModelScope.launch {
            db.structuredDocumentDao().observeForSession(sessionId).collect { doc ->
                if (doc != null) {
                    val cert = db.certificateDao().forDocument(doc.documentId)
                    _uiState.update { it.copy(document = doc, certificate = cert, processing = false, processingStage = ProcessingStage.DONE, error = null) }

                    // Host, paired (not single-phone): hand the finished draft to the
                    // partner device so it never has to call Gemini itself. Guarded so a
                    // later DB emission for the same session (e.g. re-collecting after
                    // loadExistingSession) doesn't resend.
                    val s = _uiState.value
                    val isHost = !s.singlePhoneMode && s.role == PartyRole.PARTY_A
                    if (isHost && draftSentForSession != sessionId) {
                        draftSentForSession = sessionId
                        pairing.send(
                            SessionMessage.DraftReady(
                                agreementType = doc.agreementType.name,
                                amount = doc.amount,
                                currency = doc.currency,
                                termsJson = doc.termsJson,
                                conditions = doc.conditions,
                                unclearItemsJson = doc.unclearItemsJson,
                                dateOfConversation = doc.dateOfConversation,
                                modelUsed = doc.modelUsed
                            )
                        )
                    }
                }
            }
        }
    }

    fun loadExistingSession(sessionId: String) {
        viewModelScope.launch {
            val doc = db.structuredDocumentDao().forSession(sessionId)
            val cert = doc?.let { db.certificateDao().forDocument(it.documentId) }
            val recording = db.audioRecordingDao().forSession(sessionId)
            _uiState.update { it.copy(sessionId = sessionId, document = doc, certificate = cert, audioRecording = recording) }
            if (doc == null) observeDocumentReady(sessionId)
        }
    }

    /** Deletes a session and everything under it - recording file, transcript, document,
     *  certificate, consent log. Works regardless of what state the session got stuck in,
     *  which covers both phantom/never-connected attempts and ones that connected but
     *  never actually recorded anything. */
    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            val session = db.sessionDao().getById(sessionId) ?: return@launch
            deleteSessionInternal(sessionId, session)
        }
    }

    private suspend fun deleteSessionInternal(sessionId: String, @Suppress("UNUSED_PARAMETER") session: SessionEntity) {
        // Delete the actual audio file(s) from disk, not just the DB rows.
        val recordings = db.audioRecordingDao().allForSession(sessionId)
        recordings.forEach { recording ->
            db.transcriptDao().deleteForRecording(recording.recordingId)
            File(recording.storagePath).delete()
        }
        db.audioRecordingDao().deleteForSession(sessionId)

        db.structuredDocumentDao().forSession(sessionId)?.let { doc ->
            db.certificateDao().deleteForDocument(doc.documentId)
        }
        db.structuredDocumentDao().deleteForSession(sessionId)

        db.consentEventDao().deleteForSession(sessionId)
        db.sessionDao().deleteById(sessionId)
    }

    override fun onCleared() {
        super.onCleared()
        pairing.teardown()
        recorder.cancel()
    }
}
