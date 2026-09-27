package dev.rutvik.flutter_developer_tools.dart.hints

import com.intellij.codeInsight.hints.declarative.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.psi.util.childrenOfType
import com.jetbrains.lang.dart.ide.info.DartFunctionDescription
import com.jetbrains.lang.dart.psi.*
import com.jetbrains.lang.dart.util.DartResolveUtil
import dev.rutvik.flutter_developer_tools.utils.guardExtension

private val PARAM_WHITESPACE_REGEX = Regex("\\s+")
private val PARAM_IDENTIFIER_REGEX = Regex("[A-Za-z_$][A-Za-z0-9_$]*")

class DartDeclarativeParameterHintsProvider : InlayHintsProvider {

    override fun createCollector(file: PsiFile, editor: Editor): InlayHintsCollector {
        return Collector()
    }

    private class Collector : SharedBypassCollector {

        override fun collectFromElement(element: PsiElement, sink: InlayTreeSink) =
            guardExtension("Dart parameter hints", Unit) { collectHints(element, sink) }

        private fun collectHints(element: PsiElement, sink: InlayTreeSink) {
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
                        // Direct children only: the named-constructor reference after `Type.`. Avoids
                        // getReferenceExpressionList(), which Dart plugin 508.1.0 replaced with getReferenceExpression().
                        val referenceExpressions = element.childrenOfType<DartReferenceExpression>()
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
            } catch (e: ProcessCanceledException) {
                throw e
            } catch (_: Exception) {
                null
            }
        }

        private fun String.extractParameterName(): String {
            // The rendered parameter text is one of:
            //   "Type name", "Type name = default", "ReturnType Function(...) name"
            //   (modern function-typed parameter), or "ReturnType name(...)" (old-style
            //   function formal). Resolve the identifier that is actually the parameter name.
            val text = substringBefore("=").trim()
            if (text.contains('(')) {
                // Modern function-typed parameter: the name follows the closing ')'.
                text.substringAfterLast(')').lastIdentifierOrNull()?.let { return it }
                // Old-style function formal: the name precedes the '('.
                text.substringBefore('(').lastIdentifierOrNull()?.let { return it }
            }
            return text.lastIdentifierOrNull() ?: text
        }

        private fun String.lastIdentifierOrNull(): String? {
            val lastToken = trim().split(PARAM_WHITESPACE_REGEX).lastOrNull()?.substringAfterLast('.') ?: return null
            return PARAM_IDENTIFIER_REGEX.findAll(lastToken).lastOrNull()?.value
        }
    }
}