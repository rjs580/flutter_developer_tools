package dev.rutvik.flutter_developer_tools.pubspec.completions

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import dev.rutvik.flutter_developer_tools.pubspec.services.PubDevApiService
import dev.rutvik.flutter_developer_tools.pubspec.models.PubPackage
import icons.FlutterIcons
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLMapping

class PubPackageCompletionProvider : CompletionProvider<CompletionParameters>() {
    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet
    ) {
        val position = parameters.position
        if (!isPubspecYaml(position) || !isInDependenciesSection(position)) {
            return
        }

        val prefix = result.prefixMatcher.prefix
        if (prefix.length < 2) return

        val apiService = parameters.editor.project?.service<PubDevApiService>() ?: return

        ProgressManager.checkCanceled()

        val packages = apiService.searchPackages(prefix)

        packages.forEach { pkg ->
            ProgressManager.checkCanceled()

            val lookupElement = LookupElementBuilder.create(pkg.name)
                .withTypeText(pkg.latest)
                .withTailText(buildTailText(pkg), true)
                .withPresentableText(pkg.name)
                .withInsertHandler { ctx, _ ->
                    ctx.document.insertString(ctx.tailOffset, ": ^${pkg.latest}")
                    ctx.editor.caretModel.moveToOffset(ctx.tailOffset)
                }
                .let { builder ->
                    if (pkg.isFlutterFavorite) {
                        builder.withIcon(FlutterIcons.Flutter).bold()
                    } else {
                        builder
                    }
                }

            result.addElement(
                PrioritizedLookupElement.withPriority(
                    lookupElement,
                    calculatePriority(pkg)
                )
            )
        }

        // 🛑 BLOCK ALL OTHER SUGGESTIONS
        result.stopHere()
    }

    private fun buildTailText(pkg: PubPackage): String {
        val parts = mutableListOf<String>()

        if (pkg.isFlutterFavorite) {
            parts.add("Flutter Favorite")
        }

        parts.add("❤ ${pkg.likes}")
        parts.add("${pkg.pubPoints} pts")

        pkg.description?.take(50)?.let {
            parts.add(0, it)
        }

        return " - " + parts.joinToString(" | ")
    }

    private fun calculatePriority(pkg: PubPackage): Double {
        var priority = 0.0

        if (pkg.isFlutterFavorite) {
            priority += 1000.0
        }

        priority += pkg.likes * 0.5
        priority += pkg.pubPoints * 0.2

        return priority
    }

    private fun isPubspecYaml(element: PsiElement): Boolean {
        return element.containingFile?.name == "pubspec.yaml"
    }

    private fun isInDependenciesSection(element: PsiElement): Boolean {
        var parent = element.parent
        while (parent != null) {
            if (parent is YAMLMapping) {
                val keyValue = parent.parent as? YAMLKeyValue
                val keyText = keyValue?.keyText
                if (keyText == "dependencies" || keyText == "dev_dependencies") {
                    return true
                }
            }
            parent = parent.parent
        }
        return false
    }
}