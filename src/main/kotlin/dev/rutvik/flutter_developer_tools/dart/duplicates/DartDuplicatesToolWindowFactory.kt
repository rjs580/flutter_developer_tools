
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
import com.intellij.ui.components.JBCheckBox
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
import javax.swing.ListSelectionModel
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableCellRenderer

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
    private val showCheckboxes = allDuplicates.size > 2
    private val selectionOrder = mutableListOf<Int>()

    init {
        tableModel = DuplicatesTableModel(allDuplicates, currentDuplicate, showCheckboxes)
        table = object : JBTable(tableModel) {
            override fun changeSelection(rowIndex: Int, columnIndex: Int, toggle: Boolean, extend: Boolean) {
                // For checkbox column, handle selection manually via mouse listener
                if (showCheckboxes && columnIndex == 0) {
                    return
                }
                super.changeSelection(rowIndex, columnIndex, toggle, extend)
            }
        }

        table.apply {
            setDefaultRenderer(String::class.java, DuplicateCellRenderer(currentDuplicate))
            rowHeight = 32
            showVerticalLines = false
            showHorizontalLines = true

            // Disable cell editing to prevent interference with checkbox clicks
            setDefaultEditor(Any::class.java, null)

            // Configure checkbox column if needed
            if (showCheckboxes) {
                columnModel.getColumn(0).apply {
                    cellRenderer = CheckboxRenderer()
                    preferredWidth = 40
                    maxWidth = 40
                }

                // Enable multi-selection
                selectionModel.selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION

                // Column widths with checkbox
                columnModel.getColumn(1).preferredWidth = 50  // #
                columnModel.getColumn(2).preferredWidth = 200 // File
                columnModel.getColumn(3).preferredWidth = 80  // Line
                columnModel.getColumn(4).preferredWidth = 100 // Lines
            } else {
                // Single selection for 2 duplicates
                selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION

                // Column widths without checkbox
                columnModel.getColumn(0).preferredWidth = 50  // #
                columnModel.getColumn(1).preferredWidth = 200 // File
                columnModel.getColumn(2).preferredWidth = 80  // Line
                columnModel.getColumn(3).preferredWidth = 100 // Lines
            }

            // Selection listener for preview
            selectionModel.addListSelectionListener { e ->
                if (!e.valueIsAdjusting) {
                    val row = selectedRow
                    if (row >= 0 && selectedRowCount == 1) {
                        updatePreview(allDuplicates[row])
                    }
                }
            }

            // Selection listener for preview
            selectionModel.addListSelectionListener { e ->
                if (!e.valueIsAdjusting) {
                    val row = selectedRow
                    if (row >= 0 && selectedRowCount == 1) {
                        updatePreview(allDuplicates[row])
                    }

                    // Track selection order and limit to 2 selections for checkboxes
                    if (showCheckboxes) {
                        updateSelectionOrder()
                    }
                }
            }

            // Mouse listener for checkbox clicks and double-click navigation
            addMouseListener(object : MouseAdapter() {
                override fun mousePressed(e: MouseEvent) {
                    val row = rowAtPoint(e.point)
                    val col = columnAtPoint(e.point)

                    if (row < 0) return

                    // Handle checkbox click (only for 3+ duplicates)
                    if (showCheckboxes && col == 0) {
                        // Toggle selection on checkbox click
                        if (isRowSelected(row)) {
                            removeRowSelectionInterval(row, row)
                            selectionOrder.remove(row)
                        } else {
                            // Check if we already have 2 selections
                            if (selectedRowCount >= 2 && selectionOrder.size >= 2) {
                                // Remove the oldest selection
                                val oldestRow = selectionOrder.removeAt(0)
                                removeRowSelectionInterval(oldestRow, oldestRow)
                            }
                            addRowSelectionInterval(row, row)
                            selectionOrder.add(row)
                        }
                        repaint() // Force repaint to update checkbox state
                    }
                }

                override fun mouseClicked(e: MouseEvent) {
                    val row = rowAtPoint(e.point)
                    val col = columnAtPoint(e.point)

                    if (row < 0) return

                    // Handle double-click navigation (but not on checkbox column)
                    if (e.clickCount == 2 && col != 0) {
                        navigateToDuplicate(row)
                    }
                }
            })
        }

        // Create editor for preview with Dart syntax highlighting
        val editorFactory = EditorFactory.getInstance()
        val document = editorFactory.createDocument(getInitialPreviewText())
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

    private fun getInitialPreviewText(): String {
        return if (allDuplicates.size == 2) {
            "Select a duplicate to view code, or click 'Show Diff' to compare both."
        } else {
            "Select one duplicate to view code, or use checkboxes to select 2 duplicates to compare."
        }
    }

    private fun createHeaderPanel(): JPanel {
        val panel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(8)
        }

        val labelText = if (allDuplicates.size == 2) {
            "Found ${allDuplicates.size} duplicate code fragments. Click 'Show Diff' to compare or double-click to navigate."
        } else {
            "Found ${allDuplicates.size} duplicate code fragments. Check 2 items to compare or double-click to navigate."
        }

        val label = JBLabel(labelText).apply {
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

    private fun updatePreview(duplicate: DuplicateInfo) {
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

    private fun updateSelectionOrder() {
        // Get currently selected rows
        val currentlySelected = table.selectedRows.toSet()

        // Remove deselected rows from order tracking
        selectionOrder.removeAll { !currentlySelected.contains(it) }

        // Add newly selected rows to order tracking
        currentlySelected.forEach { row ->
            if (!selectionOrder.contains(row)) {
                selectionOrder.add(row)
            }
        }

        // If more than 2 selections, remove the oldest ones
        while (selectionOrder.size > 2) {
            val oldestRow = selectionOrder.removeAt(0)
            table.removeRowSelectionInterval(oldestRow, oldestRow)
        }
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

    private inner class CheckboxRenderer : TableCellRenderer {
        private val checkBox = JBCheckBox()

        override fun getTableCellRendererComponent(
            table: JTable?,
            value: Any?,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int
        ): Component {
            if (table != null) {
                checkBox.isSelected = table.isRowSelected(row)
                checkBox.background = if (isSelected) {
                    table.selectionBackground
                } else {
                    table.background
                }
            }
            checkBox.horizontalAlignment = JBCheckBox.CENTER
            return checkBox
        }
    }

    private inner class ShowDiffAction : AnAction(
        "Show Diff",
        "Compare selected duplicates",
        AllIcons.Actions.Diff
    ) {
        override fun actionPerformed(e: AnActionEvent) {
            val selectedRows = table.selectedRows

            val first: DuplicateInfo
            val second: DuplicateInfo

            if (allDuplicates.size == 2) {
                // For 2 duplicates, always compare them
                first = allDuplicates[0]
                second = allDuplicates[1]
            } else {
                // For 3+, use the 2 selected items
                if (selectedRows.size != 2) return
                first = allDuplicates[selectedRows[0]]
                second = allDuplicates[selectedRows[1]]
            }

            val contentFactory = DiffContentFactory.getInstance()
            val content1 = contentFactory.create(project, first.element.text, first.file.fileType)
            val content2 = contentFactory.create(project, second.element.text, second.file.fileType)

            val request = SimpleDiffRequest(
                "Duplicate Code Comparison",
                content1,
                content2,
                "${first.file.name}:${first.lineNumber}",
                "${second.file.name}:${second.lineNumber}"
            )

            DiffManager.getInstance().showDiff(project, request)
        }

        override fun update(e: AnActionEvent) {
            val selectedRows = table.selectedRows

            if (allDuplicates.size == 2) {
                // Always enabled for 2 duplicates
                e.presentation.isEnabled = true
                e.presentation.description = "Compare the 2 duplicate code fragments"
            } else {
                // For 3+, require exactly 2 selections
                val isEnabled = selectedRows.size == 2
                e.presentation.isEnabled = isEnabled

                if (selectedRows.isEmpty()) {
                    e.presentation.description = "Check 2 duplicates to compare"
                } else if (selectedRows.size == 1) {
                    val selected = allDuplicates[selectedRows[0]]
                    e.presentation.description = "Check 1 more duplicate to compare with ${selected.file.name}:${selected.lineNumber}"
                } else if (selectedRows.size == 2) {
                    val first = allDuplicates[selectedRows[0]]
                    val second = allDuplicates[selectedRows[1]]
                    e.presentation.description = "Compare ${first.file.name}:${first.lineNumber} with ${second.file.name}:${second.lineNumber}"
                } else {
                    e.presentation.description = "Check exactly 2 duplicates to compare (${selectedRows.size} selected)"
                }
            }
        }

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
    }

    private inner class NavigateToSelectedAction : AnAction(
        "Navigate",
        "Navigate to selected duplicate",
        AllIcons.Actions.Forward
    ) {
        override fun actionPerformed(e: AnActionEvent) {
            val row = table.selectedRow
            if (row >= 0) {
                // Navigate to the current selection
                navigateToDuplicate(row)

                // Move to next duplicate with wrap-around
                val nextRow = (row + 1) % allDuplicates.size
                table.setRowSelectionInterval(nextRow, nextRow)
                table.scrollRectToVisible(table.getCellRect(nextRow, 0, true))
            }
        }

        override fun update(e: AnActionEvent) {
            val selectedRows = table.selectedRows
            e.presentation.isEnabled = selectedRows.size == 1

            if (selectedRows.isEmpty()) {
                e.presentation.description = "Select a duplicate to navigate"
            } else if (selectedRows.size == 1) {
                val duplicate = allDuplicates[selectedRows[0]]
                e.presentation.description = "Navigate to ${duplicate.file.name}:${duplicate.lineNumber}"
            } else {
                e.presentation.description = "Select only 1 duplicate to navigate"
            }
        }

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
    }

    private class DuplicatesTableModel(
        private val duplicates: List<DuplicateInfo>,
        private val currentDuplicate: DuplicateInfo,
        private val showCheckboxes: Boolean
    ) : AbstractTableModel() {

        private val columnNames = if (showCheckboxes) {
            arrayOf("", "#", "File", "Line", "Lines")
        } else {
            arrayOf("#", "File", "Line", "Lines")
        }

        override fun getRowCount(): Int = duplicates.size
        override fun getColumnCount(): Int = columnNames.size
        override fun getColumnName(column: Int): String = columnNames[column]

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val duplicate = duplicates[rowIndex]

            return if (showCheckboxes) {
                when (columnIndex) {
                    0 -> false // Checkbox state (managed by selection)
                    1 -> rowIndex + 1
                    2 -> duplicate.file.name
                    3 -> duplicate.lineNumber
                    4 -> duplicate.lineCount
                    else -> ""
                }
            } else {
                when (columnIndex) {
                    0 -> rowIndex + 1
                    1 -> duplicate.file.name
                    2 -> duplicate.lineNumber
                    3 -> duplicate.lineCount
                    else -> ""
                }
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
                    // Determine column offset based on whether checkboxes are shown
                    val fileColumn = if (model.columnCount == 5) 2 else 1
                    val lineColumn = if (model.columnCount == 5) 3 else 2

                    val duplicates = (0 until model.rowCount).map { r ->
                        val fileValue = model.getValueAt(r, fileColumn) as? String
                        val lineValue = model.getValueAt(r, lineColumn) as? Int
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