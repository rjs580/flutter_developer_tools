
package dev.rutvik.flutter_developer_tools.dart.codeVision

import com.intellij.codeInsight.codeVision.CodeVisionRelativeOrdering
import com.intellij.codeInsight.hints.codeVision.ReferencesCodeVisionProvider
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction
import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.ui.awt.RelativePoint
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService
import com.jetbrains.lang.dart.ide.findUsages.DartServerFindUsagesHandler
import com.jetbrains.lang.dart.psi.*
import com.jetbrains.lang.dart.test.DartTestSourcesFilter
import java.awt.event.MouseEvent
import java.util.concurrent.atomic.AtomicInteger

/**
 * Provides code vision showing usage counts for Dart elements.
 * Uses optimized search with progress checking and cancellation support.
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
            // Non-abstract classes (exclude local classes inside functions)
            is DartClassDefinition -> {
                !element.isAbstract && isTopLevelOrClassMember(element)
            }

            // Top-level functions (exclude main and local functions)
            is DartFunctionDeclarationWithBody -> {
                val name = element.componentName.text
                name != null && name != "main" && isTopLevelOrClassMember(element)
            }

            // Methods (including getters, setters, operators)
            is DartMethodDeclaration -> !element.isAbstract

            // Getters
            is DartGetterDeclaration -> true

            // Setters
            is DartSetterDeclaration -> true

            // Named constructors, factory constructors, const constructors
            is DartNamedConstructorDeclaration -> true
            is DartFactoryConstructorDeclaration -> true

            // Regular variables, fields, const fields (exclude local variables)
            is DartVarDeclarationList -> isTopLevelOrClassMember(element)

            // Enums
            is DartEnumDefinition -> isTopLevelOrClassMember(element)

            // Enum constants
            is DartEnumConstantDeclaration -> true

            // Mixins
            is DartMixinDeclaration -> true

            // Extensions
            is DartExtensionDeclaration -> true

            // Type aliases
            is DartFunctionTypeAlias -> true

            else -> false
        }
    }

    /**
     * Checks if element is either top-level (direct child of DartFile) or a class member.
     * This excludes local classes, functions, and variables defined inside function bodies.
     */
    private fun isTopLevelOrClassMember(element: PsiElement): Boolean {
        var parent = element.parent
        while (parent != null) {
            when (parent) {
                // Top-level: direct child of file
                is DartFile -> return true
                // Class member
                is DartClassMembers -> return true
                // Inside a function body - this is local
                is DartFunctionBody -> return false
            }
            parent = parent.parent
        }
        return false
    }

    override fun getHint(element: PsiElement, file: PsiFile): String? {
        // Ensure Dart analysis server is ready
        val das = DartAnalysisServerService.getInstance(element.project)
        if (!das.isServerProcessActive) {
            return null
        }

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
            // Check for cancellation to avoid blocking the UI
            ProgressManager.checkCanceled()

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

        val totalUsages = usages.get()
        val totalTestUsages = testUsages.get()

        // Don't show if no usages for non-abstract elements
        if (totalUsages == 0 && !el.isAbstract) {
            return "No usages"
        }

        val sourceUsagesLabel = when (totalUsages) {
            0 -> return null
            1 -> "1 usage"
            else -> if (totalUsages > MAX_USAGES) "$MAX_USAGES+ usages" else "$totalUsages usages"
        }

        return if (totalTestUsages == 0) {
            sourceUsagesLabel
        } else {
            "$sourceUsagesLabel ($totalTestUsages in tests)"
        }
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

    override fun preparePreview(editor: Editor, file: PsiFile) {
        // Skip preview computation for performance
    }

    override val relativeOrderings: List<CodeVisionRelativeOrdering>
        get() = emptyList()
}