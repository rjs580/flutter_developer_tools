
package dev.rutvik.flutter_developer_tools.dart.documentation

import com.intellij.lang.documentation.DocumentationProvider
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.psi.PsiElement
import com.jetbrains.lang.dart.highlight.DartSyntaxHighlighterColors
import com.jetbrains.lang.dart.highlight.DartSyntaxHighlighter
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.parser.MarkdownParser

/**
 * Enhanced documentation provider for Dart elements that properly highlights
 * code examples within documentation comments using the user's color scheme.
 *
 * This provider wraps the standard Dart documentation provider and enhances
 * code blocks with syntax highlighting that respects the user's IDE theme.
 */
class DartEnhancedDocumentationProvider : DocumentationProvider {

    private val markdownFlavour = GFMFlavourDescriptor()

    override fun generateDoc(element: PsiElement?, originalElement: PsiElement?): String? {
        element ?: return null

        // Get the standard Dart documentation
        val dartDocProvider = com.jetbrains.lang.dart.ide.documentation.DartDocumentationProvider()
        val originalDoc = dartDocProvider.generateDoc(element, originalElement) ?: return null

        println("=====================")
        println(originalDoc)
        println("=====================")

        // Enhance code blocks with syntax highlighting
        return enhanceDocumentationWithSyntaxHighlighting(originalDoc, element)
    }

    override fun getQuickNavigateInfo(element: PsiElement?, originalElement: PsiElement?): String? {
        val dartDocProvider = com.jetbrains.lang.dart.ide.documentation.DartDocumentationProvider()
        return dartDocProvider.getQuickNavigateInfo(element, originalElement)
    }

    /**
     * Enhances documentation by finding code blocks and applying Dart syntax highlighting.
     * Handles both markdown-style code blocks and HTML pre/code blocks.
     * Preserves all other HTML content unchanged.
     */
    private fun enhanceDocumentationWithSyntaxHighlighting(
        documentation: String,
        contextElement: PsiElement
    ): String {
        var enhanced = documentation

        // First, handle markdown code blocks (```dart or ```)
        // These might exist in raw dartdoc comments
        val markdownCodePattern = Regex(
            """```(?:dart)?\s*\n([\s\S]*?)```""",
            RegexOption.MULTILINE
        )

        enhanced = markdownCodePattern.replace(enhanced) { matchResult ->
            val code = matchResult.groups[1]?.value ?: return@replace matchResult.value
            val highlightedCode = highlightDartCodeWithColorScheme(code.trim(), contextElement)
            """<pre style="margin: 8px 0; padding: 8px; background-color: transparent; font-family: monospace;">$highlightedCode</pre>"""
        }

        // Handle HTML <pre><code> blocks that Dart documentation generates
        val preCodePattern = Regex(
            """<pre><code>([\s\S]*?)</code></pre>""",
            RegexOption.MULTILINE
        )

        enhanced = preCodePattern.replace(enhanced) { matchResult ->
            val encodedCode = matchResult.groups[1]?.value ?: return@replace matchResult.value

            // Decode HTML entities back to actual code
            val code = decodeHtmlEntities(encodedCode).trim()

            // Skip if it's empty
            if (code.isEmpty()) return@replace matchResult.value

            // Apply Dart syntax highlighting
            val highlightedCode = highlightDartCodeWithColorScheme(code, contextElement)

            // Return the enhanced code block with preserved formatting
            """<pre style="margin: 8px 0; padding: 8px; background-color: transparent; font-family: monospace; white-space: pre-wrap;">$highlightedCode</pre>"""
        }

        // Handle standalone <code> tags (inline code)
        val inlineCodePattern = Regex("""(?<!<pre>)<code>([^<]+)</code>(?!</pre>)""")
        enhanced = inlineCodePattern.replace(enhanced) { matchResult ->
            val encodedCode = matchResult.groups[1]?.value ?: return@replace matchResult.value
            val code = decodeHtmlEntities(encodedCode)

            // For inline code, only highlight if it looks like Dart code
            if (shouldHighlightInline(code)) {
                val highlighted = highlightDartCodeWithColorScheme(code, contextElement)
                """<code style="font-family: monospace;">$highlighted</code>"""
            } else {
                // Keep simple text as-is
                """<code style="font-family: monospace;">${escapeHtml(code)}</code>"""
            }
        }

        return enhanced
    }

    /**
     * Determines if inline code should be syntax highlighted.
     * Checks for Dart keywords, operators, or typical code patterns.
     */
    private fun shouldHighlightInline(code: String): Boolean {
        val dartPatterns = listOf(
            Regex("""\b(class|void|var|final|const|if|for|return|import|extends|implements|with|mixin|enum|abstract)\b"""),
            Regex("""[{}()\[\]<>]"""),  // Brackets
            Regex("""=>"""),  // Arrow function
            Regex("""\.\w+\(""")  // Method calls
        )
        return dartPatterns.any { it.containsMatchIn(code) }
    }

    /**
     * Highlights Dart code using the IDE's color scheme for proper theming.
     * Uses Dart-specific syntax highlighter with appropriate text attribute keys.
     */
    private fun highlightDartCodeWithColorScheme(code: String, contextElement: PsiElement): String {
        try {
            val colorsScheme = EditorColorsManager.getInstance().globalScheme
            val highlighter = DartSyntaxHighlighter()
            val htmlBuilder = StringBuilder()

            val lexer = highlighter.highlightingLexer
            lexer.start(code)

            while (lexer.tokenType != null) {
                val tokenText = lexer.tokenText
                val tokenType = lexer.tokenType

                // Get the highlighting attributes for this token type
                val keys = highlighter.getTokenHighlights(tokenType)

                if (keys.isNotEmpty()) {
                    // Use the first (most specific) attribute key
                    val textAttributes = colorsScheme.getAttributes(keys[0])

                    if (textAttributes != null && textAttributes.foregroundColor != null) {
                        htmlBuilder.append(formatWithColorScheme(tokenText, textAttributes))
                    } else {
                        // Fallback to default highlighting for this token type
                        htmlBuilder.append(formatWithDefaultHighlighting(tokenText, keys[0], colorsScheme))
                    }
                } else {
                    // No specific highlighting, use escaped plain text
                    htmlBuilder.append(escapeHtml(tokenText))
                }

                lexer.advance()
            }

            return htmlBuilder.toString()
        } catch (e: Exception) {
            // Fallback: return escaped code without highlighting
            return escapeHtml(code)
        }
    }

    /**
     * Formats text with HTML spans applying the color scheme's text attributes.
     * This ensures the code highlighting matches the user's IDE theme.
     */
    private fun formatWithColorScheme(text: String, attributes: TextAttributes): String {
        val styles = mutableListOf<String>()

        // Apply foreground color from the color scheme
        attributes.foregroundColor?.let { color ->
            val rgb = String.format("#%02x%02x%02x", color.red, color.green, color.blue)
            styles.add("color: $rgb")
        }

        // Apply background color if set (for specific highlights)
        attributes.backgroundColor?.let { color ->
            if (color.alpha > 0) {  // Only apply if not fully transparent
                val rgb = String.format("#%02x%02x%02x", color.red, color.green, color.blue)
                styles.add("background-color: $rgb")
            }
        }

        // Apply font styling (bold, italic)
        if (attributes.fontType and java.awt.Font.BOLD != 0) {
            styles.add("font-weight: bold")
        }
        if (attributes.fontType and java.awt.Font.ITALIC != 0) {
            styles.add("font-style: italic")
        }

        val escapedText = escapeHtml(text)

        return if (styles.isNotEmpty()) {
            """<span style="${styles.joinToString("; ")}">$escapedText</span>"""
        } else {
            escapedText
        }
    }

    /**
     * Applies default highlighting based on text attribute key type.
     * Used as fallback when specific colors aren't defined.
     */
    private fun formatWithDefaultHighlighting(
        text: String,
        key: TextAttributesKey,
        colorsScheme: com.intellij.openapi.editor.colors.EditorColorsScheme
    ): String {
        // Try to get default language highlighter colors
        val defaultKey = when {
            key == DartSyntaxHighlighterColors.KEYWORD -> DefaultLanguageHighlighterColors.KEYWORD
            key == DartSyntaxHighlighterColors.NUMBER -> DefaultLanguageHighlighterColors.NUMBER
            key == DartSyntaxHighlighterColors.STRING -> DefaultLanguageHighlighterColors.STRING
            key == DartSyntaxHighlighterColors.LINE_COMMENT ||
                    key == DartSyntaxHighlighterColors.BLOCK_COMMENT ||
                    key == DartSyntaxHighlighterColors.DOC_COMMENT -> DefaultLanguageHighlighterColors.LINE_COMMENT
            key == DartSyntaxHighlighterColors.OPERATION_SIGN -> DefaultLanguageHighlighterColors.OPERATION_SIGN
            key == DartSyntaxHighlighterColors.PARENTHS ||
                    key == DartSyntaxHighlighterColors.BRACKETS ||
                    key == DartSyntaxHighlighterColors.BRACES -> DefaultLanguageHighlighterColors.BRACES
            key == DartSyntaxHighlighterColors.CLASS -> DefaultLanguageHighlighterColors.CLASS_NAME
            else -> null
        }

        val attributes = defaultKey?.let { colorsScheme.getAttributes(it) }

        return if (attributes != null) {
            formatWithColorScheme(text, attributes)
        } else {
            escapeHtml(text)
        }
    }

    /**
     * Decodes HTML entities to get the actual code text.
     * This is necessary because the Dart documentation provider HTML-encodes code blocks.
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

    /**
     * Converts markdown documentation to HTML using the GFM flavour.
     * This method processes markdown and applies custom code block highlighting.
     */
    private fun convertMarkdownToHtml(markdown: String, contextElement: PsiElement): String {
        return try {
            // Parse markdown to AST
            val parsedTree = MarkdownParser(markdownFlavour).buildMarkdownTreeFromString(markdown)

            // Generate HTML from markdown
            var html = HtmlGenerator(markdown, parsedTree, markdownFlavour).generateHtml()

            // Post-process to add syntax highlighting to code blocks
            html = enhanceDocumentationWithSyntaxHighlighting(html, contextElement)

            html
        } catch (e: Exception) {
            "<pre>${escapeHtml(markdown)}</pre>"
        }
    }
}