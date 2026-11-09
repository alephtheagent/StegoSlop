package com.p0lymatic.stegoslop.core

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Zero-Width Unicode Steganography engine for hiding secret data inside visible text.
 */
object ZeroWidthStego {
    const val CHAR_ZERO = '\u200C'    // Bit 0 (Zero-Width Non-Joiner, survives Telegram/chat sanitizers)
    const val CHAR_ONE = '\u200D'     // Bit 1 (Zero-Width Joiner, survives Telegram/chat sanitizers)
    const val CHAR_DELIM = '\u2060'   // Delimiter / Separator (Word Joiner, survives Telegram)
    const val CHAR_HEADER = '\uFEFF'  // Magic Header (Zero-Width No-Break Space)

    val SIGNATURE = byteArrayOf(0x53, 0x4C, 0x4F, 0x50) // "SLOP"

    /**
     * Checks if the text contains the magic header followed by zero-width characters.
     */
    fun hasHiddenData(text: String): Boolean {
        val idx = text.indexOf(CHAR_HEADER)
        if (idx == -1 || idx >= text.length - 1) return false
        val next = text[idx + 1]
        return next == CHAR_ZERO || next == CHAR_ONE || next == CHAR_DELIM
    }

    /**
     * Encodes secret data into cover text using zero-width Unicode characters.
     */
    fun encode(coverText: String, secretData: ByteArray, password: String? = null): String {
        val isEncrypted = !password.isNullOrEmpty()
        val payload: ByteArray
        val flags: Byte

        if (isEncrypted) {
            val salt = CryptoUtils.generateSalt(16)
            val iv = CryptoUtils.generateIv(12)
            val cipherText = CryptoUtils.encrypt(secretData, password!!, salt, iv)
            payload = ByteArray(16 + 12 + cipherText.size)
            System.arraycopy(salt, 0, payload, 0, 16)
            System.arraycopy(iv, 0, payload, 16, 12)
            System.arraycopy(cipherText, 0, payload, 28, cipherText.size)
            flags = 0x01
        } else {
            payload = secretData
            flags = 0x00
        }

        val totalSize = SIGNATURE.size + 1 + 4 + payload.size
        val buffer = ByteBuffer.allocate(totalSize).order(ByteOrder.BIG_ENDIAN)
        buffer.put(SIGNATURE)
        buffer.put(flags)
        buffer.putInt(payload.size)
        buffer.put(payload)
        val packetBytes = buffer.array()

        val zwBuilder = java.lang.StringBuilder()
        zwBuilder.append(CHAR_HEADER)
        for (b in packetBytes) {
            val intVal = b.toInt() and 0xFF
            for (bit in 7 downTo 0) {
                if (((intVal ushr bit) and 1) == 1) {
                    zwBuilder.append(CHAR_ONE)
                } else {
                    zwBuilder.append(CHAR_ZERO)
                }
            }
        }
        zwBuilder.append(CHAR_DELIM)
        val zwString = zwBuilder.toString()

        val firstSpace = coverText.indexOf(' ')
        val insertIndex = if (firstSpace != -1) {
            firstSpace + 1
        } else {
            coverText.length / 2
        }

        return coverText.substring(0, insertIndex) + zwString + coverText.substring(insertIndex)
    }

    /**
     * Decodes secret data from text containing zero-width Unicode characters.
     */
    fun decode(text: String, password: String? = null): ByteArray {
        val headerIdx = text.indexOf(CHAR_HEADER)
        if (headerIdx == -1) {
            throw IllegalArgumentException("No hidden zero-width data found: missing magic header")
        }

        val bitBuilder = java.lang.StringBuilder()
        for (i in (headerIdx + 1) until text.length) {
            val c = text[i]
            if (c == CHAR_ZERO) {
                bitBuilder.append('0')
            } else if (c == CHAR_ONE) {
                bitBuilder.append('1')
            } else if (c == CHAR_DELIM) {
                break
            } else {
                break
            }
        }

        val bitStr = bitBuilder.toString()
        val numBytes = bitStr.length / 8
        if (numBytes < SIGNATURE.size + 1 + 4) {
            throw IllegalArgumentException("Extracted zero-width data too short: $numBytes bytes")
        }

        val rawBytes = ByteArray(numBytes)
        for (i in 0 until numBytes) {
            var b = 0
            for (bit in 0 until 8) {
                val bitVal = if (bitStr[i * 8 + bit] == '1') 1 else 0
                b = (b shl 1) or bitVal
            }
            rawBytes[i] = b.toByte()
        }

        val buffer = ByteBuffer.wrap(rawBytes).order(ByteOrder.BIG_ENDIAN)
        val sig = ByteArray(SIGNATURE.size)
        buffer.get(sig)
        if (!sig.contentEquals(SIGNATURE)) {
            throw IllegalArgumentException("Invalid zero-width signature")
        }

        val flags = buffer.get().toInt() and 0xFF
        val isEncrypted = (flags and 0x01) != 0
        val payloadLength = buffer.int
        if (payloadLength < 0 || buffer.remaining() < payloadLength) {
            throw IllegalArgumentException("Corrupted or truncated zero-width payload: expected $payloadLength, got ${buffer.remaining()}")
        }

        val payload = ByteArray(payloadLength)
        buffer.get(payload)

        return if (isEncrypted) {
            if (password.isNullOrEmpty()) {
                throw IllegalArgumentException("Password required for encrypted payload")
            }
            if (payload.size < 16 + 12) {
                throw IllegalArgumentException("Corrupted encrypted payload: missing salt/IV")
            }
            val salt = payload.copyOfRange(0, 16)
            val iv = payload.copyOfRange(16, 28)
            val ciphertext = payload.copyOfRange(28, payload.size)
            try {
                CryptoUtils.decrypt(ciphertext, password, salt, iv)
            } catch (e: Exception) {
                throw IllegalArgumentException("Decryption failed: invalid password or corrupted data", e)
            }
        } else {
            payload
        }
    }
}
