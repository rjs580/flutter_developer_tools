package dev.rutvik.flutter_developer_tools.dart.duplicates

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import dev.rutvik.flutter_developer_tools.dart.duplicates.DartDuplicatesFinder.DuplicateInfo

/**
 * Intention action for navigating to the next duplicate code fragment.
 * Used by the annotator (not inspection).
 */
class NavigateToNextDuplicateIntentionAction(
    private val allDuplicates: List<DuplicateInfo>,
    private val currentDuplicate: DuplicateInfo
) : IntentionAction {

    override fun getText(): String {
        val currentIndex = allDuplicates.indexOf(currentDuplicate)
        val nextIndex = (currentIndex + 1) % allDuplicates.size
        val next = allDuplicates[nextIndex]

        return if (next.file == currentDuplicate.file) {
            "Navigate to next duplicate (line ${next.lineNumber})"
        } else {
            "Navigate to next duplicate (${next.file.name}:${next.lineNumber})"
        }
    }

    override fun getFamilyName(): String = "Navigate to duplicate code"

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        return allDuplicates.size > 1
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        editor ?: return

        // Find next duplicate (circular navigation)
        val currentIndex = allDuplicates.indexOf(currentDuplicate)
        val nextIndex = (currentIndex + 1) % allDuplicates.size
        val next = allDuplicates[nextIndex]

        // Navigate to the duplicate
        if (next.file == file) {
            // Same file - just move cursor
            val offset = next.element.textRange.startOffset
            editor.caretModel.moveToOffset(offset)
            editor.scrollingModel.scrollToCaret(ScrollType.CENTER)
            editor.selectionModel.setSelection(
                next.element.textRange.startOffset,
                next.element.textRange.endOffset
            )
        } else {
            // Different file - open file and navigate
            val virtualFile = next.file.virtualFile ?: return
            val fileEditorManager = FileEditorManager.getInstance(project)

            fileEditorManager.openFile(virtualFile, true).firstOrNull()?.let { fileEditor ->
                val newEditor = (fileEditor as? TextEditor)?.editor
                newEditor?.let { ed ->
                    val offset = next.element.textRange.startOffset
                    ed.caretModel.moveToOffset(offset)
                    ed.scrollingModel.scrollToCaret(ScrollType.CENTER)
                    ed.selectionModel.setSelection(
                        next.element.textRange.startOffset,
                        next.element.textRange.endOffset
                    )
                }
            }
        }
    }

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
        return IntentionPreviewInfo.EMPTY
    }

    override fun startInWriteAction(): Boolean = false
}