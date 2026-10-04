package com.kaos.tvappmanager.data.manifest

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Manifest(
    @SerialName("schemaVersion") val schemaVersion: Int,
    val apps: List<ManifestApp> = emptyList(),
)

@Serializable
data class ManifestApp(
    val name: String,
    @SerialName("package") val packageName: String,
    val version: String,
    val apkUrl: String,
    val iconUrl: String? = null,
    val sha256: String? = null,
    val versionMode: String = VERSION_MODE_SEMVER,
) {
    companion object {
        const val VERSION_MODE_SEMVER = "semver"
        const val VERSION_MODE_RAW = "raw"
    }
}
