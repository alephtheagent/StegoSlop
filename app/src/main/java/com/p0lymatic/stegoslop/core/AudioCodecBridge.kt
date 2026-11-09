package com.p0lymatic.stegoslop.core

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Bridge for decoding FLAC audio to standard 16-bit PCM WAV and encoding PCM WAV back to FLAC
 * using Android's native MediaCodec and MediaExtractor APIs.
 */
object AudioCodecBridge {

    private const val FLAC_MIME = "audio/flac"
    private const val TIMEOUT_US = 10_000L

    /**
     * Checks whether the provided byte array starts with the "fLaC" magic 4-byte signature.
     */
    fun isFlac(bytes: ByteArray): Boolean {
        if (bytes.size < 4) return false
        return bytes[0] == 'f'.code.toByte() &&
                bytes[1] == 'L'.code.toByte() &&
                bytes[2] == 'a'.code.toByte() &&
                bytes[3] == 'C'.code.toByte()
    }

    /**
     * Decodes FLAC bytes into a standard 16-bit PCM RIFF WAV byte array.
     */
    fun decodeFlacToPcmWav(context: Context, flacBytes: ByteArray): ByteArray {
        val tempFile = File.createTempFile("stego_flac_in_", ".flac", context.cacheDir)
        try {
            FileOutputStream(tempFile).use { it.write(flacBytes) }

            val extractor = MediaExtractor()
            extractor.setDataSource(tempFile.absolutePath)

            var trackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val trackFormat = extractor.getTrackFormat(i)
                val mime = trackFormat.getString(MediaFormat.KEY_MIME)
                if (mime != null && (mime.equals(FLAC_MIME, ignoreCase = true) || mime.startsWith("audio/"))) {
                    trackIndex = i
                    format = trackFormat
                    break
                }
            }

            if (trackIndex < 0 || format == null) {
                extractor.release()
                throw IllegalArgumentException("No audio track found in FLAC file")
            }

            extractor.selectTrack(trackIndex)

            val mime = format.getString(MediaFormat.KEY_MIME) ?: FLAC_MIME
            val decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(format, null, null, 0)
            decoder.start()

            val pcmOutputStream = ByteArrayOutputStream()
            val bufferInfo = MediaCodec.BufferInfo()
            var isExtractorEOS = false
            var isDecoderEOS = false

            var sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else 44100

            var channelCount = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else 2

            try {
                while (!isDecoderEOS) {
                    if (!isExtractorEOS) {
                        val inIndex = decoder.dequeueInputBuffer(TIMEOUT_US)
                        if (inIndex >= 0) {
                            val inBuffer = decoder.getInputBuffer(inIndex)
                            if (inBuffer != null) {
                                inBuffer.clear()
                                val sampleSize = extractor.readSampleData(inBuffer, 0)
                                if (sampleSize < 0) {
                                    decoder.queueInputBuffer(
                                        inIndex,
                                        0,
                                        0,
                                        0L,
                                        MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                    )
                                    isExtractorEOS = true
                                } else {
                                    decoder.queueInputBuffer(
                                        inIndex,
                                        0,
                                        sampleSize,
                                        extractor.sampleTime,
                                        0
                                    )
                                    extractor.advance()
                                }
                            }
                        }
                    }

                    val outIndex = decoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                    when {
                        outIndex >= 0 -> {
                            val outBuffer = decoder.getOutputBuffer(outIndex)
                            if (outBuffer != null && bufferInfo.size > 0) {
                                outBuffer.position(bufferInfo.offset)
                                outBuffer.limit(bufferInfo.offset + bufferInfo.size)
                                val chunk = ByteArray(bufferInfo.size)
                                outBuffer.get(chunk)
                                pcmOutputStream.write(chunk)
                            }
                            decoder.releaseOutputBuffer(outIndex, false)
                            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                isDecoderEOS = true
                            }
                        }
                        outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val newFormat = decoder.outputFormat
                            if (newFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                                sampleRate = newFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            }
                            if (newFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                                channelCount = newFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            }
                        }
                    }
                }
            } finally {
                decoder.stop()
                decoder.release()
                extractor.release()
            }

            val pcmData = pcmOutputStream.toByteArray()
            return buildWavHeader(pcmData, sampleRate, channelCount, bitsPerSample = 16)
        } finally {
            tempFile.delete()
        }
    }

    /**
     * Encodes a standard 16-bit PCM RIFF WAV byte array into a FLAC byte array.
     */
    fun encodePcmWavToFlac(context: Context, wavBytes: ByteArray): ByteArray {
        val wavInfo = AudioLsbEngine.parseWav(wavBytes)
        val pcmDataOffset = wavInfo.dataOffset
        val pcmDataSize = wavInfo.dataSize
        val sampleRate = wavInfo.sampleRate
        val channelCount = wavInfo.numChannels

        val encoder = MediaCodec.createEncoderByType(FLAC_MIME)
        val format = MediaFormat.createAudioFormat(FLAC_MIME, sampleRate, channelCount).apply {
            setInteger(MediaFormat.KEY_SAMPLE_RATE, sampleRate)
            setInteger(MediaFormat.KEY_CHANNEL_COUNT, channelCount)
            // Optional compression level for FLAC (standard default is 5)
            setInteger(MediaFormat.KEY_FLAC_COMPRESSION_LEVEL, 5)
        }

        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()

        val flacOutputStream = ByteArrayOutputStream()
        val bufferInfo = MediaCodec.BufferInfo()

        var currentPcmOffset = pcmDataOffset
        val pcmEnd = pcmDataOffset + pcmDataSize
        var isInputEOS = false
        var isOutputEOS = false

        val bytesPerSec = sampleRate * channelCount * 2
        var ptsUs = 0L

        try {
            while (!isOutputEOS) {
                if (!isInputEOS) {
                    val inIndex = encoder.dequeueInputBuffer(TIMEOUT_US)
                    if (inIndex >= 0) {
                        val inBuffer = encoder.getInputBuffer(inIndex)
                        if (inBuffer != null) {
                            inBuffer.clear()
                            val remainingPcm = pcmEnd - currentPcmOffset
                            if (remainingPcm <= 0) {
                                encoder.queueInputBuffer(
                                    inIndex,
                                    0,
                                    0,
                                    ptsUs,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                isInputEOS = true
                            } else {
                                val toWrite = minOf(remainingPcm, inBuffer.capacity())
                                inBuffer.put(wavBytes, currentPcmOffset, toWrite)
                                encoder.queueInputBuffer(
                                    inIndex,
                                    0,
                                    toWrite,
                                    ptsUs,
                                    0
                                )
                                currentPcmOffset += toWrite
                                val samplesSent = toWrite / (channelCount * 2)
                                ptsUs += (samplesSent * 1_000_000L) / sampleRate
                            }
                        }
                    }
                }

                val outIndex = encoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                when {
                    outIndex >= 0 -> {
                        val outBuffer = encoder.getOutputBuffer(outIndex)
                        if (outBuffer != null && bufferInfo.size > 0) {
                            outBuffer.position(bufferInfo.offset)
                            outBuffer.limit(bufferInfo.offset + bufferInfo.size)
                            val chunk = ByteArray(bufferInfo.size)
                            outBuffer.get(chunk)
                            flacOutputStream.write(chunk)
                        }
                        encoder.releaseOutputBuffer(outIndex, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            isOutputEOS = true
                        }
                    }
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        // Encoder output format confirmed
                    }
                }
            }
        } finally {
            encoder.stop()
            encoder.release()
        }

        return flacOutputStream.toByteArray()
    }

    /**
     * Constructs a valid 44-byte RIFF WAV header prepended to raw PCM samples.
     */
    private fun buildWavHeader(
        pcmData: ByteArray,
        sampleRate: Int,
        numChannels: Int,
        bitsPerSample: Int = 16
    ): ByteArray {
        val totalDataLen = pcmData.size
        val totalAudioLen = totalDataLen + 36
        val byteRate = sampleRate * numChannels * (bitsPerSample / 8)
        val blockAlign = numChannels * (bitsPerSample / 8)

        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt(totalAudioLen)
        header.put("WAVE".toByteArray(Charsets.US_ASCII))

        header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16) // Subchunk1Size for PCM
        header.putShort(1.toShort()) // AudioFormat (1 = PCM)
        header.putShort(numChannels.toShort())
        header.putInt(sampleRate)
        header.putInt(byteRate)
        header.putShort(blockAlign.toShort())
        header.putShort(bitsPerSample.toShort())

        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(totalDataLen)

        val out = ByteArray(44 + totalDataLen)
        System.arraycopy(header.array(), 0, out, 0, 44)
        System.arraycopy(pcmData, 0, out, 44, totalDataLen)
        return out
    }
}
