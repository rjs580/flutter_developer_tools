
package dev.rutvik.flutter_developer_tools.dart.documentation

import com.intellij.lang.documentation.DocumentationProvider
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.editor.richcopy.HtmlSyntaxInfoUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileFactory
import com.jetbrains.lang.dart.DartFileType
import com.jetbrains.lang.dart.highlight.DartSyntaxHighlighter
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.parser.MarkdownParser

/**
 * Enhanced documentation provider for Dart elements that properly highlights
 * code examples within documentation comments using the user's color scheme.
 */
class DartEnhancedDocumentationProvider : DocumentationProvider {

    private val markdownFlavour = GFMFlavourDescriptor()

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

    private fun enhanceDocumentationWithSyntaxHighlighting(
        documentation: String,
        contextElement: PsiElement
    ): String {
        var enhanced = documentation

        // Pattern to find code blocks in the documentation
        // Matches: ```dart ... ``` or ``` ... ``` or <code>...</code>
        val codeBlockPattern = Regex(
            """```(?:dart)?[\s\n]+([\s\S]*?)```""",
            RegexOption.MULTILINE
        )

        enhanced = codeBlockPattern.replace(enhanced) { matchResult ->
            val code = matchResult.groups[1]?.value ?: return@replace matchResult.value

            // Apply Dart syntax highlighting to the code
            val highlightedCode = highlightDartCode(code, contextElement)

            // Wrap in pre tag with proper styling
            "<pre style=\"background-color: transparent; padding: 8px; margin: 8px 0;\">$highlightedCode</pre>"
        }

        // Also handle inline code blocks with basic styling
        val inlineCodePattern = Regex("""<code>([\s\S]*?)</code>""")
        enhanced = inlineCodePattern.replace(enhanced) { matchResult ->
            val code = matchResult.groups[1]?.value ?: return@replace matchResult.value

            // For short inline code, just use monospace without full highlighting
            if (code.length < 100 && !code.contains('\n')) {
                "<code style=\"font-family: monospace;\">${escapeHtml(code)}</code>"
            } else {
                // For longer inline code blocks, apply highlighting
                val highlightedCode = highlightDartCode(code, contextElement)
                "<code style=\"font-family: monospace;\">$highlightedCode</code>"
            }
        }

        return enhanced
    }

    private fun highlightDartCode(code: String, contextElement: PsiElement): String {
        try {
            val project = contextElement.project
            val psiFileFactory = PsiFileFactory.getInstance(project)

            // Create a temporary Dart file with the code
            val tempFile = psiFileFactory.createFileFromText(
                "temp.dart",
                DartFileType.INSTANCE,
                code
            )

            // Get the user's current color scheme
            val colorsScheme = EditorColorsManager.getInstance().globalScheme
            val htmlBuilder = StringBuilder()

            // Generate syntax-highlighted HTML using the Dart lexer and user's color scheme
            // This will apply the user's theme colors to keywords, strings, comments, etc.
            HtmlSyntaxInfoUtil.appendHighlightedByLexerAndEncodedAsHtmlCodeSnippet(
                htmlBuilder,
                project,
                tempFile.language,
                code,
                1.0f  // fontSize parameter
            )

            // The HtmlSyntaxInfoUtil uses the color scheme internally to generate colored HTML
            return htmlBuilder.toString()
        } catch (e: Exception) {
            // Fallback to manually highlighted code if automatic highlighting fails
            return highlightDartCodeManually(code, contextElement)
        }
    }

    /**
     * Manual fallback highlighting using Dart syntax highlighter with color scheme.
     * This ensures we still get colored output even if the automatic method fails.
     */
    private fun highlightDartCodeManually(code: String, contextElement: PsiElement): String {
        try {
            val colorsScheme = EditorColorsManager.getInstance().globalScheme
            val highlighter = DartSyntaxHighlighter()
            val htmlBuilder = StringBuilder()

            val lexer = highlighter.highlightingLexer
            lexer.start(code)

            while (lexer.tokenType != null) {
                val tokenText = lexer.tokenText
                val tokenType = lexer.tokenType

                // Get the text attributes for this token type from the color scheme
                val keys = highlighter.getTokenHighlights(tokenType)
                val textAttributes = if (keys.isNotEmpty()) {
                    colorsScheme.getAttributes(keys[0])
                } else {
                    null
                }

                // Apply HTML styling based on the color scheme attributes
                if (textAttributes != null) {
                    htmlBuilder.append(applyTextAttributes(escapeHtml(tokenText), textAttributes))
                } else {
                    htmlBuilder.append(escapeHtml(tokenText))
                }

                lexer.advance()
            }

            return htmlBuilder.toString()
        } catch (e: Exception) {
            // Final fallback - just escape HTML
            return escapeHtml(code)
        }
    }

    /**
     * Applies text attributes from the color scheme to HTML.
     * This respects the user's theme colors for syntax highlighting.
     */
    private fun applyTextAttributes(text: String, attributes: TextAttributes): String {
        val styles = mutableListOf<String>()

        // Apply foreground color
        attributes.foregroundColor?.let { color ->
            val rgb = String.format("#%02x%02x%02x", color.red, color.green, color.blue)
            styles.add("color: $rgb")
        }

        // Apply background color if set
        attributes.backgroundColor?.let { color ->
            val rgb = String.format("#%02x%02x%02x", color.red, color.green, color.blue)
            styles.add("background-color: $rgb")
        }

        // Apply font style (bold, italic)
        if (attributes.fontType and java.awt.Font.BOLD != 0) {
            styles.add("font-weight: bold")
        }
        if (attributes.fontType and java.awt.Font.ITALIC != 0) {
            styles.add("font-style: italic")
        }

        return if (styles.isNotEmpty()) {
            "<span style=\"${styles.joinToString("; ")}\">$text</span>"
        } else {
            text
        }
    }

    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
}