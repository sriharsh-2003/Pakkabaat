package com.pakkabaat.app.recording

import android.content.Context
import kotlinx.coroutines.delay
import java.io.File

/**
 * Produces the rough, on-the-spot "Draft — not yet verified" transcript that must work
 * with zero signal (spec sections 6.3, 6.4, 8.6). This is intentionally an interface:
 * swap the implementation without touching any other layer of the app.
 *
 * WHAT TO PLUG IN FOR PRODUCTION (this is the one piece this build does not fully wire up,
 * because it needs native model binaries that can't be fetched/compiled in this environment):
 *
 *   Option 1 — whisper.cpp (matches the spec exactly):
 *     1. Add the whisper.android sample's JNI module (github.com/ggerganov/whisper.cpp,
 *        under examples/whisper.android) as a Gradle module, or use a maintained AAR wrapper.
 *     2. Ship a quantized tiny/base multilingual model (ggml-tiny.bin or similar, a few MB
 *        to ~150MB) as an asset or download it on first run over Wi-Fi.
 *     3. Implement `transcribe()` below to call the native `whisper_full()` binding on the
 *        recorded file's PCM samples and return the text it produces.
 *
 *   Option 2 — Vosk-Android (github.com/alphacep/vosk-api, Android module):
 *     Lighter to integrate than whisper.cpp, has a small Hindi model, API accepts raw
 *     audio directly. Good first swap-in if whisper.cpp's NDK setup is more than you need.
 *
 * Until one of those is wired in, this stub returns a clearly-labeled placeholder so the
 * rest of the pipeline (hashing, cloud structuring, document display) is fully exercised.
 */
interface OnDeviceTranscriber {
    suspend fun transcribe(audioFile: File, languageHint: String): DraftTranscriptResult
}

data class DraftTranscriptResult(
    val text: String,
    val isPlaceholder: Boolean
)

class StubOnDeviceTranscriber(@Suppress("UNUSED_PARAMETER") context: Context) : OnDeviceTranscriber {
    override suspend fun transcribe(audioFile: File, languageHint: String): DraftTranscriptResult {
        // Simulate the near-instant on-device pass. Replace this whole body once
        // whisper.cpp / Vosk is wired in — the pipeline around it needs no other changes.
        delay(400)
        return DraftTranscriptResult(
            text = "(On-device draft engine not yet wired into this build — see " +
                "OnDeviceTranscriber.kt for whisper.cpp/Vosk integration steps. " +
                "Your audio was recorded and hashed correctly; the final document " +
                "below is produced by the cloud pipeline, which is fully wired up.)",
            isPlaceholder = true
        )
    }
}
