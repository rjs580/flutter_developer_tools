package dev.rutvik.flutter_developer_tools.toolWindow

import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import com.jetbrains.lang.dart.ide.findUsages.DartServerFindUsagesHandler
import com.jetbrains.lang.dart.psi.DartClass
import com.jetbrains.lang.dart.psi.DartFile
import java.awt.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.swing.*
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer

/**
 * Widget Usage Heatmap Tool Window
 *
 * Shows how often widgets are used across the project to help identify
 * candidates for refactoring or generalization.
 */
class WidgetHeatmapWindow(private val project: Project) {

    private val panel = SimpleToolWindowPanel(true, true)
    private val table: JBTable
    private val tableModel: WidgetHeatmapTableModel
    private val statusLabel: JBLabel
    private var lastUpdateTime: Long = 0

    data class WidgetUsageInfo(
        val className: String,
        val usageCount: Int,
        val fileCount: Int,
        val filePath: String,
        val isCustomWidget: Boolean = true,
        val extendsWidget: String? = null
    )

    init {
        tableModel = WidgetHeatmapTableModel()
        table = JBTable(tableModel).apply {
            setDefaultRenderer(Any::class.java, HeatmapCellRenderer())
            autoCreateRowSorter = true
            fillsViewportHeight = true
            rowHeight = 32

            // Column widths
            columnModel.getColumn(0).preferredWidth = 250 // Widget Name
            columnModel.getColumn(1).preferredWidth = 100 // Usage Count
            columnModel.getColumn(2).preferredWidth = 100 // File Count
            columnModel.getColumn(3).preferredWidth = 80  // Heatmap
            columnModel.getColumn(4).preferredWidth = 150 // Extends
            columnModel.getColumn(5).preferredWidth = 300 // File Path

            // Double-click to open file
            addMouseListener(object : java.awt.event.MouseAdapter() {
                override fun mouseClicked(e: java.awt.event.MouseEvent) {
                    if (e.clickCount == 2) {
                        val row = rowAtPoint(e.point)
                        if (row >= 0) {
                            openWidgetFile(row)
                        }
                    }
                }
            })
        }

        statusLabel = JBLabel("Ready. Click refresh to analyze widgets.").apply {
            border = JBUI.Borders.empty(5, 10)
            foreground = JBColor.GRAY
        }

        setupToolbar()
        setupContent()
    }

    private fun setupToolbar() {
        val actionGroup = DefaultActionGroup().apply {
            add(RefreshAction())
            add(ExportAction())
            addSeparator()
            add(FilterCustomOnlyAction())
            add(FilterHighUsageAction())
        }

        val toolbar = ActionManager.getInstance()
            .createActionToolbar("WidgetHeatmapToolbar", actionGroup, true)
        toolbar.targetComponent = panel
        panel.toolbar = toolbar.component
    }

    private fun setupContent() {
        val scrollPane = JBScrollPane(table)

        val mainPanel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            add(scrollPane, BorderLayout.CENTER)
            add(statusLabel, BorderLayout.SOUTH)
        }

        panel.setContent(mainPanel)
    }

    fun getContent(): JComponent = panel

    private fun refreshData() {
        statusLabel.text = "Analyzing widgets..."

        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            "Analyzing widget usage",
            true
        ) {
            override fun run(indicator: ProgressIndicator) {
                indicator.text = "Scanning Dart files..."
                indicator.isIndeterminate = false

                val widgetUsages = mutableMapOf<String, WidgetUsageInfo>()

                ReadAction.run<Exception> {
                    val dartFiles = findAllDartFiles()
                    val totalFiles = dartFiles.size

                    dartFiles.forEachIndexed { index, dartFile ->
                        if (indicator.isCanceled) return@forEachIndexed

                        indicator.fraction = index.toDouble() / totalFiles
                        indicator.text = "Analyzing ${dartFile.name}..."

                        val classes = PsiTreeUtil.findChildrenOfType(dartFile, DartClass::class.java)

                        classes.forEach { dartClass ->
                            if (indicator.isCanceled) return@forEach

                            val className = dartClass.name ?: return@forEach

                            // Check if it's a widget (extends StatelessWidget, StatefulWidget, etc.)
                            val superClass = dartClass.superClass?.text
                            val isWidget = superClass != null && (
                                    superClass.contains("Widget") ||
                                            superClass.contains("State<")
                                    )

                            if (isWidget) {
                                indicator.text2 = "Counting usages for $className..."
                                val usageCount = countUsages(dartClass, indicator)
                                val fileCount = countFileReferences(dartClass, indicator)

                                widgetUsages[className] = WidgetUsageInfo(
                                    className = className,
                                    usageCount = usageCount,
                                    fileCount = fileCount,
                                    filePath = dartFile.virtualFile.path,
                                    isCustomWidget = true,
                                    extendsWidget = superClass
                                )
                            }
                        }
                    }
                }

                lastUpdateTime = System.currentTimeMillis()

                ApplicationManager.getApplication().invokeLater {
                    tableModel.setData(widgetUsages.values.toList())
                    updateStatusLabel(widgetUsages.size)
                }
            }
        })
    }

    private fun findAllDartFiles(): List<DartFile> {
        val dartFiles = mutableListOf<DartFile>()
        val psiManager = PsiManager.getInstance(project)
        val scope = GlobalSearchScope.projectScope(project)

        com.intellij.openapi.roots.ProjectFileIndex.getInstance(project)
            .iterateContent { virtualFile ->
                if (virtualFile.extension == "dart" && scope.contains(virtualFile)) {
                    psiManager.findFile(virtualFile)?.let { psiFile ->
                        if (psiFile is DartFile) {
                            dartFiles.add(psiFile)
                        }
                    }
                }
                true
            }

        return dartFiles
    }

    private fun countUsages(dartClass: DartClass, indicator: ProgressIndicator): Int {
        if (indicator.isCanceled) return 0

        val componentName = dartClass.componentName ?: return 0
        var count = 0

        try {
            val handler = DartServerFindUsagesHandler(dartClass)
            val options = FindUsagesOptions(GlobalSearchScope.projectScope(project))
            options.isUsages = true

            handler.processElementUsages(componentName, { _ ->
                if (indicator.isCanceled) return@processElementUsages false
                count++
                count < 1000 // Limit to prevent performance issues
            }, options)
        } catch (_: Exception) {
            // Fail gracefully
        }

        return count
    }

    private fun countFileReferences(dartClass: DartClass, indicator: ProgressIndicator): Int {
        if (indicator.isCanceled) return 0

        val componentName = dartClass.componentName ?: return 0
        val files = mutableSetOf<String>()

        try {
            val handler = DartServerFindUsagesHandler(dartClass)
            val options = FindUsagesOptions(GlobalSearchScope.projectScope(project))
            options.isUsages = true

            handler.processElementUsages(componentName, { usage ->
                if (indicator.isCanceled) return@processElementUsages false
                usage.element?.containingFile?.virtualFile?.path?.let { files.add(it) }
                files.size < 500 // Limit to prevent performance issues
            }, options)
        } catch (_: Exception) {
            // Fail gracefully
        }

        return files.size
    }

    private fun updateStatusLabel(widgetCount: Int) {
        val timeStr = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(lastUpdateTime))

        statusLabel.text = "Found $widgetCount widgets • Last updated: $timeStr"
    }

    private fun openWidgetFile(row: Int) {
        val widgetInfo = tableModel.getWidgetAt(row) ?: return
        val virtualFile = com.intellij.openapi.vfs.LocalFileSystem.getInstance()
            .findFileByPath(widgetInfo.filePath) ?: return

        FileEditorManager.getInstance(project).openFile(virtualFile, true)
    }

    // Actions
    inner class RefreshAction : AnAction("Refresh", "Refresh widget usage data", AllIcons.Actions.Refresh) {
        override fun actionPerformed(e: AnActionEvent) {
            refreshData()
        }
    }

    inner class ExportAction : AnAction("Export", "Export to CSV", AllIcons.Actions.Download) {
        override fun actionPerformed(e: AnActionEvent) {
            exportToCsv()
        }
    }

    inner class FilterCustomOnlyAction : ToggleAction("Custom Widgets Only", "Show only custom widgets", AllIcons.Actions.Show) {
        private var enabled = false

        override fun isSelected(e: AnActionEvent): Boolean = enabled

        override fun setSelected(e: AnActionEvent, state: Boolean) {
            enabled = state
            tableModel.setFilterCustomOnly(state)
        }

        override fun getActionUpdateThread(): ActionUpdateThread {
            return ActionUpdateThread.EDT
        }
    }

    inner class FilterHighUsageAction : ToggleAction("High Usage Only", "Show widgets with >10 usages", AllIcons.General.Filter) {
        private var enabled = false

        override fun isSelected(e: AnActionEvent): Boolean = enabled

        override fun setSelected(e: AnActionEvent, state: Boolean) {
            enabled = state
            tableModel.setFilterHighUsage(state)
        }

        override fun getActionUpdateThread(): ActionUpdateThread {
            return ActionUpdateThread.EDT
        }
    }

    private fun exportToCsv() {
        val chooser = com.intellij.openapi.fileChooser.FileChooserFactory.getInstance()
            .createSaveFileDialog(
                com.intellij.openapi.fileChooser.FileSaverDescriptor(
                    "Export Widget Heatmap",
                    "Export widget usage data to CSV",
                    "csv"
                ),
                project
            )

        val result = chooser.save(null as com.intellij.openapi.vfs.VirtualFile?, "widget_heatmap.csv")
        result?.let { wrapper ->
            val csv = buildString {
                appendLine("Widget Name,Usage Count,File Count,Extends,File Path")
                tableModel.getAllData().forEach { widget ->
                    appendLine("${widget.className},${widget.usageCount},${widget.fileCount},${widget.extendsWidget ?: ""},${widget.filePath}")
                }
            }

            try {
                wrapper.file.writeText(csv)
                statusLabel.text = "Exported to ${wrapper.file.path}"
            } catch (e: Exception) {
                statusLabel.text = "Export failed: ${e.message}"
            }
        }
    }

    // Table Model
    class WidgetHeatmapTableModel : AbstractTableModel() {
        private var allData: List<WidgetUsageInfo> = emptyList()
        private var filteredData: List<WidgetUsageInfo> = emptyList()
        private var filterCustomOnly = false
        private var filterHighUsage = false

        private val columnNames = arrayOf("Widget Name", "Usage Count", "File Count", "Heatmap", "Extends", "File Path")

        override fun getRowCount(): Int = filteredData.size
        override fun getColumnCount(): Int = columnNames.size
        override fun getColumnName(column: Int): String = columnNames[column]

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any? {
            val widget = filteredData[rowIndex]
            return when (columnIndex) {
                0 -> widget.className
                1 -> widget.usageCount
                2 -> widget.fileCount
                3 -> widget.usageCount // For heatmap visualization
                4 -> widget.extendsWidget ?: ""
                5 -> widget.filePath
                else -> null
            }
        }

        override fun getColumnClass(columnIndex: Int): Class<*> {
            return when (columnIndex) {
                1, 2, 3 -> Int::class.java
                else -> String::class.java
            }
        }

        fun setData(data: List<WidgetUsageInfo>) {
            allData = data.sortedByDescending { it.usageCount }
            applyFilters()
        }

        fun setFilterCustomOnly(enabled: Boolean) {
            filterCustomOnly = enabled
            applyFilters()
        }

        fun setFilterHighUsage(enabled: Boolean) {
            filterHighUsage = enabled
            applyFilters()
        }

        private fun applyFilters() {
            filteredData = allData
                .let { if (filterCustomOnly) it.filter { w -> w.isCustomWidget } else it }
                .let { if (filterHighUsage) it.filter { w -> w.usageCount > 10 } else it }

            fireTableDataChanged()
        }

        fun getWidgetAt(row: Int): WidgetUsageInfo? {
            return if (row >= 0 && row < filteredData.size) filteredData[row] else null
        }

        fun getAllData(): List<WidgetUsageInfo> = allData
    }

    // Custom Cell Renderer with Heatmap Visualization
    class HeatmapCellRenderer : DefaultTableCellRenderer() {
        override fun getTableCellRendererComponent(
            table: JTable?,
            value: Any?,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int
        ): Component {
            val component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)

            if (table != null && column == 3) { // Heatmap column
                val usageCount = value as? Int ?: 0
                val maxUsage = (0 until table.rowCount)
                    .mapNotNull { table.getValueAt(it, 3) as? Int }
                    .maxOrNull() ?: 1

                // Create heat color: green (low) -> yellow -> red (high)
                val intensity = (usageCount.toFloat() / maxUsage).coerceIn(0f, 1f)
                val heatColor = getHeatColor(intensity)

                // Create a panel with colored bar
                return JPanel(BorderLayout()).apply {
                    isOpaque = true
                    background = if (isSelected) table.selectionBackground else table.background

                    val barPanel = JPanel().apply {
                        isOpaque = true
                        background = heatColor
                        preferredSize = Dimension((intensity * 60).toInt(), 20)
                    }

                    val label = JLabel(usageCount.toString(), CENTER).apply {
                        foreground = if (isSelected) table.selectionForeground else table.foreground
                    }

                    add(barPanel, BorderLayout.WEST)
                    add(label, BorderLayout.CENTER)
                }
            }

            return component
        }

        private fun getHeatColor(intensity: Float): JBColor {
            return when {
                intensity < 0.33f -> {
                    // Green to Yellow (light theme) / Darker Green to Yellow (dark theme)
                    val factor = intensity / 0.33f
                    JBColor(
                        Color(
                            (0x4C + (0xFF - 0x4C) * factor).toInt(),
                            (0xAF + (0xFF - 0xAF) * factor).toInt(),
                            (0x50 + (0x00 - 0x50) * factor).toInt()
                        ),
                        Color(
                            (0x3A + (0xCC - 0x3A) * factor).toInt(),
                            (0x8C + (0xCC - 0x8C) * factor).toInt(),
                            (0x3C + (0x00 - 0x3C) * factor).toInt()
                        )
                    )
                }
                intensity < 0.66f -> {
                    // Yellow to Orange (light theme) / Darker Yellow to Orange (dark theme)
                    val factor = (intensity - 0.33f) / 0.33f
                    JBColor(
                        Color(
                            0xFF,
                            (0xFF + (0xA5 - 0xFF) * factor).toInt(),
                            0x00
                        ),
                        Color(
                            0xCC,
                            (0xCC + (0x88 - 0xCC) * factor).toInt(),
                            0x00
                        )
                    )
                }
                else -> {
                    // Orange to Red (light theme) / Darker Orange to Red (dark theme)
                    val factor = (intensity - 0.66f) / 0.34f
                    JBColor(
                        Color(
                            0xFF,
                            (0xA5 + (0x00 - 0xA5) * factor).toInt(),
                            0x00
                        ),
                        Color(
                            0xCC,
                            (0x88 + (0x00 - 0x88) * factor).toInt(),
                            0x00
                        )
                    )
                }
            }
        }
    }
}