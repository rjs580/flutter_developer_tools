package dev.rutvik.flutter_developer_tools.pubspec.documentation

import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import dev.rutvik.flutter_developer_tools.api.PubDevApi
import dev.rutvik.flutter_developer_tools.api.RepositoryMarkdownFetcher
import dev.rutvik.flutter_developer_tools.models.PubPackage
import dev.rutvik.flutter_developer_tools.utils.PubspecUtils
import dev.rutvik.flutter_developer_tools.utils.PubspecUtils.isPubPackageName
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.parser.MarkdownParser
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLScalar
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * Provides hover documentation for pub.dev packages in pubspec.yaml files.
 *
 * - Shows README.md when hovering over package names
 * - Shows CHANGELOG.md when hovering over version numbers
 */
class PubPackageDocumentationProvider : AbstractDocumentationProvider() {

    private val markdownFlavour = GFMFlavourDescriptor()

    override fun generateDoc(element: PsiElement?, originalElement: PsiElement?): String? {
        element ?: return null

        if (!PubspecUtils.isPubspecFile(element.containingFile)) {
            return null
        }

        return when (element) {
            is YAMLKeyValue -> generatePackageNameDoc(element)
            is YAMLScalar -> generateVersionDoc(element)
            else -> null
        }
    }

    override fun getCustomDocumentationElement(
        editor: Editor,
        file: PsiFile,
        contextElement: PsiElement?,
        targetOffset: Int
    ): PsiElement? {
        contextElement ?: return null

        if (!PubspecUtils.isPubspecFile(file)) {
            return null
        }

        return when (contextElement.parent) {
            is YAMLKeyValue -> {
                val yamlKv = contextElement.parent as YAMLKeyValue
                if (PubspecUtils.isInDependencySection(yamlKv) &&
                    yamlKv.keyText.isPubPackageName() &&
                    PubspecUtils.isPubDevPackage(yamlKv)) {
                    yamlKv
                } else null
            }
            is YAMLScalar -> {
                val yamlScalar = contextElement.parent as YAMLScalar
                val yamlKv = yamlScalar.parent as? YAMLKeyValue
                if (yamlKv != null &&
                    yamlKv.value == yamlScalar &&
                    PubspecUtils.isInDependencySection(yamlKv) &&
                    yamlKv.keyText.isPubPackageName() &&
                    PubspecUtils.isPubDevPackage(yamlKv)) {
                    yamlScalar
                } else null
            }
            else -> null
        }
    }

    private fun generatePackageNameDoc(yamlKv: YAMLKeyValue): String? {
        if (!PubspecUtils.isInDependencySection(yamlKv)) {
            return null
        }

        val pkgName = yamlKv.keyText
        if (!pkgName.isPubPackageName() || !PubspecUtils.isPubDevPackage(yamlKv)) {
            return null
        }

        // Wait for package info to load
        val pkgInfo = PubDevApi.waitForPackageInfo(pkgName)
            ?: return buildLoadingDoc(pkgName, "package information")

        // Try repository URL first, fall back to homepage URL
        val repoUrl = pkgInfo.repositoryUrl ?: pkgInfo.homepageUrl
        if (repoUrl == null) {
            return buildBasicPackageDoc(pkgName, pkgInfo)
        }

        val readmeFuture = CompletableFuture.supplyAsync {
            RepositoryMarkdownFetcher.fetchReadme(repoUrl, pkgName)
        }

        val readme = try {
            readmeFuture.get(30, TimeUnit.SECONDS)
        } catch (_: Exception) {
            null
        }

        return buildPackageDocWithReadme(pkgName, pkgInfo, readme, repoUrl)
    }

    private fun generateVersionDoc(yamlScalar: YAMLScalar): String? {
        val yamlKv = yamlScalar.parent as? YAMLKeyValue ?: return null

        if (yamlKv.value != yamlScalar) {
            return null
        }

        if (!PubspecUtils.isInDependencySection(yamlKv)) {
            return null
        }

        val pkgName = yamlKv.keyText
        if (!pkgName.isPubPackageName() || !PubspecUtils.isPubDevPackage(yamlKv)) {
            return null
        }

        val versionText = yamlScalar.textValue
        if (!PubspecUtils.isSimpleVersion(versionText)) {
            return null
        }

        val normalizedVersion = PubspecUtils.normalizeVersionString(versionText)

        // Wait for package info to load
        val pkgInfo = PubDevApi.waitForPackageInfo(pkgName)
            ?: return buildLoadingDoc(pkgName, "version information")

        // Try repository URL first, fall back to homepage URL
        val repoUrl = pkgInfo.repositoryUrl ?: pkgInfo.homepageUrl
        if (repoUrl == null) {
            return buildBasicVersionDoc(pkgName, normalizedVersion)
        }

        val changelogFuture = CompletableFuture.supplyAsync {
            RepositoryMarkdownFetcher.fetchChangelog(repoUrl, pkgName)
        }

        val changelog = try {
            changelogFuture.get(30, TimeUnit.SECONDS)
        } catch (_: Exception) {
            null
        }

        return buildVersionDocWithChangelog(pkgName, normalizedVersion, changelog, repoUrl)
    }

    private fun buildLoadingDoc(pkgName: String, what: String): String {
        return buildString {
            append(DocumentationMarkup.DEFINITION_START)
            append("<b>$pkgName</b>")
            append(DocumentationMarkup.DEFINITION_END)
            append(DocumentationMarkup.CONTENT_START)
            append("<p><i>Loading $what...</i></p>")
            append(DocumentationMarkup.CONTENT_END)
        }
    }

    private fun buildBasicPackageDoc(
        pkgName: String,
        pkgInfo: PubPackage
    ): String {
        return buildString {
            append(DocumentationMarkup.DEFINITION_START)
            append("<b>$pkgName</b>")
            pkgInfo.latestVersion?.let { append(" <code>$it</code>") }
            append(DocumentationMarkup.DEFINITION_END)

            append(DocumentationMarkup.CONTENT_START)

            pkgInfo.description?.let {
                append("<p>$it</p>")
            }

            addPackageMetadata(pkgInfo)

            append("<p><a href='https://pub.dev/packages/$pkgName'>View on pub.dev</a></p>")

            // Show homepage if available, even without repository
            pkgInfo.homepageUrl?.let { homepage ->
                append("<p><a href='$homepage'>Homepage</a></p>")
            }

            if (pkgInfo.repositoryUrl == null && pkgInfo.homepageUrl == null) {
                append("<p><i>Repository not available</i></p>")
            }

            append(DocumentationMarkup.CONTENT_END)
        }
    }

    private fun buildPackageDocWithReadme(
        pkgName: String,
        pkgInfo: PubPackage,
        readme: String?,
        repoUrl: String
    ): String {
        return buildString {
            append(DocumentationMarkup.DEFINITION_START)
            append("<b>$pkgName</b>")
            pkgInfo.latestVersion?.let { append(" <code>$it</code>") }
            append(DocumentationMarkup.DEFINITION_END)

            append(DocumentationMarkup.CONTENT_START)

            addPackageMetadata(pkgInfo)

            append("<p>")
            append("<a href='https://pub.dev/packages/$pkgName'>View on pub.dev</a>")

            // Determine if this is a repository or homepage
            val isRepository = pkgInfo.repositoryUrl != null && pkgInfo.repositoryUrl == repoUrl
            val linkText = if (isRepository) "Repository" else "Homepage"
            append(" • <a href='$repoUrl'>$linkText</a>")

            append("</p>")

            append("<hr/>")

            if (readme != null) {
                val resolvedMarkdown = RepositoryMarkdownFetcher.resolveImageUrls(readme, repoUrl)
                val html = convertMarkdownToHtml(resolvedMarkdown)
                append(html)
            } else {
                append("<p><i>README not available</i></p>")
            }

            append(DocumentationMarkup.CONTENT_END)
        }
    }

    private fun buildBasicVersionDoc(pkgName: String, version: String): String {
        return buildString {
            append(DocumentationMarkup.DEFINITION_START)
            append("<b>$pkgName</b> version <code>$version</code>")
            append(DocumentationMarkup.DEFINITION_END)

            append(DocumentationMarkup.CONTENT_START)
            append("<p><a href='https://pub.dev/packages/$pkgName/versions/$version'>View version details</a></p>")
            append("<p><i>Repository not available</i></p>")
            append(DocumentationMarkup.CONTENT_END)
        }
    }

    private fun buildVersionDocWithChangelog(
        pkgName: String,
        version: String,
        changelog: String?,
        repoUrl: String
    ): String {
        return buildString {
            append(DocumentationMarkup.DEFINITION_START)
            append("<b>$pkgName</b> version <code>$version</code>")
            append(DocumentationMarkup.DEFINITION_END)

            append(DocumentationMarkup.CONTENT_START)

            append("<p>")
            append("<a href='https://pub.dev/packages/$pkgName/versions/$version'>View version details</a>")
            append(" • <a href='$repoUrl'>Repository</a>")
            append("</p>")

            append("<hr/>")

            if (changelog != null) {
                val resolvedMarkdown = RepositoryMarkdownFetcher.resolveImageUrls(changelog, repoUrl)
                val html = convertMarkdownToHtml(resolvedMarkdown)
                append(html)
            } else {
                append("<p><i>Changelog not available</i></p>")
            }

            append(DocumentationMarkup.CONTENT_END)
        }
    }

    private fun StringBuilder.addPackageMetadata(pkgInfo: PubPackage) {
        val metadata = mutableListOf<String>()
        pkgInfo.isFlutterFavorite.let { if (it) metadata.add("Flutter Favorite ⭐") }
        pkgInfo.likes?.let { metadata.add("$it ❤") }
        pkgInfo.pubPoints?.let { metadata.add("$it pts") }

        if (metadata.isNotEmpty()) {
            append("<p>${metadata.joinToString(" • ")}</p>")
        }
    }

    /**
     * Converts markdown to HTML using IntelliJ's markdown parser.
     */
    private fun convertMarkdownToHtml(markdown: String): String {
        return try {
            val parsedTree = MarkdownParser(markdownFlavour).buildMarkdownTreeFromString(markdown)
            HtmlGenerator(markdown, parsedTree, markdownFlavour).generateHtml()
        } catch (_: Exception) {
            "<pre>${escapeHtml(markdown)}</pre>"
        }
    }

    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }
}