package dev.rutvik.flutter_developer_tools.toolWindow

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
import com.jetbrains.lang.dart.psi.DartClass
import com.jetbrains.lang.dart.psi.DartFile
import com.jetbrains.lang.dart.psi.DartReferenceExpression
import java.awt.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.swing.*
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableRowSorter

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
    private val tableSorter: TableRowSorter<WidgetHeatmapTableModel>
    private val statusLabel: JBLabel
    private val emptyStateLabel: JBLabel
    private var lastUpdateTime: Long = 0

    data class WidgetUsageInfo(
        val className: String,
        val usageCount: Int,
        val fileCount: Int,
        val filePath: String?,  // Null for Flutter framework widgets
        val isCustomWidget: Boolean,
        val extendsWidget: String? = null
    )

    init {
        tableModel = WidgetHeatmapTableModel()
        tableSorter = TableRowSorter(tableModel)

        table = JBTable(tableModel).apply {
            rowSorter = tableSorter
            fillsViewportHeight = true
            rowHeight = 36
            showVerticalLines = false
            showHorizontalLines = true
            gridColor = JBColor.border()
            intercellSpacing = Dimension(0, 1)

            // Set renderers for specific columns
            columnModel.getColumn(0).cellRenderer = WidgetNameRenderer()
            columnModel.getColumn(1).cellRenderer = NumberRenderer()
            columnModel.getColumn(2).cellRenderer = NumberRenderer()
            columnModel.getColumn(3).cellRenderer = HeatmapBarRenderer()
            columnModel.getColumn(4).cellRenderer = TypeRenderer()
            columnModel.getColumn(5).cellRenderer = PathRenderer()

            // Column widths
            columnModel.getColumn(0).preferredWidth = 200 // Widget Name
            columnModel.getColumn(1).preferredWidth = 80  // Usage Count
            columnModel.getColumn(2).preferredWidth = 80  // File Count
            columnModel.getColumn(3).preferredWidth = 150 // Heatmap
            columnModel.getColumn(4).preferredWidth = 150 // Extends
            columnModel.getColumn(5).preferredWidth = 300 // File Path

            // Double-click to open file
            addMouseListener(object : java.awt.event.MouseAdapter() {
                override fun mouseClicked(e: java.awt.event.MouseEvent) {
                    if (e.clickCount == 2) {
                        val viewRow = rowAtPoint(e.point)
                        if (viewRow >= 0) {
                            val modelRow = convertRowIndexToModel(viewRow)
                            openWidgetFile(modelRow)
                        }
                    }
                }
            })
        }

        // Empty state label shown in center when no data
        emptyStateLabel = JBLabel("Click refresh to analyze widgets", SwingConstants.CENTER).apply {
            font = font.deriveFont(Font.BOLD, 16f)
            foreground = JBColor.GRAY
            isVisible = true
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

        // Use LayeredPane to show empty state label centered over table
        val layeredPane = JLayeredPane().apply {
            layout = object : LayoutManager {
                override fun addLayoutComponent(name: String?, comp: Component?) {}
                override fun removeLayoutComponent(comp: Component?) {}
                override fun preferredLayoutSize(parent: Container?): Dimension = Dimension(400, 300)
                override fun minimumLayoutSize(parent: Container?): Dimension = Dimension(200, 150)

                override fun layoutContainer(parent: Container?) {
                    parent ?: return
                    val bounds = parent.bounds
                    scrollPane.setBounds(0, 0, bounds.width, bounds.height)

                    // Center the empty state label
                    val labelWidth = 300
                    val labelHeight = 30
                    emptyStateLabel.setBounds(
                        (bounds.width - labelWidth) / 2,
                        (bounds.height - labelHeight) / 2,
                        labelWidth,
                        labelHeight
                    )
                }
            }

            add(scrollPane, JLayeredPane.DEFAULT_LAYER)
            add(emptyStateLabel, JLayeredPane.PALETTE_LAYER)
        }

        val mainPanel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            add(layeredPane, BorderLayout.CENTER)
            add(statusLabel, BorderLayout.SOUTH)
        }

        panel.setContent(mainPanel)
    }

    fun getContent(): JComponent = panel

    private fun refreshData() {
        statusLabel.text = "Analyzing widgets..."
        emptyStateLabel.isVisible = false

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

                    // Step 1: Find all custom widget classes
                    dartFiles.forEachIndexed { index, dartFile ->
                        if (indicator.isCanceled) return@forEachIndexed

                        indicator.fraction = (index.toDouble() / totalFiles) * 0.5
                        indicator.text = "Finding widgets in ${dartFile.name}..."

                        val classes = PsiTreeUtil.findChildrenOfType(dartFile, DartClass::class.java)

                        classes.forEach { dartClass ->
                            if (indicator.isCanceled) return@forEach

                            val className = dartClass.name ?: return@forEach
                            val superClass = dartClass.superClass?.text
                            val isWidget = superClass != null && (
                                    superClass.contains("Widget") ||
                                            superClass.contains("State<")
                                    )

                            if (isWidget) {
                                widgetUsages[className] = WidgetUsageInfo(
                                    className = className,
                                    usageCount = 0,  // Will count later
                                    fileCount = 0,   // Will count later
                                    filePath = dartFile.virtualFile.path,
                                    isCustomWidget = true,
                                    extendsWidget = superClass
                                )
                            }
                        }
                    }

                    // Step 2: Count all widget usages (custom + Flutter widgets)
                    val allWidgetReferences = mutableMapOf<String, MutableMap<String, Int>>()

                    dartFiles.forEachIndexed { index, dartFile ->
                        if (indicator.isCanceled) return@forEachIndexed

                        indicator.fraction = 0.5 + (index.toDouble() / totalFiles) * 0.5
                        indicator.text = "Counting widget usage in ${dartFile.name}..."

                        // Count widget references per file
                        val widgetCountInFile = mutableMapOf<String, Int>()

                        // Find all reference expressions (widget instantiations)
                        val references = PsiTreeUtil.findChildrenOfType(dartFile, DartReferenceExpression::class.java)

                        references.forEach { ref ->
                            val refText = ref.text
                            // Check if it looks like a widget (starts with uppercase)
                            if (refText.isNotEmpty() && refText[0].isUpperCase()) {
                                widgetCountInFile[refText] = widgetCountInFile.getOrDefault(refText, 0) + 1
                            }
                        }

                        // Store counts per file
                        widgetCountInFile.forEach { (widgetName, count) ->
                            val fileMap = allWidgetReferences.getOrPut(widgetName) { mutableMapOf() }
                            fileMap[dartFile.virtualFile.path] = count
                        }
                    }

                    // Step 3: Update usage counts
                    allWidgetReferences.forEach { (widgetName, filesMap) ->
                        if (indicator.isCanceled) return@forEach

                        val fileCount = filesMap.size
                        val usageCount = filesMap.values.sum() // Total count across all files

                        if (widgetName in widgetUsages) {
                            // Update custom widget
                            val existing = widgetUsages[widgetName]!!
                            widgetUsages[widgetName] = existing.copy(
                                usageCount = usageCount,
                                fileCount = fileCount
                            )
                        } else if (fileCount >= 2) {
                            // Add Flutter/external widget if used in multiple places
                            widgetUsages[widgetName] = WidgetUsageInfo(
                                className = widgetName,
                                usageCount = usageCount,
                                fileCount = fileCount,
                                filePath = null,  // No source file for Flutter widgets
                                isCustomWidget = false,
                                extendsWidget = "Flutter Widget"
                            )
                        }
                    }
                }

                lastUpdateTime = System.currentTimeMillis()

                ApplicationManager.getApplication().invokeLater {
                    if (indicator.isCanceled) {
                        statusLabel.text = "Analysis canceled"
                        emptyStateLabel.isVisible = tableModel.getRowCount() == 0
                    } else {
                        tableModel.setData(widgetUsages.values.toList())
                        updateStatusLabel(widgetUsages.size)
                        emptyStateLabel.isVisible = widgetUsages.isEmpty()
                    }
                }
            }

            override fun onCancel() {
                ApplicationManager.getApplication().invokeLater {
                    statusLabel.text = "Analysis canceled by user"
                    emptyStateLabel.isVisible = tableModel.getRowCount() == 0
                }
            }
        })
    }

    private fun findAllDartFiles(): List<DartFile> {
        val dartFiles = mutableListOf<DartFile>()
        val psiManager = PsiManager.getInstance(project)
        val scope = GlobalSearchScope.projectScope(project)
        val projectBasePath = project.basePath ?: return emptyList()

        com.intellij.openapi.roots.ProjectFileIndex.getInstance(project)
            .iterateContent { virtualFile ->
                if (virtualFile.extension == "dart" &&
                    scope.contains(virtualFile) &&
                    virtualFile.path.startsWith(projectBasePath) &&
                    virtualFile.path.contains("/lib/") &&
                    !virtualFile.path.contains("/.symlinks/") &&
                    !virtualFile.path.contains("/build/") &&
                    !virtualFile.path.contains("/.dart_tool/") &&
                    !virtualFile.path.contains("/example/")) {

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

    private fun updateStatusLabel(widgetCount: Int) {
        val timeStr = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(lastUpdateTime))

        statusLabel.text = "Found $widgetCount widgets • Last updated: $timeStr"
    }

    private fun openWidgetFile(modelRow: Int) {
        val widgetInfo = tableModel.getWidgetAt(modelRow) ?: return
        val filePath = widgetInfo.filePath ?: return  // Can't open Flutter framework widgets

        val virtualFile = com.intellij.openapi.vfs.LocalFileSystem.getInstance()
            .findFileByPath(filePath) ?: return

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
            emptyStateLabel.isVisible = tableModel.getRowCount() == 0
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
            emptyStateLabel.isVisible = tableModel.getRowCount() == 0
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
                appendLine("Widget Name,Usage Count,File Count,Type,Extends,File Path")
                tableModel.getAllData().forEach { widget ->
                    val type = if (widget.isCustomWidget) "Custom" else "Flutter"
                    appendLine("${widget.className},${widget.usageCount},${widget.fileCount},$type,${widget.extendsWidget ?: ""},${widget.filePath ?: "N/A"}")
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
            if (rowIndex >= filteredData.size) return null

            val widget = filteredData[rowIndex]
            return when (columnIndex) {
                0 -> widget.className
                1 -> widget.usageCount
                2 -> widget.fileCount
                3 -> widget.usageCount // For heatmap visualization
                4 -> widget.extendsWidget ?: ""
                5 -> widget.filePath ?: "(Flutter Widget)"
                else -> null
            }
        }

        override fun getColumnClass(columnIndex: Int): Class<*> {
            return when (columnIndex) {
                1, 2, 3 -> Integer::class.java  // Use Integer instead of Int for proper sorting
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

    // Custom Cell Renderers for better visual appearance

    // Widget Name Renderer - Bold and prominent
    class WidgetNameRenderer : DefaultTableCellRenderer() {
        override fun getTableCellRendererComponent(
            table: JTable?,
            value: Any?,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int
        ): Component {
            val label = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column) as JLabel
            label.font = label.font.deriveFont(Font.BOLD)
            label.border = JBUI.Borders.empty(4, 8)
            return label
        }
    }

    // Number Renderer - Right aligned with styling
    class NumberRenderer : DefaultTableCellRenderer() {
        init {
            horizontalAlignment = RIGHT
        }

        override fun getTableCellRendererComponent(
            table: JTable?,
            value: Any?,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int
        ): Component {
            val label = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column) as JLabel
            label.border = JBUI.Borders.empty(4, 12, 4, 8)
            label.foreground = if (isSelected) table?.selectionForeground else JBColor.foreground()
            return label
        }
    }

    // Type/Extends Renderer - Subtle styling
    class TypeRenderer : DefaultTableCellRenderer() {
        override fun getTableCellRendererComponent(
            table: JTable?,
            value: Any?,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int
        ): Component {
            val label = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column) as JLabel
            label.foreground = if (isSelected) table?.selectionForeground else JBColor.GRAY
            label.border = JBUI.Borders.empty(4, 8)
            return label
        }
    }

    // Path Renderer - Even more subtle
    class PathRenderer : DefaultTableCellRenderer() {
        override fun getTableCellRendererComponent(
            table: JTable?,
            value: Any?,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int
        ): Component {
            val label = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column) as JLabel
            label.foreground = if (isSelected) table?.selectionForeground else JBColor.GRAY.darker()
            label.font = label.font.deriveFont(Font.PLAIN, label.font.size - 1f)
            label.border = JBUI.Borders.empty(4, 8)
            return label
        }
    }

    // Heatmap Bar Renderer - Visual graph
    class HeatmapBarRenderer : DefaultTableCellRenderer() {
        override fun getTableCellRendererComponent(
            table: JTable?,
            value: Any?,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int
        ): Component {
            if (table == null) return super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)

            val usageCount = value as? Int ?: 0
            val maxUsage = (0 until table.rowCount)
                .mapNotNull { table.model.getValueAt(table.convertRowIndexToModel(it), 3) as? Int }
                .maxOrNull() ?: 1

            val validMaxUsage = maxUsage.coerceAtLeast(1)
            val intensity = (usageCount.toFloat() / validMaxUsage).coerceIn(0f, 1f)

            return object : JPanel() {
                init {
                    layout = BorderLayout()
                    isOpaque = true
                    background = if (isSelected) table.selectionBackground else table.background
                    border = JBUI.Borders.empty(4, 8)
                }

                override fun getAccessibleContext(): javax.accessibility.AccessibleContext {
                    if (accessibleContext == null) {
                        accessibleContext = object : AccessibleJPanel() {
                            override fun getAccessibleName(): String {
                                return "Usage heatmap: $usageCount"
                            }

                            override fun getAccessibleDescription(): String {
                                return "Widget usage count is $usageCount"
                            }
                        }
                    }
                    return accessibleContext
                }

                override fun paintComponent(g: Graphics) {
                    super.paintComponent(g)
                    val g2d = g as Graphics2D
                    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                    g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)

                    if (usageCount > 0) {
                        val heatColor = getHeatColor(intensity)
                        val barHeight = height - 8
                        val barY = 4
                        val maxBarWidth = width - 50 // Leave space for text
                        val barWidth = ((intensity * maxBarWidth).toInt()).coerceAtLeast(20)

                        // Draw subtle background
                        g2d.color = if (isSelected) {
                            table.selectionBackground.darker()
                        } else {
                            JBColor.border()
                        }
                        g2d.fillRoundRect(0, barY, maxBarWidth, barHeight, 4, 4)

                        // Draw gradient bar
                        val gradient = GradientPaint(
                            0f, barY.toFloat(),
                            heatColor.brighter().brighter(),
                            barWidth.toFloat(), barY.toFloat(),
                            heatColor
                        )
                        g2d.paint = gradient
                        g2d.fillRoundRect(0, barY, barWidth, barHeight, 4, 4)

                        // Draw text
                        g2d.color = if (isSelected) table.selectionForeground else JBColor.foreground()
                        g2d.font = g2d.font.deriveFont(Font.BOLD, 11f)
                        val text = usageCount.toString()
                        val metrics = g2d.fontMetrics
                        val textX = maxBarWidth + 8
                        val textY = (height + metrics.ascent - metrics.descent) / 2
                        g2d.drawString(text, textX, textY)
                    } else {
                        // Show "0" for zero usage
                        g2d.color = JBColor.GRAY
                        g2d.font = g2d.font.deriveFont(Font.PLAIN, 11f)
                        g2d.drawString("0", 4, (height + g2d.fontMetrics.ascent - g2d.fontMetrics.descent) / 2)
                    }
                }
            }
        }

        private fun getHeatColor(intensity: Float): JBColor {
            return when {
                intensity < 0.25f -> {
                    val factor = intensity / 0.25f
                    JBColor(
                        Color(
                            (76 + (102 - 76) * factor).toInt(),
                            (175 + (204 - 175) * factor).toInt(),
                            (80 + (102 - 80) * factor).toInt()
                        ),
                        Color(
                            (60 + (85 - 60) * factor).toInt(),
                            (140 + (170 - 140) * factor).toInt(),
                            (64 + (85 - 64) * factor).toInt()
                        )
                    )
                }
                intensity < 0.5f -> {
                    val factor = (intensity - 0.25f) / 0.25f
                    JBColor(
                        Color(
                            (102 + (255 - 102) * factor).toInt(),
                            (204 + (220 - 204) * factor).toInt(),
                            (102 + (0 - 102) * factor).toInt()
                        ),
                        Color(
                            (85 + (200 - 85) * factor).toInt(),
                            (170 + (180 - 170) * factor).toInt(),
                            (85 + (0 - 85) * factor).toInt()
                        )
                    )
                }
                intensity < 0.75f -> {
                    val factor = (intensity - 0.5f) / 0.25f
                    JBColor(
                        Color(
                            255,
                            (220 + (165 - 220) * factor).toInt(),
                            0
                        ),
                        Color(
                            200,
                            (180 + (130 - 180) * factor).toInt(),
                            0
                        )
                    )
                }
                else -> {
                    val factor = (intensity - 0.75f) / 0.25f
                    JBColor(
                        Color(
                            255,
                            (165 + (0 - 165) * factor).toInt(),
                            0
                        ),
                        Color(
                            200,
                            (130 + (0 - 130) * factor).toInt(),
                            0
                        )
                    )
                }
            }
        }
    }
}