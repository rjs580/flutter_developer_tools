package dev.rutvik.flutter_developer_tools.utils

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLMapping

/**
 * Utility functions for working with pubspec.yaml files and pub.dev packages.
 */
object PubspecUtils {

    val DEPENDENCY_SECTIONS = setOf(
        "dependencies",
        "dev_dependencies",
        "dependency_overrides"
    )

    val EXCLUDED_KEYS = setOf(
        "sdk",
        "flutter",
        "ref",
        "path",
        "url",
        "hosted",
        "git",
        "version"
    )

    val NON_PUB_DEV_KEYS = setOf(
        "path",
        "git",
        "sdk",
        "hosted",
        "url",
        "ref"
    )

    private val PACKAGE_NAME_REGEX = Regex("^[a-z][a-z0-9_]*$")
    private const val MAX_DEPTH = 10

    /**
     * Checks if a file is a pubspec.yaml file.
     */
    fun isPubspecFile(file: PsiFile?): Boolean {
        return file?.name == "pubspec.yaml"
    }

    /**
     * Checks if a YAMLKeyValue is within a dependency section.
     */
    fun isInDependencySection(yamlKv: YAMLKeyValue): Boolean {
        val parent = yamlKv.parent as? YAMLMapping ?: return false
        val grandParent = parent.parent as? YAMLKeyValue ?: return false
        return grandParent.keyText in DEPENDENCY_SECTIONS
    }

    /**
     * Checks if a PSI element is within a dependency section by traversing up the tree.
     * Uses a depth limit to prevent excessive traversal.
     */
    fun isElementInDependencySection(element: PsiElement): Boolean {
        var current: PsiElement? = element
        var depth = 0

        while (current != null && depth < MAX_DEPTH) {
            when (current) {
                is YAMLKeyValue -> {
                    if (current.keyText in DEPENDENCY_SECTIONS) {
                        return true
                    }
                }
                is YAMLMapping -> {
                    val mappingParent = current.parent
                    if (mappingParent is YAMLKeyValue && mappingParent.keyText in DEPENDENCY_SECTIONS) {
                        return true
                    }
                }
            }

            current = current.parent
            depth++
        }

        return false
    }

    /**
     * Checks if a YAMLKeyValue represents a pub.dev package (not a local path, git, etc.).
     */
    fun isPubDevPackage(yamlKv: YAMLKeyValue): Boolean {
        val value = yamlKv.value ?: return false

        if (value is YAMLMapping) {
            val keys = value.keyValues.map { it.keyText }.toSet()
            if (keys.any { it in NON_PUB_DEV_KEYS }) {
                return false
            }
        }

        return true
    }

    /**
     * Checks if a string is a valid pub package name.
     */
    fun String.isPubPackageName(): Boolean {
        return this !in EXCLUDED_KEYS && matches(PACKAGE_NAME_REGEX)
    }

    /**
     * Checks if a version string is a simple version (not "any", not a complex constraint).
     */
    fun isSimpleVersion(versionText: String): Boolean {
        val trimmed = versionText.trim()

        if (trimmed.isEmpty() || trimmed.startsWith('{') || trimmed.startsWith('[')) {
            return false
        }

        if (trimmed.equals("any", ignoreCase = true)) {
            return false
        }

        if (trimmed.contains(' ')) {
            return false
        }

        return true
    }

    /**
     * Normalizes a version string by removing quotes, whitespace, and version constraint prefixes.
     * Examples:
     * - "^1.2.3" -> "1.2.3"
     * - ">=1.0.0" -> "1.0.0"
     * - "'1.2.3'" -> "1.2.3"
     */
    fun normalizeVersionString(versionText: String): String {
        var normalized = versionText.trim().trim('"', '\'')

        // Remove common version constraint prefixes
        normalized = normalized.removePrefix("^")
            .removePrefix(">=")
            .removePrefix("<=")
            .removePrefix(">")
            .removePrefix("<")
            .removePrefix("~")

        return normalized.trim()
    }
}