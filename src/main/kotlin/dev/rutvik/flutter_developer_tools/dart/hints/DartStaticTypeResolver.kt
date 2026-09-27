package dev.rutvik.flutter_developer_tools.dart.hints

import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService
import dev.rutvik.flutter_developer_tools.utils.reportPluginError
import org.dartlang.analysis.server.protocol.HoverInformation
import org.eclipse.lsp4j.Hover
import org.eclipse.lsp4j.HoverParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.services.LanguageServer
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture

/**
 * Resolves the static type of a Dart declaration from the analysis server, across Dart plugin versions.
 *
 * - Dart plugin 508.x and older: legacy `DartAnalysisServerService.analysis_getHover`.
 * - Dart plugin 509.0.0 and newer: that method was removed ("Hover via LSP is now enabled for all users"),
 *   so hover goes through the Dart plugin's LSP bridge and the type is read from the `Type: `...`` line.
 *
 * Neither API exists in every supported Dart plugin version, so both are looked up reflectively once.
 * A missing API disables that path instead of throwing `NoSuchMethodError` from every highlighting pass.
 */
internal object DartStaticTypeResolver {

    private const val HOVER_TIMEOUT_MS = 1000

    private val legacyGetHover: Method? by lazy {
        try {
            DartAnalysisServerService::class.java.getMethod(
                "analysis_getHover",
                VirtualFile::class.java,
                Int::class.javaPrimitiveType
            )
        } catch (_: NoSuchMethodException) {
            null
        }
    }

    private val lspBridge: DartLspHoverBridge? by lazy { DartLspHoverBridge.create() }

    /** Set after the first [LinkageError], so an incompatible Dart plugin costs one warning, not one per identifier. */
    @Volatile
    private var disabled = false

    fun getStaticType(project: Project, file: VirtualFile, document: Document, offset: Int): String? {
        if (disabled) return null

        return try {
            val das = DartAnalysisServerService.getInstance(project)
            if (!das.isServerProcessActive) return null

            val legacy = legacyGetHover
            if (legacy != null) {
                @Suppress("UNCHECKED_CAST")
                (legacy.invoke(das, file, offset) as List<HoverInformation>).firstOrNull()?.staticType
            } else {
                lspBridge?.getStaticType(project, file, document, offset)
            }
        } catch (e: InvocationTargetException) {
            val cause = e.cause
            if (cause is ControlFlowException || cause is CancellationException) throw cause
            null
        } catch (e: LinkageError) {
            // Guard against further Dart plugin API changes; never let this break the highlighting pass.
            disabled = true
            reportPluginError("Dart type hints (incompatible Dart plugin API)", e)
            null
        } catch (e: Exception) {
            if (e is ControlFlowException || e is CancellationException) throw e
            null
        }
    }

    /**
     * Reflective access to the Dart plugin's LSP client API (`com.intellij.platform.dartlsp.api`, Dart plugin 507+),
     * which the plugin compiles against neither at our minimum supported version nor as a stable API.
     */
    private class DartLspHoverBridge(
        private val getManager: Method,
        private val getServersForProvider: Method,
        private val providerClass: Class<*>,
        private val getState: Method,
        private val runningState: Any,
        private val sendRequestSync: Method,
        private val getDocumentIdentifier: Method,
    ) {
        fun getStaticType(project: Project, file: VirtualFile, document: Document, offset: Int): String? {
            val manager = getManager.invoke(null, project)
            val server = (getServersForProvider.invoke(manager, providerClass) as Collection<*>)
                .firstOrNull { it != null && getState.invoke(it) == runningState }
                ?: return null

            val identifier = getDocumentIdentifier.invoke(server, file) as TextDocumentIdentifier
            val line = document.getLineNumber(offset)
            val position = Position(line, offset - document.getLineStartOffset(line))

            val request: (LanguageServer) -> CompletableFuture<Hover> = { languageServer ->
                languageServer.textDocumentService.hover(HoverParams(identifier, position))
            }
            val hover = sendRequestSync.invoke(server, HOVER_TIMEOUT_MS, request) as Hover? ?: return null
            return parseStaticType(hover)
        }

        companion object {
            fun create(): DartLspHoverBridge? = try {
                val loader = DartAnalysisServerService::class.java.classLoader
                val managerClass = Class.forName("com.intellij.platform.dartlsp.api.LspServerManager", false, loader)
                val serverClass = Class.forName("com.intellij.platform.dartlsp.api.LspServer", false, loader)
                val stateClass = Class.forName("com.intellij.platform.dartlsp.api.LspServerState", false, loader)
                DartLspHoverBridge(
                    getManager = managerClass.getMethod("getInstance", Project::class.java),
                    getServersForProvider = managerClass.getMethod("getServersForProvider", Class::class.java),
                    providerClass = Class.forName("com.jetbrains.lang.dart.lsp.DartLspServerSupportProvider", false, loader),
                    getState = serverClass.getMethod("getState"),
                    runningState = stateClass.enumConstants.first { (it as Enum<*>).name == "Running" },
                    sendRequestSync = serverClass.getMethod(
                        "sendRequestSync",
                        Int::class.javaPrimitiveType,
                        Function1::class.java
                    ),
                    getDocumentIdentifier = serverClass.getMethod("getDocumentIdentifier", VirtualFile::class.java),
                )
            } catch (_: ReflectiveOperationException) {
                null
            } catch (_: LinkageError) {
                null
            } catch (_: NoSuchElementException) {
                null
            }
        }
    }

    private val LSP_TYPE_LINE_REGEX = Regex("^Type: `(.+)`\\s*$", RegexOption.MULTILINE)

    /** The Dart analysis server renders the static type of a hovered declaration as a "Type: `T`" line. */
    internal fun parseStaticType(hover: Hover): String? {
        // The Dart server answers with MarkupContent; the deprecated MarkedString form is not used.
        val contents = hover.contents ?: return null
        val markdown = if (contents.isRight) contents.right?.value else null
        return markdown?.let { parseStaticType(it) }
    }

    internal fun parseStaticType(markdown: String): String? =
        LSP_TYPE_LINE_REGEX.find(markdown)?.groupValues?.get(1)
}
