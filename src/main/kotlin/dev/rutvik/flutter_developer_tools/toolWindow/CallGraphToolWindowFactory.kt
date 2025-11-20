package dev.rutvik.flutter_developer_tools.toolWindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import io.flutter.sdk.FlutterSdk

class CallGraphToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val callGraphWindow = CallGraphWindow(project)
        val contentFactory = ContentFactory.getInstance()
        val content = contentFactory.createContent(callGraphWindow.getContent(), "", false)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project): Boolean {
        return FlutterSdk.getFlutterSdk(project) != null
    }
}