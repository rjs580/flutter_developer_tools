package dev.rutvik.flutter_developer_tools.dart.duplicates

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import dev.rutvik.flutter_developer_tools.dart.duplicates.DartDuplicatesFinder.DuplicateInfo

/**
 * Quick fix that navigates to the next duplicate code fragment.
 * Cycles through all duplicates in order of appearance.
 * Highlights are added lazily as you navigate - each visited duplicate remains highlighted.
 */
class NavigateToNextDuplicateQuickFix(
    private val allDuplicates: List<DuplicateInfo>,
    private val currentDuplicate: DuplicateInfo
) : LocalQuickFix {

    @IntentionName
    override fun getName(): String {
        val currentIndex = allDuplicates.indexOf(currentDuplicate)
        val nextIndex = (currentIndex + 1) % allDuplicates.size
        val next = allDuplicates[nextIndex]

        return if (next.file == currentDuplicate.file) {
            "Navigate to next duplicate (line ${next.lineNumber})"
        } else {
            "Navigate to next duplicate (${next.file.name}:${next.lineNumber})"
        }
    }

    @IntentionFamilyName
    override fun getFamilyName(): String = "Navigate to duplicate code"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val fileEditorManager = FileEditorManager.getInstance(project)

        // First, highlight the original/current duplicate
        val currentEditor = fileEditorManager.selectedTextEditor
        if (currentEditor != null) {
            DuplicateHighlightManager.highlightDuplicate(currentEditor, currentDuplicate)
        }

        // Find next duplicate (circular navigation)
        val currentIndex = allDuplicates.indexOf(currentDuplicate)
        val nextIndex = (currentIndex + 1) % allDuplicates.size
        val next = allDuplicates[nextIndex]

        val currentFile = descriptor.psiElement.containingFile

        // Navigate to the next duplicate
        if (next.file == currentFile) {
            // Same file - just move cursor
            currentEditor?.let { editor ->
                navigateAndHighlight(editor, next)
            }
        } else {
            // Different file - open file and navigate
            val virtualFile = next.file.virtualFile ?: return

            fileEditorManager.openFile(virtualFile, true).firstOrNull()?.let { fileEditor ->
                val newEditor = (fileEditor as? TextEditor)?.editor
                newEditor?.let { editor ->
                    navigateAndHighlight(editor, next)
                }
            }
        }
    }

    private fun navigateAndHighlight(editor: com.intellij.openapi.editor.Editor, duplicate: DuplicateInfo) {
        val startOffset = duplicate.element.textRange.startOffset

        // Move cursor and scroll
        editor.caretModel.moveToOffset(startOffset)
        editor.scrollingModel.scrollToCaret(ScrollType.CENTER)

        // Lazily add highlight - this will:
        // 1. Keep previous highlights visible
        // 2. Update previous current to regular style
        // 3. Make this duplicate the new current (bold/bright)
        DuplicateHighlightManager.highlightDuplicate(editor, duplicate)
    }

    override fun generatePreview(project: Project, previewDescriptor: ProblemDescriptor): IntentionPreviewInfo {
        return IntentionPreviewInfo.EMPTY
    }

    override fun availableInBatchMode(): Boolean = false
}