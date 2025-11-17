package dev.rutvik.flutter_developer_tools.pubspec.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
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

        yamlKv.key?.let { keyElement ->
            holder.newAnnotation(HighlightSeverity.INFORMATION, "")
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

        holder.newAnnotation(HighlightSeverity.INFORMATION, "")
            .range(yamlScalar)
            .create()
    }
}