package com.santosxd.diario.security

import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Formato del archivo de copia: "DIARIO1" + sal(16) + iv(12) + datos cifrados (AES-256-GCM).
 * La clave sale de la contraseña con PBKDF2-HMAC-SHA256.
 */
object Crypto {
    private val MAGIC = "DIARIO1".toByteArray(Charsets.US_ASCII)
    private const val ITERATIONS = 210_000
    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    private val random = SecureRandom()

    private fun deriveKey(pass: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(pass, salt, ITERATIONS, 256)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    fun encrypt(plain: ByteArray, pass: CharArray): ByteArray {
        val salt = ByteArray(SALT_LEN).also { random.nextBytes(it) }
        val iv = ByteArray(IV_LEN).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(pass, salt), GCMParameterSpec(128, iv))
        val body = cipher.doFinal(plain)
        return MAGIC + salt + iv + body
    }

    /** Devuelve null si la contraseña es incorrecta o el archivo no es válido. */
    fun decrypt(data: ByteArray, pass: CharArray): ByteArray? {
        val headerLen = MAGIC.size + SALT_LEN + IV_LEN
        if (data.size <= headerLen) return null
        for (i in MAGIC.indices) {
            if (data[i] != MAGIC[i]) return null
        }
        val salt = data.copyOfRange(MAGIC.size, MAGIC.size + SALT_LEN)
        val iv = data.copyOfRange(MAGIC.size + SALT_LEN, headerLen)
        val body = data.copyOfRange(headerLen, data.size)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(pass, salt), GCMParameterSpec(128, iv))
            cipher.doFinal(body)
        } catch (e: GeneralSecurityException) {
            null
        }
    }
}
