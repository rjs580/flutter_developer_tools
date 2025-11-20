package dev.rutvik.flutter_developer_tools.startup

import com.intellij.ide.AppLifecycleListener
import com.intellij.openapi.application.ApplicationManager
import dev.rutvik.flutter_developer_tools.api.PubDevApi
import dev.rutvik.flutter_developer_tools.services.PubPackageCacheService

/**
 * A startup activity listener that preloads and maintains the pub.dev package cache.
 *
 * This class implements [AppLifecycleListener] to initialize the package cache when
 * the IDE frame is created. It ensures the package list is updated periodically
 * by maintaining a TTL (Time To Live) of 8 hours for the cached data.
 *
 * The preloading happens on a background thread to avoid blocking the UI.
 */

class StartupPreloader : AppLifecycleListener {
    companion object {
        private const val PACKAGE_LIST_TTL_SECONDS = 8L * 60L * 60L // 8 hours
    }

    override fun appFrameCreated(commandLineArgs: MutableList<String>) {
        ApplicationManager.getApplication().executeOnPooledThread {
            val cache = PubPackageCacheService.getInstance()

            val now = System.currentTimeMillis() / 1000L
            val lastUpdate = cache.state.lastPackageListUpdate

            // Fetch if never updated OR if the cache is stale
            if (lastUpdate == 0L) {
                // First time - fetch and save to disk
                PubDevApi.fetchPackageNames {
                    cache.updatePackageListTimestamp()
                }
            } else {
                val age = now - lastUpdate
                if (age > PACKAGE_LIST_TTL_SECONDS) {
                    // Cache is stale - refetch and save to disk
                    PubDevApi.fetchPackageNames {
                        cache.updatePackageListTimestamp()
                    }
                }
            }
        }
    }
}
