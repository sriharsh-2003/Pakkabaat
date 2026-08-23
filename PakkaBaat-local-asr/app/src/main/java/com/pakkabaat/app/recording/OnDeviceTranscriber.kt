package com.pakkabaat.app.recording

import java.io.File

/**
 * Produces the transcript entirely offline (spec sections 6.3, 6.4, 8.6). On this
 * branch, this is the ONLY transcription step — Bhashini's cloud ASR has been removed
 * entirely, and whisper.cpp (see WhisperCppTranscriber) handles both what used to be
 * the "instant draft" and the "final" transcript. Only the structuring step (Gemini)
 * still needs a network connection.
 */
interface OnDeviceTranscriber {
    suspend fun transcribe(audioFile: File, languageHint: String): DraftTranscriptResult
}

data class DraftTranscriptResult(
    val text: String,
    val isPlaceholder: Boolean
)
