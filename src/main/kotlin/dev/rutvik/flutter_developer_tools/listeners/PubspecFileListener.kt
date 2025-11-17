package dev.rutvik.flutter_developer_tools.listeners

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.vfs.VirtualFile
import dev.rutvik.flutter_developer_tools.services.PubPackageCacheService

class PubspecFileListener : FileEditorManagerListener {

    override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
        if (file.name == "pubspec.yaml") {
            val cache = PubPackageCacheService.getInstance()
            cache.onPubspecOpened()
        }
    }

    override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
        if (file.name == "pubspec.yaml") {
            val cache = PubPackageCacheService.getInstance()
            cache.onPubspecClosed()
        }
    }
}