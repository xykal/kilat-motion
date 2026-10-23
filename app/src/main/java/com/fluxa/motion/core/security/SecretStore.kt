package com.fluxa.motion.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Enkripsi nilai rahasia (API key, id_token) sebelum disimpan di SharedPreferences.
 * Kunci AES-256 disimpan di Android Keystore, tidak pernah keluar dari perangkat.
 * Hasil enkripsi berformat "iv:ciphertext" (Base64 tanpa wrap).
 */
object SecretStore {
    private const val KEY_ALIAS = "fluxa_secret_v1"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORM = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    /** Enkripsi teks. Mengembalikan string kosong bila input kosong. */
    fun encrypt(plain: String): String {
        if (plain.isEmpty()) return ""
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return SecretFormat.pack(cipher.iv, ct)
    }

    /** Dekripsi. Mengembalikan null bila format salah atau kunci sudah tidak ada. */
    fun decrypt(stored: String): String? {
        if (stored.isEmpty()) return ""
        val parts = SecretFormat.unpack(stored) ?: return null
        return try {
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, parts.first))
            String(cipher.doFinal(parts.second), Charsets.UTF_8)
        } catch (e: java.security.GeneralSecurityException) {
            null
        } catch (e: IllegalStateException) {
            null
        }
    }
}

/** Format penyimpanan terenkripsi. Dipisah dari Keystore supaya bisa diuji unit. */
object SecretFormat {
    fun pack(iv: ByteArray, ciphertext: ByteArray): String =
        Base64.getEncoder().encodeToString(iv) + ":" + Base64.getEncoder().encodeToString(ciphertext)

    /** Mengembalikan (iv, ciphertext), atau null bila bentuknya tidak valid. */
    fun unpack(stored: String): Pair<ByteArray, ByteArray>? {
        val idx = stored.indexOf(':')
        if (idx <= 0 || idx == stored.length - 1) return null
        return try {
            val iv = Base64.getDecoder().decode(stored.substring(0, idx))
            val ct = Base64.getDecoder().decode(stored.substring(idx + 1))
            if (iv.size != 12 || ct.isEmpty()) null else iv to ct
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}
