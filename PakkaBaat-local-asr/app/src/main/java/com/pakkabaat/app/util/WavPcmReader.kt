package com.pakkabaat.app.util

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

object WavPcmReader {

    /**
     * Reads a 16-bit PCM mono WAV file (exactly what AudioRecorderManager produces) into
     * a normalized float32 array in [-1, 1] — the input format whisper.cpp's C API expects.
     * Skips the 44-byte header rather than assuming a fixed offset, in case of extra chunks.
     */
    fun readAsFloatPcm(wavFile: File): FloatArray {
        RandomAccessFile(wavFile, "r").use { raf ->
            val header = ByteArray(12)
            raf.readFully(header)
            require(String(header, 0, 4, Charsets.US_ASCII) == "RIFF") { "Not a RIFF/WAV file" }

            // Walk sub-chunks until we find "data" — robust to any extra chunks before it.
            var dataOffset = -1L
            var dataSize = -1
            while (raf.filePointer < raf.length()) {
                val chunkId = ByteArray(4)
                raf.readFully(chunkId)
                val sizeBytes = ByteArray(4)
                raf.readFully(sizeBytes)
                val chunkSize = ByteBuffer.wrap(sizeBytes).order(ByteOrder.LITTLE_ENDIAN).int

                if (String(chunkId, Charsets.US_ASCII) == "data") {
                    dataOffset = raf.filePointer
                    dataSize = chunkSize
                    break
                } else {
                    raf.seek(raf.filePointer + chunkSize)
                }
            }
            require(dataOffset >= 0) { "No data chunk found in WAV file" }

            raf.seek(dataOffset)
            val bytes = ByteArray(dataSize)
            raf.readFully(bytes)

            val sampleCount = dataSize / 2 // 16-bit samples
            val floats = FloatArray(sampleCount)
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until sampleCount) {
                floats[i] = buffer.short / 32768.0f
            }
            return floats
        }
    }
}
