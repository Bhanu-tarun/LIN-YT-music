package com.lin.music.core.data.datastore

import android.annotation.SuppressLint
import android.content.Context
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import com.lin.music.core.innertube.SessionProvider
import com.lin.music.core.model.AccountSession
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.KeyStore
import javax.inject.Inject
import javax.inject.Singleton

private val Context.authDataStore: DataStore<Preferences> by preferencesDataStore(name = "lin_auth_store")

@Singleton
class AuthStore @Inject constructor(
    @ApplicationContext private val context: Context
) : SessionProvider {

    private val json = Json { ignoreUnknownKeys = true }
    private val keySession = stringPreferencesKey("encrypted_session")
    private val initMutex = Mutex()

    @Volatile
    private var aeadInstance: Aead? = null

    private suspend fun getAead(): Aead {
        aeadInstance?.let { return it }
        return initMutex.withLock {
            aeadInstance?.let { return@withLock it }
            val aead = withContext(Dispatchers.IO) {
                AeadConfig.register()
                runCatching { buildAead(useMasterKey = true) }.getOrElse {
                    resetBrokenKeysetAndKeystoreAlias()
                    runCatching { buildAead(useMasterKey = true) }.getOrElse {
                        clearKeysetPrefs()
                        buildAead(useMasterKey = false)
                    }
                }
            }
            aeadInstance = aead
            aead
        }
    }

    private fun buildAead(useMasterKey: Boolean): Aead {
        val builder = AndroidKeysetManager.Builder()
            .withSharedPref(context, KEYSET_NAME, KEYSET_PREF_NAME)
            .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
        if (useMasterKey) {
            builder.withMasterKeyUri(MASTER_KEY_URI)
        }
        return builder.build().keysetHandle.getPrimitive(Aead::class.java)
    }

    private fun resetBrokenKeysetAndKeystoreAlias() {
        clearKeysetPrefs()
        runCatching {
            val ks = KeyStore.getInstance("AndroidKeyStore")
            ks.load(null)
            if (ks.containsAlias(MASTER_KEY_ALIAS)) {
                ks.deleteEntry(MASTER_KEY_ALIAS)
            }
        }
    }

    @SuppressLint("ApplySharedPref")
    private fun clearKeysetPrefs() {
        context.getSharedPreferences(KEYSET_PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    val sessionFlow: Flow<AccountSession?> = context.authDataStore.data.map { prefs ->
        val encoded = prefs[keySession] ?: return@map null
        runCatching {
            val cipherBytes = Base64.decode(encoded, Base64.NO_WRAP)
            val plain = getAead().decrypt(cipherBytes, AUTH_ASSOCIATED_DATA)
            json.decodeFromString<AccountSession>(plain.decodeToString())
        }.getOrNull()
    }

    override suspend fun currentSession(): AccountSession? = sessionFlow.first()

    suspend fun saveSession(session: AccountSession) {
        val plain = json.encodeToString(session).encodeToByteArray()
        val encrypted = getAead().encrypt(plain, AUTH_ASSOCIATED_DATA)
        val encoded = Base64.encodeToString(encrypted, Base64.NO_WRAP)
        context.authDataStore.edit { prefs ->
            prefs[keySession] = encoded
        }
    }

    suspend fun clearSession() {
        context.authDataStore.edit { prefs ->
            prefs.remove(keySession)
        }
    }

    private companion object {
        const val KEYSET_NAME = "lin_auth_keyset"
        const val KEYSET_PREF_NAME = "lin_auth_keyset_prefs"
        const val MASTER_KEY_ALIAS = "lin_auth_master_key"
        const val MASTER_KEY_URI = "android-keystore://$MASTER_KEY_ALIAS"
        val AUTH_ASSOCIATED_DATA = "lin_ytm_session_v1".encodeToByteArray()
    }
}
