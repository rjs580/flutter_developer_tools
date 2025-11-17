package dev.rutvik.flutter_developer_tools.pubspec.completions

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import dev.rutvik.flutter_developer_tools.pubspec.api.PubDevApi
import dev.rutvik.flutter_developer_tools.pubspec.models.PubPackage
import dev.rutvik.flutter_developer_tools.pubspec.services.PubPackageCacheService
import dev.rutvik.flutter_developer_tools.pubspec.ui.PackageLookupRenderer
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLMapping

class PubPackageCompletionProvider : CompletionProvider<CompletionParameters>() {

    override fun addCompletions(params: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        val project = params.editor.project ?: return

        val position = params.position
        if (!isInDependenciesSection(position)) {
            return
        }

        val cache = PubPackageCacheService.getInstance()

        // Check if cache has packages
        if (cache.packages.isEmpty()) {
            return
        }

        // Restart completion on any prefix change for better responsiveness
        result.restartCompletionOnAnyPrefixChange()

        // Get prefix for filtering
        val prefix = result.prefixMatcher.prefix.lowercase()

        // Filter packages by prefix and sort by priority
        val matchingPackages = cache.packages.keys
            .filter { it.lowercase().startsWith(prefix) }
            .map { name ->
                val pkg = cache.getInfo(name)
                name to calculatePriority(pkg)
            }
            .sortedByDescending { it.second }

        // Get top 5 for detailed rendering
        val top5Names = matchingPackages.take(5).map { it.first }.toSet()

        // Add advertisement at the bottom of the completion popup
        result.addLookupAdvertisement("Packages from pub.dev • Top ${top5Names.size} shown with details")

        for ((name, priority) in matchingPackages) {
            // Only fetch details for top 5
            if (top5Names.contains(name)) {
                PubDevApi.requestDetailsIfNeeded(name) { info ->
                    cache.updateDetails(info)
                }
            }

            val element =  LookupElementBuilder.create(name)
                .withInsertHandler { context, item ->
                    // Custom insertion logic
                    val version = cache.getInfo(name)?.latestVersion
                    val insertString = if (version != null) {
                        "$name: ^$version"
                    } else {
                        "$name:"
                    }

                    // Replace with the correct string
                    context.document.replaceString(
                        context.startOffset,
                        context.tailOffset,
                        insertString
                    )
                }
                .withRenderer(PackageLookupRenderer(name, top5Names.contains(name)))

            // Wrap with priority to show above other suggestions
            val prioritizedElement = PrioritizedLookupElement.withPriority(element, priority)

            result.addElement(prioritizedElement)
        }

        // Only stop here if we actually added completions
        if (matchingPackages.isNotEmpty()) {
            result.stopHere()
        }
    }

    private fun calculatePriority(pkg: PubPackage?): Double {
        var priority = 0.0

        if (pkg?.isFlutterFavorite == true) {
            priority += 1000.0
        }

        pkg?.likes?.times(0.6)?.let { priority += it }
        pkg?.pubPoints?.times(0.4)?.let { priority += it }

        return priority
    }


    private fun isInDependenciesSection(element: PsiElement): Boolean {
        var parent: PsiElement? = element.parent
        var depth = 0

        while (parent != null && depth < 10) {
            if (parent is YAMLKeyValue) {
                val key = parent.keyText
                if (key == "dependencies" || key == "dev_dependencies") {
                    return true
                }
            }

            if (parent is YAMLMapping) {
                val mappingParent = parent.parent
                if (mappingParent is YAMLKeyValue) {
                    val key = mappingParent.keyText
                    if (key == "dependencies" || key == "dev_dependencies") {
                        return true
                    }
                }
            }

            parent = parent.parent
            depth++
        }
        return false
    }
}