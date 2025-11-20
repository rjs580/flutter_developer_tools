package dev.rutvik.flutter_developer_tools.listeners

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.vfs.VirtualFile
import dev.rutvik.flutter_developer_tools.services.PubPackageCacheService

/**
 * Listens for pubspec.yaml file open/close events in the IDE.
 * Manages the PubPackageCacheService state based on these events to maintain
 * package information cache when the pubspec file is being accessed.
 */

class PubspecFileListener : FileEditorManagerListener {

    /**
     * Handles pubspec.yaml file open events.
     * Notifies the cache service when pubspec.yaml is opened to initialize package tracking.
     *
     * @param source The FileEditorManager instance that triggered the event
     * @param file The VirtualFile that was opened
     */
    override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
        if (file.name == "pubspec.yaml") {
            val cache = PubPackageCacheService.getInstance()
            cache.onPubspecOpened()
        }
    }

    /**
     * Handles pubspec.yaml file close events.
     * Notifies the cache service when pubspec.yaml is closed to cleanup package tracking.
     *
     * @param source The FileEditorManager instance that triggered the event
     * @param file The VirtualFile that was closed
     */
    override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
        if (file.name == "pubspec.yaml") {
            val cache = PubPackageCacheService.getInstance()
            cache.onPubspecClosed()
        }
    }
}