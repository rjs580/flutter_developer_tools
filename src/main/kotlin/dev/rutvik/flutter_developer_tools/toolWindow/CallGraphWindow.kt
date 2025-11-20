
package dev.rutvik.flutter_developer_tools.toolWindow

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.Alarm
import com.intellij.util.ui.JBUI
import com.jetbrains.lang.dart.psi.*
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.swing.*

/**
 * Visual Hierarchy Tool Window
 *
 * Shows complete containment and call hierarchy with multiple view options.
 */
class CallGraphWindow(private val project: Project) {

    private val panel = SimpleToolWindowPanel(true, true)
    private var graphPanel: HierarchyPanel
    private val statusLabel: JBLabel
    private var lastUpdateTime: Long = 0
    private var maxDepth = 3
    private var autoRefreshEnabled = true
    private var viewMode = ViewMode.VERTICAL_TREE

    @Suppress("UnstableApiUsage")
    private val refreshAlarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, project)
    private val refreshDebounceTime = 300

    data class HierarchyData(
        val centerNode: HierarchyNode,
        val upwardPaths: List<List<HierarchyNode>>,
        val downwardPaths: List<List<HierarchyNode>>
    )

    data class HierarchyNode(
        val name: String,
        val fullPath: String,
        val element: PsiElement?,
        val type: NodeType,
        val isExternal: Boolean = false,
        val isCurrent: Boolean = false
    )

    data class NodeBounds(val node: HierarchyNode, val bounds: Rectangle)

    enum class NodeType {
        CLASS, FUNCTION, METHOD, CONSTRUCTOR, GETTER, SETTER, WIDGET, EXTERNAL
    }

    enum class ViewMode(val displayName: String) {
        VERTICAL_TREE("Vertical Tree"),
        HORIZONTAL_TREE("Horizontal Tree"),
        COMPACT_LIST("Compact List")
    }

    init {
        graphPanel = createPanelForMode(viewMode)
        statusLabel = JBLabel("Place cursor on any element to see its hierarchy").apply {
            border = JBUI.Borders.empty(5, 10)
            foreground = JBColor.GRAY
        }

        setupToolbar()
        setupContent()
        subscribeToEditorChanges()
    }

    private fun createPanelForMode(mode: ViewMode): HierarchyPanel {
        return when (mode) {
            ViewMode.VERTICAL_TREE -> VerticalTreePanel()
            ViewMode.HORIZONTAL_TREE -> HorizontalTreePanel()
            ViewMode.COMPACT_LIST -> CompactListPanel()
        }
    }

    private fun setupToolbar() {
        val depthLabel = JLabel("Depth: ")
        val depthSpinner = JSpinner(SpinnerNumberModel(3, 1, 10, 1)).apply {
            maximumSize = Dimension(60, 25)
            preferredSize = Dimension(60, 25)
            addChangeListener {
                maxDepth = (value as Int)
                if (autoRefreshEnabled) {
                    analyzeCurrentPosition()
                }
            }
        }

        val viewModeCombo = JComboBox(ViewMode.values()).apply {
            selectedItem = viewMode
            addActionListener {
                val newMode = selectedItem as ViewMode
                if (newMode != viewMode) {
                    viewMode = newMode
                    switchViewMode()
                }
            }
        }

        val autoRefreshCheckbox = JCheckBox("Auto-refresh", true).apply {
            addActionListener {
                autoRefreshEnabled = isSelected
                statusLabel.text = if (isSelected) {
                    "Auto-refresh enabled"
                } else {
                    "Auto-refresh disabled - use Refresh button to update"
                }
            }
        }

        val actionGroup = DefaultActionGroup().apply {
            add(RefreshAction())
            add(ExportAction())
        }

        val toolbar = ActionManager.getInstance()
            .createActionToolbar("HierarchyToolbar", actionGroup, true)
        toolbar.targetComponent = panel

        val toolbarPanel = JPanel(BorderLayout()).apply {
            add(toolbar.component, BorderLayout.WEST)
            add(JPanel(FlowLayout(FlowLayout.LEFT, 5, 0)).apply {
                add(JLabel("View: "))
                add(viewModeCombo)
                add(Box.createHorizontalStrut(10))
                add(depthLabel)
                add(depthSpinner)
                add(Box.createHorizontalStrut(15))
                add(autoRefreshCheckbox)
            }, BorderLayout.CENTER)
        }

        panel.toolbar = toolbarPanel
    }

    private fun switchViewMode() {
        val currentData = graphPanel.getData()
        graphPanel = createPanelForMode(viewMode)
        graphPanel.setData(currentData)
        setupContent()
    }

    private fun setupContent() {
        val scrollPane = JBScrollPane(graphPanel)
        val mainPanel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            add(scrollPane, BorderLayout.CENTER)
            add(statusLabel, BorderLayout.SOUTH)
        }
        panel.setContent(mainPanel)
        panel.revalidate()
        panel.repaint()
    }

    private fun subscribeToEditorChanges() {
        val connection = project.messageBus.connect()
        connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
            override fun selectionChanged(event: FileEditorManagerEvent) {
                val editor = event.newEditor
                if (editor is TextEditor && autoRefreshEnabled) {
                    editor.editor.caretModel.addCaretListener(object : CaretListener {
                        override fun caretPositionChanged(event: CaretEvent) {
                            scheduleRefresh()
                        }
                    })
                    scheduleRefresh()
                }
            }
        })
    }

    private fun scheduleRefresh() {
        if (!autoRefreshEnabled) return
        refreshAlarm.cancelAllRequests()
        refreshAlarm.addRequest({ analyzeCurrentPosition() }, refreshDebounceTime)
    }

    fun getContent(): JComponent = panel

    private fun analyzeCurrentPosition() {
        ReadAction.nonBlocking<HierarchyData?> {
            val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return@nonBlocking null
            val file = editor.document.let { PsiDocumentManager.getInstance(project).getPsiFile(it) } as? DartFile
                ?: return@nonBlocking null

            if (!file.virtualFile.path.contains("/lib/")) return@nonBlocking null

            val offset = editor.caretModel.offset
            val element = file.findElementAt(offset) ?: return@nonBlocking null
            val targetElement = findEnclosingElement(element) ?: return@nonBlocking null

            buildHierarchy(targetElement)
        }
            .coalesceBy(this)
            .inSmartMode(project)
            .finishOnUiThread(com.intellij.openapi.application.ModalityState.defaultModalityState()) { hierarchyData ->
                if (hierarchyData != null) {
                    graphPanel.setData(hierarchyData)
                    lastUpdateTime = System.currentTimeMillis()
                    updateStatusLabel(hierarchyData.centerNode.name)
                } else {
                    graphPanel.setData(null)
                    statusLabel.text = "Place cursor on any element to see its hierarchy"
                }
            }
            .submit { runnable -> ApplicationManager.getApplication().executeOnPooledThread(runnable) }
    }

    private fun findEnclosingElement(element: PsiElement): PsiElement? {
        return PsiTreeUtil.getParentOfType(
            element,
            DartMethodDeclaration::class.java,
            DartFunctionDeclarationWithBody::class.java,
            DartFunctionDeclarationWithBodyOrNative::class.java,
            DartGetterDeclaration::class.java,
            DartSetterDeclaration::class.java,
            DartFactoryConstructorDeclaration::class.java,
            DartNamedConstructorDeclaration::class.java,
            DartClass::class.java
        )
    }

    private fun isInProjectLib(element: PsiElement?): Boolean {
        if (element == null) return false
        val path = element.containingFile?.virtualFile?.path ?: return false
        return path.contains("/lib/") && !path.contains("/.pub-cache/") && !path.contains("/packages/")
    }

    private fun buildHierarchy(element: PsiElement): HierarchyData {
        val centerNode = createHierarchyNode(element, isCurrent = true)
        val upwardPaths = buildUpwardPaths(element, maxDepth)
        val downwardPaths = buildDownwardPaths(element, maxDepth)
        return HierarchyData(centerNode, upwardPaths, downwardPaths)
    }

    private fun buildUpwardPaths(element: PsiElement, maxDepth: Int): List<List<HierarchyNode>> {
        val allPaths = mutableListOf<List<HierarchyNode>>()
        val globalVisited = mutableSetOf<PsiElement>()

        fun explore(current: PsiElement, currentPath: List<HierarchyNode>, depth: Int) {
            if (depth >= maxDepth || current in globalVisited) {
                if (currentPath.isNotEmpty()) {
                    allPaths.add(currentPath.reversed())
                }
                return
            }
            globalVisited.add(current)

            val container = getContainingElement(current)
            if (container != null && isInProjectLib(container)) {
                val node = createHierarchyNode(container)
                explore(container, listOf(node) + currentPath, depth + 1)
                return
            }

            val references = ReferencesSearch.search(current, GlobalSearchScope.projectScope(project)).findAll()

            if (references.isEmpty()) {
                if (currentPath.isNotEmpty()) {
                    allPaths.add(currentPath.reversed())
                }
                return
            }

            var foundAny = false
            for (ref in references.take(15)) {
                val refElement = ref.element
                val enclosing = findEnclosingElement(refElement)

                if (enclosing != null && enclosing != current) {
                    foundAny = true
                    if (isInProjectLib(enclosing)) {
                        val node = createHierarchyNode(enclosing)
                        explore(enclosing, listOf(node) + currentPath, depth + 1)
                    } else {
                        val node = createHierarchyNode(enclosing)
                        allPaths.add((listOf(node) + currentPath).reversed())
                    }
                }
            }

            if (!foundAny && currentPath.isNotEmpty()) {
                allPaths.add(currentPath.reversed())
            }
        }

        val currentNode = createHierarchyNode(element, isCurrent = true)
        explore(element, listOf(currentNode), 0)

        return allPaths.distinctBy { path -> path.joinToString("::") { it.fullPath } }.take(20)
    }

    private fun buildDownwardPaths(element: PsiElement, maxDepth: Int): List<List<HierarchyNode>> {
        val allPaths = mutableListOf<List<HierarchyNode>>()
        val globalVisited = mutableSetOf<PsiElement>()

        fun explore(current: PsiElement, currentPath: List<HierarchyNode>, depth: Int) {
            if (depth >= maxDepth || current in globalVisited) {
                if (currentPath.isNotEmpty()) {
                    allPaths.add(currentPath)
                }
                return
            }
            globalVisited.add(current)

            val contained = getContainedElements(current)
            if (contained.isNotEmpty()) {
                contained.take(8).forEach { child ->
                    val node = createHierarchyNode(child)
                    explore(child, currentPath + node, depth + 1)
                }
                return
            }

            val calls = PsiTreeUtil.collectElementsOfType(current, DartCallExpression::class.java)

            if (calls.isEmpty()) {
                if (currentPath.isNotEmpty()) {
                    allPaths.add(currentPath)
                }
                return
            }

            var foundAny = false
            for (call in calls.take(15)) {
                val resolved = call.expression?.reference?.resolve()

                if (resolved != null && resolved != current) {
                    foundAny = true
                    if (isInProjectLib(resolved)) {
                        val node = createHierarchyNode(resolved)
                        explore(resolved, currentPath + node, depth + 1)
                    } else {
                        val node = createHierarchyNode(resolved)
                        allPaths.add(currentPath + node)
                    }
                }
            }

            if (!foundAny && currentPath.isNotEmpty()) {
                allPaths.add(currentPath)
            }
        }

        val currentNode = createHierarchyNode(element, isCurrent = true)
        explore(element, listOf(currentNode), 0)

        return allPaths.distinctBy { path -> path.joinToString("::") { it.fullPath } }.take(20)
    }

    private fun getContainingElement(element: PsiElement): PsiElement? {
        val containingClass = PsiTreeUtil.getParentOfType(element, DartClass::class.java, true)
        if (containingClass != null && containingClass != element) {
            return containingClass
        }

        var current = element.parent
        while (current != null) {
            if ((current is DartFunctionDeclarationWithBody ||
                        current is DartFunctionDeclarationWithBodyOrNative ||
                        current is DartMethodDeclaration) && current != element) {
                return current
            }
            current = current.parent
        }

        return null
    }

    private fun getContainedElements(element: PsiElement): List<PsiElement> {
        val contained = mutableListOf<PsiElement>()

        when (element) {
            is DartClass -> {
                PsiTreeUtil.getChildrenOfTypeAsList(element, DartMethodDeclaration::class.java).forEach { contained.add(it) }
                PsiTreeUtil.getChildrenOfTypeAsList(element, DartGetterDeclaration::class.java).forEach { contained.add(it) }
                PsiTreeUtil.getChildrenOfTypeAsList(element, DartSetterDeclaration::class.java).forEach { contained.add(it) }
                PsiTreeUtil.getChildrenOfTypeAsList(element, DartFactoryConstructorDeclaration::class.java).forEach { contained.add(it) }
                PsiTreeUtil.getChildrenOfTypeAsList(element, DartNamedConstructorDeclaration::class.java).forEach { contained.add(it) }
            }
            is DartFunctionDeclarationWithBody, is DartFunctionDeclarationWithBodyOrNative, is DartMethodDeclaration -> {
                PsiTreeUtil.getChildrenOfTypeAsList(element, DartFunctionDeclarationWithBody::class.java).forEach { contained.add(it) }
            }
        }

        return contained
    }

    private fun createHierarchyNode(element: PsiElement, isCurrent: Boolean = false): HierarchyNode {
        val isExternal = !isInProjectLib(element)
        val fullPath = buildFullPath(element)

        return when (element) {
            is DartClass -> {
                val name = element.name ?: "AnonymousClass"
                val type = if (isWidgetClass(element)) NodeType.WIDGET else NodeType.CLASS
                HierarchyNode(name, fullPath, element, type, isExternal, isCurrent)
            }
            is DartFunctionDeclarationWithBody, is DartFunctionDeclarationWithBodyOrNative -> {
                val name = (element as? PsiNamedElement)?.name ?: "anonymous"
                HierarchyNode(name, fullPath, element, NodeType.FUNCTION, isExternal, isCurrent)
            }
            is DartMethodDeclaration -> {
                val className = PsiTreeUtil.getParentOfType(element, DartClass::class.java)?.name ?: "?"
                val methodName = element.name ?: "anonymous"
                HierarchyNode("$className.$methodName", fullPath, element, NodeType.METHOD, isExternal, isCurrent)
            }
            is DartGetterDeclaration -> {
                val className = PsiTreeUtil.getParentOfType(element, DartClass::class.java)?.name ?: "?"
                val getterName = element.name ?: "getter"
                HierarchyNode("$className.$getterName", fullPath, element, NodeType.GETTER, isExternal, isCurrent)
            }
            is DartSetterDeclaration -> {
                val className = PsiTreeUtil.getParentOfType(element, DartClass::class.java)?.name ?: "?"
                val setterName = element.name ?: "setter"
                HierarchyNode("$className.$setterName", fullPath, element, NodeType.SETTER, isExternal, isCurrent)
            }
            is DartFactoryConstructorDeclaration, is DartNamedConstructorDeclaration -> {
                val className = PsiTreeUtil.getParentOfType(element, DartClass::class.java)?.name ?: "?"
                val constructorName = (element as? PsiNamedElement)?.name ?: className
                HierarchyNode("$className.$constructorName", fullPath, element, NodeType.CONSTRUCTOR, isExternal, isCurrent)
            }
            is PsiNamedElement -> {
                val name = element.name ?: "unknown"
                HierarchyNode(name, fullPath, element, NodeType.EXTERNAL, isExternal, isCurrent)
            }
            else -> HierarchyNode("unknown", fullPath, element, NodeType.EXTERNAL, isExternal, isCurrent)
        }
    }

    private fun buildFullPath(element: PsiElement): String {
        val parts = mutableListOf<String>()
        var current: PsiElement? = element

        while (current != null) {
            when (current) {
                is DartClass -> parts.add(0, current.name ?: "?")
                is DartFunctionDeclarationWithBody -> parts.add(0, current.name ?: "?")
                is DartFunctionDeclarationWithBodyOrNative -> parts.add(0, (current as? PsiNamedElement)?.name ?: "?")
                is DartMethodDeclaration -> parts.add(0, current.name ?: "?")
                is DartGetterDeclaration -> parts.add(0, "get ${current.name ?: "?"}")
                is DartSetterDeclaration -> parts.add(0, "set ${current.name ?: "?"}")
            }
            current = getContainingElement(current)
        }

        return parts.joinToString(" > ")
    }

    private fun isWidgetClass(dartClass: DartClass): Boolean {
        val superClass = dartClass.superClass?.text ?: return false
        return superClass.contains("Widget") || superClass.contains("State")
    }

    private fun updateStatusLabel(functionName: String) {
        val timeStr = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(lastUpdateTime))
        statusLabel.text = "Hierarchy for: $functionName • Depth: $maxDepth • View: ${viewMode.displayName} • Updated: $timeStr"
    }

    inner class RefreshAction : AnAction("Refresh", "Refresh hierarchy", AllIcons.Actions.Refresh) {
        override fun actionPerformed(e: AnActionEvent) {
            analyzeCurrentPosition()
        }
    }

    inner class ExportAction : AnAction("Export", "Export to PNG", AllIcons.Actions.Download) {
        override fun actionPerformed(e: AnActionEvent) {
            val chooser = com.intellij.openapi.fileChooser.FileChooserFactory.getInstance()
                .createSaveFileDialog(
                    com.intellij.openapi.fileChooser.FileSaverDescriptor(
                        "Export Hierarchy", "Export hierarchy to PNG", "png"
                    ), project
                )

            chooser.save(null as com.intellij.openapi.vfs.VirtualFile?, "hierarchy.png")?.let { wrapper ->
                try {
                    graphPanel.exportToPng(wrapper.file.toPath())
                    statusLabel.text = "Exported to ${wrapper.file.path}"
                } catch (e: Exception) {
                    statusLabel.text = "Export failed: ${e.message}"
                }
            }
        }
    }

    // Base interface for all panels
    abstract inner class HierarchyPanel : JPanel() {
        protected var hierarchyData: HierarchyData? = null
        protected val nodeBounds = mutableListOf<NodeBounds>()

        init {
            background = JBColor.background()
            isOpaque = true

            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    nodeBounds.find { it.bounds.contains(e.point) }?.let { nb ->
                        (nb.node.element as? Navigatable)?.navigate(true)
                    }
                }
            })
        }

        fun getData() = hierarchyData

        open fun setData(newData: HierarchyData?) {
            hierarchyData = newData
            nodeBounds.clear()
            revalidate()
            repaint()
        }

        abstract fun exportToPng(path: java.nio.file.Path)

        protected fun wrapText(text: String, metrics: FontMetrics, maxWidth: Int): List<String> {
            val lines = mutableListOf<String>()
            val words = text.split(" ", ".")
            var currentLine = ""

            for (word in words) {
                val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
                if (metrics.stringWidth(testLine) <= maxWidth) {
                    currentLine = testLine
                } else {
                    if (currentLine.isNotEmpty()) {
                        lines.add(currentLine)
                    }
                    currentLine = word
                }
            }

            if (currentLine.isNotEmpty()) {
                lines.add(currentLine)
            }

            return lines.ifEmpty { listOf(text) }
        }

        protected fun drawNodeBox(g2: Graphics2D, node: HierarchyNode, bounds: Rectangle) {
            val bgColor = when {
                node.isCurrent -> JBColor(Color(255, 250, 205), Color(90, 85, 60))
                node.isExternal -> JBColor(Gray._240, Gray._60)
                node.type == NodeType.WIDGET -> JBColor(Color(220, 240, 255), Color(50, 65, 80))
                node.type == NodeType.CLASS -> JBColor(Color(255, 240, 220), Color(70, 65, 50))
                else -> JBColor(Color(235, 245, 255), Color(45, 55, 70))
            }

            g2.color = bgColor
            g2.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 10, 10)

            val borderColor = when {
                node.isCurrent -> JBColor(Color(210, 150, 20), Color(190, 150, 70))
                node.isExternal -> JBColor(Gray._180, Gray._100)
                node.type == NodeType.WIDGET -> JBColor(Color(100, 150, 255), Color(80, 120, 200))
                node.type == NodeType.CLASS -> JBColor(Color(220, 160, 80), Color(180, 130, 60))
                else -> JBColor(Color(120, 170, 255), Color(90, 130, 200))
            }

            g2.color = borderColor
            g2.stroke = BasicStroke(if (node.isCurrent) 3f else 2f)
            g2.drawRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 10, 10)

            g2.color = if (node.isExternal) JBColor(Gray._100, Gray._160) else JBColor(Gray._20, Gray._220)
            g2.font = g2.font.deriveFont(if (node.isCurrent) Font.BOLD else Font.PLAIN, 11f)
            val metrics = g2.fontMetrics
            val lines = wrapText(node.name, metrics, bounds.width - 20)

            var textY = bounds.y + 15 + metrics.ascent
            lines.forEach { line ->
                g2.drawString(line, bounds.x + 10, textY)
                textY += metrics.height + 2
            }

            g2.font = g2.font.deriveFont(Font.ITALIC, 9f)
            val typeText = when {
                node.isCurrent -> "[CURRENT]"
                node.isExternal -> "[external]"
                node.type == NodeType.WIDGET -> "[widget]"
                node.type == NodeType.CLASS -> "[class]"
                else -> ""
            }
            if (typeText.isNotEmpty()) {
                g2.color = if (node.isCurrent) {
                    JBColor(Color(180, 120, 0), Color(180, 140, 60))
                } else {
                    JBColor(Gray._120, Gray._140)
                }
                g2.drawString(typeText, bounds.x + 10, bounds.y + bounds.height - 8)
            }
        }
    }

    // Vertical Tree implementation (continued in next message due to length)
    inner class VerticalTreePanel : HierarchyPanel() {
        private val nodeWidth = 250
        private val nodeMinHeight = 50
        private val verticalSpacing = 25
        private val padding = 30

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

            val hierarchyData = hierarchyData
            if (hierarchyData == null) {
                g2.color = JBColor.GRAY
                g2.font = g2.font.deriveFont(14f)
                val msg = "Place cursor on any element to see its hierarchy"
                val metrics = g2.fontMetrics
                g2.drawString(msg, (width - metrics.stringWidth(msg)) / 2, height / 2)
                return
            }

            nodeBounds.clear()
            var currentY = padding

            if (hierarchyData.upwardPaths.isNotEmpty()) {
                g2.font = g2.font.deriveFont(Font.BOLD, 13f)
                g2.color = JBColor(Color(70, 130, 180), Color(100, 150, 200))
                g2.drawString("↑ Called By / Contains:", padding, currentY)
                currentY += 30

                hierarchyData.upwardPaths.forEach { path ->
                    currentY = drawVerticalPath(g2, path, currentY)
                    currentY += verticalSpacing
                }
            }

            currentY += 20

            if (hierarchyData.downwardPaths.isNotEmpty()) {
                g2.font = g2.font.deriveFont(Font.BOLD, 13f)
                g2.color = JBColor(Color(34, 139, 34), Color(60, 180, 60))
                g2.drawString("↓ Calls / Contains:", padding, currentY)
                currentY += 30

                hierarchyData.downwardPaths.forEach { path ->
                    currentY = drawVerticalPath(g2, path, currentY)
                    currentY += verticalSpacing
                }
            }
        }

        private fun drawVerticalPath(g2: Graphics2D, path: List<HierarchyNode>, startY: Int): Int {
            var currentY = startY
            var currentX = padding + 20

            path.forEachIndexed { index, node ->
                val nodeHeight = calculateNodeHeight(g2, node)
                val bounds = Rectangle(currentX, currentY, nodeWidth, nodeHeight)

                if (index > 0) {
                    val prevBounds = nodeBounds.last().bounds
                    g2.color = JBColor(Gray._150, Gray._120)
                    g2.stroke = BasicStroke(2f)

                    g2.drawLine(prevBounds.x + 20, prevBounds.y + prevBounds.height, prevBounds.x + 20, currentY)
                    g2.drawLine(prevBounds.x + 20, currentY, currentX, currentY + nodeHeight / 2)

                    val arrowSize = 8
                    val xPoints = intArrayOf(currentX, currentX - arrowSize, currentX - arrowSize)
                    val yPoints = intArrayOf(currentY + nodeHeight / 2, currentY + nodeHeight / 2 - arrowSize / 2, currentY + nodeHeight / 2 + arrowSize / 2)
                    g2.fillPolygon(xPoints, yPoints, 3)
                }

                drawNodeBox(g2, node, bounds)
                nodeBounds.add(NodeBounds(node, bounds))

                currentY += nodeHeight + 15
                currentX += 30
            }

            return currentY
        }

        private fun calculateNodeHeight(g2: Graphics2D, node: HierarchyNode): Int {
            g2.font = g2.font.deriveFont(Font.PLAIN, 11f)
            val metrics = g2.fontMetrics
            val lines = wrapText(node.name, metrics, nodeWidth - 20)
            val textHeight = lines.size * (metrics.height + 2)
            val typeHeight = if (node.isCurrent || node.isExternal ||
                node.type == NodeType.WIDGET ||
                node.type == NodeType.CLASS) 14 else 0

            return kotlin.math.max(nodeMinHeight, textHeight + typeHeight + 25)
        }

        override fun getPreferredSize(): Dimension {
            val hierarchyData = hierarchyData ?: return Dimension(900, 700)
            val totalPaths = hierarchyData.upwardPaths.size + hierarchyData.downwardPaths.size
            val estimatedHeight = (totalPaths * 200) + 200
            return Dimension(1200, kotlin.math.max(700, estimatedHeight))
        }

        override fun exportToPng(path: java.nio.file.Path) {
            val bufferedImage = com.intellij.util.ui.ImageUtil.createImage(
                graphics, preferredSize.width, preferredSize.height,
                java.awt.image.BufferedImage.TYPE_INT_RGB
            )

            val g2 = bufferedImage.createGraphics()
            g2.color = JBColor.WHITE
            g2.fillRect(0, 0, bufferedImage.width, bufferedImage.height)
            paint(g2)
            g2.dispose()

            javax.imageio.ImageIO.write(bufferedImage, "png", path.toFile())
        }
    }

    // Horizontal Tree - draws tree left to right
    inner class HorizontalTreePanel : HierarchyPanel() {
        private val nodeWidth = 200
        private val nodeHeight = 60
        private val horizontalSpacing = 80
        private val verticalSpacing = 20
        private val padding = 30

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

            val hierarchyData = hierarchyData
            if (hierarchyData == null) {
                g2.color = JBColor.GRAY
                g2.font = g2.font.deriveFont(14f)
                val msg = "Place cursor on any element to see its hierarchy"
                val metrics = g2.fontMetrics
                g2.drawString(msg, (width - metrics.stringWidth(msg)) / 2, height / 2)
                return
            }

            nodeBounds.clear()
            var currentY = padding

            if (hierarchyData.upwardPaths.isNotEmpty()) {
                g2.font = g2.font.deriveFont(Font.BOLD, 13f)
                g2.color = JBColor(Color(70, 130, 180), Color(100, 150, 200))
                g2.drawString("← Called By", padding, currentY + 20)
                currentY += 40

                hierarchyData.upwardPaths.forEach { path ->
                    drawHorizontalPath(g2, path.reversed(), padding, currentY, false)
                    currentY += nodeHeight + verticalSpacing
                }
            }

            currentY += 30

            if (hierarchyData.downwardPaths.isNotEmpty()) {
                g2.font = g2.font.deriveFont(Font.BOLD, 13f)
                g2.color = JBColor(Color(34, 139, 34), Color(60, 180, 60))
                g2.drawString("Calls →", padding, currentY + 20)
                currentY += 40

                hierarchyData.downwardPaths.forEach { path ->
                    drawHorizontalPath(g2, path, padding, currentY, true)
                    currentY += nodeHeight + verticalSpacing
                }
            }
        }

        private fun drawHorizontalPath(g2: Graphics2D, path: List<HierarchyNode>, startX: Int, startY: Int, drawArrowRight: Boolean) {
            var currentX = startX

            path.forEachIndexed { index, node ->
                val bounds = Rectangle(currentX, startY, nodeWidth, nodeHeight)

                if (index > 0) {
                    val prevBounds = nodeBounds.last().bounds
                    g2.color = JBColor(Gray._150, Gray._120)
                    g2.stroke = BasicStroke(2f)

                    g2.drawLine(prevBounds.x + prevBounds.width, prevBounds.y + prevBounds.height / 2,
                        currentX, startY + nodeHeight / 2)

                    if (drawArrowRight) {
                        val arrowSize = 8
                        val xPoints = intArrayOf(currentX, currentX - arrowSize, currentX - arrowSize)
                        val yPoints = intArrayOf(startY + nodeHeight / 2, startY + nodeHeight / 2 - arrowSize / 2, startY + nodeHeight / 2 + arrowSize / 2)
                        g2.fillPolygon(xPoints, yPoints, 3)
                    }
                }

                drawNodeBox(g2, node, bounds)
                nodeBounds.add(NodeBounds(node, bounds))

                currentX += nodeWidth + horizontalSpacing
            }
        }

        override fun getPreferredSize(): Dimension {
            val hierarchyData = hierarchyData ?: return Dimension(1200, 700)
            val maxPathLength = kotlin.math.max(
                hierarchyData.upwardPaths.maxOfOrNull { it.size } ?: 0,
                hierarchyData.downwardPaths.maxOfOrNull { it.size } ?: 0
            )
            val width = kotlin.math.max(1200, maxPathLength * (nodeWidth + horizontalSpacing) + 200)
            val totalPaths = hierarchyData.upwardPaths.size + hierarchyData.downwardPaths.size
            val height = kotlin.math.max(700, totalPaths * (nodeHeight + verticalSpacing) + 200)
            return Dimension(width, height)
        }

        override fun exportToPng(path: java.nio.file.Path) {
            val bufferedImage = com.intellij.util.ui.ImageUtil.createImage(
                graphics, preferredSize.width, preferredSize.height,
                java.awt.image.BufferedImage.TYPE_INT_RGB
            )

            val g2 = bufferedImage.createGraphics()
            g2.color = JBColor.WHITE
            g2.fillRect(0, 0, bufferedImage.width, bufferedImage.height)
            paint(g2)
            g2.dispose()

            javax.imageio.ImageIO.write(bufferedImage, "png", path.toFile())
        }
    }

    // Compact List - simple text-based list view
    inner class CompactListPanel : HierarchyPanel() {
        private val padding = 20
        private val lineHeight = 25

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

            val hierarchyData = hierarchyData
            if (hierarchyData == null) {
                g2.color = JBColor.GRAY
                g2.font = g2.font.deriveFont(14f)
                val msg = "Place cursor on any element to see its hierarchy"
                val metrics = g2.fontMetrics
                g2.drawString(msg, (width - metrics.stringWidth(msg)) / 2, height / 2)
                return
            }

            nodeBounds.clear()
            var currentY = padding

            g2.font = g2.font.deriveFont(Font.BOLD, 13f)
            g2.color = JBColor(Color(70, 130, 180), Color(100, 150, 200))
            g2.drawString("↑ Called By / Contains:", padding, currentY)
            currentY += 30

            g2.font = g2.font.deriveFont(Font.PLAIN, 11f)
            hierarchyData.upwardPaths.forEach { path ->
                currentY = drawCompactPath(g2, path, currentY)
                currentY += 10
            }

            currentY += 20
            g2.font = g2.font.deriveFont(Font.BOLD, 13f)
            g2.color = JBColor(Color(34, 139, 34), Color(60, 180, 60))
            g2.drawString("↓ Calls / Contains:", padding, currentY)
            currentY += 30

            g2.font = g2.font.deriveFont(Font.PLAIN, 11f)
            hierarchyData.downwardPaths.forEach { path ->
                currentY = drawCompactPath(g2, path, currentY)
                currentY += 10
            }
        }

        private fun drawCompactPath(g2: Graphics2D, path: List<HierarchyNode>, startY: Int): Int {
            var currentY = startY
            val indent = padding + 20

            path.forEachIndexed { index, node ->
                val x = indent + (index * 30)
                val prefix = "  ".repeat(index) + if (index > 0) "→ " else "• "
                val text = prefix + node.name

                g2.color = when {
                    node.isCurrent -> JBColor(Color(180, 120, 0), Color(200, 150, 70))
                    node.isExternal -> JBColor(Gray._120, Gray._140)
                    else -> JBColor(Gray._20, Gray._220)
                }

                g2.font = g2.font.deriveFont(if (node.isCurrent) Font.BOLD else Font.PLAIN, 11f)
                g2.drawString(text, x, currentY)

                val bounds = Rectangle(x, currentY - lineHeight + 5, 400, lineHeight)
                nodeBounds.add(NodeBounds(node, bounds))

                currentY += lineHeight
            }

            return currentY
        }

        override fun getPreferredSize(): Dimension {
            val hierarchyData = hierarchyData ?: return Dimension(900, 600)
            val totalLines = (hierarchyData.upwardPaths.sumOf { it.size } +
                    hierarchyData.downwardPaths.sumOf { it.size }) * lineHeight
            return Dimension(900, kotlin.math.max(600, totalLines + 200))
        }

        override fun exportToPng(path: java.nio.file.Path) {
            val bufferedImage = com.intellij.util.ui.ImageUtil.createImage(
                graphics, preferredSize.width, preferredSize.height,
                java.awt.image.BufferedImage.TYPE_INT_RGB
            )

            val g2 = bufferedImage.createGraphics()
            g2.color = JBColor.WHITE
            g2.fillRect(0, 0, bufferedImage.width, bufferedImage.height)
            paint(g2)
            g2.dispose()

            javax.imageio.ImageIO.write(bufferedImage, "png", path.toFile())
        }
    }
}