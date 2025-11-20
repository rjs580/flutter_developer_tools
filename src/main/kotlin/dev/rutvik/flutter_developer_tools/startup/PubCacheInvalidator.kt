package dev.rutvik.flutter_developer_tools.startup

import com.intellij.ide.caches.CachesInvalidator
import com.intellij.openapi.application.ApplicationManager
import dev.rutvik.flutter_developer_tools.services.PubPackageCacheService


/**
 * Cache invalidator for Flutter/Dart package cache that executes when user triggers "Invalidate Caches and Restart".
 *
 * This invalidator is responsible for clearing the cached Flutter/Dart package information through [PubPackageCacheService].
 * The cache clearing operation is performed on a background thread to avoid blocking the UI.
 */
class PubCacheInvalidator : CachesInvalidator() {
    override fun invalidateCaches() {
        // This is called when user clicks "Invalidate Caches and Restart"
        ApplicationManager.getApplication().executeOnPooledThread {
            val pubPackageCacheService = PubPackageCacheService.getInstance()

            // Clear all package cache
            pubPackageCacheService.clearAll()
        }
    }

    override fun getDescription(): String {
        return "Clears Flutter/Dart package cache"
    }

    override fun optionalCheckboxDefaultValue(): Boolean {
        return true
    }
}