package com.ahsantraders.admin.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Tokens are encrypted with an Android Keystore key. Passwords are never stored. */
class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("admin_session", Context.MODE_PRIVATE)
    private val alias = "ahsan-admin-session"
    var baseUrl: String
        get() = prefs.getString("base_url", "http://10.0.2.2:8000/")!!
        set(value) { prefs.edit().putString("base_url", value).apply() }
    var language: String
        get() = prefs.getString("language", "en")!!
        set(value) { prefs.edit().putString("language", value).apply() }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun token(): String? = runCatching {
        val saved = prefs.getString("token", null) ?: return null
        val parts = saved.split(":")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
        String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8)
    }.getOrNull()

    fun saveToken(token: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val encrypted = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        prefs.edit().putString("token", Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(encrypted, Base64.NO_WRAP)).commit()
    }
    fun clearToken() { prefs.edit().remove("token").commit() }
    // Only request hashes and random keys are stored, never request/password content.
    fun requestKey(hash: String): String = prefs.getString("request:$hash", null) ?: java.util.UUID.randomUUID().toString().also {
        check(prefs.edit().putString("request:$hash", it).commit()) { "Unable to save retry key; request not sent" }
    }
    fun completeRequest(hash: String) { prefs.edit().remove("request:$hash").commit() }
}
