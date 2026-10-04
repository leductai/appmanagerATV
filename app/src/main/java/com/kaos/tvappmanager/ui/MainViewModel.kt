package com.kaos.tvappmanager.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kaos.tvappmanager.di.AppContainer
import com.kaos.tvappmanager.data.apps.AppCatalog
import com.kaos.tvappmanager.data.apps.AppEntry
import com.kaos.tvappmanager.data.apps.InstalledApp
import com.kaos.tvappmanager.data.manifest.Manifest
import com.kaos.tvappmanager.data.manifest.ManifestLoad
import com.kaos.tvappmanager.data.source.LanguagePreference
import com.kaos.tvappmanager.data.source.ManifestSource
import com.kaos.tvappmanager.data.source.SourceStore
import com.kaos.tvappmanager.platform.install.InstallQueue
import com.kaos.tvappmanager.platform.install.QueueState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class MainTab { UPDATES, INSTALLED }

enum class Screen {
    FIRST_RUN,
    MAIN,
    DETAIL,
    PROGRESS,
    HIDDEN_SETTINGS,
    SOURCES,
    NETWORK,
    STORAGE,
    LANGUAGE,
    DIAGNOSTICS,
    ABOUT,
}

data class MainUiState(
    val loading: Boolean = true,
    val manifestError: String? = null,
    val manifest: Manifest? = null,
    val usingCachedManifest: Boolean = false,
    val hasActiveSource: Boolean = false,
    val entries: List<AppEntry> = emptyList(),
    val installedApps: List<InstalledApp> = emptyList(),
    val groups: AppCatalog.AppGroups = AppCatalog.AppGroups(emptyList(), emptyList(), emptyList(), emptyList()),
    val tab: MainTab = MainTab.UPDATES,
    val canInstall: Boolean = false,
    val includeSystemApps: Boolean = false,    val selection: Set<String> = emptySet(),
    val pendingSelectionKind: SelectionKind? = null,
    val pendingEntries: List<AppEntry> = emptyList(),
    val settings: SourceStore.StoreSnapshot? = null,
) {
    val updateCount: Int get() = groups.hasUpdate.size
    val installCount: Int get() = groups.notInstalled.size

    fun entriesFor(kind: SelectionKind): List<AppEntry> = when (kind) {
        SelectionKind.UPDATE -> groups.hasUpdate
        SelectionKind.INSTALL -> groups.notInstalled
    }
}

enum class SelectionKind { UPDATE, INSTALL }

@Immutable
data class NavigationState(
    val screen: Screen = Screen.FIRST_RUN,
    val detailPackage: String? = null,
)

class MainViewModel(private val container: AppContainer) : ViewModel() {

    private val _ui = MutableStateFlow(MainUiState())
    val ui: StateFlow<MainUiState> = _ui.asStateFlow()

    private val _nav = MutableStateFlow(NavigationState())
    val nav: StateFlow<NavigationState> = _nav.asStateFlow()

    val queue: StateFlow<QueueState> = container.installQueue.state

    /**
     * Goi nho focus hien tai. Spec yeu cau app nho vi tri focus khi quay lai tu
     * trang chi tiet hoac hop thoai (section 5).
     */
    var focusedPackage by mutableStateOf<String?>(null)
        private set

    init {
        container.installQueue.onInstalled = { refreshInstalled() }
        viewModelScope.launch {
            container.sourceStore.ensureDefaultSource()
            container.refreshSettings()
            val snapshot = container.sourceStore.snapshot()
            val granted = container.installPermission.has()
            _ui.value = _ui.value.copy(
                settings = snapshot,
                hasActiveSource = snapshot.activeSource != null,
                canInstall = granted,
            )
            // Man hinh xin quyen chi xuat hien lan dau tien. Sau do app van dung duoc,
            // chi nút cai va cap nhat bi vo hieu hoa (spec section 3).
            if (!granted && !snapshot.hasSeenFirstRun) {
                _nav.value = NavigationState(Screen.FIRST_RUN)
            } else {
                _nav.value = NavigationState(Screen.MAIN)
            }
            if (snapshot.activeSource != null) refreshManifest()
            else _ui.value = _ui.value.copy(loading = false)
        }
    }

    // ---- Dieu huong ----

    fun navigate(screen: Screen, packageName: String? = null) {
        _nav.value = NavigationState(screen, packageName)
    }

    fun markFocus(packageName: String?) {
        focusedPackage = packageName
    }

    fun setTab(tab: MainTab) {
        _ui.value = _ui.value.copy(tab = tab)
    }

    fun toggleIncludeSystemApps() {
        _ui.value = _ui.value.copy(includeSystemApps = !_ui.value.includeSystemApps)
        refreshInstalled()
    }

    // ---- Du lieu ----

    fun refreshManifest() {
        val source = _ui.value.settings?.activeSource
        if (source == null) {
            _ui.value = _ui.value.copy(loading = false, hasActiveSource = false)
            return
        }
        _ui.value = _ui.value.copy(loading = true, manifestError = null)
        viewModelScope.launch {
            when (val load = container.manifestRepository.load(source)) {
                is ManifestLoad.Success -> {
                    val apps = container.installedApps.listAll()
                    val entries = AppCatalog.build(load.manifest, apps)
                    container.sourceStore.recordSync(source.id, null)
                    _ui.value = _ui.value.copy(
                        loading = false,
                        usingCachedManifest = load.fromCache,
                        manifestError = null,
                        manifest = load.manifest,
                        entries = entries,
                        installedApps = apps,
                        groups = AppCatalog.splitIntoGroups(entries),
                        settings = container.sourceStore.snapshot(),
                    )
                }

                is ManifestLoad.Failure -> {
                    _ui.value = _ui.value.copy(
                        loading = false,
                        manifestError = load.message,
                        entries = emptyList(),
                        groups = AppCatalog.splitIntoGroups(emptyList()),
                    )
                }
            }
        }
    }

    fun refreshInstalled() {
        viewModelScope.launch {
            val apps = container.installedApps.listAll()
            val entries = AppCatalog.build(_ui.value.manifest, apps)
            _ui.value = _ui.value.copy(
                canInstall = container.installPermission.has(),
                entries = entries,
                installedApps = apps,
                groups = AppCatalog.splitIntoGroups(entries),
            )
        }
    }

    // ---- Hang doi ----

    fun openSelection(kind: SelectionKind) {
        val entries = _ui.value.entriesFor(kind)
        if (entries.isEmpty()) return
        if (!_ui.value.canInstall) {
            navigate(Screen.FIRST_RUN)
            return
        }
        _ui.value = _ui.value.copy(
            pendingSelectionKind = kind,
            pendingEntries = entries,
            selection = entries.map { it.packageName }.toSet(),
        )
    }

    /** Cai mot app don le tu trang chi tiet. */
    fun openSingle(entry: AppEntry) {
        if (!_ui.value.canInstall) {
            navigate(Screen.FIRST_RUN)
            return
        }
        val kind = when {
            entry.isInstalled -> SelectionKind.UPDATE
            else -> SelectionKind.INSTALL
        }
        _ui.value = _ui.value.copy(
            pendingSelectionKind = kind,
            pendingEntries = listOf(entry),
            selection = setOf(entry.packageName),
        )
    }

    fun toggleSelection(packageName: String) {
        val current = _ui.value.selection
        _ui.value = _ui.value.copy(
            selection = if (packageName in current) current - packageName else current + packageName
        )
    }

    fun setAllSelected(selected: Boolean) {
        val entries = _ui.value.pendingEntries
        _ui.value = _ui.value.copy(
            selection = if (selected) entries.map { it.packageName }.toSet() else emptySet()
        )
    }

    fun closeSelection() {
        _ui.value = _ui.value.copy(
            pendingSelectionKind = null,
            pendingEntries = emptyList(),
            selection = emptySet(),
        )
    }

    fun startQueue() {
        val entries = _ui.value.pendingEntries
            .filter { it.packageName in _ui.value.selection }
            .mapNotNull { it.manifestApp }
        if (entries.isEmpty()) return
        closeSelection()
        container.installQueue.start(entries)
        // Bat buoc mo man hinh tien do ngay: neu khong nguoi dung khong thay gi xay ra
        // va tuong app khong tai/cai.
        navigate(Screen.PROGRESS)
    }

    fun cancelQueue() = container.installQueue.cancel()
    fun retryFailed() = container.installQueue.retryFailed()
    fun resetQueue() = container.installQueue.reset()

    // ---- Nguon manifest ----

    fun addSource(name: String, location: String) {
        viewModelScope.launch {
            container.sourceStore.addSource(name, location)
            container.refreshSettings()
            _ui.value = _ui.value.copy(settings = container.sourceStore.snapshot())
        }
    }

    fun updateSource(source: ManifestSource, name: String, location: String) {
        viewModelScope.launch {
            container.sourceStore.updateSource(source.id, name, location)
            container.refreshSettings()
            _ui.value = _ui.value.copy(settings = container.sourceStore.snapshot())
        }
    }

    fun deleteSource(source: ManifestSource) {
        viewModelScope.launch {
            container.sourceStore.deleteSource(source.id)
            container.refreshSettings()
            _ui.value = _ui.value.copy(settings = container.sourceStore.snapshot())
            refreshManifest()
        }
    }

    fun selectSource(source: ManifestSource) {
        viewModelScope.launch {
            container.sourceStore.setActiveSource(source.id)
            container.refreshSettings()
            _ui.value = _ui.value.copy(
                settings = container.sourceStore.snapshot(),
                hasActiveSource = true,
            )
            refreshManifest()
        }
    }

    // ---- Tuy chon ----

    fun setWifiOnly(value: Boolean) {
        viewModelScope.launch {
            container.sourceStore.setWifiOnly(value)
            container.refreshSettings()
            _ui.value = _ui.value.copy(settings = container.sourceStore.snapshot())
        }
    }

    fun setTimeoutSeconds(value: Int) {
        viewModelScope.launch {
            container.sourceStore.setTimeoutSeconds(value)
            container.refreshSettings()
            _ui.value = _ui.value.copy(settings = container.sourceStore.snapshot())
        }
    }

    fun setAutoDeleteApk(value: Boolean) {
        viewModelScope.launch {
            container.sourceStore.setAutoDeleteApk(value)
            container.refreshSettings()
            _ui.value = _ui.value.copy(settings = container.sourceStore.snapshot())
        }
    }

    fun setAdbInstallEnabled(value: Boolean) {
        viewModelScope.launch {
            container.sourceStore.setAdbInstallEnabled(value)
            container.refreshSettings()
            _ui.value = _ui.value.copy(settings = container.sourceStore.snapshot())
        }
    }

    fun setLanguage(value: LanguagePreference) {
        viewModelScope.launch {
            container.sourceStore.setLanguage(value)
            container.refreshSettings()
            _ui.value = _ui.value.copy(settings = container.sourceStore.snapshot())
        }
    }

    fun clearCaches() {
        container.iconCache.clear()
        container.manifestRepository.clearCache()
    }

    fun permissionState(): Boolean = container.installPermission.has()

    /**
     * Kiem tra lai quyen sau khi quay ve tu man hinh Cai dat Android, va sau khi
     * app quay len lai (ON_RESUME). Khong thi nguoi dung bat quyen xong van thay
     * man hinh xin quyen cu.
     */
    fun recheckPermission() {
        val granted = container.installPermission.has()
        val seen = _ui.value.settings?.hasSeenFirstRun ?: false
        _ui.value = _ui.value.copy(canInstall = granted)
        if (granted && _nav.value.screen == Screen.FIRST_RUN) {
            _nav.value = NavigationState(Screen.MAIN)
        } else if (_nav.value.screen == Screen.FIRST_RUN && seen && !granted) {
            _nav.value = NavigationState(Screen.MAIN)
        }
    }

    /** Nguoi dung bam "Cap quyen sau": dua vao app, de tiep tuc khi chua co quyen. */
    fun dismissFirstRun() {
        viewModelScope.launch {
            container.sourceStore.setHasSeenFirstRun()
            container.refreshSettings()
            _ui.value = _ui.value.copy(settings = container.sourceStore.snapshot())
            _nav.value = NavigationState(Screen.MAIN)
        }
    }

    fun entryFor(packageName: String): AppEntry? =
        _ui.value.entries.firstOrNull { it.packageName == packageName }

    // ---- Menu cai dat an ----

    val logLines: List<String> get() = container.logger.dump()
    fun cacheBytes(): Long = container.iconCache.cacheSizeBytes() + container.manifestRepository.cacheSizeBytes()
    fun tempStats(): Pair<Int, Long> = container.settingsExporter.tempApkStats()
    fun exportConfig() {
        viewModelScope.launch { container.settingsExporter.exportConfig() }
    }

    fun exportLog() {
        viewModelScope.launch { container.settingsExporter.exportLog() }
    }

    fun clearLog() {
        container.logger.clear()
    }

    fun cleanTempFiles() {
        container.settingsExporter.cleanTemp()
    }

    fun importManifestFrom(uri: android.net.Uri) {
        viewModelScope.launch {
            val id = container.sourceStore.addSource(
                name = "Local manifest",
                location = uri.toString(),
            )
            container.refreshSettings()
            _ui.value = _ui.value.copy(settings = container.sourceStore.snapshot())
            container.sourceStore.setActiveSource(id)
            refreshManifest()
        }
    }

    fun activeSourceId(): String? = _ui.value.settings?.activeSource?.id
}
