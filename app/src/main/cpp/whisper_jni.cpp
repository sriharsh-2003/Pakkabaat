#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include "whisper.h"

#define LOG_TAG "PakkaBaatWhisper"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {

// Loads the ggml model file once; the returned pointer is kept by the Kotlin
// side and passed back into nativeTranscribe/nativeFree. One instance per
// recording session is plenty — don't reload the model per utterance.
JNIEXPORT jlong JNICALL
Java_com_pakkabaat_app_recording_WhisperCppTranscriber_nativeInit(
        JNIEnv *env, jobject /* this */, jstring modelPath) {
    const char *path = env->GetStringUTFChars(modelPath, nullptr);

    struct whisper_context_params cparams = whisper_context_default_params();
    struct whisper_context *ctx = whisper_init_from_file_with_params(path, cparams);

    env->ReleaseStringUTFChars(modelPath, path);

    if (ctx == nullptr) {
        LOGE("Failed to load whisper model — check the path and that the file isn't corrupt/truncated");
        return 0;
    }
    return reinterpret_cast<jlong>(ctx);
}

// samples: mono float32 PCM at 16kHz, normalized to [-1, 1] — exactly what
// WavPcmReader.kt produces from PakkaBaat's own WAV recordings, so no
// resampling/conversion is needed on either side of this call.
// language: an ISO code like "hi", "en", "mr" — or "" to let Whisper auto-detect.
// enableDiarization: when true and the loaded model supports tinydiarize
// (a "-tdrz" model file), inserts a "[speaker change]" marker at each
// detected speaker turn. Silently ignored on non-tdrz models.
JNIEXPORT jstring JNICALL
Java_com_pakkabaat_app_recording_WhisperCppTranscriber_nativeTranscribe(
        JNIEnv *env, jobject /* this */, jlong ctxPtr, jfloatArray samples,
        jstring language, jboolean enableDiarization, jint nThreads) {

    auto *ctx = reinterpret_cast<struct whisper_context *>(ctxPtr);
    if (ctx == nullptr) {
        return env->NewStringUTF("");
    }

    jsize n_samples = env->GetArrayLength(samples);
    std::vector<float> pcm(n_samples);
    env->GetFloatArrayRegion(samples, 0, n_samples, pcm.data());

    const char *lang = env->GetStringUTFChars(language, nullptr);
    bool hasLang = lang != nullptr && lang[0] != '\0';

    struct whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_progress   = false;
    params.print_realtime   = false;
    params.print_timestamps = false;
    params.translate        = false; // keep the original language, never auto-translate to English
    params.language         = hasLang ? lang : nullptr; // nullptr = auto-detect
    // Was hardcoded to 4 regardless of device — on a 6/8-core mid-range phone that
    // left cores idle and was a big chunk of "why is this so slow". Kotlin now passes
    // Runtime.getRuntime().availableProcessors() (minus a little headroom), so this
    // scales to the actual device instead of leaving performance on the table.
    params.n_threads        = nThreads > 0 ? nThreads : 4;
    params.tdrz_enable      = enableDiarization;

    int result = whisper_full(ctx, params, pcm.data(), n_samples);
    env->ReleaseStringUTFChars(language, lang);

    if (result != 0) {
        LOGE("whisper_full failed with code %d", result);
        return env->NewStringUTF("");
    }

    std::string output;
    int n_segments = whisper_full_n_segments(ctx);
    for (int i = 0; i < n_segments; i++) {
        output += whisper_full_get_segment_text(ctx, i);
        if (enableDiarization && whisper_full_get_segment_speaker_turn_next(ctx, i)) {
            output += " [speaker change] ";
        }
    }

    return env->NewStringUTF(output.c_str());
}

JNIEXPORT void JNICALL
Java_com_pakkabaat_app_recording_WhisperCppTranscriber_nativeFree(
        JNIEnv * /* env */, jobject /* this */, jlong ctxPtr) {
    auto *ctx = reinterpret_cast<struct whisper_context *>(ctxPtr);
    if (ctx != nullptr) {
        whisper_free(ctx);
    }
}

} // extern "C"
