package com.yomu.app.translation

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * The AES-GCM key held in the Android Keystore under [alias], made on first use. Its bytes never leave
 * the Keystore; a cipher given it runs there. Throws when the Keystore can't be used, which
 * [ServerSettingsStore] answers by holding the API key in memory.
 */
fun androidKeystoreKey(alias: String = SERVER_KEY_ALIAS): SecretKey {
    val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    (keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
    val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setKeySize(KEY_BITS)
        .build()
    return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).apply { init(spec) }.generateKey()
}

// Not the retired `yomu_hf_token_v1`, which YomuApplication still deletes on every start.
private const val SERVER_KEY_ALIAS = "yomu_server_key_v1"
private const val ANDROID_KEYSTORE = "AndroidKeyStore"
private const val KEY_BITS = 256
