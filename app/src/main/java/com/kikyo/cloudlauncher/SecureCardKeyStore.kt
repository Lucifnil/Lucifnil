package com.kikyo.cloudlauncher

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest

/**
 * Stores card keys only in Android Keystore-backed encrypted preferences.
 *
 * The old implementation created [EncryptedSharedPreferences] while the
 * ViewModel was being constructed.  A bad legacy encrypted file, a temporarily
 * unavailable Keystore, or an OEM crypto implementation error could therefore
 * terminate the whole process before Compose drew its first frame.  Secure
 * storage is now opened lazily and every access is contained: card keys are
 * never written to plaintext storage, but a secure-storage failure can no
 * longer crash the launcher UI.
 */
class SecureCardKeyStore(context: Context) {
    private val appContext = context.applicationContext
    private val selectionPreferences = appContext.getSharedPreferences(
        SELECTION_FILE_NAME,
        Context.MODE_PRIVATE,
    )

    @Volatile
    private var secureStorageError: Throwable? = null

    private val encryptedPreferences: SharedPreferences? by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        runCatching {
            EncryptedSharedPreferences.create(
                appContext,
                ENCRYPTED_FILE_NAME,
                MasterKey.Builder(appContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }.onFailure { error ->
            secureStorageError = error
        }.getOrNull()
    }

    /** Only a non-sensitive file name is kept in ordinary preferences. */
    fun loadSelectedSo(): String = selectionPreferences
        .getString(KEY_SELECTED_SO, "")
        .orEmpty()

    /**
     * Migrates the non-sensitive selection from versions which kept it in the
     * encrypted file.  This is deliberately not used in the constructor path.
     */
    fun restoreLegacySelectedSo(): String {
        loadSelectedSo().takeIf { it.isNotBlank() }?.let { return it }
        val legacySelection = safely { preferences ->
            preferences.getString(KEY_SELECTED_SO, "").orEmpty()
        }.orEmpty()
        if (legacySelection.isNotBlank()) {
            selectionPreferences.edit().putString(KEY_SELECTED_SO, legacySelection).apply()
        }
        return legacySelection
    }

    fun saveSelectedSo(fileName: String) {
        selectionPreferences.edit().putString(KEY_SELECTED_SO, fileName).apply()
    }

    fun clearSelectedSo() {
        selectionPreferences.edit().remove(KEY_SELECTED_SO).apply()
    }

    /** Card keys remain encrypted; failure means the save is refused, never downgraded. */
    fun loadCardKeyFor(soFileName: String): String = safely { preferences ->
        preferences.getString(cardKeyPreference(soFileName), "").orEmpty()
    }.orEmpty()

    fun hasSavedKeyFor(soFileName: String?): Boolean =
        soFileName != null && loadCardKeyFor(soFileName).isNotBlank()

    fun saveCardKeyFor(soFileName: String, cardKey: String): Boolean = safely { preferences ->
        preferences.edit().putString(cardKeyPreference(soFileName), cardKey).apply()
        true
    } ?: false

    fun clearCardKeyFor(soFileName: String): Boolean = safely { preferences ->
        preferences.edit().remove(cardKeyPreference(soFileName)).apply()
        true
    } ?: false

    fun secureStorageMessage(): String? = secureStorageError?.let {
        "安全存储暂时不可用（${it.javaClass.simpleName}），卡密未写入"
    }

    private fun <T> safely(block: (SharedPreferences) -> T): T? {
        val preferences = encryptedPreferences ?: return null
        return runCatching {
            block(preferences)
        }.onFailure { error ->
            secureStorageError = error
        }.getOrNull()
    }

    private fun cardKeyPreference(soFileName: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(soFileName.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
        return KEY_CARD_BY_SO_PREFIX + digest
    }

    private companion object {
        const val ENCRYPTED_FILE_NAME = "cloud_launcher_secure"
        const val SELECTION_FILE_NAME = "cloud_launcher_selection"
        const val KEY_CARD_BY_SO_PREFIX = "card_key_for_so_"
        const val KEY_SELECTED_SO = "selected_so_file"
    }
}
