package dev.rutvik.flutter_developer_tools.dart.duplicates

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.jetbrains.lang.dart.psi.DartFile

/**
 * Inspection tool for detecting duplicate code fragments in Dart files.
 * Can be run as part of code analysis or inspection profiles.
 */
class DartDuplicateCodeInspection : LocalInspectionTool() {

    override fun getDisplayName(): String = "Duplicate code fragment"

    override fun getShortName(): String = "DartDuplicateCode"

    override fun getGroupDisplayName(): String = "Dart"

    override fun getStaticDescription(): String {
        return """
            Detects duplicate code fragments that could be extracted into reusable functions or methods.
            This inspection helps identify opportunities for code refactoring and maintainability improvements.
        """.trimIndent()
    }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : PsiElementVisitor() {
            override fun visitFile(file: PsiFile) {
                super.visitFile(file)

                if (file !is DartFile) return

                val duplicates = DartDuplicatesFinder.findDuplicates(file)

                duplicates.forEach { (_, duplicateLocations) ->
                    if (duplicateLocations.size > 1) {
                        duplicateLocations.forEach { duplicate ->
                            val message = when (duplicateLocations.size) {
                                2 -> "Duplicate code fragment (1 duplicate found)"
                                else -> "Duplicate code fragment (${duplicateLocations.size - 1} duplicates found)"
                            }

                            // Register both quick fixes
                            val fixes = arrayOf(
                                ShowAllDuplicatesQuickFix(duplicateLocations, duplicate),
                                NavigateToNextDuplicateQuickFix(duplicateLocations, duplicate)
                            )

                            holder.registerProblem(
                                duplicate.element,
                                message,
                                ProblemHighlightType.WARNING,
                                *fixes
                            )
                        }
                    }
                }
            }
        }
    }
}