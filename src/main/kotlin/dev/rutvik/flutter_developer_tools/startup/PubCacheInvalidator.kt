package dev.rutvik.flutter_developer_tools.startup

import com.intellij.ide.caches.CachesInvalidator
import com.intellij.openapi.application.ApplicationManager
import dev.rutvik.flutter_developer_tools.pubspec.services.PubPackageCacheService

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
}