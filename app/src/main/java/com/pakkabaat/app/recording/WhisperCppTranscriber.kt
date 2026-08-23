package com.pakkabaat.app.recording

import android.content.Context
import com.pakkabaat.app.util.WavPcmReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Real, fully-offline speech-to-text via whisper.cpp (replaces both the old on-device
 * draft stub AND the Bhashini cloud call — this is now the only ASR step, local-asr branch).
 *
 * SETUP YOU STILL NEED TO DO (native compilation needs an NDK + internet, neither of
 * which this sandbox has):
 *   1. git submodule add https://github.com/ggml-org/whisper.cpp app/src/main/cpp/whisper.cpp
 *      git submodule update --init --recursive
 *   2. Download a multilingual ggml model, e.g.:
 *      curl -L -o app/src/main/assets/models/ggml-base-q5_1.bin \
 *        https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base-q5_1.bin
 *      (~57MB — see MODEL_FILENAME below if you pick a different size/model)
 *   3. Build normally in Android Studio — the CMake script in app/src/main/cpp/
 *      picks up the submodule automatically.
 *
 * To turn on speaker-turn marking (tinydiarize), swap in a "-tdrz" model file instead
 * (e.g. ggml-base-tdrz.bin) and set ENABLE_DIARIZATION = true below.
 */
class WhisperCppTranscriber(private val context: Context) : OnDeviceTranscriber {

    companion object {
        private const val MODEL_FILENAME = "ggml-base-q5_1.bin" // ~57MB, multilingual
        private const val ENABLE_DIARIZATION = false // set true once using a "-tdrz" model

        init {
            System.loadLibrary("whisper_jni")
        }
    }

    private external fun nativeInit(modelPath: String): Long
    private external fun nativeTranscribe(ctxPtr: Long, samples: FloatArray, language: String, enableDiarization: Boolean, nThreads: Int): String
    private external fun nativeFree(ctxPtr: Long)

    override suspend fun transcribe(audioFile: File, languageHint: String): DraftTranscriptResult = withContext(Dispatchers.Default) {
        val modelFile = ensureModelOnDisk()
        val ctxPtr = nativeInit(modelFile.absolutePath)
        if (ctxPtr == 0L) {
            return@withContext DraftTranscriptResult(
                text = "(Could not load the on-device speech model — check that " +
                    "$MODEL_FILENAME was bundled correctly under assets/models/.)",
                isPlaceholder = true
            )
        }
        try {
            val samples = WavPcmReader.readAsFloatPcm(audioFile)
            // Leave one core free for the UI/audio threads instead of pegging every
            // core — on an 8-core phone that's 7 threads instead of the old fixed 4,
            // which is the single biggest lever on transcription wall-clock time.
            val threads = (Runtime.getRuntime().availableProcessors() - 1).coerceIn(2, 8)
            val text = nativeTranscribe(ctxPtr, samples, languageHint, ENABLE_DIARIZATION, threads)
            DraftTranscriptResult(text = text.ifBlank { "(No speech detected)" }, isPlaceholder = false)
        } finally {
            nativeFree(ctxPtr)
        }
    }

    /** Copies the model out of assets into internal storage once, since native file I/O needs a real path. */
    private fun ensureModelOnDisk(): File {
        val modelsDir = File(context.filesDir, "models").apply { mkdirs() }
        val outFile = File(modelsDir, MODEL_FILENAME)
        if (!outFile.exists()) {
            context.assets.open("models/$MODEL_FILENAME").use { input ->
                outFile.outputStream().use { output -> input.copyTo(output) }
            }
        }
        return outFile
    }
}
