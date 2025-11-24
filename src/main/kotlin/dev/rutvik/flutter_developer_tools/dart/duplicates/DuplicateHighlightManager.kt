
package dev.rutvik.flutter_developer_tools.dart.duplicates

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import dev.rutvik.flutter_developer_tools.dart.duplicates.DartDuplicatesFinder.DuplicateInfo
import java.awt.Font

/**
 * Manages persistent highlighting of duplicate code fragments.
 * Highlights are added lazily as user navigates through duplicates.
 * All highlights persist until user clicks elsewhere in any editor.
 */
object DuplicateHighlightManager {
    private val highlightsByEditor = mutableMapOf<Editor, MutableList<HighlightInfo>>()
    private val mouseListenersByEditor = mutableMapOf<Editor, EditorMouseListener>()
    private val highlightedDuplicates = mutableSetOf<DuplicateInfo>()

    data class HighlightInfo(
        var highlighter: RangeHighlighter,
        val duplicate: DuplicateInfo,
        var isCurrent: Boolean,
        val editor: Editor
    )

    /**
     * Highlights a duplicate as the current one.
     * If this duplicate hasn't been highlighted before, adds it to the persistent collection.
     * Updates the previous current highlight to be a regular highlight.
     */
    fun highlightDuplicate(editor: Editor, duplicate: DuplicateInfo) {
        // Update previous current to regular style
        highlightsByEditor.values.flatten().forEach { info ->
            if (info.isCurrent) {
                info.isCurrent = false
                recreateHighlighter(info, isCurrentDuplicate = false)
            }
        }

        // Check if this duplicate is already highlighted
        // Compare by file, line number, and text range (not by object reference)
        val existingInfo = highlightsByEditor.values.flatten()
            .find {
                it.duplicate.file == duplicate.file &&
                        it.duplicate.lineNumber == duplicate.lineNumber &&
                        it.duplicate.element.textRange == duplicate.element.textRange
            }

        if (existingInfo != null) {
            // Already highlighted - just update to current style
            existingInfo.isCurrent = true
            recreateHighlighter(existingInfo, isCurrentDuplicate = true)
        } else {
            // New highlight - add it
            addHighlight(editor, duplicate, isCurrentDuplicate = true)
            highlightedDuplicates.add(duplicate)
        }

        // Ensure mouse listener is attached
        ensureMouseListener(editor)
    }

    private fun addHighlight(editor: Editor, duplicate: DuplicateInfo, isCurrentDuplicate: Boolean) {
        val startOffset = duplicate.element.textRange.startOffset
        val endOffset = duplicate.element.textRange.endOffset

        // Create highlight attributes
        val attributes = createHighlightAttributes(isCurrentDuplicate)

        // Add highlighter
        val markupModel = editor.markupModel
        val highlighter = markupModel.addRangeHighlighter(
            startOffset,
            endOffset,
            HighlighterLayer.SELECTION + 1, // Above selection
            attributes,
            HighlighterTargetArea.EXACT_RANGE
        )

        // Store highlighter
        val info = HighlightInfo(highlighter, duplicate, isCurrentDuplicate, editor)
        highlightsByEditor.getOrPut(editor) { mutableListOf() }.add(info)
    }

    private fun recreateHighlighter(info: HighlightInfo, isCurrentDuplicate: Boolean) {
        // Remove old highlighter
        info.highlighter.dispose()

        // Create new highlighter with updated style
        val startOffset = info.duplicate.element.textRange.startOffset
        val endOffset = info.duplicate.element.textRange.endOffset
        val attributes = createHighlightAttributes(isCurrentDuplicate)

        val markupModel = info.editor.markupModel
        val newHighlighter = markupModel.addRangeHighlighter(
            startOffset,
            endOffset,
            HighlighterLayer.SELECTION + 1,
            attributes,
            HighlighterTargetArea.EXACT_RANGE
        )

        // Update the info with new highlighter
        info.highlighter = newHighlighter
    }

    private fun createHighlightAttributes(isCurrentDuplicate: Boolean): TextAttributes {
        // Get colors from IntelliJ's color scheme
        val scheme = EditorColorsManager.getInstance().globalScheme

        return TextAttributes().apply {
            if (isCurrentDuplicate) {
                // Current duplicate - use brighter/bolder highlight
                // Use BLINKING_HIGHLIGHTS for current selection (bright green)
                backgroundColor = scheme.getAttributes(
                    com.intellij.openapi.editor.colors.CodeInsightColors.BLINKING_HIGHLIGHTS_ATTRIBUTES
                )?.backgroundColor ?: com.intellij.ui.JBColor(0xD4F5D4, 0x2D5F2D) // Light green / Dark green
                fontType = Font.BOLD
            } else {
                // Previous duplicates - use DUPLICATE_FROM_SERVER (standard duplicate color)
                backgroundColor = scheme.getAttributes(
                    com.intellij.openapi.editor.colors.CodeInsightColors.DUPLICATE_FROM_SERVER
                )?.backgroundColor ?: com.intellij.ui.JBColor(0xE8F5E8, 0x1F3F1F) // Very light green / Darker green
            }
        }
    }
    private fun ensureMouseListener(editor: Editor) {
        // Add mouse listener to clear on click (once per editor)
        if (editor !in mouseListenersByEditor) {
            val mouseListener = object : EditorMouseListener {
                override fun mouseClicked(event: EditorMouseEvent) {
                    // Clear all highlights when clicking in any editor
                    clearAll()
                }
            }
            editor.addEditorMouseListener(mouseListener)
            mouseListenersByEditor[editor] = mouseListener
        }
    }

    /**
     * Clears highlights for a specific editor.
     */
    fun clearEditor(editor: Editor) {
        highlightsByEditor[editor]?.forEach { it.highlighter.dispose() }
        highlightsByEditor.remove(editor)

        mouseListenersByEditor[editor]?.let { listener ->
            editor.removeEditorMouseListener(listener)
        }
        mouseListenersByEditor.remove(editor)
    }

    /**
     * Clears all highlights from all editors.
     */
    fun clearAll() {
        highlightsByEditor.forEach { (editor, infos) ->
            infos.forEach { it.highlighter.dispose() }
            mouseListenersByEditor[editor]?.let { listener ->
                editor.removeEditorMouseListener(listener)
            }
        }
        highlightsByEditor.clear()
        mouseListenersByEditor.clear()
        highlightedDuplicates.clear()
    }

    /**
     * Returns the set of duplicates that have been highlighted so far.
     */
    fun getHighlightedDuplicates(): Set<DuplicateInfo> = highlightedDuplicates.toSet()
}