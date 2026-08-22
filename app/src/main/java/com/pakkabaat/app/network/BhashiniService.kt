package com.pakkabaat.app.network

import android.util.Base64
import com.pakkabaat.app.BuildConfig
import java.io.File

class BhashiniService(private val api: BhashiniApiService = NetworkModule.bhashiniApi) {

    /**
     * Runs the full Bhashini ASR pipeline (spec section 6.3/8.7) on a 16kHz mono WAV file
     * and returns the recognized text. [sourceLanguage] is an ISO-639 code, e.g. "hi", "en",
     * "mr", "ta" — one of Bhashini's 22 supported Indian languages.
     */
    suspend fun transcribe(wavFile: File, sourceLanguage: String): String {
        check(BuildConfig.BHASHINI_USER_ID.isNotBlank() && BuildConfig.BHASHINI_API_KEY.isNotBlank()) {
            "Bhashini credentials are not set. Add PAKKABAAT_BHASHINI_USER_ID / " +
                "PAKKABAAT_BHASHINI_API_KEY to gradle.properties (see README)."
        }

        val configResponse = api.getPipelineConfig(
            userId = BuildConfig.BHASHINI_USER_ID,
            apiKey = BuildConfig.BHASHINI_API_KEY,
            body = PipelineConfigRequest(
                pipelineTasks = listOf(
                    PipelineTask(taskType = "asr", config = TaskConfig(language = LanguagePair(sourceLanguage)))
                ),
                pipelineRequestConfig = PipelineRequestConfig(pipelineId = BuildConfig.BHASHINI_PIPELINE_ID)
            )
        )

        val serviceId = configResponse.pipelineResponseConfig
            ?.firstOrNull { it.taskType == "asr" }
            ?.config?.firstOrNull()?.serviceId
            ?: error("Bhashini did not return an ASR serviceId for language '$sourceLanguage'. " +
                "That language/pipeline combination may not be supported — check the Pipeline " +
                "Search call in the Bhashini docs.")

        val endpoint = configResponse.pipelineInferenceAPIEndPoint
            ?: error("Bhashini config response did not include a compute endpoint.")

        val audioBytes = wavFile.readBytes()
        val base64Audio = Base64.encodeToString(audioBytes, Base64.NO_WRAP)

        val computeResponse = api.compute(
            url = endpoint.callbackUrl,
            headers = mapOf(endpoint.inferenceApiKey.name to endpoint.inferenceApiKey.value),
            body = ComputeRequest(
                pipelineTasks = listOf(
                    PipelineTask(
                        taskType = "asr",
                        config = TaskConfig(
                            language = LanguagePair(sourceLanguage),
                            serviceId = serviceId,
                            audioFormat = "wav",
                            samplingRate = 16000
                        )
                    )
                ),
                inputData = InputData(audio = listOf(AudioContent(audioContent = base64Audio)))
            )
        )

        return computeResponse.pipelineResponse
            ?.firstOrNull { it.taskType == "asr" }
            ?.output?.firstOrNull()?.source
            ?: error("Bhashini returned no transcript text.")
    }
}
