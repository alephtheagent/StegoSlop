package com.p0lymatic.stegoslop.core

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

/**
 * Supported payload types in the StegoSlop protocol.
 */
enum class PayloadType(val code: Byte) {
    TEXT(0x01),
    IMAGE(0x02),
    AUDIO(0x03),
    FILE(0x04);

    companion object {
        fun fromCode(code: Byte): PayloadType {
            return entries.firstOrNull { it.code == code }
                ?: throw IllegalArgumentException("Unknown payload type code: $code")
        }
    }
}

/**
 * Result of unpacking a StegoSlop container.
 */
data class UnpackedPayload(
    val type: PayloadType,
    val data: ByteArray,
    val isEncrypted: Boolean = false,
    val bitsPerUnit: Int = 1,
    val fileName: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as UnpackedPayload
        if (type != other.type) return false
        if (!data.contentEquals(other.data)) return false
        if (isEncrypted != other.isEncrypted) return false
        if (bitsPerUnit != other.bitsPerUnit) return false
        if (fileName != other.fileName) return false
        return true
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + data.contentHashCode()
        result = 31 * result + isEncrypted.hashCode()
        result = 31 * result + bitsPerUnit
        result = 31 * result + (fileName?.hashCode() ?: 0)
        return result
    }
}

/**
 * Binary container serialization and deserialization for the StegoSlop protocol.
 */
object StegoProtocol {
    val MAGIC_BYTES = byteArrayOf(0x53.toByte(), 0x4C.toByte(), 0x4F.toByte(), 0x50.toByte()) // "SLOP"
    const val SALT_LENGTH = 16
    const val IV_LENGTH = 12
    const val HEADER_MIN_LENGTH = 14 // 4 (magic) + 1 (type) + 1 (flags) + 4 (length) + 4 (crc32)
    const val FLAG_HAS_FILENAME = 0x08

    /**
     * Packs payload data into a StegoSlop container.
     */
    fun packPayload(
        type: PayloadType,
        data: ByteArray,
        password: String? = null,
        bitsPerUnit: Int = 1,
        fileName: String? = null
    ): ByteArray {
        val isEncrypted = !password.isNullOrEmpty()
        val bpuCode = when (bitsPerUnit) {
            1 -> 0
            2 -> 1
            4 -> 2
            else -> throw IllegalArgumentException("Unsupported bitsPerUnit: $bitsPerUnit. Must be 1, 2, or 4.")
        }

        var flags = (bpuCode shl 1)
        if (isEncrypted) {
            flags = flags or 0x01
        }

        val fileNameBytes = if (fileName != null) {
            flags = flags or FLAG_HAS_FILENAME
            val bytes = fileName.toByteArray(Charsets.UTF_8)
            require(bytes.size <= 255) { "Filename too long (max 255 bytes in UTF-8)" }
            bytes
        } else {
            null
        }

        val salt: ByteArray?
        val iv: ByteArray?
        val payloadData: ByteArray

        if (isEncrypted) {
            salt = CryptoUtils.generateSalt(SALT_LENGTH)
            iv = CryptoUtils.generateIv(IV_LENGTH)
            payloadData = CryptoUtils.encrypt(data, password!!, salt, iv)
        } else {
            salt = null
            iv = null
            payloadData = data
        }

        val crc = CRC32().apply { update(payloadData) }.value
        val fileNameLength = if (fileNameBytes != null) 1 + fileNameBytes.size else 0
        val headerLength = HEADER_MIN_LENGTH + (if (isEncrypted) SALT_LENGTH + IV_LENGTH else 0) + fileNameLength
        val totalSize = headerLength + payloadData.size

        val buffer = ByteBuffer.allocate(totalSize).order(ByteOrder.BIG_ENDIAN)
        buffer.put(MAGIC_BYTES)
        buffer.put(type.code)
        buffer.put(flags.toByte())
        buffer.putInt(payloadData.size)
        buffer.putInt((crc and 0xFFFFFFFFL).toInt())
        if (isEncrypted) {
            buffer.put(salt!!)
            buffer.put(iv!!)
        }
        if (fileNameBytes != null) {
            buffer.put(fileNameBytes.size.toByte())
            buffer.put(fileNameBytes)
        }
        buffer.put(payloadData)
        return buffer.array()
    }

    /**
     * Unpacks a StegoSlop container.
     */
    fun unpackPayload(
        containerBytes: ByteArray,
        password: String? = null
    ): UnpackedPayload {
        if (containerBytes.size < HEADER_MIN_LENGTH) {
            throw IllegalArgumentException("Container too short: ${containerBytes.size} bytes (minimum $HEADER_MIN_LENGTH required)")
        }

        val buffer = ByteBuffer.wrap(containerBytes).order(ByteOrder.BIG_ENDIAN)

        val magic = ByteArray(4)
        buffer.get(magic)
        if (!magic.contentEquals(MAGIC_BYTES)) {
            throw IllegalArgumentException("Invalid magic bytes: expected 'SLOP'")
        }

        val type = PayloadType.fromCode(buffer.get())

        val flags = buffer.get().toInt() and 0xFF
        val isEncrypted = (flags and 0x01) != 0
        val bpuCode = (flags ushr 1) and 0x03
        val bitsPerUnit = when (bpuCode) {
            0 -> 1
            1 -> 2
            2 -> 4
            else -> throw IllegalArgumentException("Invalid bits per unit code: $bpuCode")
        }

        val payloadLength = buffer.int.toLong() and 0xFFFFFFFFL
        val expectedCrc = buffer.int.toLong() and 0xFFFFFFFFL

        val salt = if (isEncrypted) {
            if (buffer.remaining() < SALT_LENGTH) {
                throw IllegalArgumentException("Container truncated: missing salt")
            }
            ByteArray(SALT_LENGTH).also { buffer.get(it) }
        } else null

        val iv = if (isEncrypted) {
            if (buffer.remaining() < IV_LENGTH) {
                throw IllegalArgumentException("Container truncated: missing IV")
            }
            ByteArray(IV_LENGTH).also { buffer.get(it) }
        } else null
        val fileName = if ((flags and FLAG_HAS_FILENAME) != 0) {
            if (buffer.remaining() < 1) {
                throw IllegalArgumentException("Container truncated: missing filename length")
            }
            val fileNameLen = buffer.get().toInt() and 0xFF
            if (buffer.remaining() < fileNameLen) {
                throw IllegalArgumentException("Container truncated: missing filename")
            }
            val fileNameBytes = ByteArray(fileNameLen)
            buffer.get(fileNameBytes)
            String(fileNameBytes, Charsets.UTF_8)
        } else null

        if (buffer.remaining().toLong() < payloadLength) {
            throw IllegalArgumentException("Container truncated: expected $payloadLength payload bytes, found ${buffer.remaining()}")
        }

        val payloadData = ByteArray(payloadLength.toInt())
        buffer.get(payloadData)

        val actualCrc = CRC32().apply { update(payloadData) }.value
        if (actualCrc != expectedCrc) {
            throw IllegalArgumentException("CRC32 checksum mismatch: expected $expectedCrc, got $actualCrc")
        }

        val data = if (isEncrypted) {
            if (password.isNullOrEmpty()) {
                throw IllegalArgumentException("Password required for encrypted payload")
            }
            try {
                CryptoUtils.decrypt(payloadData, password, salt!!, iv!!)
            } catch (e: Exception) {
                throw IllegalArgumentException("Decryption failed: invalid password or corrupted data", e)
            }
        } else {
            payloadData
        }

        return UnpackedPayload(
            type = type,
            data = data,
            isEncrypted = isEncrypted,
            bitsPerUnit = bitsPerUnit,
            fileName = fileName
        )
    }
}
