package dev.rutvik.flutter_developer_tools.pubspec.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.psi.PsiElement
import dev.rutvik.flutter_developer_tools.api.PubDevApi
import dev.rutvik.flutter_developer_tools.pubspec.quickfix.FullUpgradeQuickFix
import dev.rutvik.flutter_developer_tools.pubspec.quickfix.SafeUpgradeQuickFix
import dev.rutvik.flutter_developer_tools.services.PubPackageCacheService
import dev.rutvik.flutter_developer_tools.utils.PubspecUtils
import dev.rutvik.flutter_developer_tools.utils.PubspecUtils.isPubPackageName
import dev.rutvik.flutter_developer_tools.utils.VersionUtils
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

        val cache = PubPackageCacheService.getInstance()
        val pkgInfo = cache.getInfo(pkgName)

        if (pkgInfo == null || pkgInfo.latestVersion == null) {
            PubDevApi.requestDetailsIfNeeded(pkgName) { info ->
                cache.updateDetails(info)
            }

            return
        }

        val latestVersion = pkgInfo.latestVersion ?: return

        val normalizedCurrent = PubspecUtils.normalizeVersionString(versionText)
        val updateType = VersionUtils.getUpdateType(normalizedCurrent, latestVersion)

        // Only annotate if there's an update available
        if (updateType == VersionUtils.UpdateType.NONE) {
            holder.newAnnotation(HighlightSeverity.INFORMATION, "")
                .range(yamlScalar)
                .create()
            return
        }

        // Get safe upgrade version
        val safeVersion = VersionUtils.getSafeUpgradeVersion(normalizedCurrent, pkgInfo.versions ?: emptyList())

        // Create annotation with warning/info severity
        val severity = if (updateType == VersionUtils.UpdateType.MAJOR) {
            HighlightSeverity.WARNING
        } else {
            HighlightSeverity.WEAK_WARNING
        }

        val updateLabel = VersionUtils.formatUpdateType(updateType)
        val message = "Update available: $latestVersion ($updateLabel)"

        val builder = holder.newAnnotation(severity, message)
            .range(yamlScalar)
            .textAttributes(CodeInsightColors.WEAK_WARNING_ATTRIBUTES)

        // Add quick fixes
        // If safe version is same as latest, only show one set of fixes
        if (safeVersion != null && safeVersion != latestVersion) {
            builder.withFix(SafeUpgradeQuickFix(pkgName, normalizedCurrent, pkgInfo, runPubGet = false))
            builder.withFix(SafeUpgradeQuickFix(pkgName, normalizedCurrent, pkgInfo, runPubGet = true))
        }
        builder.withFix(FullUpgradeQuickFix(pkgName, latestVersion, runPubGet = false))
        builder.withFix(FullUpgradeQuickFix(pkgName, latestVersion, runPubGet = true))

        builder.create()
    }
}