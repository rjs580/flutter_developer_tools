package dev.rutvik.flutter_developer_tools.services

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import dev.rutvik.flutter_developer_tools.models.PackageCacheState
import dev.rutvik.flutter_developer_tools.models.PubPackage
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.Volatile

/**
 * Service for caching pub.dev package information.
 * Provides both in-memory and persistent storage capabilities with lazy loading.
 */
@State(
    name = "PubPackageCacheService",
    storages = [Storage("pubPackageCache.xml", roamingType = RoamingType.DISABLED)]
)
@Service(Service.Level.APP)
class PubPackageCacheService : PersistentStateComponent<PackageCacheState> {
    private var _state = PackageCacheState()

    /** In-memory cache that is lazily loaded */
    private val memoryCache = ConcurrentHashMap<String, PubPackage>()

    /** Counter for tracking active pubspec.yaml editors */
    private val activeEditors = AtomicInteger(0)

    @Volatile
    private var isMemoryCacheLoaded = false

    private val saveLock = Any()

    val lastPackageListUpdate: String
        get() = formatTimestamp(_state.lastPackageListUpdate)

    companion object {
        /** TTL for package details cache (5 minutes) */
        private const val PACKAGE_DETAILS_TTL_SECONDS = 5L * 60L

        fun getInstance(): PubPackageCacheService =
            ApplicationManager.getApplication().getService(PubPackageCacheService::class.java)
    }

    override fun getState(): PackageCacheState {
        synchronized(saveLock) {
            // Only persist to disk, don't keep everything in state
            return _state.copy()
        }
    }

    override fun loadState(state: PackageCacheState) {
        synchronized(saveLock) {
            _state = state
            // Don't load into memory yet - wait for demand
            memoryCache.clear()
            isMemoryCacheLoaded = false
        }
    }

    override fun noStateLoaded() {
        synchronized(saveLock) {
            _state = PackageCacheState()
            memoryCache.clear()
            isMemoryCacheLoaded = false
        }
    }

    /** Called when a pubspec.yaml file is opened */
    fun onPubspecOpened() {
        activeEditors.incrementAndGet()
        ensureMemoryCacheLoaded()
    }

    /** Called when a pubspec.yaml file is closed */
    fun onPubspecClosed() {
        val count = activeEditors.decrementAndGet()
        if (count <= 0) {
            // Schedule unload after a delay (grace period for quick reopens)
            ApplicationManager.getApplication().executeOnPooledThread {
                Thread.sleep(30_000) // 30 seconds grace period
                if (activeEditors.get() <= 0) {
                    unloadMemoryCache()
                }
            }
        }
    }

    /** Loads packages from persistent state into memory */
    private fun ensureMemoryCacheLoaded() {
        if (isMemoryCacheLoaded) return

        synchronized(saveLock) {
            if (isMemoryCacheLoaded) return

            memoryCache.clear()
            memoryCache.putAll(_state.packages.associateBy { it.name })
            isMemoryCacheLoaded = true
        }
    }

    /** Clears in-memory cache while keeping disk storage intact */
    private fun unloadMemoryCache() {
        synchronized(saveLock) {
            if (!isMemoryCacheLoaded) return

            memoryCache.clear()
            isMemoryCacheLoaded = false
        }
    }

    fun isMemoryCacheLoaded(): Boolean = isMemoryCacheLoaded

    /** Formats a timestamp into a human readable string */
    private fun formatTimestamp(timestamp: Long): String {
        if (timestamp == 0L) return "never"

        val now = System.currentTimeMillis() / 1000L
        val diff = now - timestamp

        return when {
            diff < 60 -> "just now"
            diff < 3600 -> "${diff / 60} minutes ago"
            diff < 86400 -> "${diff / 3600} hours ago"
            diff < 604800 -> "${diff / 86400} days ago"
            diff < 2592000 -> "${diff / 604800} weeks ago"
            else -> "${diff / 2592000} months ago"
        }
    }

    /** Updates the timestamp of last package list update */
    fun updatePackageListTimestamp() {
        synchronized(saveLock) {
            _state.lastPackageListUpdate = Instant.now().epochSecond
        }
    }

    /** Adds new package names to both persistent and memory cache */
    fun addPackageNames(names: List<String>) {
        synchronized(saveLock) {
            // Build an O(1) lookup of existing names once, then collect only the new
            // packages and assign the list a single time. Avoids the O(n^2) rebuild that
            // froze startup when seeding the full pub.dev name list.
            val existingNames = _state.packages.mapTo(HashSet()) { it.name }
            val newPackages = ArrayList<PubPackage>()
            for (name in names) {
                if (existingNames.add(name)) {
                    val pkg = PubPackage.withName(name)
                    newPackages.add(pkg)
                    if (isMemoryCacheLoaded) {
                        memoryCache.putIfAbsent(name, pkg)
                    }
                }
            }
            if (newPackages.isNotEmpty()) {
                _state.packages = _state.packages + newPackages
            }
        }
    }

    /** Checks if package details need to be refetched based on TTL */
    fun shouldRefetchDetails(packageName: String): Boolean {
        val ts = synchronized(saveLock) { _state.packageDetailsTimestamps[packageName] } ?: return true

        val now = Instant.now().epochSecond
        return (now - ts) > PACKAGE_DETAILS_TTL_SECONDS
    }

    /** Updates package details in both persistent and memory cache */
    fun updateDetails(info: PubPackage) {
        // Update memory cache immediately without lock (it's a ConcurrentHashMap)
        if (isMemoryCacheLoaded) {
            memoryCache[info.name] = info
        }

        // Update persistent state with minimal lock time
        synchronized(saveLock) {
            val index = _state.packages.indexOfFirst { it.name == info.name }
            if (index >= 0) {
                val mutable = _state.packages.toMutableList()
                mutable[index] = info
                _state.packages = mutable
            } else {
                _state.packages += info
            }

            _state.packageDetailsTimestamps[info.name] = Instant.now().epochSecond
        }
        // The platform persists this PersistentStateComponent automatically; no explicit
        // (and previously EDT-blocking) saveSettings() call is needed here.
    }

    /** Retrieves package info from cache */
    fun getInfo(name: String): PubPackage? {
        // Try memory cache first if loaded
        if (isMemoryCacheLoaded) {
            return memoryCache[name]
        }

        // Otherwise search in persistent state (slower, but avoids loading everything)
        synchronized(saveLock) {
            return _state.packages.find { it.name == name }
        }
    }

    /** Searches packages by name with fuzzy matching */
    fun searchPackages(query: String, limit: Int = 50): List<PubPackage> {
        ensureMemoryCacheLoaded() // Need memory for fast search

        return memoryCache.values
            .filter { it.name.contains(query, ignoreCase = true) }
            .sortedWith(compareBy<PubPackage>
            // Primary sort: match quality
            { pkg ->
                when {
                    pkg.name.equals(query, ignoreCase = true) -> 0  // Exact match
                    pkg.name.startsWith(query, ignoreCase = true) -> 1  // Starts with
                    else -> 2  // Contains
                }
            }.thenByDescending { pkg ->
                // Secondary sort: popularity first (likes + flutter favorite bonus)
                var score = (pkg.likes ?: 0)
                if (pkg.isFlutterFavorite) score += 10000
                score
            })
            .take(limit)
            .toList()
    }

    /** Clears all cached data */
    fun clearAll() {
        synchronized(saveLock) {
            _state.packages = emptyList()
            _state.lastPackageListUpdate = 0L
            _state.packageDetailsTimestamps.clear()
            memoryCache.clear()
            isMemoryCacheLoaded = false
        }
    }
}