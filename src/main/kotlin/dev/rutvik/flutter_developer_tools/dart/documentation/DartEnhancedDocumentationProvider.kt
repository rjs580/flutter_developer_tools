
package dev.rutvik.flutter_developer_tools.dart.documentation

import com.intellij.lang.Language
import com.intellij.lang.documentation.DocumentationProvider
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.richcopy.HtmlSyntaxInfoUtil
import com.intellij.psi.PsiElement
import com.intellij.ui.ColorUtil
import com.jetbrains.lang.dart.DartLanguage
import org.jetbrains.yaml.YAMLLanguage

/**
 * Enhanced documentation provider for Dart elements that properly highlights
 * code examples within documentation comments using the user's color scheme.
 *
 * This provider wraps the standard Dart documentation provider and enhances
 * code blocks with syntax highlighting that respects the user's IDE theme.
 */
class DartEnhancedDocumentationProvider : DocumentationProvider {

    override fun generateDoc(element: PsiElement?, originalElement: PsiElement?): String? {
        element ?: return null

        // Get the standard Dart documentation
        val dartDocProvider = com.jetbrains.lang.dart.ide.documentation.DartDocumentationProvider()
        val originalDoc = dartDocProvider.generateDoc(element, originalElement) ?: return null

        // Enhance code blocks with syntax highlighting
        return enhanceDocumentationWithSyntaxHighlighting(originalDoc, element)
    }

    override fun getQuickNavigateInfo(element: PsiElement?, originalElement: PsiElement?): String? {
        val dartDocProvider = com.jetbrains.lang.dart.ide.documentation.DartDocumentationProvider()
        return dartDocProvider.getQuickNavigateInfo(element, originalElement)
    }

    /**
     * Enhances documentation by finding code blocks and applying syntax highlighting.
     * Uses IntelliJ's HtmlSyntaxInfoUtil for consistent highlighting across the IDE.
     */
    private fun enhanceDocumentationWithSyntaxHighlighting(
        documentation: String,
        contextElement: PsiElement
    ): String {
        var enhanced = documentation

        // Get background color for code blocks
        val backgroundColor = getCodeBackgroundColor()

        // Handle markdown code blocks with language specification (```dart, ```yaml, etc.)
        val markdownCodeWithLangPattern = Regex(
            """```(\w+)?\s*\n([\s\S]*?)```""",
            RegexOption.MULTILINE
        )

        enhanced = markdownCodeWithLangPattern.replace(enhanced) { matchResult ->
            val language = matchResult.groups[1]?.value?.lowercase() ?: "dart"
            val code = matchResult.groups[2]?.value ?: return@replace matchResult.value
            val highlightedCode = highlightCode(code.trim(), language, contextElement)
            """<pre style="margin: 8px 0; padding: 8px; background-color: $backgroundColor; font-family: monospace;">$highlightedCode</pre>"""
        }

        // Handle HTML <pre><code class="language-*"> blocks (with language detection)
        val preCodeWithClassPattern = Regex(
            """<pre><code(?:\s+class="language-(\w+)")?\s*>([\s\S]*?)</code></pre>""",
            RegexOption.MULTILINE
        )

        enhanced = preCodeWithClassPattern.replace(enhanced) { matchResult ->
            val language = matchResult.groups[1]?.value?.lowercase() ?: "dart"
            val encodedCode = matchResult.groups[2]?.value ?: return@replace matchResult.value
            val code = decodeHtmlEntities(encodedCode).trim()

            if (code.isEmpty()) return@replace matchResult.value

            val highlightedCode = highlightCode(code, language, contextElement)
            """<pre style="margin: 8px 0; padding: 8px; background-color: $backgroundColor; font-family: monospace; white-space: pre-wrap;">$highlightedCode</pre>"""
        }

        // Handle complex <code> blocks with embedded HTML (like method signatures with <b>, <br/>, etc.)
        // This pattern matches <code>...</code> including any nested HTML tags
        val complexCodePattern = Regex("""<code>([\s\S]*?)</code>""", RegexOption.MULTILINE)

        enhanced = complexCodePattern.replace(enhanced) { matchResult ->
            val rawContent = matchResult.groups[1]?.value ?: return@replace matchResult.value

            // Check if this is a complex multi-line code block (contains <br or <b tags)
            if (rawContent.contains("<br") || rawContent.contains("<b>")) {
                // Process complex code blocks - extract and highlight the actual code parts
                processComplexCodeBlock(rawContent, backgroundColor, contextElement)
            } else {
                // Simple inline code - highlight normally
                val code = decodeHtmlEntities(rawContent)
                val highlighted = highlightCode(code, "dart", contextElement)
                """<code style="font-family: monospace; background-color: $backgroundColor; padding: 2px 4px;">$highlighted</code>"""
            }
        }

        return enhanced
    }

    /**
     * Processes complex code blocks that contain HTML formatting like <b>, <br/>, etc.
     * These are typically method signatures or class declarations.
     */
    private fun processComplexCodeBlock(htmlContent: String, backgroundColor: String, contextElement: PsiElement): String {
        // Remove trailing <br> tags before processing
        val cleanedContent = htmlContent.replace(Regex("""(<br/?>\s*)+$"""), "")

        // Extract the raw text content, preserving line breaks
        val textWithBreaks = cleanedContent
            .replace(Regex("""<br/?>\s*<br/?>"""), "\n\n")  // Convert double <br><br> to double newlines
            .replace(Regex("""<br/?>"""), "\n")  // Convert single <br> tags to single newlines
            .replace(Regex("""<[^>]+>"""), "")   // Remove all other HTML tags

        val decodedText = decodeHtmlEntities(textWithBreaks)

        // Trim trailing whitespace and newlines
        var trimmedText = decodedText.trimEnd()

        // Add separation after Dart import/package lines
        // If first line ends with .dart and there's not already double newline separation, add it
        val lines = trimmedText.lines()
        if (lines.size > 1 && lines[0].trim().endsWith(".dart")) {
            // Check if there's already double newline after first line
            val afterFirstLine = trimmedText.substringAfter(lines[0])
            if (!afterFirstLine.startsWith("\n\n")) {
                // Replace the first single newline with double newline
                trimmedText = lines[0] + "\n\n" + lines.drop(1).joinToString("\n")
            }
        }

        // Highlight the entire code block
        val highlighted = highlightCode(trimmedText, "dart", contextElement)

        // Wrap in pre tag to preserve newlines and give proper code block styling
        return """<pre style="margin: 0; padding: 12px 8px; background-color: $backgroundColor; font-family: monospace; white-space: pre-wrap;">$highlighted</pre>"""
    }

    /**
     * Gets the background color for code blocks from the current color scheme.
     */
    private fun getCodeBackgroundColor(): String {
        return try {
            val scheme = EditorColorsManager.getInstance().globalScheme
            val backgroundColor = scheme.defaultBackground
            // Make it slightly different from the default background
            val adjustedColor = ColorUtil.darker(backgroundColor, 1)
            ColorUtil.toHtmlColor(adjustedColor)
        } catch (_: Exception) {
            "#f5f5f5" // Fallback light gray
        }
    }

    /**
     * Highlights code using IntelliJ's HtmlSyntaxInfoUtil with language detection.
     * Supports multiple languages like Dart, YAML, etc.
     */
    private fun highlightCode(code: String, languageId: String, contextElement: PsiElement): String {
        return try {
            val project = contextElement.project
            val language = detectLanguage(languageId)
            val buffer = StringBuilder()

            // Use ReadAction to safely access PSI and perform highlighting
            ReadAction.compute<String, Exception> {
                HtmlSyntaxInfoUtil.appendHighlightedByLexerAndEncodedAsHtmlCodeSnippet(
                    buffer,
                    project,
                    language,
                    code,
                    true,  // doTrimIndent
                    1.0f   // saturationFactor - 1.0f means use colors as-is from the theme
                )
                buffer.toString()
            }
        } catch (_: Exception) {
            // Fallback: return escaped code without highlighting
            escapeHtml(code)
        }
    }

    /**
     * Detects the Language instance based on the language identifier string.
     * Supports common languages found in Dart/Flutter documentation.
     */
    private fun detectLanguage(languageId: String): Language {
        return when (languageId.lowercase()) {
            "dart" -> DartLanguage.INSTANCE
            "yaml", "yml" -> YAMLLanguage.INSTANCE
            "json" -> Language.findLanguageByID("JSON") ?: DartLanguage.INSTANCE
            "xml", "html" -> Language.findLanguageByID("XML") ?: DartLanguage.INSTANCE
            "kotlin", "kt" -> Language.findLanguageByID("kotlin") ?: DartLanguage.INSTANCE
            "java" -> Language.findLanguageByID("JAVA") ?: DartLanguage.INSTANCE
            "javascript", "js" -> Language.findLanguageByID("JavaScript") ?: DartLanguage.INSTANCE
            "typescript", "ts" -> Language.findLanguageByID("TypeScript") ?: DartLanguage.INSTANCE
            "swift" -> Language.findLanguageByID("Swift") ?: DartLanguage.INSTANCE
            "objectivec", "objc" -> Language.findLanguageByID("ObjectiveC") ?: DartLanguage.INSTANCE
            else -> DartLanguage.INSTANCE // Default to Dart
        }
    }

    /**
     * Decodes HTML entities to get the actual code text.
     */
    private fun decodeHtmlEntities(html: String): String {
        return html
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&#x27;", "'")
            .replace("&nbsp;", " ")
            .replace("&#160;", " ")
    }

    /**
     * Escapes special HTML characters to prevent rendering issues.
     */
    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
}