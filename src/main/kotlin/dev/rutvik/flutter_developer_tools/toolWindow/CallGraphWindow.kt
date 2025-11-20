
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
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Visual Call Graph Tool Window
 *
 * Shows a recursive call chain for the function at cursor position.
 */
class CallGraphWindow(private val project: Project) {

    private val panel = SimpleToolWindowPanel(true, true)
    private val graphPanel: CallGraphPanel
    private val statusLabel: JBLabel
    private var lastUpdateTime: Long = 0
    private var maxDepth = 3

    @Suppress("UnstableApiUsage")
    private val refreshAlarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, project)
    private val refreshDebounceTime = 300

    data class CallGraphData(
        val centerNode: CallNode,
        val callerChains: List<CallChain>,
        val calleeChains: List<CallChain>
    )

    data class CallChain(
        val nodes: List<CallNode>,
        val isExternal: Boolean = false
    )

    data class CallNode(
        val name: String,
        val element: PsiElement?,
        val type: NodeType,
        val isExternal: Boolean = false
    )

    enum class NodeType {
        FUNCTION, METHOD, CONSTRUCTOR, GETTER, SETTER, EXTERNAL
    }

    init {
        graphPanel = CallGraphPanel()
        statusLabel = JBLabel("Place cursor on a function/method to see its call graph").apply {
            border = JBUI.Borders.empty(5, 10)
            foreground = JBColor.GRAY
        }

        setupToolbar()
        setupContent()
        subscribeToEditorChanges()
    }

    private fun setupToolbar() {
        val depthLabel = JLabel("Chain Depth: ")
        val depthSpinner = JSpinner(SpinnerNumberModel(3, 1, 10, 1)).apply {
            maximumSize = Dimension(60, 25)
            preferredSize = Dimension(60, 25)
            addChangeListener {
                maxDepth = (value as Int)
                analyzeCurrentPosition()
            }
        }

        val actionGroup = DefaultActionGroup().apply {
            add(RefreshAction())
            add(ExportAction())
        }

        val toolbar = ActionManager.getInstance()
            .createActionToolbar("CallGraphToolbar", actionGroup, true)
        toolbar.targetComponent = panel

        val toolbarPanel = JPanel(BorderLayout()).apply {
            add(toolbar.component, BorderLayout.WEST)
            add(JPanel(FlowLayout(FlowLayout.LEFT, 5, 0)).apply {
                add(depthLabel)
                add(depthSpinner)
            }, BorderLayout.CENTER)
        }

        panel.toolbar = toolbarPanel
    }

    private fun setupContent() {
        val scrollPane = JBScrollPane(graphPanel)
        val mainPanel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            add(scrollPane, BorderLayout.CENTER)
            add(statusLabel, BorderLayout.SOUTH)
        }
        panel.setContent(mainPanel)
    }

    private fun subscribeToEditorChanges() {
        val connection = project.messageBus.connect()
        connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
            override fun selectionChanged(event: FileEditorManagerEvent) {
                val editor = event.newEditor
                if (editor is TextEditor) {
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
        refreshAlarm.cancelAllRequests()
        refreshAlarm.addRequest({ analyzeCurrentPosition() }, refreshDebounceTime)
    }

    fun getContent(): JComponent = panel

    private fun analyzeCurrentPosition() {
        ReadAction.nonBlocking<CallGraphData?> {
            val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return@nonBlocking null
            val file = editor.document.let { PsiDocumentManager.getInstance(project).getPsiFile(it) } as? DartFile
                ?: return@nonBlocking null

            if (!file.virtualFile.path.contains("/lib/")) return@nonBlocking null

            val offset = editor.caretModel.offset
            val element = file.findElementAt(offset) ?: return@nonBlocking null
            val function = findEnclosingFunction(element) ?: return@nonBlocking null

            buildCallGraph(function)
        }
            .coalesceBy(this)
            .inSmartMode(project)
            .finishOnUiThread(com.intellij.openapi.application.ModalityState.defaultModalityState()) { graphData ->
                if (graphData != null) {
                    graphPanel.setData(graphData)
                    lastUpdateTime = System.currentTimeMillis()
                    updateStatusLabel(graphData.centerNode.name)
                } else {
                    graphPanel.setData(null)
                    statusLabel.text = "Place cursor on a function/method"
                }
            }
            .submit { runnable -> ApplicationManager.getApplication().executeOnPooledThread(runnable) }
    }

    private fun findEnclosingFunction(element: PsiElement): PsiElement? {
        return PsiTreeUtil.getParentOfType(
            element,
            DartFunctionDeclarationWithBody::class.java,
            DartMethodDeclaration::class.java,
            DartGetterDeclaration::class.java,
            DartSetterDeclaration::class.java,
            DartFactoryConstructorDeclaration::class.java,
            DartNamedConstructorDeclaration::class.java
        )
    }

    private fun buildCallGraph(function: PsiElement): CallGraphData {
        val centerNode = createNode(function)
        val callerChains = buildCallerChains(function, maxDepth)
        val calleeChains = buildCalleeChains(function, maxDepth)
        return CallGraphData(centerNode, callerChains, calleeChains)
    }

    private fun buildCallerChains(element: PsiElement, maxDepth: Int): List<CallChain> {
        val allChains = mutableListOf<CallChain>()
        val globalVisited = mutableSetOf<PsiElement>()

        fun exploreCallers(current: PsiElement, currentChain: List<CallNode>, depth: Int) {
            if (depth >= maxDepth) {
                if (currentChain.isNotEmpty()) {
                    allChains.add(CallChain(currentChain.reversed(), false))
                }
                return
            }

            if (current in globalVisited) return
            globalVisited.add(current)

            // Search for all references to this function
            val references = ReferencesSearch.search(
                current,
                GlobalSearchScope.projectScope(project)
            ).findAll()

            if (references.isEmpty()) {
                // Dead end - add chain if we have one
                if (currentChain.isNotEmpty()) {
                    allChains.add(CallChain(currentChain.reversed(), false))
                }
                return
            }

            var foundCaller = false
            for (ref in references) {
                val refElement = ref.element
                val enclosingFunc = findEnclosingFunction(refElement)

                if (enclosingFunc != null && enclosingFunc != current) {
                    foundCaller = true
                    val node = createNode(enclosingFunc)
                    val isInLib = enclosingFunc.containingFile?.virtualFile?.path?.contains("/lib/") == true

                    if (isInLib) {
                        // Continue exploring this chain
                        exploreCallers(enclosingFunc, currentChain + node, depth + 1)
                    } else {
                        // External caller - terminate this chain
                        allChains.add(CallChain((currentChain + node).reversed(), true))
                    }
                }
            }

            // If we found callers but didn't reach max depth, add current chain
            if (!foundCaller && currentChain.isNotEmpty()) {
                allChains.add(CallChain(currentChain.reversed(), false))
            }
        }

        exploreCallers(element, emptyList(), 0)
        return allChains.distinctBy { chain -> chain.nodes.joinToString("->") { it.name } }
            .take(20)
    }

    private fun buildCalleeChains(element: PsiElement, maxDepth: Int): List<CallChain> {
        val allChains = mutableListOf<CallChain>()
        val globalVisited = mutableSetOf<PsiElement>()

        fun exploreCallees(current: PsiElement, currentChain: List<CallNode>, depth: Int) {
            if (depth >= maxDepth) {
                if (currentChain.isNotEmpty()) {
                    allChains.add(CallChain(currentChain, false))
                }
                return
            }

            if (current in globalVisited) return
            globalVisited.add(current)

            // Find all function calls within this function
            val calls = PsiTreeUtil.collectElementsOfType(current, DartCallExpression::class.java)

            if (calls.isEmpty()) {
                // Dead end - add chain if we have one
                if (currentChain.isNotEmpty()) {
                    allChains.add(CallChain(currentChain, false))
                }
                return
            }

            var foundCallee = false
            for (call in calls) {
                val resolved = call.expression?.reference?.resolve()

                if (resolved != null && resolved != current) {
                    foundCallee = true
                    val node = createNode(resolved)
                    val isInLib = resolved.containingFile?.virtualFile?.path?.contains("/lib/") == true

                    if (isInLib) {
                        // Continue exploring this chain
                        exploreCallees(resolved, currentChain + node, depth + 1)
                    } else {
                        // External call - terminate this chain
                        allChains.add(CallChain(currentChain + node, true))
                    }
                }
            }

            // If we found callees but didn't go deeper, add current chain
            if (!foundCallee && currentChain.isNotEmpty()) {
                allChains.add(CallChain(currentChain, false))
            }
        }

        exploreCallees(element, emptyList(), 0)
        return allChains.distinctBy { chain -> chain.nodes.joinToString("->") { it.name } }
            .take(20)
    }

    private fun createNode(element: PsiElement): CallNode {
        val isInLib = element.containingFile?.virtualFile?.path?.contains("/lib/") == true

        return when (element) {
            is DartFunctionDeclarationWithBody -> {
                val name = element.name ?: "anonymous"
                CallNode(name, element, NodeType.FUNCTION, !isInLib)
            }
            is DartMethodDeclaration -> {
                val className = PsiTreeUtil.getParentOfType(element, DartClass::class.java)?.name ?: "?"
                val methodName = element.name ?: "anonymous"
                CallNode("$className.$methodName", element, NodeType.METHOD, !isInLib)
            }
            is DartGetterDeclaration -> {
                val className = PsiTreeUtil.getParentOfType(element, DartClass::class.java)?.name ?: "?"
                val getterName = element.name ?: "getter"
                CallNode("$className.$getterName", element, NodeType.GETTER, !isInLib)
            }
            is DartSetterDeclaration -> {
                val className = PsiTreeUtil.getParentOfType(element, DartClass::class.java)?.name ?: "?"
                val setterName = element.name ?: "setter"
                CallNode("$className.$setterName", element, NodeType.SETTER, !isInLib)
            }
            is DartFactoryConstructorDeclaration -> {
                val className = PsiTreeUtil.getParentOfType(element, DartClass::class.java)?.name ?: "?"
                val constructorName = element.name ?: className
                CallNode("$className.$constructorName", element, NodeType.CONSTRUCTOR, !isInLib)
            }
            is DartNamedConstructorDeclaration -> {
                val className = PsiTreeUtil.getParentOfType(element, DartClass::class.java)?.name ?: "?"
                val constructorName = element.name ?: "new"
                CallNode("$className.$constructorName", element, NodeType.CONSTRUCTOR, !isInLib)
            }
            is DartComponentName -> {
                // Handle component names (function/method names)
                val parent = element.parent
                val name = element.name ?: "unknown"
                when (parent) {
                    is DartFunctionDeclarationWithBody -> CallNode(name, parent, NodeType.FUNCTION, !isInLib)
                    is DartMethodDeclaration -> {
                        val className = PsiTreeUtil.getParentOfType(parent, DartClass::class.java)?.name ?: "?"
                        CallNode("$className.$name", parent, NodeType.METHOD, !isInLib)
                    }
                    else -> CallNode(name, element, NodeType.EXTERNAL, !isInLib)
                }
            }
            is PsiNamedElement -> {
                // Fallback for any named element
                val name = element.name ?: "unknown"
                CallNode(name, element, NodeType.EXTERNAL, !isInLib)
            }
            else -> {
                CallNode("unknown[${element.javaClass.simpleName}]", element, NodeType.EXTERNAL, !isInLib)
            }
        }
    }

    private fun updateStatusLabel(functionName: String) {
        val timeStr = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(lastUpdateTime))
        statusLabel.text = "Call graph for: $functionName • Depth: $maxDepth • Updated: $timeStr"
    }

    inner class RefreshAction : AnAction("Refresh", "Refresh call graph", AllIcons.Actions.Refresh) {
        override fun actionPerformed(e: AnActionEvent) {
            analyzeCurrentPosition()
        }
    }

    inner class ExportAction : AnAction("Export", "Export to PNG", AllIcons.Actions.Download) {
        override fun actionPerformed(e: AnActionEvent) {
            val chooser = com.intellij.openapi.fileChooser.FileChooserFactory.getInstance()
                .createSaveFileDialog(
                    com.intellij.openapi.fileChooser.FileSaverDescriptor(
                        "Export Call Graph", "Export call graph to PNG", "png"
                    ), project
                )

            chooser.save(null as com.intellij.openapi.vfs.VirtualFile?, "call_graph.png")?.let { wrapper ->
                try {
                    graphPanel.exportToPng(wrapper.file.toPath())
                    statusLabel.text = "Exported to ${wrapper.file.path}"
                } catch (e: Exception) {
                    statusLabel.text = "Export failed: ${e.message}"
                }
            }
        }
    }

    inner class CallGraphPanel : JPanel() {
        private var data: CallGraphData? = null
        private val nodeHeight = 35
        private val nodeSpacing = 10
        private val columnWidth = 200
        private val horizontalSpacing = 60

        init {
            background = JBColor.background()
            isOpaque = true

            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    data?.let { graphData ->
                        findNodeAt(e.point, graphData)?.let { node ->
                            (node.element as? Navigatable)?.navigate(true)
                        }
                    }
                }
            })
        }

        fun setData(newData: CallGraphData?) {
            data = newData
            revalidate()
            repaint()
        }

        private fun findNodeAt(point: Point, graphData: CallGraphData): CallNode? {
            val centerX = width / 2
            var startY = 50

            // Check caller chains
            graphData.callerChains.forEach { chain ->
                var x = centerX - (chain.nodes.size * (columnWidth + horizontalSpacing))
                chain.nodes.forEach { node ->
                    if (point.x in x..(x + columnWidth) && point.y in startY..(startY + nodeHeight)) {
                        return node
                    }
                    x += columnWidth + horizontalSpacing
                }
                startY += nodeHeight + nodeSpacing
            }

            // Check center node
            startY += 30
            val centerNodeX = centerX - columnWidth / 2
            if (point.x in centerNodeX..(centerNodeX + columnWidth) &&
                point.y in startY..(startY + nodeHeight + 10)) {
                return graphData.centerNode
            }

            // Check callee chains
            startY += nodeHeight + 50
            graphData.calleeChains.forEach { chain ->
                var x = centerX + horizontalSpacing
                chain.nodes.forEach { node ->
                    if (point.x in x..(x + columnWidth) && point.y in startY..(startY + nodeHeight)) {
                        return node
                    }
                    x += columnWidth + horizontalSpacing
                }
                startY += nodeHeight + nodeSpacing
            }

            return null
        }

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

            val graphData = data
            if (graphData == null) {
                g2.color = JBColor.GRAY
                g2.font = g2.font.deriveFont(14f)
                val msg = "Place cursor on a function to see its call graph"
                val metrics = g2.fontMetrics
                g2.drawString(msg, (width - metrics.stringWidth(msg)) / 2, height / 2)
                return
            }

            val centerX = width / 2
            var currentY = 50

            // Draw caller chains
            if (graphData.callerChains.isNotEmpty()) {
                g2.font = g2.font.deriveFont(Font.BOLD, 11f)
                g2.color = JBColor.GRAY
                g2.drawString("← Called by (incoming calls)", 20, currentY - 20)

                val callerEndY = drawChains(g2, graphData.callerChains, centerX, currentY, true)
                currentY = callerEndY + 30
            }

            // Draw center node
            val centerNodeX = centerX - columnWidth / 2
            drawCenterNode(g2, graphData.centerNode, centerNodeX, currentY)
            currentY += nodeHeight + 50

            // Draw callee chains
            if (graphData.calleeChains.isNotEmpty()) {
                g2.font = g2.font.deriveFont(Font.BOLD, 11f)
                g2.color = JBColor.GRAY
                g2.drawString("Calls → (outgoing calls)", 20, currentY - 20)

                drawChains(g2, graphData.calleeChains, centerX, currentY, false)
            }
        }

        private fun drawChains(
            g2: Graphics2D,
            chains: List<CallChain>,
            centerX: Int,
            startY: Int,
            isCallers: Boolean
        ): Int {
            var currentY = startY

            chains.forEach { chain ->
                var x = if (isCallers) {
                    centerX - (chain.nodes.size * (columnWidth + horizontalSpacing))
                } else {
                    centerX + horizontalSpacing
                }

                var prevX = if (isCallers) centerX else centerX - horizontalSpacing
                var prevY = startY - 30

                chain.nodes.forEachIndexed { index, node ->
                    drawNode(g2, node, x, currentY)

                    // Draw arrow
                    if (index == 0 && !isCallers) {
                        drawArrow(g2, prevX, prevY, x, currentY + nodeHeight / 2, chain.isExternal)
                    } else if (index > 0) {
                        drawArrow(g2, prevX + columnWidth, prevY + nodeHeight / 2, x, currentY + nodeHeight / 2, chain.isExternal)
                    }

                    prevX = x
                    prevY = currentY
                    x += if (isCallers) columnWidth + horizontalSpacing else columnWidth + horizontalSpacing
                }

                // Connect last caller to center
                if (isCallers && chain.nodes.isNotEmpty()) {
                    drawArrow(g2, prevX + columnWidth, prevY + nodeHeight / 2, centerX - columnWidth / 2, startY + nodeHeight * chains.size + 30, chain.isExternal)
                }

                currentY += nodeHeight + nodeSpacing
            }

            return currentY
        }

        private fun drawNode(g2: Graphics2D, node: CallNode, x: Int, y: Int) {
            g2.color = if (node.isExternal) {
                JBColor(Gray._240, Gray._60)
            } else {
                JBColor(Color(230, 245, 255), Color(45, 55, 70))
            }
            g2.fillRoundRect(x, y, columnWidth, nodeHeight, 8, 8)

            g2.color = if (node.isExternal) {
                JBColor(Gray._180, Gray._100)
            } else {
                JBColor(Color(100, 150, 255), Color(80, 120, 200))
            }
            g2.stroke = BasicStroke(1.5f)
            g2.drawRoundRect(x, y, columnWidth, nodeHeight, 8, 8)

            g2.color = if (node.isExternal) {
                JBColor(Gray._100, Gray._160)
            } else {
                JBColor(Gray._20, Gray._220)
            }
            g2.font = g2.font.deriveFont(Font.PLAIN, 10f)

            val text = if (node.name.length > 25) node.name.take(22) + "..." else node.name
            val metrics = g2.fontMetrics
            g2.drawString(text, x + 8, y + (nodeHeight + metrics.ascent) / 2 - 2)

            if (node.isExternal) {
                g2.font = g2.font.deriveFont(Font.ITALIC, 8f)
                g2.drawString("[external]", x + 8, y + nodeHeight - 5)
            }
        }

        private fun drawCenterNode(g2: Graphics2D, node: CallNode, x: Int, y: Int) {
            g2.color = JBColor(Color(255, 245, 220), Color(80, 75, 60))
            g2.fillRoundRect(x, y, columnWidth, nodeHeight + 10, 10, 10)

            g2.color = JBColor(Color(200, 140, 0), Color(180, 140, 60))
            g2.stroke = BasicStroke(2.5f)
            g2.drawRoundRect(x, y, columnWidth, nodeHeight + 10, 10, 10)

            g2.color = JBColor(Gray._20, Gray._240)
            g2.font = g2.font.deriveFont(Font.BOLD, 11f)

            val text = if (node.name.length > 25) node.name.take(22) + "..." else node.name
            val metrics = g2.fontMetrics
            g2.drawString(text, x + 10, y + (nodeHeight + 10 + metrics.ascent) / 2 - 2)
        }

        private fun drawArrow(g2: Graphics2D, x1: Int, y1: Int, x2: Int, y2: Int, isExternal: Boolean) {
            g2.color = if (isExternal) {
                JBColor(Gray._200, Gray._100)
            } else {
                JBColor(Gray._120, Gray._140)
            }
            g2.stroke = if (isExternal) {
                BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0f, floatArrayOf(5f), 0f)
            } else {
                BasicStroke(1.5f)
            }

            g2.drawLine(x1, y1, x2, y2)

            val angle = atan2((y2 - y1).toDouble(), (x2 - x1).toDouble())
            val arrowSize = 6
            val x1Arrow = (x2 - arrowSize * cos(angle - Math.PI / 6)).toInt()
            val y1Arrow = (y2 - arrowSize * sin(angle - Math.PI / 6)).toInt()
            val x2Arrow = (x2 - arrowSize * cos(angle + Math.PI / 6)).toInt()
            val y2Arrow = (y2 - arrowSize * sin(angle + Math.PI / 6)).toInt()

            g2.stroke = BasicStroke(1.5f)
            g2.drawLine(x2, y2, x1Arrow, y1Arrow)
            g2.drawLine(x2, y2, x2Arrow, y2Arrow)
        }

        override fun getPreferredSize(): Dimension {
            val graphData = data ?: return Dimension(800, 600)
            val maxChainLength = max(
                graphData.callerChains.maxOfOrNull { it.nodes.size } ?: 0,
                graphData.calleeChains.maxOfOrNull { it.nodes.size } ?: 0
            )
            val width = max(800, maxChainLength * (columnWidth + horizontalSpacing) + 200)
            val height = max(600, (graphData.callerChains.size + graphData.calleeChains.size) * (nodeHeight + nodeSpacing) + 300)
            return Dimension(width, height)
        }

        fun exportToPng(path: java.nio.file.Path) {
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