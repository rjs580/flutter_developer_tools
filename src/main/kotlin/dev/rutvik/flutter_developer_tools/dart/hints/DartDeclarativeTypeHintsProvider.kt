package dev.rutvik.flutter_developer_tools.dart.hints

import com.intellij.codeInsight.hints.declarative.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.childrenOfType
import com.jetbrains.lang.dart.psi.DartComponentName
import com.jetbrains.lang.dart.psi.DartSimpleFormalParameter
import com.jetbrains.lang.dart.psi.DartType
import com.jetbrains.lang.dart.psi.DartVarAccessDeclaration
import dev.rutvik.flutter_developer_tools.utils.guardExtension

class DartDeclarativeTypeHintsProvider : InlayHintsProvider {

    override fun createCollector(file: PsiFile, editor: Editor): InlayHintsCollector {
        return Collector(file)
    }

    private class Collector(private val file: PsiFile) : SharedBypassCollector {

        override fun collectFromElement(element: PsiElement, sink: InlayTreeSink) =
            guardExtension("Dart type hints", Unit) { collectHints(element, sink) }

        private fun collectHints(element: PsiElement, sink: InlayTreeSink) {
            if (DumbService.isDumb(element.project)) return

            when (element) {
                is DartVarAccessDeclaration -> collectVariableTypeHint(element, sink)
                is DartSimpleFormalParameter -> collectParameterTypeHint(element, sink)
            }
        }

        private fun collectVariableTypeHint(element: DartVarAccessDeclaration, sink: InlayTreeSink) {
            // Skip if already has explicit type
            if (element.childrenOfType<DartType>().isNotEmpty()) return

            val identifiers = element.childrenOfType<DartComponentName>()
            identifiers.forEach { identifier ->
                val type = getTypeFromAnalyzer(identifier)
                if (type != null && type != "dynamic" && !type.startsWith("_")) {
                    sink.addPresentation(
                        position = InlineInlayPosition(identifier.textRange.startOffset, true),
                        payloads = null,
                        tooltip = null,
                        hintFormat = HintFormat.default
                    ) {
                        text(type)
                    }
                }
            }
        }

        private fun collectParameterTypeHint(element: DartSimpleFormalParameter, sink: InlayTreeSink) {
            // Skip if already has explicit type
            if (element.childrenOfType<DartType>().isNotEmpty()) return

            val identifier = element.childrenOfType<DartComponentName>().firstOrNull() ?: return
            val type = getTypeFromAnalyzer(identifier)
            if (type != null && type != "dynamic" && !type.startsWith("_")) {
                sink.addPresentation(
                    position = InlineInlayPosition(identifier.textRange.startOffset, true),
                    payloads = null,
                    tooltip = null,
                    hintFormat = HintFormat.default
                ) {
                    text(type)
                }
            }
        }

        private fun getTypeFromAnalyzer(identifier: DartComponentName): String? {
            val virtualFile = file.virtualFile ?: return null
            val document = file.viewProvider.document ?: return null
            return DartStaticTypeResolver.getStaticType(file.project, virtualFile, document, identifier.textOffset)
        }
    }
}