package com.xanichka.xacode.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Keeps provider credentials encrypted by a non-exportable Android Keystore key. */
class SecretStore(context: Context) {
    private val preferences = context.getSharedPreferences("xacode_secrets", Context.MODE_PRIVATE)

    fun writeApiKey(profileId: String, value: String) {
        val valueName = "apiKey:$profileId"
        val ivName = "apiKeyIv:$profileId"
        if (value.isBlank()) {
            preferences.edit().remove(valueName).remove(ivName).apply()
            return
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        preferences.edit()
            .putString(valueName, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString(ivName, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
    }

    fun readApiKey(profileId: String): String {
        val useLegacy = profileId == "deepseek-default" &&
            !preferences.contains("apiKey:$profileId") && preferences.contains("apiKey")
        val valueName = if (useLegacy) "apiKey" else "apiKey:$profileId"
        val ivName = if (useLegacy) "apiKeyIv" else "apiKeyIv:$profileId"
        val value = runCatching {
        val encrypted = preferences.getString(valueName, null) ?: return@runCatching ""
        val iv = preferences.getString(ivName, null) ?: return@runCatching ""
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP))
        )
        cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP)).toString(Charsets.UTF_8)
        }.getOrElse {
        preferences.edit().remove("apiKey:$profileId").remove("apiKeyIv:$profileId").apply()
        ""
        }
        if (useLegacy && value.isNotBlank()) {
            writeApiKey(profileId, value)
            preferences.edit().remove("apiKey").remove("apiKeyIv").apply()
        }
        return value
    }

    fun deleteApiKey(profileId: String) = writeApiKey(profileId, "")

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
            generateKey()
        }
    }

    private companion object {
        const val KEY_ALIAS = "xacode_provider_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
