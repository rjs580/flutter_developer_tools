package dev.rutvik.flutter_developer_tools.dart.hints

import com.intellij.codeInsight.hints.HintInfo
import com.intellij.codeInsight.hints.InlayInfo
import com.intellij.codeInsight.hints.InlayParameterHintsProvider
import com.intellij.lang.Language
import com.intellij.psi.PsiElement
import com.intellij.psi.util.childrenOfType
import com.jetbrains.lang.dart.DartLanguage
import com.jetbrains.lang.dart.ide.info.DartFunctionDescription
import com.jetbrains.lang.dart.psi.*

/**
 * Provides parameter name hints for non-named arguments in Dart method calls.
 * Shows hints like: methodCall(value: 42)
 */
@Suppress("UnstableApiUsage")
class DartParameterHintsProvider : InlayParameterHintsProvider {

    override fun getDefaultBlackList(): MutableSet<String> {
        return mutableSetOf(
            "dart.core",
            "(fn)",
            "(a)",
            "(a, b)",
            "(x)",
            "(y)",
            "(z)"
        )
    }

    override fun getBlackListDependencyLanguage(): Language = DartLanguage.INSTANCE

    override fun getParameterHints(element: PsiElement): List<InlayInfo> {
        val arguments = when (element) {
            is DartCallExpression -> element.childrenOfType<DartArguments>().firstOrNull()
            is DartNewExpression -> element.arguments
            else -> null
        } ?: return emptyList()

        val expressionList = arguments.argumentList?.expressionList ?: return emptyList()
        val functionDescription = getFunctionDescription(element) ?: return emptyList()
        val parameterNames = functionDescription.parameters.map { it.text }

        return expressionList.mapIndexedNotNull { index, expression ->
            if (index >= parameterNames.size) return@mapIndexedNotNull null

            val parameterName = parameterNames[index].extractParameterName()

            // Skip if parameter name matches the argument text (already clear)
            if (parameterName == expression.text) return@mapIndexedNotNull null

            val offset = expression.textOffset
            InlayInfo(parameterName, offset)
        }
    }

    override fun getHintInfo(element: PsiElement): HintInfo? {
        return getFunctionDescription(element)?.let { getMethodInfo(it) }
    }

    private fun getMethodInfo(functionDescription: DartFunctionDescription): HintInfo.MethodInfo {
        val parameterNames = functionDescription.parameters.map { it.text.extractParameterName() }
        return HintInfo.MethodInfo(functionDescription.name, parameterNames)
    }

    private fun getFunctionDescription(element: PsiElement): DartFunctionDescription? {
        return when (element) {
            is DartCallExpression -> DartFunctionDescription.tryGetDescription(element)
            is DartNewExpression -> {
                val type = element.type
                val referenceExpressions = element.referenceExpressionList
                val psiElement = if (referenceExpressions.isEmpty() && type != null) {
                    type.referenceExpression
                } else {
                    referenceExpressions.lastOrNull()
                }

                val target = psiElement?.resolve()
                if (target is DartComponentName) {
                    val classResolveResult = type?.let {
                        com.jetbrains.lang.dart.util.DartResolveUtil.resolveClassByType(it)
                    }
                    DartFunctionDescription.createDescription(target.parent as DartComponent, classResolveResult)
                } else null
            }
            else -> null
        }
    }

    private fun String.extractParameterName(): String {
        if (length == 1) return this
        return split("(").first().replace(Regex("[^a-zA-Z\\s]"), "").split(" ").last()
    }
}