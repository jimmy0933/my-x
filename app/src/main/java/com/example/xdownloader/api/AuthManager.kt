package com.example.xdownloader.api

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AuthManager(private val context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "secure_x_auth",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val _authState = MutableStateFlow(isConfigured())
    val authState: StateFlow<Boolean> = _authState.asStateFlow()

    fun saveCredentials(authToken: String, ct0: String) {
        sharedPreferences.edit()
            .putString("auth_token", authToken)
            .putString("ct0", ct0)
            .apply()
        _authState.value = isConfigured()
    }

    fun getAuthToken(): String? {
        return sharedPreferences.getString("auth_token", null)
    }

    fun getCt0(): String? {
        return sharedPreferences.getString("ct0", null)
    }

    fun clearCredentials() {
        sharedPreferences.edit().clear().apply()
        _authState.value = false
    }

    fun isConfigured(): Boolean {
        return !getAuthToken().isNullOrEmpty() && !getCt0().isNullOrEmpty()
    }

    companion object {
        @Volatile
        private var INSTANCE: AuthManager? = null

        fun getInstance(context: Context): AuthManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AuthManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
