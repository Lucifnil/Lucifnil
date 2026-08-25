package com.kikyo.cloudlauncher

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest

/**
 * Keeps the card key in Android Keystore-backed encrypted preferences instead
 * of ordinary plaintext SharedPreferences.
 */
class SecureCardKeyStore(context: Context) {
    private val preferences = EncryptedSharedPreferences.create(
        context,
        FILE_NAME,
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    /**
     * Card keys belong to a specific selected SO.  The preference key uses a
     * SHA-256 identifier so filenames are not stored as plaintext preference
     * keys, and two SO files never overwrite one another's card key.
     */
    fun loadCardKeyFor(soFileName: String): String =
        preferences.getString(cardKeyPreference(soFileName), "") ?: ""

    fun hasSavedKeyFor(soFileName: String?): Boolean =
        soFileName != null && loadCardKeyFor(soFileName).isNotBlank()

    fun saveCardKeyFor(soFileName: String, cardKey: String) {
        preferences.edit().putString(cardKeyPreference(soFileName), cardKey).apply()
    }

    fun clearCardKeyFor(soFileName: String) {
        preferences.edit().remove(cardKeyPreference(soFileName)).apply()
    }

    fun loadSelectedSo(): String = preferences.getString(KEY_SELECTED_SO, "") ?: ""

    fun saveSelectedSo(fileName: String) {
        preferences.edit().putString(KEY_SELECTED_SO, fileName).apply()
    }

    fun clearSelectedSo() {
        preferences.edit().remove(KEY_SELECTED_SO).apply()
    }

    private fun cardKeyPreference(soFileName: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(soFileName.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
        return KEY_CARD_BY_SO_PREFIX + digest
    }

    private companion object {
        const val FILE_NAME = "cloud_launcher_secure"
        const val KEY_CARD_BY_SO_PREFIX = "card_key_for_so_"
        const val KEY_SELECTED_SO = "selected_so_file"
    }
}
