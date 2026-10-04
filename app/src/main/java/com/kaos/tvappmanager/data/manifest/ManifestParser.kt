package com.kaos.tvappmanager.data.manifest

import kotlinx.serialization.json.Json

/**
 * Doc va kiem tra manifest schema v1.
 *
 * - Khong dung schema khac `schemaVersion = 1`.
 * - Bo mot muc trong `apps` (khong bo toan bo manifest) khi co can cu sau:
 *   thieu truong bat buoc, khong trung package, URL APK khong phai https://.
 *   Mot loi so cua chu doc van cho phep app van dung duoc, khong bi chet toan bo.
 */
object ManifestParser {

    const val SUPPORTED_SCHEMA_VERSION = 1

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    data class Result(
        val manifest: Manifest? = null,
        val error: String? = null,
        val warnings: List<String> = emptyList(),
    )

    fun parse(raw: String): Result {
        if (raw.isBlank()) return Result(error = "Manifest rong.")

        val decoded = try {
            json.decodeFromString<Manifest>(raw)
        } catch (e: Exception) {
            return Result(error = "JSON khong hop le: ${e.message ?: e.javaClass.simpleName}")
        }

        if (decoded.schemaVersion != SUPPORTED_SCHEMA_VERSION) {
            return Result(error = "schemaVersion khong ho tro: ${decoded.schemaVersion}")
        }

        val warnings = mutableListOf<String>()
        val valid = mutableListOf<ManifestApp>()
        val seenPackages = mutableSetOf<String>()

        for (app in decoded.apps) {
            val reason = validateEntry(app, seenPackages)
            if (reason != null) {
                warnings += "${app.name.ifBlank { app.packageName.ifBlank { "unknow" } }}: $reason"
                continue
            }
            seenPackages += app.packageName
            valid += app
        }

        return Result(manifest = decoded.copy(apps = valid), warnings = warnings)
    }

    /** Tra ve loi cho truong mot hoac null neu hop le. */
    private fun validateEntry(app: ManifestApp, seen: Set<String>): String? {
        if (app.name.isBlank()) return "thieu ten."
        if (app.packageName.isBlank()) return "thieu package."
        if (!app.packageName.contains('.')) return "package khong hop le."
        if (app.version.isBlank()) return "thieu version."
        if (app.packageName in seen) return "package bi trung."
        if (!isHttps(app.apkUrl)) return "apkUrl khong phai https."
        val icon = app.iconUrl
        if (!icon.isNullOrBlank() && !isHttps(icon)) return "iconUrl khong phai https."
        return null
    }

    fun isHttps(url: String): Boolean =
        url.startsWith("https://", ignoreCase = true) && url.length > "https://".length
}
