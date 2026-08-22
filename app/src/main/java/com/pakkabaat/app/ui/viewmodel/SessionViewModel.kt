package com.pakkabaat.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pakkabaat.app.consent.stopTimeoutCountdown
import com.pakkabaat.app.data.db.*
import com.pakkabaat.app.data.model.*
import com.pakkabaat.app.data.repository.OtpService
import com.pakkabaat.app.pairing.NearbyPairingManager
import com.pakkabaat.app.pairing.PairingEvent
import com.pakkabaat.app.pairing.SessionMessage
import com.pakkabaat.app.recording.AudioRecorderManager
import com.pakkabaat.app.recording.CloudProcessingWorker
import com.pakkabaat.app.recording.OnDeviceTranscriber
import com.pakkabaat.app.recording.StubOnDeviceTranscriber
import com.pakkabaat.app.util.ConnectivityUtil
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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
    val document: StructuredDocumentEntity? = null,
    val certificate: CertificateEntity? = null,
    val error: String? = null
)

class SessionViewModel(application: Application) : AndroidViewModel(application) {

    private val db = PakkaBaatDatabase.getInstance(application)
    private val pairing = NearbyPairingManager(application)
    private val recorder = AudioRecorderManager(application)
    private val onDeviceTranscriber: OnDeviceTranscriber = StubOnDeviceTranscriber(application)
    val otpService = OtpService()

    private val _uiState = MutableStateFlow(SessionUiState())
    val uiState: StateFlow<SessionUiState> = _uiState.asStateFlow()

    val pastSessions: Flow<List<SessionEntity>> = db.sessionDao().observeAll()

    private var pairingJob: Job? = null
    private var tickerJob: Job? = null
    private var stopTimeoutJob: Job? = null
    private var currentRecordingId: String? = null

    // ---------- Onboarding (spec 8.1) ----------

    fun setMyIdentity(userId: String, name: String, language: String) {
        _uiState.update { it.copy(myUserId = userId, myName = name, myLanguage = language) }
    }

    // ---------- Starting a session, in-person mode (spec 8.2) ----------

    /** Party A: create a new session and start advertising nearby for Party B to scan+join. */
    fun startAsInitiator() {
        val sessionId = UUID.randomUUID().toString()
        _uiState.update {
            it.copy(sessionId = sessionId, role = PartyRole.PARTY_A, qrToken = sessionId, pairingConnected = false)
        }
        viewModelScope.launch {
            db.sessionDao().upsert(
                SessionEntity(
                    sessionId = sessionId,
                    partyAUserId = _uiState.value.myUserId,
                    partyBUserId = null,
                    partyBPhone = null,
                    partyAName = _uiState.value.myName,
                    partyBName = null,
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
        listenOnPairingChannel(sessionId, isAdvertiser = true)
    }

    /** Party B: after scanning Party A's QR code, join that specific session. */
    fun joinAsScanner(scannedSessionToken: String) {
        _uiState.update {
            it.copy(sessionId = scannedSessionToken, role = PartyRole.PARTY_B, qrToken = null, pairingConnected = false)
        }
        viewModelScope.launch {
            db.sessionDao().upsert(
                SessionEntity(
                    sessionId = scannedSessionToken,
                    partyAUserId = "",              // learned once Hello arrives; fine for local bookkeeping
                    partyBUserId = _uiState.value.myUserId,
                    partyBPhone = null,
                    partyAName = "",
                    partyBName = _uiState.value.myName,
                    mode = SessionMode.IN_PERSON,
                    status = SessionStatus.PENDING_CONSENT,
                    startedAt = null,
                    endedAt = null,
                    stopRequestedBy = null,
                    stopConfirmedBy = null,
                    myRole = PartyRole.PARTY_B
                )
            )
        }
        listenOnPairingChannel(scannedSessionToken, isAdvertiser = false)
    }

    private fun listenOnPairingChannel(sessionToken: String, isAdvertiser: Boolean) {
        pairingJob?.cancel()
        pairingJob = viewModelScope.launch {
            pairing.events(sessionToken, isAdvertiser).collect { event ->
                when (event) {
                    is PairingEvent.Connected -> {
                        _uiState.update { it.copy(pairingConnected = true) }
                        pairing.send(SessionMessage.Hello(sessionToken, _uiState.value.myName, _uiState.value.myLanguage))
                    }
                    is PairingEvent.MessageReceived -> handleIncoming(event.message)
                    is PairingEvent.Disconnected -> _uiState.update { it.copy(pairingConnected = false) }
                    is PairingEvent.Error -> _uiState.update { it.copy(error = event.reason) }
                }
            }
        }
    }

    private fun handleIncoming(message: SessionMessage) {
        when (message) {
            is SessionMessage.Hello -> _uiState.update { it.copy(partnerName = message.name) }
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

            db.audioRecordingDao().upsert(
                AudioRecordingEntity(
                    recordingId = recordingId,
                    sessionId = sessionId,
                    storagePath = result.file.absolutePath,
                    durationSeconds = result.durationSeconds,
                    sha256Hash = result.sha256,
                    languageDetected = language,
                    uploadStatus = UploadStatus.LOCAL_ONLY
                )
            )
            db.sessionDao().getById(sessionId)?.let {
                db.sessionDao().upsert(
                    it.copy(
                        status = SessionStatus.STOPPED,
                        endedAt = System.currentTimeMillis(),
                        stopViaTimeout = viaTimeout
                    )
                )
            }

            // On-device draft — must appear instantly, with zero signal (spec 6.4/8.6).
            _uiState.update { it.copy(processing = true) }
            val draft = onDeviceTranscriber.transcribe(result.file, language)
            db.transcriptDao().upsert(
                TranscriptEntity(
                    transcriptId = UUID.randomUUID().toString(),
                    recordingId = recordingId,
                    rawText = draft.text,
                    language = language,
                    source = TranscriptSource.ON_DEVICE_DRAFT,
                    confidence = null
                )
            )
            _uiState.update { it.copy(draftTranscript = draft.text, draftIsPlaceholder = draft.isPlaceholder) }

            // Queue the real document. Runs now if online, or the moment signal returns
            // if not — WorkManager's NetworkType.CONNECTED constraint handles both (spec 6.4/8.7).
            val online = ConnectivityUtil.isOnline(getApplication())
            _uiState.update { it.copy(isOnline = online) }
            val s = _uiState.value
            CloudProcessingWorker.enqueue(
                context = getApplication(),
                sessionId = sessionId,
                recordingId = recordingId,
                audioPath = result.file.absolutePath,
                language = language,
                languageName = LanguageCatalog.nameFor(language),
                partyAName = if (s.role == PartyRole.PARTY_A) s.myName else (s.partnerName ?: "Party A"),
                partyBName = if (s.role == PartyRole.PARTY_B) s.myName else (s.partnerName ?: "Party B")
            )

            observeDocumentReady(sessionId)
        }
    }

    private fun observeDocumentReady(sessionId: String) {
        viewModelScope.launch {
            db.structuredDocumentDao().observeForSession(sessionId).collect { doc ->
                if (doc != null) {
                    val cert = db.certificateDao().forDocument(doc.documentId)
                    _uiState.update { it.copy(document = doc, certificate = cert, processing = false) }
                }
            }
        }
    }

    fun loadExistingSession(sessionId: String) {
        viewModelScope.launch {
            val doc = db.structuredDocumentDao().forSession(sessionId)
            val cert = doc?.let { db.certificateDao().forDocument(it.documentId) }
            _uiState.update { it.copy(sessionId = sessionId, document = doc, certificate = cert) }
            if (doc == null) observeDocumentReady(sessionId)
        }
    }

    override fun onCleared() {
        super.onCleared()
        pairing.teardown()
        recorder.cancel()
    }
}
