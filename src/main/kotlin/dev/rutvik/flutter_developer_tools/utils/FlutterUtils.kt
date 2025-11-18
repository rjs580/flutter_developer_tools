package dev.rutvik.flutter_developer_tools.utils

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import io.flutter.pub.PubRoot
import io.flutter.sdk.FlutterSdk

/**
 * Utility object for Flutter-specific operations.
 * Provides functionality to run Flutter pub get commands and manage Flutter project dependencies.
 * This object handles document saving, pub root detection, and Flutter SDK operations.
 */
object FlutterUtils {
    fun runFlutterPubGet(project: Project, file: PsiFile) {
        PubRoot.forDescendant(file.virtualFile, project)?.let { pubRoot ->
            PsiDocumentManager.getInstance(project).let { psiDocManager ->
                psiDocManager.getDocument(file)?.let { doc ->
                    psiDocManager.doPostponedOperationsAndUnblockDocument(doc)
                    FileDocumentManager.getInstance().saveAllDocuments()
                    executePubGet(pubRoot, project)
                }
            }
        }
    }

    private fun executePubGet(pubRoot: PubRoot, project: Project) {
        pubRoot.getModule(project)?.let { module ->
            FlutterSdk.getFlutterSdk(project)?.flutterPackagesGet(pubRoot)
                ?.startInModuleConsole(module, { pubRoot.refresh() }, null)
        }
    }
}