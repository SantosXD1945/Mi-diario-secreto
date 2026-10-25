package com.santosxd.diario.security

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Ajustes locales. Del PIN solo se guarda un hash con sal, nunca el PIN. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("diario_prefs", Context.MODE_PRIVATE)

    var theme: String
        get() = sp.getString("theme", "dark") ?: "dark"
        set(value) = sp.edit().putString("theme", value).apply()

    var biometric: Boolean
        get() = sp.getBoolean("biometric", false)
        set(value) = sp.edit().putBoolean("biometric", value).apply()

    var backupTree: String?
        get() = sp.getString("backup_tree", null)
        set(value) = sp.edit().putString("backup_tree", value).apply()

    val pinLength: Int
        get() = sp.getInt("pin_len", 4)

    fun hasPin(): Boolean = sp.contains("pin_hash")

    fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        sp.edit()
            .putString("pin_salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString("pin_hash", Base64.encodeToString(hash(pin, salt), Base64.NO_WRAP))
            .putInt("pin_len", pin.length)
            .putInt("fails", 0)
            .putLong("lock_until", 0L)
            .apply()
    }

    fun clearPin() {
        sp.edit()
            .remove("pin_salt")
            .remove("pin_hash")
            .remove("pin_len")
            .remove("fails")
            .remove("lock_until")
            .putBoolean("biometric", false)
            .apply()
    }

    fun lockedForMs(): Long =
        maxOf(0L, sp.getLong("lock_until", 0L) - System.currentTimeMillis())

    /** Comprueba el PIN. Tras 5 fallos seguidos, bloquea el intento unos segundos. */
    fun verify(pin: String): Boolean {
        if (lockedForMs() > 0) return false
        val saltText = sp.getString("pin_salt", null) ?: return false
        val hashText = sp.getString("pin_hash", null) ?: return false
        val salt = Base64.decode(saltText, Base64.NO_WRAP)
        val expected = Base64.decode(hashText, Base64.NO_WRAP)
        val ok = MessageDigest.isEqual(hash(pin, salt), expected)
        if (ok) {
            sp.edit().putInt("fails", 0).putLong("lock_until", 0L).apply()
        } else {
            val fails = sp.getInt("fails", 0) + 1
            val editor = sp.edit().putInt("fails", fails)
            if (fails >= 5) {
                editor.putLong("lock_until", System.currentTimeMillis() + 30_000L * (fails - 4))
            }
            editor.apply()
        }
        return ok
    }

    private fun hash(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 120_000, 256)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}
