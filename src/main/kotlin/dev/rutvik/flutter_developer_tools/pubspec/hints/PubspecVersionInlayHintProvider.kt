package dev.rutvik.flutter_developer_tools.pubspec.hints

import com.intellij.codeInsight.hints.*
import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.codeInsight.hints.presentation.MenuOnClickPresentation
import com.intellij.codeInsight.hints.presentation.SequencePresentation
import com.intellij.lang.Language
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import dev.rutvik.flutter_developer_tools.api.PubDevApi
import dev.rutvik.flutter_developer_tools.models.PubPackage
import dev.rutvik.flutter_developer_tools.services.PubPackageCacheService
import dev.rutvik.flutter_developer_tools.utils.PubspecUtils
import dev.rutvik.flutter_developer_tools.utils.PubspecUtils.isPubPackageName
import dev.rutvik.flutter_developer_tools.utils.VersionUtils
import org.jetbrains.yaml.YAMLLanguage
import org.jetbrains.yaml.psi.YAMLKeyValue
import javax.swing.JPanel

/**
 * Provides inlay hints showing available package updates in pubspec.yaml.
 * Displays hints like "7.0.0 (Major Update)" next to package versions.
 */
@Suppress("UnstableApiUsage")
class PubspecVersionInlayHintProvider : InlayHintsProvider<NoSettings> {

    override val key: SettingsKey<NoSettings> = SettingsKey("pubspec.version.hints")
    override val name: String = "Package version updates"
    override val previewText: String = """
            dependencies:
              provider: ^6.0.5
              http: ^0.13.0
        """.trimIndent()

    override fun createSettings(): NoSettings = NoSettings()

    override fun getCollectorFor(
        file: PsiFile,
        editor: Editor,
        settings: NoSettings,
        sink: InlayHintsSink
    ): InlayHintsCollector? {
        if (!PubspecUtils.isPubspecFile(file)) return null

        return PubspecVersionInlayCollector(editor, file)
    }

    override fun createConfigurable(settings: NoSettings): ImmediateConfigurable {
        return object : ImmediateConfigurable {
            override fun createComponent(listener: ChangeListener): JPanel = JPanel()
        }
    }

    private class PubspecVersionInlayCollector(
        editor: Editor,
        private val file: PsiFile
    ) : FactoryInlayHintsCollector(editor) {

        override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
            if (element !is YAMLKeyValue) return true

            if (!PubspecUtils.isInDependencySection(element)) return true

            val pkgName = element.keyText
            if (!pkgName.isPubPackageName()) return true
            if (!PubspecUtils.isPubDevPackage(element)) return true

            val valueElement = element.value ?: return true
            val currentVersion = valueElement.text.trim().trim('"', '\'')

            if (!PubspecUtils.isSimpleVersion(currentVersion)) return true

            val cache = PubPackageCacheService.getInstance()
            val pkgInfo = cache.getInfo(pkgName)

            if (pkgInfo == null || pkgInfo.latestVersion == null) {
                PubDevApi.requestDetailsIfNeeded(pkgName) { info ->
                    cache.updateDetails(info)
                }

                return true
            }

            val latestVersion = pkgInfo.latestVersion ?: return true

            val normalizedCurrent = PubspecUtils.normalizeVersionString(currentVersion)
            val updateType = VersionUtils.getUpdateType(normalizedCurrent, latestVersion)

            if (updateType == VersionUtils.UpdateType.NONE) return true

            // Get safe upgrade version if available
            val safeVersion = VersionUtils.getSafeUpgradeVersion(normalizedCurrent, pkgInfo.versions ?: emptyList())

            // Build the inlay hint presentation
            val presentation = buildHintPresentation(
                latestVersion,
                safeVersion,
                updateType,
                pkgInfo
            )

            // Add hint at the end of the line
            sink.addInlineElement(
                offset = valueElement.textRange.endOffset,
                relatesToPrecedingText = true,
                presentation = presentation,
                placeAtTheEndOfLine = false
            )

            return true
        }

        private fun buildHintPresentation(
            latestVersion: String,
            safeVersion: String?,
            updateType: VersionUtils.UpdateType,
            pkgInfo: PubPackage,
        ): InlayPresentation {
            val updateLabel = VersionUtils.formatUpdateType(updateType)

            val parts = mutableListOf<InlayPresentation>()

            // Add spacing
            parts.add(factory.textSpacePlaceholder(2, true))

            // Show safe version if it's different from latest, otherwise show latest
            if (safeVersion != null && safeVersion != latestVersion) {
                parts.add(factory.smallText("Safe: "))
                parts.add(factory.smallText(safeVersion))
                parts.add(factory.smallText(" | Latest: "))
                parts.add(factory.smallText(latestVersion))
            } else {
                parts.add(factory.smallText(latestVersion))
            }

            // " (Major Update)"
            if (updateLabel.isNotEmpty()) {
                parts.add(factory.smallText(" ("))
                parts.add(factory.smallText(updateLabel))
                parts.add(factory.smallText(")"))
            }

            // Add badges with better visibility
            if (pkgInfo.isFlutterFavorite) {
                parts.add(factory.textSpacePlaceholder(2, true))
                val badgeText = factory.smallText(" ★ FLUTTER FAVORITE ")
                parts.add(factory.roundWithBackground(badgeText))
            }

            if (pkgInfo.isDiscontinued) {
                parts.add(factory.textSpacePlaceholder(2, true))
                val badgeText = factory.smallText(" ⚠ DISCONTINUED ")
                parts.add(factory.roundWithBackground(badgeText))
            }

            if (pkgInfo.isDart3Incompatible) {
                parts.add(factory.textSpacePlaceholder(2, true))
                val badgeText = factory.smallText(" ⚠ DART3-INCOMPATIBLE ")
                parts.add(factory.roundWithBackground(badgeText))
            }

            val sequence = SequencePresentation(parts)

            // Make it clickable to show context menu (optional)
            return MenuOnClickPresentation(sequence, file.project) {
                emptyList()
            }
        }

        private fun createBadge(text: String): InlayPresentation {
            val textPresentation = factory.smallText(text)
            return factory.roundWithBackground(textPresentation)
        }
    }

    override fun isLanguageSupported(language: Language): Boolean {
        return language is YAMLLanguage
    }
}