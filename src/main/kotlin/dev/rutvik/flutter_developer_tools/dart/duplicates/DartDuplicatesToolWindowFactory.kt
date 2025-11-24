package dev.rutvik.flutter_developer_tools.dart.duplicates

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
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
 * Panel showing all duplicate code fragments in a table.
 */
private class DuplicatesPanel(
    private val project: Project,
    private val allDuplicates: List<DuplicateInfo>,
    private val currentDuplicate: DuplicateInfo
) : JPanel(BorderLayout()) {

    private val table: JBTable
    private val tableModel: DuplicatesTableModel

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
            columnModel.getColumn(3).preferredWidth = 400 // Code Preview

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

        val scrollPane = JBScrollPane(table)

        val headerPanel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(8)
            val label = JBLabel("Found ${allDuplicates.size} duplicate code fragments. Double-click to navigate.")
            label.font = label.font.deriveFont(Font.BOLD)
            add(label, BorderLayout.WEST)
        }

        add(headerPanel, BorderLayout.NORTH)
        add(scrollPane, BorderLayout.CENTER)
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
        }
    }

    private class DuplicatesTableModel(
        private val duplicates: List<DuplicateInfo>,
        private val currentDuplicate: DuplicateInfo
    ) : AbstractTableModel() {

        private val columnNames = arrayOf("#", "File", "Line", "Code Preview")

        override fun getRowCount(): Int = duplicates.size
        override fun getColumnCount(): Int = columnNames.size
        override fun getColumnName(column: Int): String = columnNames[column]

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val duplicate = duplicates[rowIndex]
            return when (columnIndex) {
                0 -> rowIndex + 1
                1 -> duplicate.file.name
                2 -> duplicate.lineNumber
                3 -> getCodePreview(duplicate)
                else -> ""
            }
        }

        private fun getCodePreview(duplicate: DuplicateInfo): String {
            val text = duplicate.element.text
            val preview = text.lines().firstOrNull()?.trim() ?: text
            return if (preview.length > 80) {
                preview.take(77) + "..."
            } else {
                preview
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
                val duplicates = (0 until (model?.rowCount ?: 0)).map { r ->
                    // We need to access duplicates list, so we'll check based on content
                    val fileValue = model?.getValueAt(r, 1) as? String
                    val lineValue = model?.getValueAt(r, 2) as? Int
                    fileValue == currentDuplicate.file.name && lineValue == currentDuplicate.lineNumber
                }

                if (row < duplicates.size && duplicates[row] && !isSelected) {
                    background = JBUI.CurrentTheme.List.Selection.background(false)
                    font = font.deriveFont(Font.BOLD)
                }
            }

            border = JBUI.Borders.empty(4, 8)
            return component
        }
    }
}