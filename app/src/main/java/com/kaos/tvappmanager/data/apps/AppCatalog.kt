package com.kaos.tvappmanager.data.apps

import com.kaos.tvappmanager.data.manifest.Manifest
import com.kaos.tvappmanager.data.manifest.ManifestApp
import com.kaos.tvappmanager.data.manifest.VersionComparator

/**
 * Mot muc trong danh sach chinh: gop thong tin manifest voi app dang cai tren may.
 *
 * `status` quyet dinh nhom hien thi (spec section 4). Muc `UNKNOWN` khong duoc
 * dua vao nhom "Co ban cap nhat" — nguoi dung van cai duoc tu trang chi tiet.
 */
data class AppEntry(
    val packageName: String,
    val name: String,
    val manifestApp: ManifestApp?,
    val installed: InstalledApp?,
    val status: Status,
) {
    val manifestVersion: String? get() = manifestApp?.version
    val installedVersion: String? get() = installed?.versionName?.takeIf { it.isNotBlank() }
    val isInstalled: Boolean get() = installed != null
    val apkUrl: String get() = manifestApp?.apkUrl.orEmpty()
    val iconUrl: String? get() = manifestApp?.iconUrl

    val versionOrder: VersionComparator.Order
        get() = VersionComparator.compare(
            manifestVersion.orEmpty(),
            installedVersion,
            manifestApp?.versionMode ?: ManifestApp.VERSION_MODE_SEMVER,
        )

    enum class Status { HAS_UPDATE, UP_TO_DATE, NOT_INSTALLED, UNKNOWN, INSTALLED_ONLY }
}

object AppCatalog {

    fun build(manifest: Manifest?, installed: List<InstalledApp>): List<AppEntry> {
        val installedByPackage = installed.associateBy { it.packageName }
        val entries = mutableListOf<AppEntry>()

        for (app in manifest?.apps.orEmpty()) {
            val installedApp = installedByPackage[app.packageName]
            entries += AppEntry(
                packageName = app.packageName,
                name = app.name,
                manifestApp = app,
                installed = installedApp,
                status = statusOf(app, installedApp),
            )
        }
        return entries
    }

    private fun statusOf(app: ManifestApp, installed: InstalledApp?): AppEntry.Status = when {
        installed == null -> AppEntry.Status.NOT_INSTALLED
        else -> when (app.rawVersionForCompare(installed)) {
            VersionComparator.Order.NEWER -> AppEntry.Status.HAS_UPDATE
            VersionComparator.Order.UNKNOWN -> AppEntry.Status.UNKNOWN
            else -> AppEntry.Status.UP_TO_DATE
        }
    }

    private fun ManifestApp.rawVersionForCompare(installed: InstalledApp): VersionComparator.Order {
        val mode = versionMode
        val installedValue = when (mode) {
            // Version dang chuoi khong doi chieu duoc bang versionCode long, nhung spec
            // cho phep khai bao `version` theo thu tu phat hanh cho manifest raw.
            ManifestApp.VERSION_MODE_RAW -> installed.manifestVersionName()
            else -> installed.versionName
        }
        return VersionComparator.compare(version, installedValue, mode)
    }

    /** Danh sach tren tab "Da cai dat": app tren may, co khop voi manifest hay khong deu duoc. */
    fun installedEntries(
        manifest: Manifest?,
        installed: List<InstalledApp>,
        includeSystem: Boolean,
    ): List<AppEntry> {
        val indexed = build(manifest, installed).associateBy { it.packageName }
        return installed
            .filter { includeSystem || !it.isSystem }
            .map { device ->
                indexed[device.packageName] ?: AppEntry(
                    packageName = device.packageName,
                    name = device.label,
                    manifestApp = null,
                    installed = device,
                    status = AppEntry.Status.INSTALLED_ONLY,
                )
            }
    }

    /** Phan ba nhom hien thi tren tab Cap nhat. */
    fun splitIntoGroups(entries: List<AppEntry>): AppGroups = AppGroups(
        hasUpdate = entries.filter { it.status == AppEntry.Status.HAS_UPDATE },
        upToDate = entries.filter { it.status == AppEntry.Status.UP_TO_DATE },
        notInstalled = entries.filter { it.status == AppEntry.Status.NOT_INSTALLED },
        unknown = entries.filter { it.status == AppEntry.Status.UNKNOWN },
    )

    data class AppGroups(
        val hasUpdate: List<AppEntry>,
        val upToDate: List<AppEntry>,
        val notInstalled: List<AppEntry>,
        val unknown: List<AppEntry>,
    )
}
