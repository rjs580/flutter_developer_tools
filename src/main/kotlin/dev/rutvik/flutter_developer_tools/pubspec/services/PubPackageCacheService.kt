package dev.rutvik.flutter_developer_tools.pubspec.services

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import dev.rutvik.flutter_developer_tools.pubspec.models.PackageCacheState
import dev.rutvik.flutter_developer_tools.pubspec.models.PubPackage
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@State(
    name = "PubPackageCacheService",
    storages = [Storage(StoragePathMacros.CACHE_FILE)]
)
@Service(Service.Level.APP)
class PubPackageCacheService : PersistentStateComponent<PackageCacheState> {
    private var _state = PackageCacheState()
    val packages = ConcurrentHashMap<String, PubPackage>()
    private val saveLock = Any()

    companion object {
        private const val PACKAGE_DETAILS_TTL_SECONDS = 5 * 60 // 5 minutes

        fun getInstance(): PubPackageCacheService =
            ApplicationManager.getApplication().getService(PubPackageCacheService::class.java)
    }

    override fun getState(): PackageCacheState = _state

    override fun loadState(state: PackageCacheState) {
        _state = state
        packages.clear()
        state.packages.forEach { packages[it.name] = it }
    }

    // ---- PACKAGE NAMES ----

    fun updatePackageListTimestamp() {
        synchronized(saveLock) {
            _state.lastPackageListUpdate = Instant.now().epochSecond
            save()
        }
    }

    fun addPackageNames(names: List<String>) {
        synchronized(saveLock) {
            var changed = false
            names.forEach { name ->
                if (!packages.containsKey(name)) {
                    packages[name] = PubPackage.withName(name)
                    changed = true
                }
            }
            if (changed) {
                save()
            }
        }
    }

    // ---- PACKAGE DETAILS ----

    fun shouldRefetchDetails(packageName: String): Boolean {
        val ts = _state.packageDetailsTimestamps[packageName] ?: return true

        val now = Instant.now().epochSecond
        return (now - ts) > PACKAGE_DETAILS_TTL_SECONDS
    }

    fun updateDetails(info: PubPackage) {
        synchronized(saveLock) {
            packages[info.name] = info
            _state.packageDetailsTimestamps[info.name] = Instant.now().epochSecond
            save()
        }
    }

    fun getInfo(name: String): PubPackage? = packages[name]

    private fun save() {
        _state.packages = packages.values.toList()
        ApplicationManager.getApplication().saveSettings()
    }
}