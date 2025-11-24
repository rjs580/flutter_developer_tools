package dev.rutvik.flutter_developer_tools.dart.duplicates

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import dev.rutvik.flutter_developer_tools.dart.duplicates.DartDuplicatesFinder.DuplicateInfo

/**
 * Quick fix that opens the Duplicates tool window showing all duplicate locations.
 *
 * Note: This action opens a tool window and cannot provide a meaningful preview.
 */
class ShowAllDuplicatesQuickFix(
    private val allDuplicates: List<DuplicateInfo>,
    private val currentDuplicate: DuplicateInfo
) : LocalQuickFix {

    @IntentionName
    override fun getName(): String {
        val count = allDuplicates.size - 1
        return "Show all duplicates ($count found)"
    }

    @IntentionFamilyName
    override fun getFamilyName(): String = "Show duplicate code fragments"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val toolWindowManager = ToolWindowManager.getInstance(project)

        // Register tool window dynamically if not already registered
        var toolWindow = toolWindowManager.getToolWindow("Dart Duplicates")
        if (toolWindow == null) {
            toolWindow = toolWindowManager.registerToolWindow("Dart Duplicates") {
                icon = com.intellij.icons.AllIcons.Toolwindows.ToolWindowDuplicates
                anchor = com.intellij.openapi.wm.ToolWindowAnchor.BOTTOM
                canCloseContent = true
                stripeTitle = java.util.function.Supplier { "Dart Duplicates" }
            }
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

    override fun generatePreview(project: Project, previewDescriptor: ProblemDescriptor): IntentionPreviewInfo {
        return IntentionPreviewInfo.EMPTY
    }

    override fun availableInBatchMode(): Boolean = false
}