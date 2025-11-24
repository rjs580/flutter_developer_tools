package dev.rutvik.flutter_developer_tools.dart.duplicates

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.PsiFile
import dev.rutvik.flutter_developer_tools.dart.duplicates.DartDuplicatesFinder.DuplicateInfo
import java.util.function.Supplier

/**
 * Intention action for showing all duplicates in a tool window.
 * Used by the annotator (not inspection).
 */
class ShowAllDuplicatesIntentionAction(
    private val allDuplicates: List<DuplicateInfo>,
    private val currentDuplicate: DuplicateInfo
) : IntentionAction {

    override fun getText(): String {
        val count = allDuplicates.size - 1
        return "Show all duplicates ($count found)"
    }

    override fun getFamilyName(): String = "Show duplicate code fragments"

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        return allDuplicates.size > 1
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        // Get or create the tool window
        val toolWindowManager = ToolWindowManager.getInstance(project)
        val toolWindow = toolWindowManager.getToolWindow("Dart Duplicates")
            ?: toolWindowManager.registerToolWindow("Dart Duplicates") {
                icon = com.intellij.icons.AllIcons.Toolwindows.ToolWindowFind
                stripeTitle = Supplier { "Dart Duplicates" }
            }

        // Update the content with current duplicates
        DartDuplicatesToolWindowFactory.showDuplicates(
            project,
            toolWindow,
            allDuplicates,
            currentDuplicate
        )

        // Show the tool window
        toolWindow.show()
    }

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
        return IntentionPreviewInfo.EMPTY
    }

    override fun startInWriteAction(): Boolean = false
}