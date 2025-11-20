
package dev.rutvik.flutter_developer_tools.dart.hints

import com.intellij.codeInsight.hints.*
import com.intellij.lang.Language
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.childrenOfType
import com.intellij.ui.dsl.builder.panel
import com.jetbrains.lang.dart.DartLanguage
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService
import com.jetbrains.lang.dart.psi.DartComponentName
import com.jetbrains.lang.dart.psi.DartSimpleFormalParameter
import com.jetbrains.lang.dart.psi.DartType
import com.jetbrains.lang.dart.psi.DartVarAccessDeclaration
import javax.swing.JPanel

/**
 * Provides type hints for variables without explicit type declarations.
 * Shows inferred types for variables declared with 'var', 'final', or 'const'.
 * Uses caching to minimize calls to Dart Analysis Server.
 */
@Suppress("UnstableApiUsage")
class DartTypeHintsProvider : InlayHintsProvider<DartTypeHintsProvider.Settings> {

    data class Settings(var showBeforeIdentifier: Boolean = true)

    override val key: SettingsKey<Settings> = settingsKey
    override val name: String = "Variable type hints"
    override val group: InlayGroup = InlayGroup.TYPES_GROUP

    override val previewText: String = """
        void main() {
          var name = "Flutter";
          final count = 42;
          const isActive = true;
          
          // Before identifier mode (default):
          // String name = "Flutter";
          
          // After identifier mode:
          // name: String = "Flutter";
        }
    """.trimIndent()

    override fun createSettings(): Settings = Settings()

    override fun getCollectorFor(
        file: PsiFile,
        editor: Editor,
        settings: Settings,
        sink: InlayHintsSink
    ): InlayHintsCollector {
        return DartTypeHintsCollector(editor, file, settings)
    }

    override fun createConfigurable(settings: Settings): ImmediateConfigurable {
        return object : ImmediateConfigurable {
            private val initialShowBefore = settings.showBeforeIdentifier

            override fun createComponent(listener: ChangeListener): JPanel = panel {
                row {
                    checkBox("Show type before identifier")
                        .comment("When enabled: <b>String</b> name = \"value\"<br/>When disabled: name<b>: String</b> = \"value\"")
                        .applyToComponent {
                            isSelected = initialShowBefore
                            addItemListener {
                                settings.showBeforeIdentifier = this.isSelected
                                listener.settingsChanged()
                            }
                        }
                }
            }

            override fun reset() {
                settings.showBeforeIdentifier = initialShowBefore
            }

            override val mainCheckboxText: String = "Show variable type hints"
        }
    }

    override fun isLanguageSupported(language: Language): Boolean {
        return language is DartLanguage
    }

    private class DartTypeHintsCollector(
        editor: Editor,
        private val file: PsiFile,
        private val settings: Settings
    ) : FactoryInlayHintsCollector(editor) {

        override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
            ProgressManager.checkCanceled()

            if (DumbService.isDumb(element.project)) {
                return true
            }

            // Handle variable declarations
            if (element is DartVarAccessDeclaration) {
                // If already has explicit type, skip
                if (element.childrenOfType<DartType>().isNotEmpty()) return true

                val identifiers = element.childrenOfType<DartComponentName>()
                if (identifiers.isEmpty()) return true

                identifiers.forEach { identifier ->
                    val type = getCachedType(element, identifier)
                    if (type != null && type != "dynamic" && !type.startsWith("_")) {
                        submitInlayHint(identifier, type, sink)
                    }
                }
            }

            // Handle simple formal parameters (function parameters without type)
            if (element is DartSimpleFormalParameter) {
                if (element.childrenOfType<DartType>().isNotEmpty()) return true

                val identifier = element.childrenOfType<DartComponentName>().firstOrNull() ?: return true
                val type = getCachedType(element, identifier)
                if (type != null && type != "dynamic" && !type.startsWith("_")) {
                    submitInlayHint(identifier, type, sink)
                }
            }

            return true
        }

        /**
         * Gets type from cache or fetches from Dart Analysis Server if not cached.
         * Cache is invalidated when the file is modified.
         */
        private fun getCachedType(element: PsiElement, identifier: DartComponentName): String? {
            return CachedValuesManager.getCachedValue(identifier, TYPE_CACHE_KEY) {
                val type = getTypeFromAnalyzer(identifier)
                CachedValueProvider.Result.create(
                    type,
                    identifier,
                    PsiModificationTracker.getInstance(element.project)
                )
            }
        }

        private fun getTypeFromAnalyzer(identifier: DartComponentName): String? {
            if (file.virtualFile == null) return null

            val das = DartAnalysisServerService.getInstance(file.project)

            // Early exit if analysis server is not ready
            if (!das.isServerProcessActive) return null

            return try {
                das.analysis_getHover(file.virtualFile, identifier.textOffset)
                    .firstOrNull()?.staticType
            } catch (_: Exception) {
                // If analysis server is unavailable or slow, don't block
                null
            }
        }

        private fun submitInlayHint(
            identifier: DartComponentName,
            type: String,
            sink: InlayHintsSink
        ) {
            val identifierRange = identifier.textRange
            val typeRepresentation = factory.smallText(type)

            val (offset, representation) = if (settings.showBeforeIdentifier) {
                // Before: String name
                identifierRange.startOffset to factory.seq(
                    factory.roundWithBackground(typeRepresentation),
                    factory.textSpacePlaceholder(1, true)
                )
            } else {
                // After: name: String
                identifierRange.endOffset to factory.roundWithBackground(
                    factory.seq(factory.smallText(": "), typeRepresentation)
                )
            }

            sink.addInlineElement(offset, true, representation, false)
        }
    }
}

@Suppress("UnstableApiUsage")
private val settingsKey = SettingsKey<DartTypeHintsProvider.Settings>("dart.type.hints")
private val TYPE_CACHE_KEY = Key.create<CachedValue<String?>>("DART_TYPE_HINT_CACHE")