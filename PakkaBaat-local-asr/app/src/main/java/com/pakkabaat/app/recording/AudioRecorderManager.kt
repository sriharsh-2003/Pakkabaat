package com.pakkabaat.app.recording

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import com.pakkabaat.app.util.HashUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.*

/**
 * Captures the conversation via the device microphone (in-person mode, spec section 8.4).
 *
 * Records raw 16-bit PCM mono at 16kHz directly to a WAV file. This is a deliberate choice:
 * whisper.cpp (this branch's on-device ASR) also wants 16kHz mono PCM — by
 * recording in that format natively we avoid a lossy transcode step before upload, and the
 * SHA-256 hash below is computed over exactly the bytes that later get sent for processing
 * and the bytes that stay as the tamper-evidence source of truth (spec section 8.9).
 */
class AudioRecorderManager(private val context: Context) {

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var pcmFile: File? = null
    private var wavFile: File? = null
    private var startedAtMs: Long = 0L
    @Volatile private var isRecording = false

    val recordingsDir: File
        get() = File(context.filesDir, "recordings").apply { mkdirs() }

    @SuppressLint("MissingPermission")
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start(sessionId: String) {
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        require(minBuf > 0) { "This device does not support the requested audio format" }

        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        pcmFile = File(recordingsDir, "session_${sessionId}_$stamp.pcm")
        wavFile = File(recordingsDir, "session_${sessionId}_$stamp.wav")

        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, minBuf * 4
        )
        audioRecord = record
        record.startRecording()
        isRecording = true
        startedAtMs = System.currentTimeMillis()

        recordingJob = CoroutineScope(Dispatchers.IO).launch {
            val buffer = ByteArray(minBuf)
            pcmFile!!.outputStream().use { out ->
                while (isRecording) {
                    val read = record.read(buffer, 0, buffer.size)
                    if (read > 0) out.write(buffer, 0, read)
                }
            }
        }
    }

    /** Stops recording, writes the WAV header, and returns the finished result with its SHA-256 hash. */
    suspend fun stop(): RecordingResult {
        isRecording = false
        recordingJob?.join()
        audioRecord?.apply { stop(); release() }
        audioRecord = null

        val pcm = pcmFile ?: error("Recorder was never started")
        val wav = wavFile ?: error("Recorder was never started")
        writeWavFile(pcm, wav, SAMPLE_RATE, channels = 1, bitsPerSample = 16)
        pcm.delete()

        val durationSeconds = ((System.currentTimeMillis() - startedAtMs) / 1000).toInt()
        val hash = HashUtil.sha256File(wav)
        return RecordingResult(file = wav, durationSeconds = durationSeconds, sha256 = hash)
    }

    fun cancel() {
        isRecording = false
        try {
            audioRecord?.stop()
        } catch (_: Exception) {
            // fine — we're discarding this recording anyway
        } finally {
            audioRecord?.release()
            audioRecord = null
            pcmFile?.delete()
            wavFile?.delete()
        }
    }

    /** Prepends a standard 44-byte PCM WAV header onto the raw samples captured above. */
    private fun writeWavFile(pcmSource: File, wavTarget: File, sampleRate: Int, channels: Int, bitsPerSample: Int) {
        val pcmDataSize = pcmSource.length()
        val byteRate = sampleRate * channels * bitsPerSample / 8

        RandomAccessFile(wavTarget, "rw").use { out ->
            out.setLength(0)
            // RIFF header
            out.writeBytes("RIFF")
            out.write(intToLe(36 + pcmDataSize.toInt()))
            out.writeBytes("WAVE")
            // fmt sub-chunk
            out.writeBytes("fmt ")
            out.write(intToLe(16))                                  // sub-chunk size
            out.write(shortToLe(1))                                 // PCM = 1
            out.write(shortToLe(channels.toShort()))
            out.write(intToLe(sampleRate))
            out.write(intToLe(byteRate))
            out.write(shortToLe((channels * bitsPerSample / 8).toShort())) // block align
            out.write(shortToLe(bitsPerSample.toShort()))
            // data sub-chunk
            out.writeBytes("data")
            out.write(intToLe(pcmDataSize.toInt()))
            pcmSource.inputStream().use { it.copyTo(out.asOutputStream()) }
        }
    }

    private fun intToLe(v: Int): ByteArray = byteArrayOf(
        (v and 0xff).toByte(), ((v shr 8) and 0xff).toByte(),
        ((v shr 16) and 0xff).toByte(), ((v shr 24) and 0xff).toByte()
    )

    private fun shortToLe(v: Short): ByteArray = byteArrayOf(
        (v.toInt() and 0xff).toByte(), ((v.toInt() shr 8) and 0xff).toByte()
    )

    private fun RandomAccessFile.asOutputStream() = object : java.io.OutputStream() {
        override fun write(b: Int) = this@asOutputStream.write(b)
        override fun write(b: ByteArray, off: Int, len: Int) = this@asOutputStream.write(b, off, len)
    }

    data class RecordingResult(val file: File, val durationSeconds: Int, val sha256: String)
}
