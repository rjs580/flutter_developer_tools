package dev.rutvik.flutter_developer_tools.toolWindow

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.jetbrains.lang.dart.psi.*
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Visual Call Graph Tool Window
 *
 * Shows function/method call relationships in the current Dart file
 * to help understand control flow.
 */
class CallGraphWindow(private val project: Project) {

    private val panel = SimpleToolWindowPanel(true, true)
    private val graphPanel: CallGraphPanel
    private val statusLabel: JBLabel
    private var lastUpdateTime: Long = 0
    private var currentFile: DartFile? = null
    private var autoRefresh = true

    data class CallNode(
        val name: String,
        val element: PsiElement,
        val type: NodeType,
        val calledBy: MutableSet<String> = mutableSetOf(),
        val calls: MutableSet<String> = mutableSetOf(),
        var level: Int = 0  // Changed from 'val' to 'var'
    )

    enum class NodeType {
        FUNCTION,
        METHOD,
        CONSTRUCTOR,
        GETTER,
        SETTER,
        LAMBDA
    }

    init {
        graphPanel = CallGraphPanel()

        statusLabel = JBLabel("Open a Dart file to see the call graph").apply {
            border = JBUI.Borders.empty(5, 10)
            foreground = JBColor.GRAY
        }

        setupToolbar()
        setupContent()
        subscribeToFileChanges()

        // Initial load if a Dart file is already open
        FileEditorManager.getInstance(project).selectedEditor?.file?.let { file ->
            if (file.extension == "dart") {
                loadCurrentFile()
            }
        }
    }

    private fun setupToolbar() {
        val actionGroup = DefaultActionGroup().apply {
            add(RefreshAction())
            add(ZoomInAction())
            add(ZoomOutAction())
            add(ResetZoomAction())
            addSeparator()
            add(ToggleAutoRefreshAction())
            add(ExportAction())
            addSeparator()
            add(ShowTopLevelOnlyAction())
            add(ShowMethodsOnlyAction())
        }

        val toolbar = ActionManager.getInstance()
            .createActionToolbar("CallGraphToolbar", actionGroup, true)
        toolbar.targetComponent = panel
        panel.toolbar = toolbar.component
    }

    private fun setupContent() {
        val scrollPane = JBScrollPane(graphPanel)

        val mainPanel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            add(scrollPane, BorderLayout.CENTER)
            add(statusLabel, BorderLayout.SOUTH)
        }

        panel.setContent(mainPanel)
    }

    private fun subscribeToFileChanges() {
        project.messageBus.connect().subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun selectionChanged(event: FileEditorManagerEvent) {
                    if (autoRefresh) {
                        event.newFile?.let { file ->
                            if (file.extension == "dart") {
                                loadCurrentFile()
                            }
                        }
                    }
                }
            }
        )
    }

    fun getContent(): JComponent = panel

    private fun loadCurrentFile() {
        val editor = FileEditorManager.getInstance(project).selectedEditor ?: return
        val file = editor.file ?: return

        if (file.extension != "dart") return

        statusLabel.text = "Analyzing ${file.name}..."

        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            "Building call graph",
            false
        ) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                indicator.text = "Analyzing function calls..."

                val nodes = mutableMapOf<String, CallNode>()

                ReadAction.run<Exception> {
                    val psiFile = PsiManager.getInstance(project).findFile(file)
                    if (psiFile is DartFile) {
                        currentFile = psiFile
                        analyzeDartFile(psiFile, nodes)
                    }
                }

                lastUpdateTime = System.currentTimeMillis()

                ApplicationManager.getApplication().invokeLater {
                    graphPanel.setNodes(nodes)
                    updateStatusLabel(file.name, nodes.size)
                }
            }
        })
    }

    private fun analyzeDartFile(dartFile: DartFile, nodes: MutableMap<String, CallNode>) {
        // Find all functions, methods, getters, setters
        val functions = PsiTreeUtil.findChildrenOfType(dartFile, DartFunctionDeclarationWithBody::class.java)
        val methods = PsiTreeUtil.findChildrenOfType(dartFile, DartMethodDeclaration::class.java)
        val getters = PsiTreeUtil.findChildrenOfType(dartFile, DartGetterDeclaration::class.java)
        val setters = PsiTreeUtil.findChildrenOfType(dartFile, DartSetterDeclaration::class.java)
        val constructors = PsiTreeUtil.findChildrenOfType(dartFile, DartFactoryConstructorDeclaration::class.java)

        // Add all nodes first
        functions.forEach { function ->
            val name = function.name ?: return@forEach
            nodes[name] = CallNode(name, function, NodeType.FUNCTION)
        }

        methods.forEach { method ->
            val className = (method.parent?.parent as? DartClass)?.name ?: ""
            val methodName = method.name ?: return@forEach
            val fullName = if (className.isNotEmpty()) "$className.$methodName" else methodName
            nodes[fullName] = CallNode(fullName, method, NodeType.METHOD)
        }

        getters.forEach { getter ->
            val name = getter.name ?: return@forEach
            nodes[name] = CallNode(name, getter, NodeType.GETTER)
        }

        setters.forEach { setter ->
            val name = setter.name ?: return@forEach
            nodes[name] = CallNode(name, setter, NodeType.SETTER)
        }

        constructors.forEach { constructor ->
            val name = constructor.name ?: return@forEach
            nodes[name] = CallNode(name, constructor, NodeType.CONSTRUCTOR)
        }

        // Analyze calls within each node
        nodes.values.forEach { node ->
            val callExpressions = PsiTreeUtil.findChildrenOfType(node.element, DartCallExpression::class.java)

            callExpressions.forEach { callExpr ->
                val calledName = callExpr.expression?.text ?: return@forEach

                // Check if it's a call to another node in our graph
                nodes[calledName]?.let { calledNode ->
                    node.calls.add(calledName)
                    calledNode.calledBy.add(node.name)
                }
            }
        }

        // Calculate levels (depth in call tree)
        calculateLevels(nodes)
    }

    private fun calculateLevels(nodes: MutableMap<String, CallNode>) {
        // Find root nodes (not called by anyone in this file)
        val rootNodes = nodes.values.filter { it.calledBy.isEmpty() }

        val visited = mutableSetOf<String>()
        val queue = ArrayDeque<Pair<String, Int>>()

        rootNodes.forEach { queue.add(Pair(it.name, 0)) }

        while (queue.isNotEmpty()) {
            val (nodeName, level) = queue.removeFirst()
            if (nodeName in visited) continue

            visited.add(nodeName)
            nodes[nodeName]?.let { node ->
                node.level = level
                node.calls.forEach { calledName ->
                    if (calledName !in visited) {
                        queue.add(Pair(calledName, level + 1))
                    }
                }
            }
        }
    }

    private fun updateStatusLabel(fileName: String, nodeCount: Int) {
        val timeStr = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(lastUpdateTime))

        statusLabel.text = "$fileName • $nodeCount nodes • Last updated: $timeStr"
    }

    // Actions
    inner class RefreshAction : AnAction("Refresh", "Refresh call graph", AllIcons.Actions.Refresh) {
        override fun actionPerformed(e: AnActionEvent) {
            loadCurrentFile()
        }
    }

    inner class ZoomInAction : AnAction("Zoom In", "Zoom in", AllIcons.General.ZoomIn) {
        override fun actionPerformed(e: AnActionEvent) {
            graphPanel.zoomIn()
        }
    }

    inner class ZoomOutAction : AnAction("Zoom Out", "Zoom out", AllIcons.General.ZoomOut) {
        override fun actionPerformed(e: AnActionEvent) {
            graphPanel.zoomOut()
        }
    }

    inner class ResetZoomAction : AnAction("Reset Zoom", "Reset zoom to 100%", AllIcons.General.ActualZoom) {
        override fun actionPerformed(e: AnActionEvent) {
            graphPanel.resetZoom()
        }
    }

    inner class ToggleAutoRefreshAction : ToggleAction("Auto-Refresh", "Automatically refresh when file changes", AllIcons.Actions.ForceRefresh) {
        override fun isSelected(e: AnActionEvent): Boolean = autoRefresh

        override fun setSelected(e: AnActionEvent, state: Boolean) {
            autoRefresh = state
        }

        override fun getActionUpdateThread(): ActionUpdateThread {
            return ActionUpdateThread.EDT
        }
    }

    inner class ExportAction : AnAction("Export", "Export graph to PNG", AllIcons.Actions.Download) {
        override fun actionPerformed(e: AnActionEvent) {
            exportToPng()
        }
    }

    inner class ShowTopLevelOnlyAction : ToggleAction("Top Level Only", "Show only top-level functions", AllIcons.Actions.Show) {
        private var enabled = false

        override fun isSelected(e: AnActionEvent): Boolean = enabled

        override fun setSelected(e: AnActionEvent, state: Boolean) {
            enabled = state
            graphPanel.setShowTopLevelOnly(state)
        }

        override fun getActionUpdateThread(): ActionUpdateThread {
            return ActionUpdateThread.EDT
        }
    }

    inner class ShowMethodsOnlyAction : ToggleAction("Methods Only", "Show only class methods", AllIcons.Actions.Show) {
        private var enabled = false

        override fun isSelected(e: AnActionEvent): Boolean = enabled

        override fun setSelected(e: AnActionEvent, state: Boolean) {
            enabled = state
            graphPanel.setShowMethodsOnly(state)
        }

        override fun getActionUpdateThread(): ActionUpdateThread {
            return ActionUpdateThread.EDT
        }
    }

    private fun exportToPng() {
        val chooser = com.intellij.openapi.fileChooser.FileChooserFactory.getInstance()
            .createSaveFileDialog(
                com.intellij.openapi.fileChooser.FileSaverDescriptor(
                    "Export Call Graph",
                    "Export call graph to PNG",
                    "png"
                ),
                project
            )

        val result = chooser.save(null as com.intellij.openapi.vfs.VirtualFile?, "call_graph.png")
        result?.let { wrapper ->
            try {
                graphPanel.exportToPng(wrapper.file.toPath())
                statusLabel.text = "Exported to ${wrapper.file.path}"
            } catch (e: Exception) {
                statusLabel.text = "Export failed: ${e.message}"
            }
        }
    }

    // Custom Panel for Drawing the Graph
    inner class CallGraphPanel : JPanel() {
        private var nodes: Map<String, CallNode> = emptyMap()
        private var filteredNodes: Map<String, CallNode> = emptyMap()
        private var nodePositions: Map<String, Point> = emptyMap()
        private var scale = 1.0
        private var showTopLevelOnly = false
        private var showMethodsOnly = false

        private val nodeWidth = 150
        private val nodeHeight = 40
        private val levelGap = 120
        private val nodeGap = 60

        init {
            background = JBColor.WHITE
            isOpaque = true

            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    // Find clicked node and navigate to it
                    val scaledPoint = Point(
                        (e.x / scale).toInt(),
                        (e.y / scale).toInt()
                    )

                    val entry: Map.Entry<String, Point>? = nodePositions.entries.find { (_, pos) ->
                        scaledPoint.x in pos.x..(pos.x + nodeWidth) &&
                                scaledPoint.y in pos.y..(pos.y + nodeHeight)
                    }

                    entry?.let { (nodeName, _) ->
                        nodes[nodeName]?.element?.let { element ->
                            // Navigate using Navigatable interface
                            (element as? Navigatable)?.navigate(true)
                        }
                    }
                }
            })
        }

        fun setNodes(newNodes: Map<String, CallNode>) {
            nodes = newNodes
            applyFilters()
            calculateLayout()
            revalidate()
            repaint()
        }

        fun setShowTopLevelOnly(enabled: Boolean) {
            showTopLevelOnly = enabled
            applyFilters()
            calculateLayout()
            repaint()
        }

        fun setShowMethodsOnly(enabled: Boolean) {
            showMethodsOnly = enabled
            applyFilters()
            calculateLayout()
            repaint()
        }

        private fun applyFilters() {
            filteredNodes = nodes
                .let { if (showTopLevelOnly) it.filter { (_, node) -> node.level == 0 } else it }
                .let { if (showMethodsOnly) it.filter { (_, node) -> node.type == NodeType.METHOD } else it }
        }

        private fun calculateLayout() {
            if (filteredNodes.isEmpty()) {
                nodePositions = emptyMap()
                return
            }

            val positions = mutableMapOf<String, Point>()
            val levels = filteredNodes.values.groupBy { it.level }

            var maxWidth = 0
            var maxHeight = 0

            levels.forEach { (level, nodesAtLevel) ->
                val y = level * levelGap + 50
                val totalWidth = nodesAtLevel.size * (nodeWidth + nodeGap)
                var x = max(50, (width - totalWidth) / 2)

                nodesAtLevel.forEach { node ->
                    positions[node.name] = Point(x, y)
                    x += nodeWidth + nodeGap

                    maxWidth = max(maxWidth, x + nodeWidth + 50)
                    maxHeight = max(maxHeight, y + nodeHeight + 50)
                }
            }

            nodePositions = positions
            preferredSize = Dimension(
                (maxWidth * scale).toInt(),
                (maxHeight * scale).toInt()
            )
        }

        fun zoomIn() {
            scale = (scale * 1.2).coerceAtMost(3.0)
            preferredSize = Dimension(
                (preferredSize.width * 1.2).toInt(),
                (preferredSize.height * 1.2).toInt()
            )
            revalidate()
            repaint()
        }

        fun zoomOut() {
            scale = (scale / 1.2).coerceAtLeast(0.3)
            preferredSize = Dimension(
                (preferredSize.width / 1.2).toInt(),
                (preferredSize.height / 1.2).toInt()
            )
            revalidate()
            repaint()
        }

        fun resetZoom() {
            scale = 1.0
            calculateLayout()
            revalidate()
            repaint()
        }

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.scale(scale, scale)

            if (filteredNodes.isEmpty()) {
                g2.color = JBColor.GRAY
                g2.drawString("No call graph to display", 20, 30)
                return
            }

            // Draw connections first
            g2.color = JBColor.GRAY
            g2.stroke = BasicStroke(1.5f)

            filteredNodes.forEach { (nodeName, node) ->
                val fromPos = nodePositions[nodeName] ?: return@forEach

                node.calls.forEach { calledName ->
                    if (calledName in filteredNodes) {
                        val toPos = nodePositions[calledName] ?: return@forEach

                        val fromX = fromPos.x + nodeWidth / 2
                        val fromY = fromPos.y + nodeHeight
                        val toX = toPos.x + nodeWidth / 2
                        val toY = toPos.y

                        // Draw arrow
                        g2.drawLine(fromX, fromY, toX, toY)

                        // Draw arrowhead
                        val angle = atan2((toY - fromY).toDouble(), (toX - fromX).toDouble())
                        val arrowSize = 8
                        val x1 = toX - arrowSize * cos(angle - Math.PI / 6)
                        val y1 = toY - arrowSize * sin(angle - Math.PI / 6)
                        val x2 = toX - arrowSize * cos(angle + Math.PI / 6)
                        val y2 = toY - arrowSize * sin(angle + Math.PI / 6)

                        g2.drawLine(toX, toY, x1.toInt(), y1.toInt())
                        g2.drawLine(toX, toY, x2.toInt(), y2.toInt())
                    }
                }
            }

            // Draw nodes
            filteredNodes.forEach { (nodeName, node) ->
                val pos = nodePositions[nodeName] ?: return@forEach

                // Node background color based on type
                g2.color = when (node.type) {
                    NodeType.FUNCTION -> JBColor(Color(200, 230, 255), Color(70, 100, 140))
                    NodeType.METHOD -> JBColor(Color(255, 230, 200), Color(140, 100, 70))
                    NodeType.CONSTRUCTOR -> JBColor(Color(230, 255, 200), Color(100, 140, 70))
                    NodeType.GETTER -> JBColor(Color(255, 240, 200), Color(140, 120, 70))
                    NodeType.SETTER -> JBColor(Color(255, 200, 240), Color(140, 70, 120))
                    NodeType.LAMBDA -> JBColor(Gray._230, Gray._100)
                }

                g2.fillRoundRect(pos.x, pos.y, nodeWidth, nodeHeight, 10, 10)

                // Node border
                g2.color = JBColor.BLACK
                g2.stroke = BasicStroke(2f)
                g2.drawRoundRect(pos.x, pos.y, nodeWidth, nodeHeight, 10, 10)

                // Node text
                g2.color = JBColor.BLACK
                val fm = g2.fontMetrics
                val text = if (nodeName.length > 20) nodeName.substring(0, 17) + "..." else nodeName
                val textWidth = fm.stringWidth(text)
                val textX = pos.x + (nodeWidth - textWidth) / 2
                val textY = pos.y + (nodeHeight + fm.ascent) / 2 - 2

                g2.drawString(text, textX, textY)

                // Type label
                g2.font = g2.font.deriveFont(9f)
                g2.color = JBColor.GRAY
                val typeText = node.type.name.lowercase()
                val typeWidth = g2.fontMetrics.stringWidth(typeText)
                g2.drawString(typeText, pos.x + (nodeWidth - typeWidth) / 2, pos.y + nodeHeight - 5)
            }
        }

        fun exportToPng(path: java.nio.file.Path) {
            val bufferedImage = com.intellij.util.ui.ImageUtil.createImage(
                graphPanel.graphics,
                preferredSize.width,
                preferredSize.height,
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