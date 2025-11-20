
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
import com.intellij.psi.search.GlobalSearchScope
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
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Visual Call Graph Tool Window
 *
 * Shows function call relationships including:
 * - Direct calls: myFunction()
 * - Lambda/closure calls: onPressed: () { myFunction(); }
 * - Method calls: obj.method()
 * - Calls across the entire project (lib/ folder)
 */
class CallGraphWindow(private val project: Project) {

    private val panel = SimpleToolWindowPanel(true, true)
    private val graphPanel: CallGraphPanel
    private val statusLabel: JBLabel
    private var lastUpdateTime: Long = 0
    private var currentFile: DartFile? = null
    private var autoRefresh = true

    // Debouncing for auto-refresh (wait 500ms after file switch)
    private val refreshAlarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, project)
    private val DEBOUNCE_DELAY_MS = 500

    data class CallNode(
        val name: String,
        val element: PsiElement,
        val type: NodeType,
        val fileName: String,
        val isInCurrentFile: Boolean,
        val calledBy: MutableSet<String> = mutableSetOf(),
        val calls: MutableSet<String> = mutableSetOf(),
        var level: Int = 0
    )

    enum class NodeType {
        FUNCTION,
        METHOD,
        CONSTRUCTOR,
        GETTER,
        SETTER
    }

    init {
        graphPanel = CallGraphPanel()

        statusLabel = JBLabel("Open a Dart file in lib/ to see the call graph").apply {
            border = JBUI.Borders.empty(5, 10)
            foreground = JBColor.GRAY
        }

        setupToolbar()
        setupContent()
        subscribeToFileChanges()

        // Initial load if a Dart file is already open
        FileEditorManager.getInstance(project).selectedEditor?.file?.let { file ->
            if (file.extension == "dart" && file.path.contains("/lib/")) {
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
                            if (file.extension == "dart" && file.path.contains("/lib/")) {
                                // Debounce: cancel previous request and schedule new one
                                refreshAlarm.cancelAllRequests()
                                refreshAlarm.addRequest({
                                    loadCurrentFile()
                                }, DEBOUNCE_DELAY_MS)
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

        if (file.extension != "dart" || !file.path.contains("/lib/")) return

        statusLabel.text = "Analyzing ${file.name}..."

        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            "Building call graph",
            true
        ) {
            override fun run(indicator: ProgressIndicator) {
                indicator.text = "Analyzing function calls..."
                indicator.isIndeterminate = false

                val nodes = mutableMapOf<String, CallNode>()

                ReadAction.run<Exception> {
                    val psiFile = PsiManager.getInstance(project).findFile(file)
                    if (psiFile is DartFile) {
                        currentFile = psiFile
                        analyzeDartFileWithProjectContext(psiFile, nodes, indicator)
                    }
                }

                lastUpdateTime = System.currentTimeMillis()

                ApplicationManager.getApplication().invokeLater {
                    if (!indicator.isCanceled) {
                        graphPanel.setNodes(nodes)
                        updateStatusLabel(file.name, nodes.size)
                    }
                }
            }

            override fun onCancel() {
                ApplicationManager.getApplication().invokeLater {
                    statusLabel.text = "Analysis canceled"
                }
            }
        })
    }

    private fun analyzeDartFileWithProjectContext(
        currentFile: DartFile,
        nodes: MutableMap<String, CallNode>,
        indicator: ProgressIndicator
    ) {
        indicator.text = "Step 1/3: Finding all project functions..."

        // Get project base path
        val projectBasePath = project.basePath ?: return

        // Step 1: Build index of ALL functions in lib/ folder
        val allProjectFunctions = mutableMapOf<String, PsiElement>()
        findAllProjectFunctions(allProjectFunctions, indicator, projectBasePath)

        if (indicator.isCanceled) return

        indicator.text = "Step 2/3: Analyzing current file..."
        indicator.fraction = 0.33

        // Step 2: Analyze functions in current file
        val currentFileElements = mutableMapOf<String, PsiElement>()
        extractFunctionsFromFile(currentFile, currentFileElements, currentFile.name)

        currentFileElements.forEach { (name, element) ->
            if (indicator.isCanceled) return

            nodes[name] = CallNode(
                name = name,
                element = element,
                type = getNodeType(element),
                fileName = currentFile.name,
                isInCurrentFile = true
            )
        }

        if (indicator.isCanceled) return

        indicator.text = "Step 3/3: Tracing call relationships..."
        indicator.fraction = 0.66

        // Step 3: Find what each function calls (including in lambdas/closures)
        nodes.values.forEach { node ->
            if (indicator.isCanceled) return@forEach

            // Find ALL call expressions including nested ones in lambdas
            val allCallExpressions = findAllCallExpressions(node.element)

            allCallExpressions.forEach { callExpr ->
                val calledName = extractCalledFunctionName(callExpr)
                if (calledName != null && allProjectFunctions.containsKey(calledName)) {
                    // Add called function to graph if not already there
                    if (calledName !in nodes) {
                        val calledElement = allProjectFunctions[calledName]!!
                        val calledFile = calledElement.containingFile
                        nodes[calledName] = CallNode(
                            name = calledName,
                            element = calledElement,
                            type = getNodeType(calledElement),
                            fileName = calledFile.name,
                            isInCurrentFile = false
                        )
                    }

                    node.calls.add(calledName)
                    nodes[calledName]?.calledBy?.add(node.name)
                }
            }
        }

        // Also find who calls functions in the current file (from elsewhere in project)
        findCallersOfCurrentFile(currentFile, allProjectFunctions, nodes, indicator)

        indicator.fraction = 1.0

        // Calculate levels for visual hierarchy
        calculateLevels(nodes)
    }

    /**
     * Finds ALL call expressions including those nested in lambdas/closures
     * This is key to detecting: onPressed: () { testFunc(); }
     */
    private fun findAllCallExpressions(element: PsiElement): List<DartCallExpression> {
        return PsiTreeUtil.collectElementsOfType(element, DartCallExpression::class.java).toList()
    }

    private fun findAllProjectFunctions(
        functions: MutableMap<String, PsiElement>,
        indicator: ProgressIndicator,
        projectBasePath: String
    ) {
        val psiManager = PsiManager.getInstance(project)
        val scope = GlobalSearchScope.projectScope(project)

        val dartFiles = mutableListOf<DartFile>()
        com.intellij.openapi.roots.ProjectFileIndex.getInstance(project)
            .iterateContent { virtualFile ->
                if (indicator.isCanceled) return@iterateContent false

                if (virtualFile.extension == "dart" &&
                    scope.contains(virtualFile) &&
                    virtualFile.path.startsWith(projectBasePath) &&
                    virtualFile.path.contains("/lib/") &&
                    !virtualFile.path.contains("/.symlinks/") &&
                    !virtualFile.path.contains("/build/") &&
                    !virtualFile.path.contains("/.dart_tool/") &&
                    !virtualFile.path.contains("/.github/") &&
                    !virtualFile.path.contains("/example/")) {

                    psiManager.findFile(virtualFile)?.let { psiFile ->
                        if (psiFile is DartFile) {
                            dartFiles.add(psiFile)
                        }
                    }
                }
                true
            }

        dartFiles.forEach { dartFile ->
            if (indicator.isCanceled) return
            extractFunctionsFromFile(dartFile, functions, dartFile.name)
        }
    }

    private fun extractFunctionsFromFile(
        dartFile: DartFile,
        functions: MutableMap<String, PsiElement>,
        fileName: String
    ) {
        // Top-level functions
        PsiTreeUtil.findChildrenOfType(dartFile, DartFunctionDeclarationWithBody::class.java).forEach { func ->
            func.name?.let { name ->
                functions["$fileName::$name"] = func
            }
        }

        // Methods in classes
        PsiTreeUtil.findChildrenOfType(dartFile, DartClass::class.java).forEach { dartClass ->
            val className = dartClass.name ?: return@forEach

            PsiTreeUtil.findChildrenOfType(dartClass, DartMethodDeclaration::class.java).forEach { method ->
                method.name?.let { methodName ->
                    functions["$fileName::$className.$methodName"] = method
                }
            }

            // Getters and setters
            PsiTreeUtil.findChildrenOfType(dartClass, DartGetterDeclaration::class.java).forEach { getter ->
                getter.name?.let { name ->
                    functions["$fileName::$className.$name"] = getter
                }
            }

            PsiTreeUtil.findChildrenOfType(dartClass, DartSetterDeclaration::class.java).forEach { setter ->
                setter.name?.let { name ->
                    functions["$fileName::$className.$name"] = setter
                }
            }

            // Constructors
            PsiTreeUtil.findChildrenOfType(dartClass, DartFactoryConstructorDeclaration::class.java).forEach { constructor ->
                constructor.name?.let { name ->
                    functions["$fileName::$className.$name"] = constructor
                }
            }
        }
    }

    private fun extractCalledFunctionName(callExpr: DartCallExpression): String? {
        val expression = callExpr.expression ?: return null

        // Try to resolve to actual declaration
        val reference = expression.reference?.resolve()
        if (reference != null) {
            val fileName = reference.containingFile.name
            val name = when (reference) {
                is DartComponent -> reference.name
                else -> null
            } ?: return null

            // Build qualified name
            val className = (reference.parent?.parent as? DartClass)?.name
            return if (className != null) {
                "$fileName::$className.$name"
            } else {
                "$fileName::$name"
            }
        }

        return null
    }

    private fun findCallersOfCurrentFile(
        currentFile: DartFile,
        allFunctions: Map<String, PsiElement>,
        nodes: MutableMap<String, CallNode>,
        indicator: ProgressIndicator
    ) {
        val currentFileName = currentFile.name
        val currentFileFunctions = nodes.keys.filter { it.startsWith("$currentFileName::") }

        allFunctions.forEach { (funcName, element) ->
            if (indicator.isCanceled) return
            if (funcName.startsWith("$currentFileName::")) return@forEach // Skip current file

            // Find ALL call expressions including in lambdas
            val allCallExpressions = findAllCallExpressions(element)

            allCallExpressions.forEach { callExpr ->
                val calledName = extractCalledFunctionName(callExpr)
                if (calledName != null && calledName in currentFileFunctions) {
                    // This external function calls something in our current file
                    if (funcName !in nodes) {
                        nodes[funcName] = CallNode(
                            name = funcName,
                            element = element,
                            type = getNodeType(element),
                            fileName = element.containingFile.name,
                            isInCurrentFile = false
                        )
                    }

                    nodes[funcName]?.calls?.add(calledName)
                    nodes[calledName]?.calledBy?.add(funcName)
                }
            }
        }
    }

    private fun getNodeType(element: PsiElement): NodeType {
        return when (element) {
            is DartMethodDeclaration -> NodeType.METHOD
            is DartFunctionDeclarationWithBody -> NodeType.FUNCTION
            is DartGetterDeclaration -> NodeType.GETTER
            is DartSetterDeclaration -> NodeType.SETTER
            is DartFactoryConstructorDeclaration -> NodeType.CONSTRUCTOR
            else -> NodeType.FUNCTION
        }
    }

    private fun calculateLevels(nodes: MutableMap<String, CallNode>) {
        // Find root nodes (functions in current file that aren't called by anything)
        val currentFileNodes = nodes.values.filter { it.isInCurrentFile }
        val rootNodes = currentFileNodes.filter { it.calledBy.isEmpty() }

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

        // Handle nodes that call into current file (negative levels - callers from external files)
        val callers = nodes.values.filter { !it.isInCurrentFile && it.calls.any { called -> nodes[called]?.isInCurrentFile == true } }
        callers.forEach { it.level = -1 }
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
        private var nodePositions: Map<String, Point> = emptyMap()
        private var scale = 1.0

        private val nodeWidth = 120
        private val nodeHeight = 28
        private val levelGap = 70
        private val nodeGap = 30

        init {
            background = JBColor.WHITE
            isOpaque = true

            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
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
                            (element as? Navigatable)?.navigate(true)
                        }
                    }
                }
            })
        }

        fun setNodes(newNodes: Map<String, CallNode>) {
            nodes = newNodes
            calculateLayout()
            revalidate()
            repaint()
        }

        private fun calculateLayout() {
            if (nodes.isEmpty()) {
                nodePositions = emptyMap()
                return
            }

            val positions = mutableMapOf<String, Point>()
            val levels = nodes.values.groupBy { it.level }.toSortedMap()

            var maxWidth = 0
            var maxHeight = 0

            levels.forEach { (level, nodesAtLevel) ->
                val y = (level + 2) * levelGap + 50
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
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            g2.scale(scale, scale)

            if (nodes.isEmpty()) {
                g2.color = JBColor.GRAY
                g2.font = g2.font.deriveFont(14f)
                g2.drawString("No call graph to display. Open a Dart file in lib/", 20, 30)
                return
            }

            // Draw connections first
            g2.stroke = BasicStroke(1.5f)

            nodes.forEach { (nodeName, node) ->
                val fromPos = nodePositions[nodeName] ?: return@forEach

                node.calls.forEach { calledName ->
                    if (calledName in nodes) {
                        val toPos = nodePositions[calledName] ?: return@forEach

                        g2.color = if (node.isInCurrentFile && nodes[calledName]?.isInCurrentFile == true) {
                            JBColor(Gray._120, Gray._140)
                        } else {
                            JBColor(Gray._180, Gray._100)
                        }

                        val fromX = fromPos.x + nodeWidth / 2
                        val fromY = fromPos.y + nodeHeight
                        val toX = toPos.x + nodeWidth / 2
                        val toY = toPos.y

                        g2.drawLine(fromX, fromY, toX, toY)

                        // Arrowhead
                        val angle = atan2((toY - fromY).toDouble(), (toX - fromX).toDouble())
                        val arrowSize = 6
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
            nodes.forEach { (nodeName, node) ->
                val pos = nodePositions[nodeName] ?: return@forEach

                g2.color = if (node.isInCurrentFile) {
                    when (node.type) {
                        NodeType.FUNCTION -> JBColor(Color(100, 150, 255), Color(60, 90, 150))
                        NodeType.METHOD -> JBColor(Color(150, 180, 255), Color(90, 110, 150))
                        NodeType.CONSTRUCTOR -> JBColor(Color(120, 220, 180), Color(70, 130, 110))
                        NodeType.GETTER -> JBColor(Color(255, 220, 120), Color(150, 130, 70))
                        NodeType.SETTER -> JBColor(Color(255, 180, 120), Color(150, 110, 70))
                    }
                } else {
                    JBColor(Gray._220, Gray._80)
                }

                g2.fillRoundRect(pos.x, pos.y, nodeWidth, nodeHeight, 8, 8)

                g2.color = if (node.isInCurrentFile) JBColor.BLACK else JBColor.GRAY
                g2.stroke = BasicStroke(if (node.isInCurrentFile) 2f else 1f)
                g2.drawRoundRect(pos.x, pos.y, nodeWidth, nodeHeight, 8, 8)

                g2.color = if (node.isInCurrentFile) JBColor.BLACK else JBColor.DARK_GRAY
                g2.font = g2.font.deriveFont(if (node.isInCurrentFile) Font.BOLD else Font.PLAIN, 10f)

                val displayName = node.name.substringAfter("::")
                val text = if (displayName.length > 16) displayName.substring(0, 13) + "..." else displayName

                val fm = g2.fontMetrics
                val textWidth = fm.stringWidth(text)
                val textX = pos.x + (nodeWidth - textWidth) / 2
                val textY = pos.y + (nodeHeight + fm.ascent) / 2 - 1

                g2.drawString(text, textX, textY)
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