package com.p0lymatic.stegoslop.core

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Cryptographic utility providing AES-256-GCM authenticated encryption and decryption
 * with PBKDF2WithHmacSHA256 key derivation.
 */
object CryptoUtils {
    private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val KEY_LENGTH_BITS = 256
    private const val PBKDF2_ITERATIONS = 10_000
    private const val GCM_TAG_LENGTH_BITS = 128

    fun generateSalt(size: Int = 16): ByteArray {
        val salt = ByteArray(size)
        SecureRandom().nextBytes(salt)
        return salt
    }

    fun generateIv(size: Int = 12): ByteArray {
        val iv = ByteArray(size)
        SecureRandom().nextBytes(iv)
        return iv
    }

    fun deriveKey(password: String, salt: ByteArray): SecretKey {
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    fun encrypt(data: ByteArray, password: String, salt: ByteArray, iv: ByteArray): ByteArray {
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        return cipher.doFinal(data)
    }

    fun decrypt(ciphertext: ByteArray, password: String, salt: ByteArray, iv: ByteArray): ByteArray {
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        return cipher.doFinal(ciphertext)
    }
}
