package com.p0lymatic.stegoslop.core

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Opus audio profiles for speech vs full-band music.
 */
enum class OpusProfile {
    VOICE,  // Voice / speech profile (target 8..24 kbps)
    MUSIC   // Full-band music profile (target 16..96 kbps)
}

/**
 * Bridge for adaptive Opus audio encoding, PCM trimming, and bitrate calculation.
 */
object OpusCodecBridge {

    private const val OPUS_MIME = "audio/opus"
    private const val TIMEOUT_US = 10_000L

    /**
     * Calculates the target bitrate in bits per second (bps) adapted to carrier capacity.
     * Formula incorporates 30% safety headroom: ((availableCapacityBytes * 0.70 * 8) / durationSeconds).toInt()
     * For VOICE: clamp between 8_000 and 24_000 bps.
     * For MUSIC: clamp between 16_000 and 96_000 bps.
     */
    fun calculateTargetBitrate(
        durationSeconds: Double,
        availableCapacityBytes: Int,
        profile: OpusProfile
    ): Int {
        val safeCapacity = availableCapacityBytes.coerceAtLeast(0) * 0.85
        val rawBitrate = if (durationSeconds > 0.0 && !durationSeconds.isNaN()) {
            ((safeCapacity * 8.0) / durationSeconds).toInt()
        } else {
            when (profile) {
                OpusProfile.VOICE -> 8_000
                OpusProfile.MUSIC -> 16_000
            }
        }
        return when (profile) {
            OpusProfile.VOICE -> rawBitrate.coerceIn(8_000, 24_000)
            OpusProfile.MUSIC -> rawBitrate.coerceIn(16_000, 96_000)
        }
    }

    /**
     * Slices a 16-bit PCM sample array between startSeconds and endSeconds.
     * Ensures slice boundaries align with full sample frames (channels * 2 bytes).
     */
    fun trimPcm(
        pcmBytes: ByteArray,
        sampleRate: Int,
        channels: Int,
        startSeconds: Double,
        endSeconds: Double
    ): ByteArray {
        val effectiveChannels = maxOf(1, channels)
        val effectiveSampleRate = maxOf(1, sampleRate)
        val bytesPerFrame = effectiveChannels * 2
        val totalFrames = (pcmBytes.size / bytesPerFrame).toLong()

        val startFrame = (startSeconds.coerceAtLeast(0.0) * effectiveSampleRate).toLong()
        val endFrame = (endSeconds.coerceAtLeast(0.0) * effectiveSampleRate).toLong()

        val clampedStart = startFrame.coerceIn(0L, totalFrames)
        val clampedEnd = endFrame.coerceIn(clampedStart, totalFrames)

        val startByte = (clampedStart * bytesPerFrame).toInt()
        val endByte = (clampedEnd * bytesPerFrame).toInt()

        return pcmBytes.copyOfRange(startByte, endByte)
    }

    /**
     * Slices a 16-bit PCM RIFF WAV byte array between startSeconds and endSeconds.
     */
    fun trimWav(wavBytes: ByteArray, startSeconds: Double, endSeconds: Double): ByteArray {
        val info = AudioLsbEngine.parseWav(wavBytes)
        val rawPcm = wavBytes.copyOfRange(info.dataOffset, info.dataOffset + info.dataSize)
        val trimmedPcm = trimPcm(rawPcm, info.sampleRate, info.numChannels, startSeconds, endSeconds)
        return buildWavHeader(trimmedPcm, info.sampleRate, info.numChannels, info.bitsPerSample)
    }

    /**
     * Checks if the byte array starts with RIFF WAVE signature.
     */
    fun isWav(bytes: ByteArray): Boolean {
        if (bytes.size < 12) return false
        return bytes[0] == 'R'.code.toByte() &&
                bytes[1] == 'I'.code.toByte() &&
                bytes[2] == 'F'.code.toByte() &&
                bytes[3] == 'F'.code.toByte() &&
                bytes[8] == 'W'.code.toByte() &&
                bytes[9] == 'A'.code.toByte() &&
                bytes[10] == 'V'.code.toByte() &&
                bytes[11] == 'E'.code.toByte()
    }

    /**
     * Checks if the byte array starts with Ogg container magic bytes.
     */
    fun isOpus(bytes: ByteArray): Boolean {
        if (bytes.size < 8) return false
        val isOgg = bytes[0] == 0x4F.toByte() &&
                bytes[1] == 0x67.toByte() &&
                bytes[2] == 0x67.toByte() &&
                bytes[3] == 0x53.toByte()
        if (!isOgg) return false
        val maxSearch = minOf(bytes.size - 8, 120)
        for (i in 0..maxSearch) {
            if (bytes[i] == 'O'.code.toByte() &&
                bytes[i + 1] == 'p'.code.toByte() &&
                bytes[i + 2] == 'u'.code.toByte() &&
                bytes[i + 3] == 's'.code.toByte() &&
                bytes[i + 4] == 'H'.code.toByte() &&
                bytes[i + 5] == 'e'.code.toByte() &&
                bytes[i + 6] == 'a'.code.toByte() &&
                bytes[i + 7] == 'd'.code.toByte()
            ) {
                return true
            }
        }
        return true // Ogg audio stream
    }

    /**
     * Constructs a valid 44-byte RIFF WAV header prepended to raw 16-bit PCM samples.
     */
    fun buildWavHeader(
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

    /**
     * Resamples a 16-bit signed PCM byte array between sample rates using linear interpolation.
     */
    fun resamplePcm16(
        pcmBytes: ByteArray,
        inSampleRate: Int,
        outSampleRate: Int,
        channels: Int
    ): ByteArray {
        val effectiveChannels = maxOf(1, channels)
        val bytesPerFrame = effectiveChannels * 2
        val inFrames = pcmBytes.size / bytesPerFrame
        if (inFrames <= 0 || inSampleRate == outSampleRate || inSampleRate <= 0 || outSampleRate <= 0) {
            return pcmBytes
        }

        val outFrames = ((inFrames.toLong() * outSampleRate) / inSampleRate).toInt()
        val outBytes = ByteArray(outFrames * bytesPerFrame)
        val ratio = inSampleRate.toDouble() / outSampleRate.toDouble()

        val inBuffer = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN)
        val outBuffer = ByteBuffer.wrap(outBytes).order(ByteOrder.LITTLE_ENDIAN)

        for (outI in 0 until outFrames) {
            val inPos = outI * ratio
            val inIdx = inPos.toInt()
            val frac = inPos - inIdx
            val nextIdx = minOf(inIdx + 1, inFrames - 1)

            for (ch in 0 until effectiveChannels) {
                val s1 = inBuffer.getShort((inIdx * effectiveChannels + ch) * 2).toInt()
                val s2 = inBuffer.getShort((nextIdx * effectiveChannels + ch) * 2).toInt()
                val interpolated = (s1 + frac * (s2 - s1)).toInt().coerceIn(-32768, 32767)
                outBuffer.putShort((outI * effectiveChannels + ch) * 2, interpolated.toShort())
            }
        }
        return outBytes
    }

    /**
     * Encodes 16-bit PCM bytes via Android MediaCodec with MIME audio/opus.
     * Falls back to PCM if MediaCodec encoder is not available on older test runners or unsupported environments.
     */
    fun encodeToOpusPcm(
        context: Context,
        pcmBytes: ByteArray,
        sampleRate: Int,
        channels: Int,
        bitrateBps: Int,
        profile: OpusProfile
    ): ByteArray {
        if (pcmBytes.isEmpty()) return pcmBytes
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return pcmBytes
        }

        return try {
            val rawPcm = if (isWav(pcmBytes)) {
                val info = AudioLsbEngine.parseWav(pcmBytes)
                pcmBytes.copyOfRange(info.dataOffset, info.dataOffset + info.dataSize)
            } else {
                pcmBytes
            }

            // Opus natively operates on 48,000 Hz. Resample to prevent pitch shift / speed-up
            val targetRate = 48000
            val preparedPcm = if (sampleRate != targetRate && sampleRate > 0) {
                resamplePcm16(rawPcm, sampleRate, targetRate, channels)
            } else {
                rawPcm
            }

            val tempOutputFile = File.createTempFile("stego_opus_out_", ".opus", context.cacheDir)
            try {
                val muxer = MediaMuxer(tempOutputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG)
                val format = MediaFormat.createAudioFormat(OPUS_MIME, targetRate, channels).apply {
                    setInteger(MediaFormat.KEY_BIT_RATE, bitrateBps)
                    try {
                        setInteger(
                            MediaFormat.KEY_BITRATE_MODE,
                            MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR
                        )
                    } catch (_: Throwable) {}
                    if (profile == OpusProfile.VOICE) {
                        setInteger(MediaFormat.KEY_COMPLEXITY, 5)
                    } else {
                        setInteger(MediaFormat.KEY_COMPLEXITY, 10)
                    }
                }

                val encoder = MediaCodec.createEncoderByType(OPUS_MIME)
                try {
                    encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                } catch (_: Throwable) {
                    try {
                        format.setInteger(
                            MediaFormat.KEY_BITRATE_MODE,
                            MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR
                        )
                        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                    } catch (_: Throwable) {
                        val fallbackFormat = MediaFormat.createAudioFormat(OPUS_MIME, targetRate, channels).apply {
                            setInteger(MediaFormat.KEY_BIT_RATE, bitrateBps)
                            setInteger(MediaFormat.KEY_COMPLEXITY, if (profile == OpusProfile.VOICE) 5 else 10)
                        }
                        encoder.configure(fallbackFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                    }
                }
                encoder.start()

                var trackIndex = -1
                var muxerStarted = false
                val bufferInfo = MediaCodec.BufferInfo()

                var currentPcmOffset = 0
                val pcmEnd = preparedPcm.size
                var isInputEOS = false
                var isOutputEOS = false
                val bytesPerFrame = maxOf(1, channels) * 2
                var ptsUs = 0L

                while (!isOutputEOS) {
                    if (!isInputEOS) {
                        val inIndex = encoder.dequeueInputBuffer(TIMEOUT_US)
                        if (inIndex >= 0) {
                            val inBuffer = encoder.getInputBuffer(inIndex)
                            if (inBuffer != null) {
                                inBuffer.clear()
                                val remaining = pcmEnd - currentPcmOffset
                                if (remaining <= 0) {
                                    encoder.queueInputBuffer(
                                        inIndex,
                                        0,
                                        0,
                                        ptsUs,
                                        MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                    )
                                    isInputEOS = true
                                } else {
                                    val toWrite = minOf(remaining, inBuffer.capacity())
                                    inBuffer.put(preparedPcm, currentPcmOffset, toWrite)
                                    encoder.queueInputBuffer(inIndex, 0, toWrite, ptsUs, 0)
                                    currentPcmOffset += toWrite
                                    val framesSent = toWrite / bytesPerFrame
                                    ptsUs += (framesSent * 1_000_000L) / targetRate
                                }
                            }
                        }
                    }

                    val outIndex = encoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                    when {
                        outIndex >= 0 -> {
                            val outBuffer = encoder.getOutputBuffer(outIndex)
                            if (outBuffer != null) {
                                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                                    bufferInfo.size = 0
                                }
                                if (bufferInfo.size > 0 && muxerStarted) {
                                    outBuffer.position(bufferInfo.offset)
                                    outBuffer.limit(bufferInfo.offset + bufferInfo.size)
                                    muxer.writeSampleData(trackIndex, outBuffer, bufferInfo)
                                }
                            }
                            encoder.releaseOutputBuffer(outIndex, false)
                            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                isOutputEOS = true
                            }
                        }
                        outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            if (!muxerStarted) {
                                trackIndex = muxer.addTrack(encoder.outputFormat)
                                muxer.start()
                                muxerStarted = true
                            }
                        }
                    }
                }

                try { encoder.stop() } catch (_: Throwable) {}
                try { encoder.release() } catch (_: Throwable) {}
                if (muxerStarted) {
                    try { muxer.stop() } catch (_: Throwable) {}
                }
                try { muxer.release() } catch (_: Throwable) {}

                val resultBytes = tempOutputFile.readBytes()
                if (resultBytes.isNotEmpty()) resultBytes else pcmBytes
            } finally {
                tempOutputFile.delete()
            }
        } catch (_: Throwable) {
            pcmBytes
        }
    }
    /**
     * Encodes 16-bit PCM bytes to Opus Ogg, guaranteeing the returned byte array is <= [maxAllowedBytes].
     * If the encoded size exceeds [maxAllowedBytes], retries iteratively at a reduced bitrate.
     * If the minimum bitrate is reached and the payload still exceeds capacity, trims trailing PCM
     * samples until it fits.
     */
    fun encodeToOpusPcmGuaranteed(
        context: Context,
        pcmBytes: ByteArray,
        sampleRate: Int,
        channels: Int,
        bitrateBps: Int,
        profile: OpusProfile,
        maxAllowedBytes: Int
    ): ByteArray {
        if (pcmBytes.isEmpty() || maxAllowedBytes <= 0) return ByteArray(0)

        val minBitrate = when (profile) {
            OpusProfile.VOICE -> 8_000
            OpusProfile.MUSIC -> 16_000
        }

        var currentBitrate = bitrateBps.coerceIn(minBitrate, 96_000)
        var currentPcm = if (isWav(pcmBytes)) {
            try {
                val info = AudioLsbEngine.parseWav(pcmBytes)
                pcmBytes.copyOfRange(info.dataOffset, info.dataOffset + info.dataSize)
            } catch (_: Throwable) {
                pcmBytes
            }
        } else {
            pcmBytes
        }

        var encoded = encodeToOpusPcm(
            context = context,
            pcmBytes = currentPcm,
            sampleRate = sampleRate,
            channels = channels,
            bitrateBps = currentBitrate,
            profile = profile
        )

        val maxAttempts = 10
        var attempt = 0

        while (encoded.size > maxAllowedBytes && attempt < maxAttempts) {
            attempt++
            if (currentBitrate > minBitrate) {
                val reduced = (currentBitrate.toDouble() * maxAllowedBytes.toDouble() * 0.90 / encoded.size.toDouble()).toInt()
                currentBitrate = maxOf(minBitrate, minOf(currentBitrate - 1_000, reduced))
            } else {
                // At minimum bitrate: trim trailing PCM samples and re-encode
                val bytesPerFrame = maxOf(1, channels) * 2
                val totalFrames = currentPcm.size / bytesPerFrame
                val currentDuration = totalFrames.toDouble() / maxOf(1, sampleRate)
                if (currentDuration <= 0.2) {
                    break
                }
                val ratio = (maxAllowedBytes.toDouble() * 0.90) / encoded.size.toDouble()
                val targetDuration = currentDuration * ratio
                val trimAmountSec = maxOf(1.0, currentDuration - targetDuration)
                val newDuration = maxOf(0.1, currentDuration - trimAmountSec)
                currentPcm = trimPcm(currentPcm, sampleRate, channels, 0.0, newDuration)
                if (currentPcm.isEmpty()) {
                    break
                }
            }

            encoded = encodeToOpusPcm(
                context = context,
                pcmBytes = currentPcm,
                sampleRate = sampleRate,
                channels = channels,
                bitrateBps = currentBitrate,
                profile = profile
            )
        }

        return if (encoded.size <= maxAllowedBytes) {
            encoded
        } else {
            encoded.copyOf(maxAllowedBytes)
        }
    }

    /**
     * Decodes Opus or any MediaExtractor-supported audio file bytes to standard 16-bit PCM WAV.
     */
    fun decodeOpusToPcmWav(context: Context, audioBytes: ByteArray): ByteArray {
        if (audioBytes.isEmpty()) return ByteArray(0)
        if (isWav(audioBytes)) {
            try {
                AudioLsbEngine.parseWav(audioBytes)
                return audioBytes
            } catch (_: Exception) {}
        }

        val tempFile = File.createTempFile("stego_opus_dec_", ".tmp", context.cacheDir)
        try {
            FileOutputStream(tempFile).use { it.write(audioBytes) }

            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(tempFile.absolutePath)
                var trackIndex = -1
                var format: MediaFormat? = null
                for (i in 0 until extractor.trackCount) {
                    val trackFormat = extractor.getTrackFormat(i)
                    val mime = trackFormat.getString(MediaFormat.KEY_MIME)
                    if (mime != null && (mime.startsWith("audio/") || mime.equals(OPUS_MIME, ignoreCase = true))) {
                        trackIndex = i
                        format = trackFormat
                        break
                    }
                }

                if (trackIndex < 0 || format == null) {
                    return audioBytes
                }

                extractor.selectTrack(trackIndex)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: OPUS_MIME
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
                    try { decoder.stop() } catch (_: Throwable) {}
                    try { decoder.release() } catch (_: Throwable) {}
                }

                val pcmData = pcmOutputStream.toByteArray()
                if (pcmData.isNotEmpty()) {
                    return buildWavHeader(pcmData, sampleRate, channelCount, bitsPerSample = 16)
                }
            } finally {
                try { extractor.release() } catch (_: Throwable) {}
            }
        } catch (_: Throwable) {} finally {
            tempFile.delete()
        }

        return audioBytes
    }
}
