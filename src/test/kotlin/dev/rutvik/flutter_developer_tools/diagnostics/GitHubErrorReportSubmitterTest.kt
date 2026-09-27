package dev.rutvik.flutter_developer_tools.diagnostics

import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class GitHubErrorReportSubmitterTest {

    private val environment = listOf("Plugin" to "1.0.1", "Dart plugin" to "509.0.0", "Flutter plugin" to null)

    private fun body(url: String): String =
        URLDecoder.decode(url.substringAfter("&body="), StandardCharsets.UTF_8)

    @Test
    fun buildsPrefilledIssueUrl() {
        val url = GitHubErrorReportSubmitter.buildIssueUrl(
            "[Error report] NoSuchMethodError: analysis_getHover",
            "Typing in main.dart",
            environment,
            "java.lang.NoSuchMethodError: analysis_getHover\n\tat dev.rutvik.Foo.bar(Foo.kt:1)"
        )
        assertTrue(url.startsWith("https://github.com/rjs580/flutter_developer_tools/issues/new?labels=bug&title="))
        val body = body(url)
        assertTrue(body.contains("Typing in main.dart"))
        assertTrue(body.contains("- Dart plugin: 509.0.0"))
        assertTrue(body.contains("- Flutter plugin: unknown"))
        assertTrue(body.contains("at dev.rutvik.Foo.bar(Foo.kt:1)"))
    }

    @Test
    fun truncatesLongStackTraceKeepingTheTop() {
        val trace = "java.lang.IllegalStateException: boom\n" +
                (1..2000).joinToString("\n") { "\tat com.example.Frame$it.method(Frame.kt:$it)" }
        val url = GitHubErrorReportSubmitter.buildIssueUrl("[Error report] boom", null, environment, trace)
        assertTrue("URL too long: ${url.length}", url.length <= 7_500)
        val body = body(url)
        assertTrue(body.contains("java.lang.IllegalStateException: boom"))
        assertTrue(body.contains("Frame1.method"))
    }
}
