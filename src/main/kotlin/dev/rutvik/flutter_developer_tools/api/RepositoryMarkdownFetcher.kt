package dev.rutvik.flutter_developer_tools.api

import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.io.HttpRequests
import java.io.IOException
import java.net.URI

/**
 * Utility to fetch README.md and CHANGELOG.md from various Git repository hosts.
 * Supports GitHub, GitLab, Bitbucket, and other common hosts.
 * Handles monorepo structures where packages are in subdirectories.
 */
object RepositoryMarkdownFetcher {
    private val log = Logger.getInstance(RepositoryMarkdownFetcher::class.java)

    private const val CACHE_TTL_MILLIS = 60L * 60L * 1000L // 1 hour for successful fetches
    private const val NEGATIVE_CACHE_TTL_MILLIS = 5L * 60L * 1000L // 5 minutes for misses/failures
    private const val MAX_MARKDOWN_BYTES = 2 * 1024 * 1024 // 2 MB safety cap for untrusted content

    private data class CacheEntry(val content: String?, val timestampMillis: Long)

    private val cache = java.util.concurrent.ConcurrentHashMap<String, CacheEntry>()

    /**
     * Wraps a fetch with a short-lived cache keyed by (kind, repository, package). Misses are
     * cached too, so a repository that has no such file is not re-scanned (hundreds of
     * requests) on every hover; misses use a shorter TTL so a transient network failure does
     * not hide a README for long.
     */
    private fun cachedFetch(
        kind: String,
        repositoryUrl: String,
        packageName: String?,
        fetch: () -> String?
    ): String? {
        val key = "$kind|$repositoryUrl|${packageName ?: ""}"
        val now = System.currentTimeMillis()
        cache[key]?.let {
            val ttl = if (it.content != null) CACHE_TTL_MILLIS else NEGATIVE_CACHE_TTL_MILLIS
            if (now - it.timestampMillis < ttl) return it.content
        }
        val content = fetch()
        cache[key] = CacheEntry(content, now)
        return content
    }

    /**
     * Fetches README.md from a repository URL.
     * Automatically detects the repository type and constructs the appropriate raw file URL.
     *
     * @param repositoryUrl The repository URL
     * @param packageName Optional package name to search for in common monorepo locations
     */
    fun fetchReadme(repositoryUrl: String, packageName: String? = null): String? =
        cachedFetch("readme", repositoryUrl, packageName) { fetchReadmeUncached(repositoryUrl, packageName) }

    private fun fetchReadmeUncached(repositoryUrl: String, packageName: String?): String? {
        // Try multiple README variants
        val readmeVariants = listOf(
            "README.md",
            "Readme.md",
            "readme.md",
            "README.MD",
            "README",
            "readme"
        )

        for (variant in readmeVariants) {
            val urls = buildRawFileUrls(repositoryUrl, variant, packageName)
            val content = fetchFirstAvailable(urls)
            if (content != null) {
                return content
            }
        }

        return null
    }

    /**
     * Fetches CHANGELOG.md from a repository URL.
     * Tries multiple common changelog file names.
     *
     * @param repositoryUrl The repository URL
     * @param packageName Optional package name to search for in common monorepo locations
     */
    fun fetchChangelog(repositoryUrl: String, packageName: String? = null): String? =
        cachedFetch("changelog", repositoryUrl, packageName) { fetchChangelogUncached(repositoryUrl, packageName) }

    private fun fetchChangelogUncached(repositoryUrl: String, packageName: String?): String? {
        // Try multiple changelog variants
        val changelogVariants = listOf(
            "CHANGELOG.md",
            "Changelog.md",
            "changelog.md",
            "CHANGELOG.MD",
            "CHANGELOG",
            "CHANGES.md",
            "Changes.md",
            "changes.md",
            "HISTORY.md",
            "History.md",
            "history.md",
            "RELEASES.md",
            "Releases.md",
            "releases.md",
            "NEWS.md",
            "News.md",
            "news.md"
        )

        for (variant in changelogVariants) {
            val urls = buildRawFileUrls(repositoryUrl, variant, packageName)
            val content = fetchFirstAvailable(urls)
            if (content != null) {
                return content
            }
        }

        return null
    }

    /**
     * Parses a repository URL to extract the base repo path and subdirectory.
     *
     * Examples:
     * - "https://github.com/user/repo" -> RepoInfo("github.com", "user/repo", "", "main")
     * - "https://github.com/flutter/packages/tree/main/third_party/packages/flutter_svg"
     *   -> RepoInfo("github.com", "flutter/packages", "third_party/packages/flutter_svg", "main")
     */
    private data class RepoInfo(
        val host: String,
        val repoPath: String,
        val subPath: String,
        val branch: String
    )

    private fun parseRepoUrl(repositoryUrl: String): RepoInfo? {
        try {
            val cleanUrl = repositoryUrl.removeSuffix("/").removeSuffix(".git")
            val uri = URI(cleanUrl)
            val host = uri.host?.lowercase() ?: return null
            val fullPath = uri.path.removePrefix("/").removeSuffix("/")
            val parts = fullPath.split("/")

            if (parts.size < 2) return null

            return when {
                host.contains("github.com") -> parseGitHubUrl(host, parts)
                host.contains("gitlab.com") || host.contains("gitlab") -> parseGitLabUrl(host, parts)
                host.contains("bitbucket.org") -> parseBitbucketUrl(host, parts)
                host.contains("codeberg.org") || host.contains("gitea") -> parseGiteaUrl(host, parts)
                else -> null
            }
        } catch (e: Exception) {
            log.debug("Failed to parse repository URL: $repositoryUrl", e)
            return null
        }
    }

    private fun parseGitHubUrl(host: String, parts: List<String>): RepoInfo {
        val repoPath = "${parts[0]}/${parts[1]}"

        // Check for /tree/branch/subdir or /blob/branch/subdir pattern
        val pathMarkers = listOf("tree", "blob")
        for (marker in pathMarkers) {
            val index = parts.indexOf(marker)
            if (index != -1 && index + 1 < parts.size) {
                val branch = parts[index + 1]
                val subPath = parts.drop(index + 2).joinToString("/")
                return RepoInfo(host, repoPath, subPath, branch)
            }
        }

        return RepoInfo(host, repoPath, "", "main")
    }

    private fun parseGitLabUrl(host: String, parts: List<String>): RepoInfo {
        // Find the repo path (everything before /-/)
        val dashIndex = parts.indexOf("-")
        val repoPath = if (dashIndex != -1) {
            parts.take(dashIndex).joinToString("/")
        } else {
            "${parts[0]}/${parts[1]}"
        }

        // Check for /-/tree/branch/subdir pattern
        val treeIndex = parts.indexOf("tree")
        if (treeIndex != -1 && treeIndex + 1 < parts.size) {
            val branch = parts[treeIndex + 1]
            val subPath = parts.drop(treeIndex + 2).joinToString("/")
            return RepoInfo(host, repoPath, subPath, branch)
        }

        return RepoInfo(host, repoPath, "", "main")
    }

    private fun parseBitbucketUrl(host: String, parts: List<String>): RepoInfo {
        val repoPath = "${parts[0]}/${parts[1]}"

        val srcIndex = parts.indexOf("src")
        if (srcIndex != -1 && srcIndex + 1 < parts.size) {
            val branch = parts[srcIndex + 1]
            val subPath = parts.drop(srcIndex + 2).joinToString("/")
            return RepoInfo(host, repoPath, subPath, branch)
        }

        return RepoInfo(host, repoPath, "", "main")
    }

    private fun parseGiteaUrl(host: String, parts: List<String>): RepoInfo {
        val repoPath = "${parts[0]}/${parts[1]}"

        val srcIndex = parts.indexOf("src")
        if (srcIndex != -1 && srcIndex + 1 < parts.size) {
            val branch = parts[srcIndex + 1]
            val subPath = parts.drop(srcIndex + 2).joinToString("/")
            return RepoInfo(host, repoPath, subPath, branch)
        }

        return RepoInfo(host, repoPath, "", "main")
    }

    /**
     * Builds raw file URLs for different repository hosts.
     * Handles monorepo structures with subdirectories.
     *
     * @param packageName If provided, tries common monorepo patterns like packages/{packageName}/
     */
    private fun buildRawFileUrls(repositoryUrl: String, filename: String, packageName: String? = null): List<String> {
        val repoInfo = parseRepoUrl(repositoryUrl) ?: return emptyList()
        val urls = mutableListOf<String>()

        // If subPath is explicitly provided in URL, use that
        if (repoInfo.subPath.isNotEmpty()) {
            val filePath = "${repoInfo.subPath}/$filename"
            urls.addAll(buildUrlsForHost(repoInfo, filePath))
        }
        // If packageName is provided, try common monorepo patterns
        else if (packageName != null) {
            val monorepoPatterns = listOf(
                "packages/$packageName",     // packages/provider
                "pkgs/$packageName",          // pkgs/provider
                packageName,                  // provider (root) - THIS WAS MISSING the / before filename
                "$packageName/$packageName",  // provider/provider (some repos use this)
                ""                            // Root of repository as fallback
            )

            for (pattern in monorepoPatterns) {
                val filePath = if (pattern.isEmpty()) filename else "$pattern/$filename"
                urls.addAll(buildUrlsForHost(repoInfo, filePath))
            }
        }
        // Default: root of repository
        else {
            urls.addAll(buildUrlsForHost(repoInfo, filename))
        }

        return urls
    }

    /**
     * Builds URLs for a specific host and file path
     */
    private fun buildUrlsForHost(repoInfo: RepoInfo, filePath: String): List<String> {
        return when {
            repoInfo.host.contains("github.com") -> buildGitHubRawUrls(repoInfo, filePath)
            repoInfo.host.contains("gitlab") -> buildGitLabRawUrls(repoInfo, filePath)
            repoInfo.host.contains("bitbucket.org") -> buildBitbucketRawUrls(repoInfo, filePath)
            repoInfo.host.contains("codeberg.org") || repoInfo.host.contains("gitea") -> buildGiteaRawUrls(repoInfo, filePath)
            repoInfo.host.contains("sr.ht") -> buildSourceHutRawUrls(repoInfo, filePath)
            else -> emptyList()
        }
    }

    private fun buildGitHubRawUrls(repoInfo: RepoInfo, filePath: String): List<String> {
        return buildList {
            // Try the detected branch first
            add("https://raw.githubusercontent.com/${repoInfo.repoPath}/refs/heads/${repoInfo.branch}/$filePath")

            // Then try common branch names if different from detected
            addAlternativeBranches(repoInfo.branch) { branch ->
                "https://raw.githubusercontent.com/${repoInfo.repoPath}/refs/heads/$branch/$filePath"
            }

            // Fallback to simpler URLs
            add("https://raw.githubusercontent.com/${repoInfo.repoPath}/${repoInfo.branch}/$filePath")
            add("https://raw.githubusercontent.com/${repoInfo.repoPath}/HEAD/$filePath")
        }
    }

    private fun buildGitLabRawUrls(repoInfo: RepoInfo, filePath: String): List<String> {
        return buildList {
            add("https://${repoInfo.host}/${repoInfo.repoPath}/-/raw/${repoInfo.branch}/$filePath")

            addAlternativeBranches(repoInfo.branch) { branch ->
                "https://${repoInfo.host}/${repoInfo.repoPath}/-/raw/$branch/$filePath"
            }

            add("https://${repoInfo.host}/${repoInfo.repoPath}/-/raw/HEAD/$filePath")
        }
    }

    private fun buildBitbucketRawUrls(repoInfo: RepoInfo, filePath: String): List<String> {
        return buildList {
            add("https://bitbucket.org/${repoInfo.repoPath}/raw/${repoInfo.branch}/$filePath")

            addAlternativeBranches(repoInfo.branch) { branch ->
                "https://bitbucket.org/${repoInfo.repoPath}/raw/$branch/$filePath"
            }

            add("https://bitbucket.org/${repoInfo.repoPath}/raw/HEAD/$filePath")
        }
    }

    private fun buildGiteaRawUrls(repoInfo: RepoInfo, filePath: String): List<String> {
        return buildList {
            add("https://${repoInfo.host}/${repoInfo.repoPath}/raw/branch/${repoInfo.branch}/$filePath")

            addAlternativeBranches(repoInfo.branch) { branch ->
                "https://${repoInfo.host}/${repoInfo.repoPath}/raw/branch/$branch/$filePath"
            }
        }
    }

    private fun buildSourceHutRawUrls(repoInfo: RepoInfo, filePath: String): List<String> {
        return buildList {
            add("https://git.${repoInfo.host}/${repoInfo.repoPath}/blob/${repoInfo.branch}/$filePath")

            addAlternativeBranches(repoInfo.branch) { branch ->
                "https://git.${repoInfo.host}/${repoInfo.repoPath}/blob/$branch/$filePath"
            }
        }
    }

    /**
     * Helper function to add alternative branch URLs (main/master) if not already the detected branch.
     */
    private fun MutableList<String>.addAlternativeBranches(currentBranch: String, urlBuilder: (String) -> String) {
        val commonBranches = listOf("main", "master")
        for (branch in commonBranches) {
            if (branch != currentBranch) {
                add(urlBuilder(branch))
            }
        }
    }

    /**
     * Tries to fetch from multiple URLs, returning the first successful response.
     */
    private fun fetchFirstAvailable(urls: List<String>): String? {
        for (url in urls) {
            try {
                val content = HttpRequests.request(url)
                    .connectTimeout(5000)
                    .readTimeout(10000)
                    .connect { request ->
                        // Cap the read so a malicious or oversized file cannot exhaust memory.
                        val bytes = request.inputStream.readNBytes(MAX_MARKDOWN_BYTES)
                        String(bytes, Charsets.UTF_8)
                    }

                if (content.isNotBlank()) {
                    log.debug("Successfully fetched from: $url")
                    return content
                }
            } catch (e: IOException) {
                // Try next URL
                log.debug("Failed to fetch from $url: ${e.message}")
            }
        }
        return null
    }

    /**
     * Resolves relative image URLs in markdown to absolute URLs based on repository.
     * Handles monorepo structures with subdirectories.
     */
    fun resolveImageUrls(markdown: String, repositoryUrl: String): String {
        if (markdown.isEmpty()) return markdown

        try {
            val repoInfo = parseRepoUrl(repositoryUrl) ?: return markdown

            val basePath = if (repoInfo.subPath.isNotEmpty()) {
                "${repoInfo.repoPath}/refs/heads/${repoInfo.branch}/${repoInfo.subPath}"
            } else {
                "${repoInfo.repoPath}/refs/heads/${repoInfo.branch}"
            }

            val baseUrl = when {
                repoInfo.host.contains("github.com") -> "https://raw.githubusercontent.com/$basePath/"
                repoInfo.host.contains("gitlab") -> "https://${repoInfo.host}/$basePath/-/raw/"
                repoInfo.host.contains("bitbucket.org") -> "https://bitbucket.org/$basePath/raw/"
                else -> repositoryUrl.removeSuffix("/") + "/"
            }

            return markdown
                .replaceMarkdownImages(baseUrl)
                .replaceHtmlImages(baseUrl)
        } catch (e: Exception) {
            log.debug("Failed to resolve image URLs", e)
            return markdown
        }
    }

    private fun String.replaceMarkdownImages(baseUrl: String): String {
        return replace(Regex("""!\[([^\]]*)\]\((?!http)([^)]+)\)""")) { matchResult ->
            val alt = matchResult.groupValues[1]
            val relativePath = matchResult.groupValues[2].removePrefix("./")
            "![$alt]($baseUrl$relativePath)"
        }
    }

    private fun String.replaceHtmlImages(baseUrl: String): String {
        return replace(Regex("""<img\s+([^>]*?)src=["'](?!http)([^"']+)["']""", RegexOption.IGNORE_CASE)) { matchResult ->
            val attrs = matchResult.groupValues[1]
            val relativePath = matchResult.groupValues[2].removePrefix("./")
            """<img ${attrs}src="$baseUrl$relativePath""""
        }
    }
}