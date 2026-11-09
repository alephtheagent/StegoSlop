package com.p0lymatic.stegoslop.core

import android.graphics.Bitmap

/**
 * LSB Steganography engine for ARGB_8888 Bitmaps.
 */
object ImageLsbEngine {

    /**
     * Computes the maximum payload capacity in bytes for an image of given dimensions and bitsPerChannel.
     */
    fun computeCapacity(width: Int, height: Int, bitsPerChannel: Int = 1): Int {
        require(bitsPerChannel in listOf(1, 2, 4)) { "bitsPerChannel must be 1, 2, or 4" }
        return (width.toLong() * height * 3 * bitsPerChannel / 8).toInt()
    }

    /**
     * Embeds payload container bytes into the carrier bitmap using LSB substitution
     * on Red, Green, and Blue channels sequentially. Leaves Alpha channel intact (0xFF).
     */
    fun embed(carrier: Bitmap, payloadContainer: ByteArray, bitsPerChannel: Int = 1): Bitmap {
        require(carrier.config == Bitmap.Config.ARGB_8888) { "Bitmap must be ARGB_8888 format" }
        val width = carrier.width
        val height = carrier.height
        val capacity = computeCapacity(width, height, bitsPerChannel)
        if (payloadContainer.size > capacity) {
            throw IllegalArgumentException(
                "Payload size (${payloadContainer.size} bytes) exceeds carrier capacity ($capacity bytes)"
            )
        }

        val pixels = IntArray(width * height)
        carrier.getPixels(pixels, 0, width, 0, 0, width, height)
        val modifiedPixels = embedInPixels(pixels, payloadContainer, bitsPerChannel)

        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        output.setPixels(modifiedPixels, 0, width, 0, 0, width, height)
        return output
    }

    /**
     * Extracts the embedded payload container bytes from the carrier bitmap.
     */
    fun extract(carrier: Bitmap, bitsPerChannel: Int = 1): ByteArray {
        require(carrier.config == Bitmap.Config.ARGB_8888) { "Bitmap must be ARGB_8888 format" }
        val width = carrier.width
        val height = carrier.height
        val pixels = IntArray(width * height)
        carrier.getPixels(pixels, 0, width, 0, 0, width, height)
        return extractFromPixels(pixels, bitsPerChannel)
    }

    /**
     * Dumps raw LSB bits sequentially without header checks.
     */
    fun extractRawBits(bitmap: Bitmap, bitsPerChannel: Int = 1, maxBytes: Int = 262144): ByteArray {
        require(bitmap.config == Bitmap.Config.ARGB_8888) { "Bitmap must be ARGB_8888 format" }
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        return extractRawBitsFromPixels(pixels, bitsPerChannel, maxBytes)
    }

    /**
     * Dumps raw LSB bits sequentially from pixel array without header checks.
     */
    fun extractRawBitsFromPixels(pixels: IntArray, bitsPerChannel: Int = 1, maxBytes: Int = 262144): ByteArray {
        require(bitsPerChannel in listOf(1, 2, 4)) { "bitsPerChannel must be 1, 2, or 4" }
        val totalChannels = pixels.size.toLong() * 3
        val capacity = (totalChannels * bitsPerChannel / 8).toInt()
        val bytesToExtract = minOf(capacity, maxBytes)
        if (bytesToExtract <= 0) return ByteArray(0)

        val chunksPerByte = 8 / bitsPerChannel
        val mask = (1 shl bitsPerChannel) - 1
        var channelCount = 0
        val result = ByteArray(bytesToExtract)

        for (i in 0 until bytesToExtract) {
            var byteVal = 0
            for (c in 0 until chunksPerByte) {
                val pixel = pixels[channelCount / 3]
                val channelVal = when (channelCount % 3) {
                    0 -> (pixel ushr 16) and 0xFF
                    1 -> (pixel ushr 8) and 0xFF
                    else -> pixel and 0xFF
                }
                val chunk = channelVal and mask
                byteVal = (byteVal shl bitsPerChannel) or chunk
                channelCount++
            }
            result[i] = byteVal.toByte()
        }
        return result
    }

    /**
     * Generates a visual grayscale Bitmap showing the specified bit plane (amplified to 0 or 255)
     * for X-Ray inspection.
     */
    fun generateBitPlane(carrier: Bitmap, bitIndex: Int = 0): Bitmap {
        val width = carrier.width
        val height = carrier.height
        val pixels = IntArray(width * height)
        carrier.getPixels(pixels, 0, width, 0, 0, width, height)
        val planePixels = generateBitPlanePixels(pixels, bitIndex)

        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        output.setPixels(planePixels, 0, width, 0, 0, width, height)
        return output
    }

    /**
     * Pure pixel-array bit embedding logic.
     */
    fun embedInPixels(pixels: IntArray, payloadContainer: ByteArray, bitsPerChannel: Int = 1): IntArray {
        require(bitsPerChannel in listOf(1, 2, 4)) { "bitsPerChannel must be 1, 2, or 4" }
        val totalChannels = pixels.size.toLong() * 3
        val capacity = (totalChannels * bitsPerChannel / 8).toInt()
        if (payloadContainer.size > capacity) {
            throw IllegalArgumentException(
                "Payload size (${payloadContainer.size} bytes) exceeds pixel capacity ($capacity bytes)"
            )
        }

        val resultPixels = pixels.copyOf()
        val mask = (1 shl bitsPerChannel) - 1
        val clearMask = 0xFF xor mask

        var channelCount = 0

        for (b in payloadContainer) {
            for (shift in (8 - bitsPerChannel) downTo 0 step bitsPerChannel) {
                val chunk = (b.toInt() ushr shift) and mask
                val pixelIndex = channelCount / 3
                val channelIndex = channelCount % 3

                val pixel = resultPixels[pixelIndex]
                val a = 0xFF // Leave alpha intact at 0xFF
                var r = (pixel ushr 16) and 0xFF
                var g = (pixel ushr 8) and 0xFF
                var bChan = pixel and 0xFF

                when (channelIndex) {
                    0 -> r = (r and clearMask) or chunk
                    1 -> g = (g and clearMask) or chunk
                    2 -> bChan = (bChan and clearMask) or chunk
                }

                resultPixels[pixelIndex] = (a shl 24) or (r shl 16) or (g shl 8) or bChan
                channelCount++
            }
        }

        return resultPixels
    }

    /**
     * Pure pixel-array bit extraction logic.
     */
    fun extractFromPixels(pixels: IntArray, bitsPerChannel: Int = 1): ByteArray {
        require(bitsPerChannel in listOf(1, 2, 4)) { "bitsPerChannel must be 1, 2, or 4" }
        val totalChannels = pixels.size.toLong() * 3
        val capacity = (totalChannels * bitsPerChannel / 8).toInt()

        val chunksPerByte = 8 / bitsPerChannel
        val mask = (1 shl bitsPerChannel) - 1
        var channelCount = 0

        fun readByte(): Byte {
            var byteVal = 0
            for (c in 0 until chunksPerByte) {
                val pixel = pixels[channelCount / 3]
                val channelVal = when (channelCount % 3) {
                    0 -> (pixel ushr 16) and 0xFF
                    1 -> (pixel ushr 8) and 0xFF
                    else -> pixel and 0xFF
                }
                val chunk = channelVal and mask
                byteVal = (byteVal shl bitsPerChannel) or chunk
                channelCount++
            }
            return byteVal.toByte()
        }

        // Extract 14 bytes header
        if (capacity < StegoProtocol.HEADER_MIN_LENGTH) {
            throw IllegalArgumentException("Carrier too small to contain SLOP header")
        }

        val headerBytes = ByteArray(StegoProtocol.HEADER_MIN_LENGTH)
        for (i in 0 until StegoProtocol.HEADER_MIN_LENGTH) {
            headerBytes[i] = readByte()
        }

        // Validate SLOP magic
        if (headerBytes[0] != 0x53.toByte() || headerBytes[1] != 0x4C.toByte() ||
            headerBytes[2] != 0x4F.toByte() || headerBytes[3] != 0x50.toByte()
        ) {
            throw IllegalArgumentException("Invalid magic bytes: SLOP header not found")
        }

        val flags = headerBytes[5].toInt() and 0xFF
        val isEncrypted = (flags and 0x01) != 0
        val hasFilename = (flags and StegoProtocol.FLAG_HAS_FILENAME) != 0
        val payloadLength = ((headerBytes[6].toLong() and 0xFF) shl 24) or
                ((headerBytes[7].toLong() and 0xFF) shl 16) or
                ((headerBytes[8].toLong() and 0xFF) shl 8) or
                (headerBytes[9].toLong() and 0xFF)

        val saltIvBytes = if (isEncrypted) {
            ByteArray(StegoProtocol.SALT_LENGTH + StegoProtocol.IV_LENGTH) { readByte() }
        } else {
            ByteArray(0)
        }

        val filenameBytes = if (hasFilename) {
            val nameLen = readByte().toInt() and 0xFF
            val nameData = ByteArray(nameLen) { readByte() }
            val nameBlock = ByteArray(1 + nameLen)
            nameBlock[0] = nameLen.toByte()
            System.arraycopy(nameData, 0, nameBlock, 1, nameLen)
            nameBlock
        } else {
            ByteArray(0)
        }

        val totalHeaderSize = StegoProtocol.HEADER_MIN_LENGTH + saltIvBytes.size + filenameBytes.size
        val totalSize = totalHeaderSize + payloadLength
        if (totalSize < 0 || totalSize > capacity) {
            throw IllegalArgumentException(
                "Corrupted container: total size $totalSize exceeds carrier capacity $capacity"
            )
        }

        val result = ByteArray(totalSize.toInt())
        var offset = 0
        System.arraycopy(headerBytes, 0, result, offset, headerBytes.size)
        offset += headerBytes.size
        if (saltIvBytes.isNotEmpty()) {
            System.arraycopy(saltIvBytes, 0, result, offset, saltIvBytes.size)
            offset += saltIvBytes.size
        }
        if (filenameBytes.isNotEmpty()) {
            System.arraycopy(filenameBytes, 0, result, offset, filenameBytes.size)
            offset += filenameBytes.size
        }

        for (i in offset until totalSize.toInt()) {
            result[i] = readByte()
        }

        return result
    }

    /**
     * Pure pixel-array bit plane visualization logic.
     */
    fun generateBitPlanePixels(pixels: IntArray, bitIndex: Int = 0): IntArray {
        require(bitIndex in 0..7) { "bitIndex must be between 0 and 7" }
        val out = IntArray(pixels.size)
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = (pixel ushr 16) and 0xFF
            val bit = (r ushr bitIndex) and 1
            val v = bit * 255
            out[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        return out
    }
}
