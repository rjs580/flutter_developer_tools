
package dev.rutvik.flutter_developer_tools.dart.duplicates

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import dev.rutvik.flutter_developer_tools.dart.duplicates.DartDuplicatesFinder.DuplicateInfo
import java.util.function.Supplier

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

    // Explicitly disable preview for UI actions
    override fun generatePreview(project: Project, previewDescriptor: ProblemDescriptor): IntentionPreviewInfo {
        return IntentionPreviewInfo.EMPTY
    }

    override fun availableInBatchMode(): Boolean = false
}