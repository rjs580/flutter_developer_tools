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
 *
 * Note: This action performs navigation and cannot provide a meaningful preview.
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
        // Find next duplicate (circular navigation)
        val currentIndex = allDuplicates.indexOf(currentDuplicate)
        val nextIndex = (currentIndex + 1) % allDuplicates.size
        val next = allDuplicates[nextIndex]

        val fileEditorManager = FileEditorManager.getInstance(project)
        val currentFile = descriptor.psiElement.containingFile

        // Navigate to the duplicate
        if (next.file == currentFile) {
            // Same file - just move cursor in current editor
            val currentEditor = fileEditorManager.selectedTextEditor
            currentEditor?.let { editor ->
                val offset = next.element.textRange.startOffset
                editor.caretModel.moveToOffset(offset)
                editor.scrollingModel.scrollToCaret(ScrollType.CENTER)
                editor.selectionModel.setSelection(
                    next.element.textRange.startOffset,
                    next.element.textRange.endOffset
                )
            }
        } else {
            // Different file - open file and navigate
            val virtualFile = next.file.virtualFile ?: return

            fileEditorManager.openFile(virtualFile, true).firstOrNull()?.let { fileEditor ->
                val newEditor = (fileEditor as? TextEditor)?.editor
                newEditor?.let { editor ->
                    val offset = next.element.textRange.startOffset
                    editor.caretModel.moveToOffset(offset)
                    editor.scrollingModel.scrollToCaret(ScrollType.CENTER)
                    editor.selectionModel.setSelection(
                        next.element.textRange.startOffset,
                        next.element.textRange.endOffset
                    )
                }
            }
        }
    }

    // Explicitly disable preview for navigation actions
    override fun generatePreview(project: Project, previewDescriptor: ProblemDescriptor): IntentionPreviewInfo {
        return IntentionPreviewInfo.EMPTY
    }

    override fun availableInBatchMode(): Boolean = false
}