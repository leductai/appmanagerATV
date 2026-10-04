package com.kaos.tvappmanager.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.kaos.tvappmanager.core.AppLogger
import com.kaos.tvappmanager.data.apps.InstalledAppsRepository
import com.kaos.tvappmanager.data.icon.IconCache
import com.kaos.tvappmanager.data.manifest.ManifestRepository
import com.kaos.tvappmanager.data.source.SettingsExporter
import com.kaos.tvappmanager.data.source.SourceStore
import com.kaos.tvappmanager.platform.download.ApkDownloader
import com.kaos.tvappmanager.platform.install.AdbLocalClient
import com.kaos.tvappmanager.platform.install.InstallQueue
import com.kaos.tvappmanager.platform.install.PackageInstallerRunner
import com.kaos.tvappmanager.platform.permission.InstallPermission
import com.kaos.tvappmanager.platform.verify.ApkVerifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

private val Context.prefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "tv_app_manager")

/**
 * DI thu cong cho mot app duy nhat: khong dung framework, khong annotation processing.
 * Cac thay doi tuy chon doc duoc trong `SourceStore.snapshot()` va gan vao lambda
 * de hang doi lay gia tri moi nhat luc chay.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val logger = AppLogger()

    @Volatile
    private var cachedSettings: SourceStore.StoreSnapshot? = null

    val sourceStore = SourceStore(appContext.prefsDataStore)
    val settingsExporter = SettingsExporter(appContext, sourceStore, logger)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(300, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    val manifestRepository = ManifestRepository(appContext, httpClient, logger)
    val installedApps = InstalledAppsRepository(appContext)
    val iconCache = IconCache(appContext, httpClient, logger)
    val installPermission = InstallPermission(appContext)
    val apkVerifier = ApkVerifier(appContext, logger)
    val installerRunner = PackageInstallerRunner(appContext, logger)
    val adbLocalClient = AdbLocalClient(logger)

    private val downloader = ApkDownloader(
        context = appContext,
        client = httpClient,
        logger = logger,
        wifiOnly = { cachedSettings?.wifiOnly ?: true },
    )

    val installQueue = InstallQueue(
        scope = ioScope,
        downloader = downloader,
        verifier = apkVerifier,
        runner = installerRunner,
        permission = installPermission,
        autoDeleteApk = { cachedSettings?.autoDeleteApk ?: true },
        useAdbInstall = { cachedSettings?.adbInstallEnabled ?: false },
        adbClient = adbLocalClient,
        logger = logger,
    )

    /** Nap tuy chon hien tai vao bo dem de cac lambda doc duoc gia tri moi nhat. */
    suspend fun refreshSettings() {
        cachedSettings = sourceStore.snapshot()
    }
}
