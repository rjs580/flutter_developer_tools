
package dev.rutvik.flutter_developer_tools.pubspec.completions

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.util.ProcessingContext
import dev.rutvik.flutter_developer_tools.api.PubDevApi
import dev.rutvik.flutter_developer_tools.models.PubPackage
import dev.rutvik.flutter_developer_tools.services.PubPackageCacheService
import dev.rutvik.flutter_developer_tools.pubspec.ui.PackageLookupRenderer
import dev.rutvik.flutter_developer_tools.utils.PubspecUtils

/**
 * Provides code completion for pub.dev packages in pubspec.yaml files.
 *
 * This completion provider:
 * - Activates within dependencies/dev_dependencies sections
 * - Fetches package suggestions from pub.dev cache
 * - Displays package details like version, likes and pub points
 * - Automatically inserts latest version when completing a package
 * - Prioritizes results based on exact matches and package popularity
 */
class PubPackageCompletionProvider : CompletionProvider<CompletionParameters>() {

    override fun addCompletions(params: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        params.editor.project ?: return

        val position = params.position

        // Check if we're in a pubspec.yaml file and within a dependency section
        if (!PubspecUtils.isPubspecFile(position.containingFile)) {
            return
        }

        if (!PubspecUtils.isElementInDependencySection(position)) {
            return
        }

        val cache = PubPackageCacheService.getInstance()

        // Early exit when memory cache is not loaded
        if (!cache.isMemoryCacheLoaded()) return

        // Get prefix for filtering
        val prefix = result.prefixMatcher.prefix.lowercase()

        if (prefix.isEmpty()) return

        // Search packages matching the prefix
        // This will ensure memory cache is loaded and return filtered results
        val matchingPackages = cache.searchPackages(prefix, 25)

        // Add advertisement at the bottom of the completion popup
        result.addLookupAdvertisement("Results from pub.dev • Last updated ${cache.lastPackageListUpdate}")

        for (pkg in matchingPackages) {
            val name = pkg.name
            val priority = calculatePriority(pkg, prefix)

            PubDevApi.requestDetailsIfNeeded(name) { info ->
                cache.updateDetails(info)
            }

            val element =  LookupElementBuilder.create(name)
                .withInsertHandler { insertContext, _ ->
                    // Custom insertion logic
                    val version = cache.getInfo(name)?.latestVersion
                    val insertString = if (version != null) {
                        "$name: ^$version"
                    } else {
                        "$name:"
                    }

                    // Replace with the correct string
                    insertContext.document.replaceString(
                        insertContext.startOffset,
                        insertContext.tailOffset,
                        insertString
                    )
                }
                .withRenderer(PackageLookupRenderer(name))

            // Wrap with priority to show above other suggestions
            val prioritizedElement = PrioritizedLookupElement.withPriority(element, priority)

            result.addElement(prioritizedElement)
        }

        // Only stop here if we actually added completions
        if (matchingPackages.isNotEmpty()) {
            result.stopHere()
        }
    }

    private fun calculatePriority(pkg: PubPackage?, prefix: String): Double {
        var priority = 0.0

        // Strongly prefer exact/prefix matches over popularity
        pkg?.name?.let { name ->
            priority += when {
                name.equals(prefix, ignoreCase = true) -> 1000000.0  // Exact match
                name.startsWith(prefix, ignoreCase = true) -> 500000.0  // Prefix match
                else -> 100000.0  // Contains match - still higher than max popularity
            }
        }

        // Only apply popularity bonus if details are loaded
        if (pkg?.likes != null || pkg?.pubPoints != null) {
            if (pkg.isFlutterFavorite == true) {
                priority += 10000.0
            }

            pkg.likes?.times(0.6)?.let { priority += it }
            pkg.pubPoints?.times(0.4)?.let { priority += it }
        }

        return priority
    }
}