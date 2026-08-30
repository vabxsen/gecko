package com.gecko.core.security

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.util.Base64
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores API keys as AES-256-GCM ciphertext under a private SharedPreferences file. The
 * encryption key itself is generated inside AndroidKeyStore (StrongBox-backed where the
 * device supports it) and never leaves secure hardware, so the persisted ciphertext is
 * useless without it.
 */
class AndroidKeystoreSecureKeyStore(
    context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SecureKeyStore {

    private val appContext = context.applicationContext
    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    override suspend fun saveApiKey(id: String, key: String) = withContext(dispatcher) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val ciphertext = cipher.doFinal(key.toByteArray(Charsets.UTF_8))
        val combined = cipher.iv + ciphertext
        val encoded = Base64.encodeToString(combined, Base64.NO_WRAP)
        prefs().edit().putString(prefsKey(id), encoded).apply()
    }

    override suspend fun getApiKey(id: String): String? = withContext(dispatcher) {
        val encoded = prefs().getString(prefsKey(id), null) ?: return@withContext null
        val combined = Base64.decode(encoded, Base64.NO_WRAP)
        if (combined.size <= IV_LENGTH_BYTES) return@withContext null
        val iv = combined.copyOfRange(0, IV_LENGTH_BYTES)
        val ciphertext = combined.copyOfRange(IV_LENGTH_BYTES, combined.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    override suspend fun clearApiKey(id: String) = withContext(dispatcher) {
        prefs().edit().remove(prefsKey(id)).apply()
    }

    override suspend fun hasApiKey(id: String): Boolean = withContext(dispatcher) {
        prefs().contains(prefsKey(id))
    }

    private fun prefs() = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun prefsKey(id: String) = "api_key_$id"

    private fun getOrCreateSecretKey(): SecretKey {
        (keyStore.getKey(KEYSTORE_ALIAS, null) as? SecretKey)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val baseSpec = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_SIZE_BITS)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                keyGenerator.init(baseSpec.setIsStrongBoxBacked(true).build())
                return keyGenerator.generateKey()
            } catch (_: StrongBoxUnavailableException) {
                // Fall through to a non-StrongBox key below.
            }
        }

        keyGenerator.init(baseSpec.setIsStrongBoxBacked(false).build())
        return keyGenerator.generateKey()
    }

    private companion object {
        const val PREFS_NAME = "gecko_secure_prefs"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEYSTORE_ALIAS = "gecko_api_key_secret"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH_BITS = 128
        const val IV_LENGTH_BYTES = 12
        const val KEY_SIZE_BITS = 256
    }
}
