package dev.rutvik.flutter_developer_tools.dart.duplicates

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiElement
import com.jetbrains.lang.dart.psi.DartFile
import dev.rutvik.flutter_developer_tools.dart.duplicates.DartDuplicatesFinder.DuplicateInfo

/**
 * Annotator that detects and highlights duplicate code fragments in Dart files.
 * Provides quick fixes to navigate between duplicate fragments.
 */
class DartDuplicateCodeAnnotator : Annotator {

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element !is DartFile) return

        ProgressManager.checkCanceled()

        // Find all duplicate code fragments
        val duplicates = DartDuplicatesFinder.findDuplicates(element)

        if (duplicates.isEmpty()) return

        // Annotate each duplicate group
        duplicates.forEach { (fragment, duplicateLocations) ->
            if (duplicateLocations.size > 1) {
                annotateDuplicateGroup(fragment, duplicateLocations, holder)
            }
        }
    }

    private fun annotateDuplicateGroup(
        original: PsiElement,
        duplicates: List<DuplicateInfo>,
        holder: AnnotationHolder
    ) {
        duplicates.forEach { duplicate ->
            val element = duplicate.element

            holder.newAnnotation(HighlightSeverity.WARNING, buildMessage(duplicates.size))
                .range(element.textRange)
                .textAttributes(CodeInsightColors.DUPLICATE_FROM_SERVER)
                .withFix(ShowAllDuplicatesIntentionAction(duplicates, duplicate))
                .withFix(NavigateToNextDuplicateIntentionAction(duplicates, duplicate))
                .create()
        }
    }

    private fun buildMessage(count: Int): String {
        return when (count) {
            2 -> "Duplicate code fragment (1 duplicate)"
            else -> "Duplicate code fragment (${count - 1} duplicates)"
        }
    }
}