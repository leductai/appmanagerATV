package com.kaos.tvappmanager.data.source

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.kaos.tvappmanager.data.manifest.ManifestParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
data class ManifestSource(
    val id: String,
    val name: String,
    val location: String,
    val lastSyncMs: Long? = null,
    val lastError: String? = null,
)

enum class LanguagePreference(val code: String) {
    SYSTEM("system"),
    VI("vi"),
    EN("en");

    companion object {
        fun fromCode(code: String?): LanguagePreference =
            entries.firstOrNull { it.code == code } ?: SYSTEM
    }
}

/**
 * Nguon manifest va tuy chon toan cuc, luu bang DataStore Preferences.
 * Chi co mot nguon "dang hoat dong" tai mot thoi diem (yeu cau spec section 11).
 */
class SourceStore(private val dataStore: DataStore<Preferences>) {

    val sources: Flow<List<ManifestSource>> = dataStore.data.map { it.readSources() }
    val activeSource: Flow<ManifestSource?> = dataStore.data.map { prefs ->
        val id = prefs[KEY_ACTIVE_SOURCE]
        prefs.readSources().firstOrNull { it.id == id }
    }
    val wifiOnly: Flow<Boolean> = dataStore.data.map { it[KEY_WIFI_ONLY] ?: true }
    val timeoutSeconds: Flow<Int> = dataStore.data.map { it[KEY_TIMEOUT_SECONDS] ?: 60 }
    val autoDeleteApk: Flow<Boolean> = dataStore.data.map { it[KEY_AUTO_DELETE_APK] ?: true }
    val adbInstallEnabled: Flow<Boolean> = dataStore.data.map { it[KEY_ADB_INSTALL] ?: false }
    val language: Flow<LanguagePreference> = dataStore.data.map {
        LanguagePreference.fromCode(it[KEY_LANGUAGE])
    }

    /**
     * Da tung hien man hinh xin quyen chua? Spec section 3: neu nguoi dung tu choi
     * hoac chua bat quyen, app VAN phai mo duoc — chi vo hieu hoa nut cai, khong khoa
     * ca ung dung.
     */
    val hasSeenFirstRun: Flow<Boolean> = dataStore.data.map { it[KEY_HAS_SEEN_FIRST_RUN] ?: false }

    suspend fun setHasSeenFirstRun() = dataStore.edit { prefs -> prefs[KEY_HAS_SEEN_FIRST_RUN] = true }

    suspend fun snapshot(): StoreSnapshot {
        val prefs = dataStore.data.first()
        val list = prefs.readSources()
        val activeId = prefs[KEY_ACTIVE_SOURCE]
        return StoreSnapshot(
            sources = list,
            activeSource = list.firstOrNull { it.id == activeId },
            wifiOnly = prefs[KEY_WIFI_ONLY] ?: true,
            timeoutSeconds = prefs[KEY_TIMEOUT_SECONDS] ?: 60,
            autoDeleteApk = prefs[KEY_AUTO_DELETE_APK] ?: true,
            adbInstallEnabled = prefs[KEY_ADB_INSTALL] ?: false,
            language = LanguagePreference.fromCode(prefs[KEY_LANGUAGE]),
            hasSeenFirstRun = prefs[KEY_HAS_SEEN_FIRST_RUN] ?: false,
        )
    }

    /**
     * Dam bao luon co it nhat mot nguon: chi them nguon mac dinh khi danh sach dang
     * trong, de lan mo dau tien co san danh sach app. Khong bao gio ghi de nguon
     * nguoi dung da them.
     */
    suspend fun ensureDefaultSource() {
        dataStore.edit { prefs ->
            if (prefs.readSources().isNotEmpty()) return@edit
            val id = UUID.randomUUID().toString()
            prefs[KEY_SOURCES] = encode(
                listOf(ManifestSource(id = id, name = DEFAULT_SOURCE_NAME, location = DEFAULT_SOURCE_URL))
            )
            prefs[KEY_ACTIVE_SOURCE] = id
        }
    }

    /** Them nguon. Tra ve id cua nguon da them. */
    suspend fun addSource(name: String, location: String): String {
        val id = UUID.randomUUID().toString()
        dataStore.edit { prefs ->
            val list = prefs.readSources().toMutableList()
            list += ManifestSource(id = id, name = name.trim(), location = location.trim())
            prefs[KEY_SOURCES] = encode(list)
            if (prefs[KEY_ACTIVE_SOURCE] == null) prefs[KEY_ACTIVE_SOURCE] = id
        }
        return id
    }

    suspend fun updateSource(id: String, name: String, location: String) {
        dataStore.edit { prefs ->
            val list = prefs.readSources().map {
                if (it.id == id) it.copy(name = name.trim(), location = location.trim()) else it
            }
            prefs[KEY_SOURCES] = encode(list)
        }
    }

    suspend fun deleteSource(id: String) {
        dataStore.edit { prefs ->
            val list = prefs.readSources().filterNot { it.id == id }
            prefs[KEY_SOURCES] = encode(list)
            if (prefs[KEY_ACTIVE_SOURCE] == id) {
                if (list.isEmpty()) prefs.remove(KEY_ACTIVE_SOURCE)
                else prefs[KEY_ACTIVE_SOURCE] = list.first().id
            }
        }
    }

    suspend fun setActiveSource(id: String) {
        dataStore.edit { prefs -> prefs[KEY_ACTIVE_SOURCE] = id }
    }

    suspend fun recordSync(id: String, error: String?) {
        dataStore.edit { prefs ->
            val list = prefs.readSources().map {
                if (it.id == id) it.copy(lastSyncMs = System.currentTimeMillis(), lastError = error)
                else it
            }
            prefs[KEY_SOURCES] = encode(list)
        }
    }

    suspend fun setWifiOnly(value: Boolean) =
        dataStore.edit { prefs -> prefs[KEY_WIFI_ONLY] = value }

    suspend fun setTimeoutSeconds(value: Int) =
        dataStore.edit { prefs -> prefs[KEY_TIMEOUT_SECONDS] = value.coerceIn(5, 600) }

    suspend fun setAutoDeleteApk(value: Boolean) =
        dataStore.edit { prefs -> prefs[KEY_AUTO_DELETE_APK] = value }

    suspend fun setAdbInstallEnabled(value: Boolean) =
        dataStore.edit { prefs -> prefs[KEY_ADB_INSTALL] = value }

    suspend fun setLanguage(value: LanguagePreference) =
        dataStore.edit { prefs -> prefs[KEY_LANGUAGE] = value.code }

    data class StoreSnapshot(
        val sources: List<ManifestSource>,
        val activeSource: ManifestSource?,
        val wifiOnly: Boolean,
        val timeoutSeconds: Int,
        val autoDeleteApk: Boolean,
        val adbInstallEnabled: Boolean = false,
        val language: LanguagePreference,
        val hasSeenFirstRun: Boolean = false,
    )

    private fun Preferences.readSources(): List<ManifestSource> {
        val raw = this[KEY_SOURCES] ?: return emptyList()
        return try {
            json.decodeFromString<List<ManifestSource>>(raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    companion object {
        /** Nguon manifest mac dinh duoc them ngay khi cai app lan dau. */
        const val DEFAULT_SOURCE_URL =
            "https://raw.githubusercontent.com/leductai/appmanagerATV/refs/heads/main/app.json"
        const val DEFAULT_SOURCE_NAME = "Default"

        private val KEY_SOURCES = stringPreferencesKey("sources")
        private val KEY_ACTIVE_SOURCE = stringPreferencesKey("active_source")
        private val KEY_WIFI_ONLY = booleanPreferencesKey("wifi_only")
        private val KEY_TIMEOUT_SECONDS = intPreferencesKey("timeout_seconds")
        private val KEY_AUTO_DELETE_APK = booleanPreferencesKey("auto_delete_apk")
        private val KEY_ADB_INSTALL = booleanPreferencesKey("adb_install_enabled")
        private val KEY_LANGUAGE = stringPreferencesKey("language")
        private val KEY_HAS_SEEN_FIRST_RUN = booleanPreferencesKey("has_seen_first_run")

        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        private fun encode(sources: List<ManifestSource>): String = json.encodeToString(sources)

        /** Chi chap nhan https:// hoac URI file/content cho mot nguon manifest. */
        fun isValidLocation(location: String): Boolean {
            val value = location.trim()
            return when {
                value.startsWith("https://") -> ManifestParser.isHttps(value)
                value.startsWith("http://") -> false
                value.startsWith("file://") -> value.removePrefix("file://").isNotBlank()
                value.startsWith("content://") -> value.removePrefix("content://").isNotBlank()
                else -> false
            }
        }
    }
}
