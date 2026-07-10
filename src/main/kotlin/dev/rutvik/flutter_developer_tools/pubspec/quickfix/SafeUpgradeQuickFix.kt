
package dev.rutvik.flutter_developer_tools.pubspec.quickfix

import com.intellij.codeInsight.intention.impl.BaseIntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import dev.rutvik.flutter_developer_tools.models.PubPackage
import dev.rutvik.flutter_developer_tools.utils.FlutterUtils
import dev.rutvik.flutter_developer_tools.utils.PubspecUtils
import dev.rutvik.flutter_developer_tools.utils.VersionUtils
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLScalar

/**
 * Quick fix to perform a safe upgrade (skip major updates, only patch + minor).
 */
class SafeUpgradeQuickFix(
    private val packageName: String,
    private val currentVersion: String,
    private val packageInfo: PubPackage,
    private val runPubGet: Boolean
) : BaseIntentionAction() {

    override fun getText(): String {
        val safeVersion = VersionUtils.getSafeUpgradeVersion(currentVersion, packageInfo.versions ?: emptyList())
        val versionText = safeVersion?.let { " to $it" } ?: ""
        val action = if (runPubGet) "and run pub get" else ""
        return "Safe upgrade$versionText (skip major) $action".trim()
    }

    override fun getFamilyName(): String = "Upgrade package safely"

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        if (!PubspecUtils.isPubspecFile(file)) return false

        // Only show if there's a safe upgrade available
        val safeVersion = VersionUtils.getSafeUpgradeVersion(currentVersion, packageInfo.versions ?: emptyList())
        return safeVersion != null
    }

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
        val offset = editor.caretModel.offset
        val element = file.findElementAt(offset) ?: return IntentionPreviewInfo.EMPTY
        val yamlScalar = PsiTreeUtil.getParentOfType(element, YAMLScalar::class.java) ?: return IntentionPreviewInfo.EMPTY

        val targetVersion = VersionUtils.getSafeUpgradeVersion(currentVersion, packageInfo.versions ?: emptyList())
            ?: return IntentionPreviewInfo.EMPTY

        // Modify the scalar inside the preview file copy so the diff is actually shown.
        yamlScalar.updateText("^$targetVersion")

        return IntentionPreviewInfo.DIFF
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
        val targetVersion = VersionUtils.getSafeUpgradeVersion(currentVersion, packageInfo.versions ?: emptyList())
        if (targetVersion == null) {
            return
        }

        WriteCommandAction.runWriteCommandAction(project) {
            val newText = "^$targetVersion"
            yamlScalar.updateText(newText)
        }

        if (runPubGet) {
            FlutterUtils.runFlutterPubGet(project, file)
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
        val action = if (runPubGet) "and run pub get" else ""
        return "Upgrade to $latestVersion $action".trim()
    }

    override fun getFamilyName(): String = "Upgrade package to latest"

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        return PubspecUtils.isPubspecFile(file)
    }

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
        val offset = editor.caretModel.offset
        val element = file.findElementAt(offset) ?: return IntentionPreviewInfo.EMPTY
        val yamlScalar = PsiTreeUtil.getParentOfType(element, YAMLScalar::class.java) ?: return IntentionPreviewInfo.EMPTY

        // Modify the scalar inside the preview file copy so the diff is actually shown.
        yamlScalar.updateText("^$latestVersion")

        return IntentionPreviewInfo.DIFF
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
            FlutterUtils.runFlutterPubGet(project, file)
        }
    }
}