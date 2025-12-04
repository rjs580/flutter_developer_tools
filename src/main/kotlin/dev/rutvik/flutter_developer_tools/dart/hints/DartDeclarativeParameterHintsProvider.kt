package dev.rutvik.flutter_developer_tools.dart.hints

import com.intellij.codeInsight.hints.declarative.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.childrenOfType
import com.jetbrains.lang.dart.ide.info.DartFunctionDescription
import com.jetbrains.lang.dart.psi.*
import com.jetbrains.lang.dart.util.DartResolveUtil

class DartDeclarativeParameterHintsProvider : InlayHintsProvider {

    override fun createCollector(file: PsiFile, editor: Editor): InlayHintsCollector {
        return Collector()
    }

    private class Collector : SharedBypassCollector {

        override fun collectFromElement(element: PsiElement, sink: InlayTreeSink) {
            if (DumbService.isDumb(element.project)) return

            val arguments = when (element) {
                is DartCallExpression -> element.childrenOfType<DartArguments>().firstOrNull()
                is DartNewExpression -> element.arguments
                else -> null
            } ?: return

            val expressionList = arguments.argumentList?.expressionList ?: return
            val functionDescription = getFunctionDescription(element) ?: return
            val parameterNames = functionDescription.parameters.map { it.text }

            expressionList.forEachIndexed { index, expression ->
                if (index >= parameterNames.size) return@forEachIndexed

                val parameterName = parameterNames[index].extractParameterName()

                // Skip if parameter name matches the argument text
                if (parameterName == expression.text) return@forEachIndexed

                sink.addPresentation(
                    position = InlineInlayPosition(expression.textOffset, true),
                    payloads = null,
                    tooltip = null,
                    hintFormat = HintFormat.default
                ) {
                    text("$parameterName:")
                }
            }
        }

        private fun getFunctionDescription(element: PsiElement): DartFunctionDescription? {
            return try {
                when (element) {
                    is DartCallExpression -> DartFunctionDescription.tryGetDescription(element)
                    is DartNewExpression -> {
                        val type = element.type ?: return null
                        val referenceExpressions = element.referenceExpressionList
                        val psiElement = if (referenceExpressions.isEmpty()) {
                            type.referenceExpression
                        } else {
                            referenceExpressions.lastOrNull()
                        }

                        val target = psiElement?.resolve()
                        if (target is DartComponentName) {
                            val classResolveResult = DartResolveUtil.resolveClassByType(type)
                            if (classResolveResult != null) {
                                DartFunctionDescription.createDescription(
                                    target.parent as DartComponent,
                                    classResolveResult
                                )
                            } else null
                        } else null
                    }
                    else -> null
                }
            } catch (_: Exception) {
                null
            }
        }

        private fun String.extractParameterName(): String {
            if (length == 1) return this
            return split("(").first().replace(Regex("[^a-zA-Z\\s]"), "").split(" ").last()
        }
    }
}