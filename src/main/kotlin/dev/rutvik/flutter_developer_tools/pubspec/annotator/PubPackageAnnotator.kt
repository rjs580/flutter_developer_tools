package dev.rutvik.flutter_developer_tools.pubspec.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.util.containers.ContainerUtil
import dev.rutvik.flutter_developer_tools.pubspec.utils.PubspecUtils
import dev.rutvik.flutter_developer_tools.pubspec.utils.PubspecUtils.isPubPackageName
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLScalar

/**
 * Shows a tooltip indicating the shortcut key to press to open the full pub package documentation.
 * Works alongside PubPackageReferenceContributor which provides the actual clickable hyperlinks.
 */
class PubPackageAnnotator : Annotator {

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (holder.isBatchMode || !PubspecUtils.isPubspecFile(element.containingFile)) return

        when (element) {
            is YAMLKeyValue -> annotatePackageName(element, holder)
            is YAMLScalar -> annotateVersionNumber(element, holder)
        }
    }

    private fun annotatePackageName(yamlKv: YAMLKeyValue, holder: AnnotationHolder) {
        if (!PubspecUtils.isInDependencySection(yamlKv)) return

        val pkgName = yamlKv.keyText
        if (!pkgName.isPubPackageName()) return

        if (!PubspecUtils.isPubDevPackage(yamlKv)) return

        val message = getShortcutMessage(holder)

        yamlKv.key?.let { keyElement ->
            holder.newAnnotation(HighlightSeverity.INFORMATION, message)
                .range(keyElement)
                .create()
        }
    }

    private fun annotateVersionNumber(yamlScalar: YAMLScalar, holder: AnnotationHolder) {
        val yamlKv = yamlScalar.parent as? YAMLKeyValue ?: return

        // Check if this is the value of a package dependency
        if (yamlKv.value != yamlScalar) return
        if (!PubspecUtils.isInDependencySection(yamlKv)) return

        val pkgName = yamlKv.keyText
        if (!pkgName.isPubPackageName()) return
        if (!PubspecUtils.isPubDevPackage(yamlKv)) return

        val versionText = yamlScalar.textValue
        if (!PubspecUtils.isSimpleVersion(versionText)) return

        val message = getShortcutMessage(holder)

        holder.newAnnotation(HighlightSeverity.INFORMATION, message)
            .range(yamlScalar)
            .create()
    }

    private fun getShortcutMessage(holder: AnnotationHolder): String {
        return holder.currentAnnotationSession.getUserData(MESSAGE_KEY) ?: run {
            val message = buildShortcutMessage()
            holder.currentAnnotationSession.putUserData(MESSAGE_KEY, message)
            message
        }
    }

    private fun buildShortcutMessage(): String {
        val shortcuts = KeymapManager.getInstance().activeKeymap.getShortcuts(IdeActions.ACTION_QUICK_JAVADOC)
        return ContainerUtil.find(shortcuts) { it.isKeyboard }?.let { keyboardShortcut ->
            val shortcutText = KeymapUtil.getShortcutText(keyboardShortcut)
            "Press $shortcutText to open full documentation"
        } ?: "Open full documentation"
    }
}

private val MESSAGE_KEY = Key.create<String>("pub.package.hyperlink.message")