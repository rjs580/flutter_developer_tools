package dev.rutvik.flutter_developer_tools.dart.duplicates

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffManager
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import com.jetbrains.lang.dart.DartFileType
import dev.rutvik.flutter_developer_tools.dart.duplicates.DartDuplicatesFinder.DuplicateInfo
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Font
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer

class DartDuplicatesToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val content = ContentFactory.getInstance().createContent(
            createEmptyPanel(),
            "",
            false
        )
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project): Boolean = false // Don't show until needed

    private fun createEmptyPanel(): JPanel {
        return JPanel(BorderLayout()).apply {
            add(JBLabel("No duplicates to display", JBLabel.CENTER), BorderLayout.CENTER)
        }
    }

    companion object {
        fun showDuplicates(
            project: Project,
            toolWindow: ToolWindow,
            allDuplicates: List<DuplicateInfo>,
            currentDuplicate: DuplicateInfo
        ) {
            val contentManager = toolWindow.contentManager
            contentManager.removeAllContents(true)

            val panel = DuplicatesPanel(project, allDuplicates, currentDuplicate)
            val content = ContentFactory.getInstance().createContent(
                panel,
                "Duplicate Fragments (${allDuplicates.size} locations)",
                false
            )
            contentManager.addContent(content)
            contentManager.setSelectedContent(content)
        }
    }
}

/**
 * Panel showing all duplicate code fragments with preview and diff view.
 */
private class DuplicatesPanel(
    private val project: Project,
    private val allDuplicates: List<DuplicateInfo>,
    private val currentDuplicate: DuplicateInfo
) : JPanel(BorderLayout()) {

    private val table: JBTable
    private val tableModel: DuplicatesTableModel
    private val previewEditor: EditorEx
    private var selectedDuplicate: DuplicateInfo? = null

    init {
        tableModel = DuplicatesTableModel(allDuplicates, currentDuplicate)
        table = JBTable(tableModel).apply {
            setDefaultRenderer(String::class.java, DuplicateCellRenderer(currentDuplicate))
            rowHeight = 32
            showVerticalLines = false
            showHorizontalLines = true

            // Column widths
            columnModel.getColumn(0).preferredWidth = 50  // #
            columnModel.getColumn(1).preferredWidth = 200 // File
            columnModel.getColumn(2).preferredWidth = 80  // Line
            columnModel.getColumn(3).preferredWidth = 100 // Lines

            // Selection listener for preview
            selectionModel.addListSelectionListener { e ->
                if (!e.valueIsAdjusting) {
                    val row = selectedRow
                    if (row >= 0) {
                        selectedDuplicate = allDuplicates[row]
                        updatePreview()
                    }
                }
            }

            // Double-click to navigate
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (e.clickCount == 2) {
                        val row = rowAtPoint(e.point)
                        if (row >= 0) {
                            navigateToDuplicate(row)
                        }
                    }
                }
            })
        }

        // Create editor for preview with Dart syntax highlighting
        val editorFactory = EditorFactory.getInstance()
        val document = editorFactory.createDocument("Select a duplicate to view full code...")
        previewEditor = editorFactory.createEditor(document, project) as EditorEx

        // Configure preview editor
        previewEditor.apply {
            settings.isLineNumbersShown = true
            settings.isLineMarkerAreaShown = false
            settings.isFoldingOutlineShown = false
            settings.isRightMarginShown = false
            settings.isVirtualSpace = false
            isViewer = true // Read-only

            // Set Dart syntax highlighter
            val highlighter = EditorHighlighterFactory.getInstance()
                .createEditorHighlighter(project, DartFileType.INSTANCE)
            setHighlighter(highlighter)
        }

        val scrollPane = JBScrollPane(table)

        // Split pane: table on left, preview editor on right
        val splitter = JBSplitter(false, 0.5f).apply {
            firstComponent = scrollPane
            secondComponent = previewEditor.component
        }

        // Header with actions
        val headerPanel = createHeaderPanel()

        add(headerPanel, BorderLayout.NORTH)
        add(splitter, BorderLayout.CENTER)

        // Select current duplicate by default
        val currentIndex = allDuplicates.indexOf(currentDuplicate)
        if (currentIndex >= 0) {
            table.setRowSelectionInterval(currentIndex, currentIndex)
        }
    }

    private fun createHeaderPanel(): JPanel {
        val panel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(8)
        }

        val label = JBLabel("Found ${allDuplicates.size} duplicate code fragments. Double-click to navigate.").apply {
            font = font.deriveFont(Font.BOLD)
        }

        // Actions toolbar
        val actionGroup = DefaultActionGroup().apply {
            add(ShowDiffAction())
            add(NavigateToSelectedAction())
        }

        val toolbar = ActionManager.getInstance()
            .createActionToolbar("DartDuplicatesToolbar", actionGroup, true)
        toolbar.targetComponent = this

        panel.add(label, BorderLayout.WEST)
        panel.add(toolbar.component, BorderLayout.EAST)

        return panel
    }

    private fun updatePreview() {
        val duplicate = selectedDuplicate ?: return

        // Access PSI text within read action
        val text = com.intellij.openapi.application.ReadAction.compute<String, Exception> {
            duplicate.element.text
        }

        // Update preview editor with syntax highlighting
        val document = previewEditor.document
        com.intellij.openapi.application.ApplicationManager.getApplication().runWriteAction {
            document.setText(text)
        }

        // Reset scroll position
        previewEditor.scrollingModel.scrollVertically(0)
    }

    private fun navigateToDuplicate(row: Int) {
        if (row < 0 || row >= allDuplicates.size) return

        val duplicate = allDuplicates[row]
        val virtualFile = duplicate.file.virtualFile ?: return

        val descriptor = OpenFileDescriptor(
            project,
            virtualFile,
            duplicate.element.textRange.startOffset
        )

        FileEditorManager.getInstance(project).openTextEditor(descriptor, true)?.let { editor ->
            editor.selectionModel.setSelection(
                duplicate.element.textRange.startOffset,
                duplicate.element.textRange.endOffset
            )

            // Lazily highlight just this duplicate
            DuplicateHighlightManager.highlightDuplicate(editor, duplicate)
        }
    }

    private inner class ShowDiffAction : AnAction("Show Diff", "Compare selected duplicate with current", AllIcons.Actions.Diff) {
        override fun actionPerformed(e: AnActionEvent) {
            val selected = selectedDuplicate ?: return
            if (selected == currentDuplicate) return

            val contentFactory = DiffContentFactory.getInstance()
            val content1 = contentFactory.create(project, currentDuplicate.element.text, currentDuplicate.file.fileType)
            val content2 = contentFactory.create(project, selected.element.text, selected.file.fileType)

            val request = SimpleDiffRequest(
                "Duplicate Code Comparison",
                content1,
                content2,
                "${currentDuplicate.file.name}:${currentDuplicate.lineNumber}",
                "${selected.file.name}:${selected.lineNumber}"
            )

            DiffManager.getInstance().showDiff(project, request)
        }

        override fun update(e: AnActionEvent) {
            e.presentation.isEnabled = selectedDuplicate != null && selectedDuplicate != currentDuplicate
        }

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
    }

    private inner class NavigateToSelectedAction : AnAction("Navigate", "Navigate to selected duplicate", AllIcons.Actions.Forward) {
        override fun actionPerformed(e: AnActionEvent) {
            val row = table.selectedRow
            if (row >= 0) {
                navigateToDuplicate(row)
            }
        }

        override fun update(e: AnActionEvent) {
            e.presentation.isEnabled = table.selectedRow >= 0
        }

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
    }

    private class DuplicatesTableModel(
        private val duplicates: List<DuplicateInfo>,
        private val currentDuplicate: DuplicateInfo
    ) : AbstractTableModel() {

        private val columnNames = arrayOf("#", "File", "Line", "Lines")

        override fun getRowCount(): Int = duplicates.size
        override fun getColumnCount(): Int = columnNames.size
        override fun getColumnName(column: Int): String = columnNames[column]

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val duplicate = duplicates[rowIndex]
            return when (columnIndex) {
                0 -> rowIndex + 1
                1 -> duplicate.file.name
                2 -> duplicate.lineNumber
                3 -> duplicate.lineCount
                else -> ""
            }
        }
    }

    private class DuplicateCellRenderer(
        private val currentDuplicate: DuplicateInfo
    ) : DefaultTableCellRenderer() {

        override fun getTableCellRendererComponent(
            table: JTable?,
            value: Any?,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int
        ): Component {
            val component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)

            // Highlight the current duplicate row
            if (table != null && row < table.model.rowCount) {
                val model = table.model as? DuplicatesTableModel
                if (model != null) {
                    val duplicates = (0 until model.rowCount).map { r ->
                        val fileValue = model.getValueAt(r, 1) as? String
                        val lineValue = model.getValueAt(r, 2) as? Int
                        fileValue == currentDuplicate.file.name && lineValue == currentDuplicate.lineNumber
                    }

                    if (row < duplicates.size && duplicates[row] && !isSelected) {
                        background = JBUI.CurrentTheme.List.Selection.background(false)
                        font = font.deriveFont(Font.BOLD)
                    }
                }
            }

            border = JBUI.Borders.empty(4, 8)
            return component
        }
    }
}