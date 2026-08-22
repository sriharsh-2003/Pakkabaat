package com.pakkabaat.app.network

import retrofit2.http.*

/*
 * Bhashini (spec sections 6.3 / 8.7 / 12) is a two-step ULCA pipeline:
 *   1. "Config" call -> tells you which serviceId to use and where to send audio (callbackUrl).
 *   2. "Compute" call -> sent to that callbackUrl, with the actual base64 audio, returns text.
 *
 * Shape confirmed against Bhashini's own API docs (bhashini.gitbook.io/bhashini-apis) at the
 * time this was written. Government APIs do change — if a call starts failing, check that
 * gitbook first; the request/response field names below are the ones to diff against.
 */

// ---- Step 1: Pipeline Config call ----

data class PipelineConfigRequest(
    val pipelineTasks: List<PipelineTask>,
    val pipelineRequestConfig: PipelineRequestConfig
)

data class PipelineTask(
    val taskType: String,             // "asr"
    val config: TaskConfig
)

data class TaskConfig(
    val language: LanguagePair,
    val serviceId: String? = null,
    val audioFormat: String? = null,
    val samplingRate: Int? = null
)

data class LanguagePair(
    val sourceLanguage: String,
    val targetLanguage: String? = null
)

data class PipelineRequestConfig(val pipelineId: String)

data class PipelineConfigResponse(
    val pipelineResponseConfig: List<PipelineResponseConfigItem>?,
    val pipelineInferenceAPIEndPoint: PipelineInferenceEndpoint?
)

data class PipelineResponseConfigItem(
    val taskType: String,
    val config: List<ServiceConfigEntry>?
)

data class ServiceConfigEntry(
    val serviceId: String,
    val language: LanguagePair?
)

data class PipelineInferenceEndpoint(
    val callbackUrl: String,
    val inferenceApiKey: InferenceApiKey
)

data class InferenceApiKey(val name: String, val value: String)

// ---- Step 2: Pipeline Compute call ----

data class ComputeRequest(
    val pipelineTasks: List<PipelineTask>,
    val inputData: InputData
)

data class InputData(
    val audio: List<AudioContent>
)

data class AudioContent(val audioContent: String) // base64 WAV bytes

data class ComputeResponse(
    val pipelineResponse: List<TaskOutput>?
)

data class TaskOutput(
    val taskType: String,
    val output: List<OutputItem>?
)

data class OutputItem(val source: String?)

interface BhashiniApiService {

    @POST("ulca/apis/v0/model/getModelsPipeline")
    suspend fun getPipelineConfig(
        @Header("userID") userId: String,
        @Header("ulcaApiKey") apiKey: String,
        @Body body: PipelineConfigRequest
    ): PipelineConfigResponse

    @POST
    suspend fun compute(
        @Url url: String,
        @HeaderMap headers: Map<String, String>,
        @Body body: ComputeRequest
    ): ComputeResponse
}
