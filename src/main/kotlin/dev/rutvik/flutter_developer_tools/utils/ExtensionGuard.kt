package dev.rutvik.flutter_developer_tools.utils

import com.intellij.diagnostic.PluginException
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap

private val LOG = Logger.getInstance("#dev.rutvik.flutter_developer_tools.utils.ExtensionGuard")
private val PLUGIN_ID = PluginId.getId("dev.rutvik.flutter_developer_tools")

/**
 * Runs an extension-point callback that calls into the Dart or Flutter plugins, whose APIs change between
 * releases without notice (e.g. Dart 509 removed `analysis_getHover`).
 *
 * Many of these callbacks run inside the editor's highlighting session (inlay hints, code vision, sticky lines),
 * where any escaping exception cancels highlighting for the whole editor. Unexpected failures, including a
 * [LinkageError] from a removed Dart/Flutter method, are therefore replaced by [fallback]. Cancellation is
 * always rethrown.
 */
internal inline fun <T> guardExtension(feature: String, fallback: T, block: () -> T): T {
    return try {
        block()
    } catch (e: Throwable) {
        handleExtensionFailure(feature, e)
        fallback
    }
}

@PublishedApi
internal fun handleExtensionFailure(feature: String, e: Throwable) {
    if (e is ControlFlowException || e is CancellationException) throw e
    if (e !is RuntimeException && e !is LinkageError) throw e
    reportPluginError(feature, e)
}

/**
 * Reports a failure as an IDE error attributed to this plugin, so the user sees it under IDE Internal Errors and
 * can send it via [dev.rutvik.flutter_developer_tools.diagnostics.GitHubErrorReportSubmitter].
 *
 * Each distinct error (see [errorSignature]) is reported once per IDE session, and at most
 * [MAX_REPORTS_PER_FEATURE] per feature; repeats go to the debug log only. A failure inside a highlighting pass
 * recurs on every keystroke, so this keeps one broken feature from flooding the log or the errors dialog.
 */
internal fun reportPluginError(feature: String, e: Throwable) {
    val isNew = reportedSignatures.add(errorSignature(feature, e)) &&
            reportsPerFeature.merge(feature, 1, Int::plus)!! <= MAX_REPORTS_PER_FEATURE
    if (isNew) {
        LOG.error(PluginException("$feature failed; the feature was skipped for this call", e, PLUGIN_ID))
    } else {
        LOG.debug("$feature failed", e)
    }
}

/**
 * Identifies "the same error": feature, exception type and top stack frames of the root cause. The message is
 * left out on purpose, since it often embeds offsets, file names or other per-call data.
 */
internal fun errorSignature(feature: String, e: Throwable): String {
    var root = e
    while (root.cause != null && root.cause !== root) root = root.cause!!
    val frames = root.stackTrace.take(SIGNATURE_FRAMES).joinToString("|")
    return "$feature|${root.javaClass.name}|$frames"
}

private const val MAX_REPORTS_PER_FEATURE = 3
private const val SIGNATURE_FRAMES = 5
private val reportedSignatures: MutableSet<String> = ConcurrentHashMap.newKeySet()
private val reportsPerFeature = ConcurrentHashMap<String, Int>()
