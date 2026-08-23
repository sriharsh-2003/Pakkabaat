package com.pakkabaat.app.data.model

/** Matches spec section 9 — Session.mode */
enum class SessionMode { IN_PERSON, APP_CALL, BRIDGE_CALL }

/** Matches spec section 9 — Session.status */
enum class SessionStatus { PENDING_CONSENT, RECORDING, STOPPED, PROCESSING, COMPLETED, FAILED }

/** Matches spec section 9 — ConsentEvent.action */
enum class ConsentAction { CONSENT_START, CONSENT_STOP, TIMEOUT_OVERRIDE }

/** Matches spec section 9 — AudioRecording.upload_status */
enum class UploadStatus { LOCAL_ONLY, QUEUED, UPLOADED, FAILED }

/** Matches spec section 9 — Transcript.source */
enum class TranscriptSource { ON_DEVICE_DRAFT, CLOUD_FINAL }

/** Matches spec section 9 — StructuredDocument.agreement_type */
enum class AgreementType { LOAN, RENT, WAGE, SALE, OTHER, UNDETERMINED }

/** Which of the two people this device belongs to, for a given session. */
enum class PartyRole { PARTY_A, PARTY_B }

/** Drives the progress bar on ProcessingScreen so the user sees what's actually
 *  happening instead of one generic spinner for the whole draft wait. */
enum class ProcessingStage { TRANSCRIBING, STRUCTURING, WAITING_FOR_HOST, DONE }
