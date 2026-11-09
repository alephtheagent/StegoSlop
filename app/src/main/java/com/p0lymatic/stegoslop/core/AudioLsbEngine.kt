package com.p0lymatic.stegoslop.core

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Metadata extracted from a RIFF WAV audio file.
 */
data class WavInfo(
    val dataOffset: Int,
    val dataSize: Int,
    val bitsPerSample: Int,
    val numChannels: Int,
    val sampleRate: Int
) {
    val durationSeconds: Double
        get() {
            val bytesPerSample = bitsPerSample / 8
            val bytesPerFrame = numChannels * bytesPerSample
            if (bytesPerFrame == 0 || sampleRate == 0) return 0.0
            val totalFrames = dataSize / bytesPerFrame
            return totalFrames.toDouble() / sampleRate
        }
}

/**
 * LSB Steganography engine for 16-bit PCM RIFF WAV audio.
 */
object AudioLsbEngine {

    /**
     * Parses the RIFF WAV header to locate fmt and data chunks.
     */
    fun parseWav(wavBytes: ByteArray): WavInfo {
        if (wavBytes.size < 12) {
            throw IllegalArgumentException("Audio file too small to be a valid WAV file (${wavBytes.size} bytes)")
        }

        val riff = String(wavBytes, 0, 4, Charsets.US_ASCII)
        val wave = String(wavBytes, 8, 4, Charsets.US_ASCII)
        if (riff != "RIFF" || wave != "WAVE") {
            throw IllegalArgumentException("Invalid WAV: missing RIFF/WAVE header")
        }

        var offset = 12
        var dataOffset = -1
        var dataSize = 0
        var bitsPerSample = 16
        var numChannels = 1
        var sampleRate = 44100

        while (offset + 8 <= wavBytes.size) {
            val chunkId = String(wavBytes, offset, 4, Charsets.US_ASCII)
            val chunkSize = ((wavBytes[offset + 4].toLong() and 0xFF) or
                    ((wavBytes[offset + 5].toLong() and 0xFF) shl 8) or
                    ((wavBytes[offset + 6].toLong() and 0xFF) shl 16) or
                    ((wavBytes[offset + 7].toLong() and 0xFF) shl 24)).toInt()

            val chunkDataOffset = offset + 8

            if (chunkId == "fmt ") {
                if (chunkSize >= 16 && chunkDataOffset + 16 <= wavBytes.size) {
                    val audioFormat = (wavBytes[chunkDataOffset].toInt() and 0xFF) or
                            ((wavBytes[chunkDataOffset + 1].toInt() and 0xFF) shl 8)
                    if (audioFormat != 1) {
                        throw IllegalArgumentException("Unsupported WAV format: $audioFormat (must be 1 for PCM)")
                    }
                    numChannels = (wavBytes[chunkDataOffset + 2].toInt() and 0xFF) or
                            ((wavBytes[chunkDataOffset + 3].toInt() and 0xFF) shl 8)
                    sampleRate = ((wavBytes[chunkDataOffset + 4].toLong() and 0xFF) or
                            ((wavBytes[chunkDataOffset + 5].toLong() and 0xFF) shl 8) or
                            ((wavBytes[chunkDataOffset + 6].toLong() and 0xFF) shl 16) or
                            ((wavBytes[chunkDataOffset + 7].toLong() and 0xFF) shl 24)).toInt()
                    bitsPerSample = (wavBytes[chunkDataOffset + 14].toInt() and 0xFF) or
                            ((wavBytes[chunkDataOffset + 15].toInt() and 0xFF) shl 8)
                    if (bitsPerSample != 16) {
                        throw IllegalArgumentException("Unsupported WAV bits per sample: $bitsPerSample (must be 16-bit PCM)")
                    }
                }
            } else if (chunkId == "data") {
                dataOffset = chunkDataOffset
                val available = wavBytes.size - chunkDataOffset
                dataSize = if (chunkSize in 0..available) chunkSize else available
                break
            }

            offset = chunkDataOffset + chunkSize + (chunkSize % 2)
        }

        if (dataOffset == -1) {
            throw IllegalArgumentException("Invalid WAV: 'data' chunk not found")
        }

        return WavInfo(dataOffset, dataSize, bitsPerSample, numChannels, sampleRate)
    }

    /**
     * Computes the maximum payload capacity in bytes for a WAV audio file.
     * Capacity = (dataChunkSize / 2 * bitsPerSample) / 8 bytes.
     */
    fun computeCapacity(wavBytes: ByteArray, bitsPerSample: Int = 1): Int {
        require(bitsPerSample in listOf(1, 2, 4)) { "bitsPerSample must be 1, 2, or 4" }
        val info = parseWav(wavBytes)
        val sampleCount = info.dataSize / 2
        return (sampleCount.toLong() * bitsPerSample / 8).toInt()
    }

    /**
     * Embeds payloadContainer into the audio carrier replacing the lowest bit(s)
     * of each 16-bit sample in the 'data' chunk.
     */
    fun embed(wavBytes: ByteArray, payloadContainer: ByteArray, bitsPerSample: Int = 1): ByteArray {
        require(bitsPerSample in listOf(1, 2, 4)) { "bitsPerSample must be 1, 2, or 4" }
        val info = parseWav(wavBytes)
        val sampleCount = info.dataSize / 2
        val capacity = (sampleCount.toLong() * bitsPerSample / 8).toInt()

        if (payloadContainer.size > capacity) {
            throw IllegalArgumentException(
                "Payload size (${payloadContainer.size} bytes) exceeds audio capacity ($capacity bytes)"
            )
        }

        val result = wavBytes.copyOf()
        val mask = (1 shl bitsPerSample) - 1
        val clearMask = 0xFFFF xor mask

        var sampleIdx = 0
        for (b in payloadContainer) {
            for (shift in (8 - bitsPerSample) downTo 0 step bitsPerSample) {
                val chunk = (b.toInt() ushr shift) and mask
                val byteOffset = info.dataOffset + sampleIdx * 2

                val low = result[byteOffset].toInt() and 0xFF
                val high = result[byteOffset + 1].toInt() and 0xFF
                val sample = low or (high shl 8)

                val newSample = (sample and clearMask) or chunk
                result[byteOffset] = (newSample and 0xFF).toByte()
                result[byteOffset + 1] = ((newSample ushr 8) and 0xFF).toByte()

                sampleIdx++
            }
        }

        return result
    }

    /**
     * Extracts the embedded payload container bytes from the audio carrier.
     */
    fun extract(wavBytes: ByteArray, bitsPerSample: Int = 1): ByteArray {
        require(bitsPerSample in listOf(1, 2, 4)) { "bitsPerSample must be 1, 2, or 4" }
        val info = parseWav(wavBytes)
        val sampleCount = info.dataSize / 2
        val capacity = (sampleCount.toLong() * bitsPerSample / 8).toInt()

        val chunksPerByte = 8 / bitsPerSample
        val mask = (1 shl bitsPerSample) - 1
        var sampleIdx = 0

        if (capacity < StegoProtocol.HEADER_MIN_LENGTH) {
            throw IllegalArgumentException("Audio carrier too short to contain SLOP header")
        }

        // Extract 14-byte initial header
        val headerBytes = ByteArray(StegoProtocol.HEADER_MIN_LENGTH)
        for (i in 0 until StegoProtocol.HEADER_MIN_LENGTH) {
            var byteVal = 0
            for (c in 0 until chunksPerByte) {
                val byteOffset = info.dataOffset + sampleIdx * 2
                val low = wavBytes[byteOffset].toInt() and 0xFF
                val high = wavBytes[byteOffset + 1].toInt() and 0xFF
                val sample = low or (high shl 8)

                val chunk = sample and mask
                byteVal = (byteVal shl bitsPerSample) or chunk
                sampleIdx++
            }
            headerBytes[i] = byteVal.toByte()
        }

        // Verify SLOP magic
        if (headerBytes[0] != 0x53.toByte() || headerBytes[1] != 0x4C.toByte() ||
            headerBytes[2] != 0x4F.toByte() || headerBytes[3] != 0x50.toByte()
        ) {
            throw IllegalArgumentException("Invalid magic bytes: SLOP header not found in audio")
        }

        val flags = headerBytes[5].toInt() and 0xFF
        val isEncrypted = (flags and 0x01) != 0
        val payloadLength = ((headerBytes[6].toLong() and 0xFF) shl 24) or
                ((headerBytes[7].toLong() and 0xFF) shl 16) or
                ((headerBytes[8].toLong() and 0xFF) shl 8) or
                (headerBytes[9].toLong() and 0xFF)

        val headerSize = if (isEncrypted) {
            StegoProtocol.HEADER_MIN_LENGTH + StegoProtocol.SALT_LENGTH + StegoProtocol.IV_LENGTH
        } else {
            StegoProtocol.HEADER_MIN_LENGTH
        }

        val totalSize = headerSize + payloadLength
        if (totalSize < 0 || totalSize > capacity) {
            throw IllegalArgumentException(
                "Corrupted container: total size $totalSize exceeds audio capacity $capacity"
            )
        }

        val result = ByteArray(totalSize.toInt())
        System.arraycopy(headerBytes, 0, result, 0, StegoProtocol.HEADER_MIN_LENGTH)

        for (i in StegoProtocol.HEADER_MIN_LENGTH until totalSize.toInt()) {
            var byteVal = 0
            for (c in 0 until chunksPerByte) {
                val byteOffset = info.dataOffset + sampleIdx * 2
                val low = wavBytes[byteOffset].toInt() and 0xFF
                val high = wavBytes[byteOffset + 1].toInt() and 0xFF
                val sample = low or (high shl 8)

                val chunk = sample and mask
                byteVal = (byteVal shl bitsPerSample) or chunk
                sampleIdx++
            }
            result[i] = byteVal.toByte()
        }

        return result
    }

    /**
     * Generates a synthetic 16-bit PCM RIFF WAV in memory for testing and previews.
     */
    fun createSyntheticWav(sampleCount: Int, sampleRate: Int = 44100, numChannels: Int = 1): ByteArray {
        val dataSize = sampleCount * 2
        val totalSize = 44 + dataSize
        val buffer = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)

        buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
        buffer.putInt(totalSize - 8)
        buffer.put("WAVE".toByteArray(Charsets.US_ASCII))

        // fmt chunk
        buffer.put("fmt ".toByteArray(Charsets.US_ASCII))
        buffer.putInt(16) // Subchunk1Size (16 for PCM)
        buffer.putShort(1.toShort()) // AudioFormat (1 for PCM)
        buffer.putShort(numChannels.toShort())
        buffer.putInt(sampleRate)
        buffer.putInt(sampleRate * numChannels * 2) // ByteRate
        buffer.putShort((numChannels * 2).toShort()) // BlockAlign
        buffer.putShort(16.toShort()) // BitsPerSample

        // data chunk
        buffer.put("data".toByteArray(Charsets.US_ASCII))
        buffer.putInt(dataSize)

        for (i in 0 until sampleCount) {
            val sample = (Math.sin(2.0 * Math.PI * 440.0 * i / sampleRate) * 12000).toInt().toShort()
            buffer.putShort(sample)
        }

        return buffer.array()
    }
}
