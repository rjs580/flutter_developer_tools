package dev.rutvik.flutter_developer_tools.dart.codeVision

import com.intellij.codeInsight.codeVision.CodeVisionRelativeOrdering
import com.intellij.codeInsight.hints.codeVision.ReferencesCodeVisionProvider
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction
import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.ui.awt.RelativePoint
import com.jetbrains.lang.dart.ide.findUsages.DartServerFindUsagesHandler
import com.jetbrains.lang.dart.psi.*
import com.jetbrains.lang.dart.test.DartTestSourcesFilter
import java.awt.event.MouseEvent
import java.util.concurrent.atomic.AtomicInteger

/**
 * Provides code vision showing usage counts for Dart elements.
 */
class DartReferencesCodeVisionProvider : ReferencesCodeVisionProvider() {

    companion object {
        const val ID = "dart.references"
        private const val MAX_USAGES = 100
    }

    override val id: String = ID

    override fun acceptsFile(file: PsiFile): Boolean = file is DartFile

    override fun acceptsElement(element: PsiElement): Boolean {
        if (!element.manager.isInProject(element)) return false

        return when (element) {
            is DartClassDefinition -> !element.isAbstract
            is DartFunctionDeclarationWithBody -> {
                val name = element.componentName.text
                name != null && name != "main"
            }
            is DartMethodDeclaration -> !element.isAbstract
            is DartVarDeclarationList -> true
            else -> false
        }
    }

    override fun getHint(element: PsiElement, file: PsiFile): String? {
        val el = when (element) {
            is DartVarDeclarationList -> element.varAccessDeclaration
            is DartComponent -> element
            else -> null
        } ?: return null

        val referencedElement = el.componentName ?: return null

        val scope = GlobalSearchScope.projectScope(element.project)
        val usages = AtomicInteger()
        val testUsages = AtomicInteger()

        val finder = DartServerFindUsagesHandler(element)
        val options = FindUsagesOptions(scope)
        options.isUsages = true
        options.isSearchForTextOccurrences = false

        finder.processElementUsages(referencedElement, { usage ->
            usage.element?.let { usageElement ->
                if (DartTestSourcesFilter.isTestSources(
                        usageElement.containingFile.virtualFile,
                        usageElement.project
                    )
                ) {
                    testUsages.incrementAndGet()
                }
            }
            usages.incrementAndGet() <= MAX_USAGES
        }, options)

        val sourceUsagesLabel = when (val count = usages.get()) {
            0 -> return if (!el.isAbstract) "No usages" else null
            1 -> "1 usage"
            else -> "$count usages"
        }

        val testUsagesLabel = when (val count = testUsages.get()) {
            0 -> return sourceUsagesLabel
            else -> "$count in tests"
        }

        return "$sourceUsagesLabel ($testUsagesLabel)"
    }

    override fun handleClick(editor: Editor, element: PsiElement, event: MouseEvent?) {
        val actualElement = if (element is DartVarDeclarationList) {
            element.varAccessDeclaration
        } else {
            element
        }

        GotoDeclarationAction.startFindUsages(
            editor,
            element.project,
            actualElement,
            if (event == null) null else RelativePoint(event)
        )
    }

    override val relativeOrderings: List<CodeVisionRelativeOrdering>
        get() = emptyList()
}