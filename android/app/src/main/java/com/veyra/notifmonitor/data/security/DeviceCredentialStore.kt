package com.veyra.notifmonitor.data.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Securely stores the Device Token using Android Keystore directly for AES-GCM encryption.
 * Ciphertexts are stored in standard SharedPreferences.
 * Removes dependency on the deprecated androidx.security.crypto APIs.
 */
class DeviceCredentialStore(context: Context) {

    private val sharedPreferences: SharedPreferences = context.getSharedPreferences("veyra_secure_prefs", Context.MODE_PRIVATE)

    init {
        initKeystore()
    }

    private fun initKeystore() {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)

        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
            keyGenerator.init(keyGenParameterSpec)
            keyGenerator.generateKey()
        }
    }

    private fun getSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        return keyStore.getKey(KEY_ALIAS, null) as SecretKey
    }

    private fun encrypt(plaintext: String?): String? {
        if (plaintext == null) return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        
        // Format: IV(Base64) + ":" + Ciphertext(Base64)
        val ivString = Base64.encodeToString(iv, Base64.NO_WRAP)
        val cipherString = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
        return "$ivString:$cipherString"
    }

    private fun decrypt(encryptedData: String?): String? {
        if (encryptedData == null || !encryptedData.contains(":")) return null
        return try {
            val parts = encryptedData.split(":")
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val ciphertext = Base64.decode(parts[1], Base64.NO_WRAP)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)
            
            val plaintext = cipher.doFinal(ciphertext)
            String(plaintext, Charsets.UTF_8)
        } catch (e: Exception) {
            null // Decryption failure, key lost or corrupted
        }
    }

    fun saveCredentials(serverUrl: String, token: String, deviceId: String) {
        sharedPreferences.edit()
            .putString(KEY_SERVER_URL, encrypt(serverUrl))
            .putString(KEY_TOKEN, encrypt(token))
            .putString(KEY_DEVICE_ID, encrypt(deviceId))
            .apply()
    }

    fun getServerUrl(): String? = decrypt(sharedPreferences.getString(KEY_SERVER_URL, null))

    fun getToken(): String? = decrypt(sharedPreferences.getString(KEY_TOKEN, null))

    fun getDeviceId(): String? = decrypt(sharedPreferences.getString(KEY_DEVICE_ID, null))

    fun clear() {
        sharedPreferences.edit().clear().apply()
    }

    fun isProvisioned(): Boolean {
        return !getToken().isNullOrEmpty() && !getServerUrl().isNullOrEmpty()
    }

    enum class AuthState {
        AUTHENTICATED,
        AUTH_ERROR,
        DEVICE_REVOKED
    }

    fun getAuthState(): AuthState {
        val stateString = sharedPreferences.getString(KEY_AUTH_STATE, AuthState.AUTHENTICATED.name)
        return try {
            AuthState.valueOf(stateString!!)
        } catch (e: Exception) {
            AuthState.AUTHENTICATED
        }
    }

    fun setAuthState(state: AuthState) {
        sharedPreferences.edit().putString(KEY_AUTH_STATE, state.name).apply()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "veyra_device_key"
        
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_TOKEN = "device_token"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_AUTH_STATE = "auth_state"
    }
}
