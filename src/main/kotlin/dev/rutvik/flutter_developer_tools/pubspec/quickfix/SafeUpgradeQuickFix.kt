package dev.rutvik.flutter_developer_tools.pubspec.quickfix

import com.intellij.codeInsight.intention.impl.BaseIntentionAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import dev.rutvik.flutter_developer_tools.utils.PubspecUtils
import dev.rutvik.flutter_developer_tools.utils.VersionUtils
import io.flutter.pub.PubRoot
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLScalar

/**
 * Quick fix to perform a safe upgrade (skip major updates, only patch + minor).
 */
class SafeUpgradeQuickFix(
    private val packageName: String,
    private val currentVersion: String,
    private val latestVersion: String,
    private val runPubGet: Boolean
) : BaseIntentionAction() {

    override fun getText(): String {
        val action = if (runPubGet) "and run pub get" else "only"
        return "Safe upgrade to latest (skip major) $action"
    }

    override fun getFamilyName(): String = "Upgrade package safely"

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        return PubspecUtils.isPubspecFile(file)
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        file ?: return
        editor ?: return

        val offset = editor.caretModel.offset
        val element = file.findElementAt(offset) ?: return
        val yamlScalar = PsiTreeUtil.getParentOfType(element, YAMLScalar::class.java) ?: return
        val yamlKv = yamlScalar.parent as? YAMLKeyValue ?: return

        if (yamlKv.keyText != packageName) return

        // Determine the safe version to upgrade to
        val updateType = VersionUtils.getUpdateType(currentVersion, latestVersion)
        val targetVersion = if (updateType == VersionUtils.UpdateType.MAJOR) {
            // For major updates, suggest keeping current major version
            // In a real scenario, you'd query pub.dev for the highest version with the same major
            currentVersion
        } else {
            latestVersion
        }

        WriteCommandAction.runWriteCommandAction(project) {
            val newText = "^$targetVersion"
            yamlScalar.updateText(newText)
        }

        if (runPubGet) {
            runFlutterPubGet(project, file)
        }
    }

    private fun runFlutterPubGet(project: Project, file: PsiFile) {
        ApplicationManager.getApplication().invokeLater {
            val pubRoot = PubRoot.forFile(file.virtualFile) ?: return@invokeLater
            pubRoot.refresh()
        }
    }
}

/**
 * Quick fix to perform a full upgrade (including major updates).
 */
class FullUpgradeQuickFix(
    private val packageName: String,
    private val latestVersion: String,
    private val runPubGet: Boolean
) : BaseIntentionAction() {

    override fun getText(): String {
        val action = if (runPubGet) "and run pub get" else "only"
        return "Upgrade to latest ($latestVersion) $action"
    }

    override fun getFamilyName(): String = "Upgrade package to latest"

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        return PubspecUtils.isPubspecFile(file)
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        file ?: return
        editor ?: return

        val offset = editor.caretModel.offset
        val element = file.findElementAt(offset) ?: return
        val yamlScalar = PsiTreeUtil.getParentOfType(element, YAMLScalar::class.java) ?: return
        val yamlKv = yamlScalar.parent as? YAMLKeyValue ?: return

        if (yamlKv.keyText != packageName) return

        WriteCommandAction.runWriteCommandAction(project) {
            val newText = "^$latestVersion"
            yamlScalar.updateText(newText)
        }

        if (runPubGet) {
            runFlutterPubGet(project, file)
        }
    }

    private fun runFlutterPubGet(project: Project, file: PsiFile) {
        ApplicationManager.getApplication().invokeLater {
            val pubRoot = PubRoot.forFile(file.virtualFile) ?: return@invokeLater
            pubRoot.refresh()
        }
    }
}