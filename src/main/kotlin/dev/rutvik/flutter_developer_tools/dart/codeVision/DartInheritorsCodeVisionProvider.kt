package dev.rutvik.flutter_developer_tools.dart.codeVision

import com.intellij.codeInsight.codeVision.CodeVisionRelativeOrdering
import com.intellij.codeInsight.daemon.DaemonBundle
import com.intellij.codeInsight.daemon.impl.PsiElementListNavigator
import com.intellij.codeInsight.hints.codeVision.InheritorsCodeVisionProvider
import com.intellij.ide.util.DefaultPsiElementCellRenderer
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.jetbrains.lang.dart.DartBundle
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService
import com.jetbrains.lang.dart.ide.actions.DartInheritorsSearcher
import com.jetbrains.lang.dart.psi.*
import com.jetbrains.lang.dart.test.DartTestSourcesFilter
import com.jetbrains.lang.dart.util.DartResolveUtil
import dev.rutvik.flutter_developer_tools.utils.guardExtension
import java.awt.event.MouseEvent

/**
 * Provides code vision showing implementation counts for abstract Dart elements.
 */
class DartInheritorsCodeVisionProvider : InheritorsCodeVisionProvider() {

    companion object {
        const val ID = "dart.inheritors"
    }

    override val id: String = ID

    override fun acceptsFile(file: PsiFile): Boolean = file is DartFile

    override fun acceptsElement(element: PsiElement): Boolean =
        guardExtension(ID, false) { isInheritorsTarget(element) }

    override fun getHint(element: PsiElement, file: PsiFile): String? =
        guardExtension(ID, null) { computeHint(element, file) }

    override fun handleClick(editor: Editor, element: PsiElement, event: MouseEvent?) =
        guardExtension(ID, Unit) { showImplementations(editor, element, event) }

    private fun isInheritorsTarget(element: PsiElement): Boolean {
        if (!element.manager.isInProject(element)) return false

        return when {
            element is DartComponent && element.parent is DartClassMembers && element.isAbstract -> true
            element is DartClassDefinition && element.isAbstract -> true
            else -> false
        }
    }

    private fun computeHint(element: PsiElement, file: PsiFile): String? {
        ProgressManager.checkCanceled()

        if (DumbService.isDumb(element.project)) {
            return null
        }

        if (element !is DartComponent) return null

        // Ensure Dart analysis server is ready
        val project = element.project
        val das = DartAnalysisServerService.getInstance(project)
        if (!das.isServerProcessActive) {
            return null
        }

        val anchor = element.componentName ?: return null

        ProgressManager.checkCanceled()
        
        val items = das.search_getTypeHierarchy(
            file.virtualFile,
            anchor.textRange.startOffset,
            false
        )

        if (items.isEmpty()) return null

        val implementations = when (element) {
            is DartClassDefinition -> DartInheritorsSearcher.getSubClasses(
                project,
                GlobalSearchScope.allScope(project),
                items
            )
            else -> DartInheritorsSearcher.getSubMembers(
                project,
                GlobalSearchScope.allScope(project),
                items
            )
        }

        element.putUserData(IMPLEMENTATIONS_KEY, implementations)

        val sourceCount = implementations.size
        if (sourceCount == 0) return null

        val sourceLabel = when (sourceCount) {
            1 -> "1 implementation"
            else -> "$sourceCount implementations"
        }

        val testCount = implementations.count {
            DartTestSourcesFilter.isTestSources(it.containingFile.virtualFile, it.project)
        }

        return if (testCount == 0) {
            sourceLabel
        } else {
            "$sourceLabel ($testCount in tests)"
        }
    }

    private fun showImplementations(editor: Editor, element: PsiElement, event: MouseEvent?) {
        if (event == null || element !is DartComponent) return

        val anchor = element.componentName ?: return
        val components = element.getUserData(IMPLEMENTATIONS_KEY) ?: return

        val (popupTitle, findUsagesTitle) = if (element is DartClassDefinition) {
            DaemonBundle.message("navigation.title.subclass", anchor.name, components.size, "") to
                    DartBundle.message("tab.title.subclasses.of.0", anchor.name)
        } else {
            DaemonBundle.message("navigation.title.overrider.method", anchor.name, components.size) to
                    DartBundle.message("tab.title.overriding.methods.of.0", anchor.name)
        }

        PsiElementListNavigator.openTargets(
            event,
            DartResolveUtil.getComponentNameArray(components),
            popupTitle,
            findUsagesTitle,
            DefaultPsiElementCellRenderer()
        )
    }

    override fun preparePreview(editor: Editor, file: PsiFile) {
        // Skip preview computation for performance
    }

    override val relativeOrderings: List<CodeVisionRelativeOrdering>
        get() = listOf(CodeVisionRelativeOrdering.CodeVisionRelativeOrderingAfter(DartReferencesCodeVisionProvider.ID))
}

private val IMPLEMENTATIONS_KEY = Key<Set<DartComponent>>("IMPLEMENTATIONS_KEY")