package dev.rutvik.flutter_developer_tools.diagnostics

import com.intellij.ide.BrowserUtil
import com.intellij.ide.plugins.PluginManager
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.diagnostic.ErrorReportSubmitter
import com.intellij.openapi.diagnostic.IdeaLoggingEvent
import com.intellij.openapi.diagnostic.SubmittedReportInfo
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.Consumer
import java.awt.Component
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Adds "Report on GitHub" to the IDE Internal Errors dialog for errors attributed to this plugin.
 *
 * Opens a pre-filled GitHub issue in the browser with the stack trace and the versions that matter for
 * triage (IDE, plugin, Dart and Flutter plugins). Nothing is sent until the user reviews and submits it.
 */
class GitHubErrorReportSubmitter : ErrorReportSubmitter() {

    override fun getReportActionText(): String = "Report on GitHub"

    override fun getPrivacyNoticeText(): String =
        "Opens a pre-filled GitHub issue in your browser. Nothing is sent until you review and submit it there."

    override fun submit(
        events: Array<out IdeaLoggingEvent>,
        additionalInfo: String?,
        parentComponent: Component,
        consumer: Consumer<in SubmittedReportInfo>
    ): Boolean {
        val event = events.firstOrNull() ?: return false
        val throwable = event.throwable
        val title = "[Error report] " + (throwable?.let { "${it.javaClass.simpleName}: ${it.message.orEmpty()}" }
            ?: event.message.orEmpty())

        val environment = listOf(
            "Plugin" to pluginDescriptor?.version,
            "IDE" to ApplicationInfo.getInstance().let { "${it.fullApplicationName} (${it.build.asString()})" },
            "Dart plugin" to pluginVersion("Dart"),
            "Flutter plugin" to pluginVersion("io.flutter"),
            "OS" to "${SystemInfo.OS_NAME} ${SystemInfo.OS_VERSION} (${SystemInfo.OS_ARCH})",
            "JRE" to SystemInfo.JAVA_RUNTIME_VERSION,
        )

        BrowserUtil.browse(buildIssueUrl(title, additionalInfo, environment, event.throwableText))
        consumer.consume(SubmittedReportInfo(SubmittedReportInfo.SubmissionStatus.NEW_ISSUE))
        return true
    }

    // No public API exposes another plugin's version (all PluginManager lookups are @Internal as of 2026.2).
    // The Dart/Flutter versions are essential for triage, so use it, but degrade to "unknown" if it changes.
    private fun pluginVersion(id: String): String? = try {
        PluginManager.getInstance().findEnabledPlugin(PluginId.getId(id))?.version
    } catch (_: LinkageError) {
        null
    }

    companion object {
        private const val NEW_ISSUE_URL = "https://github.com/rjs580/flutter_developer_tools/issues/new"

        // Browsers and GitHub reject very long URLs; stay well below the ~8 KB GitHub accepts.
        private const val MAX_URL_LENGTH = 7_500
        private const val MAX_TITLE_LENGTH = 120

        internal fun buildIssueUrl(
            title: String,
            userDescription: String?,
            environment: List<Pair<String, String?>>,
            stackTrace: String,
        ): String {
            val shortTitle = title.lineSequence().first().take(MAX_TITLE_LENGTH)
            var trace = stackTrace.trim()
            while (true) {
                val url = "$NEW_ISSUE_URL?labels=bug" +
                        "&title=${encode(shortTitle)}" +
                        "&body=${encode(issueBody(userDescription, environment, trace))}"
                if (url.length <= MAX_URL_LENGTH || trace.isEmpty()) return url
                // Keep the top of the trace (exception and our frames); drop the tail.
                trace = trace.lines().let { it.take(it.size * 3 / 4) }.joinToString("\n") + "\n\t..."
                if (trace.lines().size <= 2) trace = ""
            }
        }

        private fun issueBody(userDescription: String?, environment: List<Pair<String, String?>>, trace: String) =
            buildString {
                appendLine("## What were you doing?")
                appendLine(userDescription?.takeIf { it.isNotBlank() } ?: "<!-- Steps or context, if known -->")
                appendLine()
                appendLine("## Environment")
                environment.forEach { (name, value) -> appendLine("- $name: ${value ?: "unknown"}") }
                appendLine()
                appendLine("## Stack trace")
                appendLine("```")
                appendLine(trace.ifEmpty { "(too long for the issue URL; please paste it from the IDE error dialog)" })
                appendLine("```")
            }

        private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)
    }
}
