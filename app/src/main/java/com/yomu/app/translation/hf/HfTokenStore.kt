package com.yomu.app.translation.hf

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores the HuggingFace OAuth token at rest, encrypted with an AES/GCM key held in the Android
 * Keystore (#90 part C). The key never leaves the Keystore; only IV+ciphertext are persisted in
 * plain SharedPreferences, so a prefs dump alone cannot reveal the token.
 *
 * ponytail: hand-rolled over androidx.security:security-crypto (EncryptedSharedPreferences), which is
 * in maintenance-only. This is ~one Keystore key + GCM round-trip; no dependency, no deprecation.
 */
@Singleton
class HfTokenStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(token: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
        val ciphertext = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString(KEY_IV, cipher.iv.b64())
            .putString(KEY_TOKEN, ciphertext.b64())
            .apply()
    }

    fun get(): String? {
        val ivB64 = prefs.getString(KEY_IV, null) ?: return null
        val tokenB64 = prefs.getString(KEY_TOKEN, null) ?: return null
        return try {
            val iv = Base64.decode(ivB64, Base64.NO_WRAP)
            val ciphertext = Base64.decode(tokenB64, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            }
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (_: Exception) {
            // Key rotated/invalidated (e.g. device credential change) — treat as signed-out.
            clear()
            null
        }
    }

    fun hasToken(): Boolean = prefs.contains(KEY_TOKEN)

    fun clear() {
        prefs.edit().remove(KEY_IV).remove(KEY_TOKEN).apply()
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    // NO_WRAP so the base64 stays single-line in prefs.
    private fun ByteArray.b64(): String = Base64.encodeToString(this, Base64.NO_WRAP)

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "yomu_hf_token_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private const val PREFS_NAME = "hf_auth"
        private const val KEY_IV = "token_iv"
        private const val KEY_TOKEN = "token_ct"
    }
}
