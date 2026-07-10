
package dev.rutvik.flutter_developer_tools.arb

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ColoredProcessHandler
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import io.flutter.FlutterMessages
import io.flutter.console.FlutterConsoles
import io.flutter.pub.PubRoot
import io.flutter.sdk.FlutterSdk
import java.nio.charset.StandardCharsets


/**
 * Runner class for executing Flutter's gen-l10n command to generate localizations from ARB files.
 *
 * This class handles the execution of the 'flutter gen-l10n' command, which processes ARB (Application
 * Resource Bundle) files and generates the corresponding Flutter localization classes.
 *
 * @property project The IntelliJ project instance
 * @property flutterSdk The Flutter SDK instance used for command execution
 */
class FlutterGenL10nRunner(
    private val project: Project,
    private val flutterSdk: FlutterSdk,
    private val contextFile: VirtualFile? = null
) {

    /**
     * Executes the 'flutter gen-l10n' command and displays the process output in the Flutter console.
     *
     * This method starts the gen-l10n process and shows its output in a dedicated Flutter console window.
     * If the process fails to start, an error message is displayed to the user.
     */
    fun runGenL10n() {
        val handler = startProcessOrShowError()
        if (handler != null) {
            FlutterConsoles.displayProcessLater(handler, project, null) {
                handler.startNotify()
            }
        }
    }

    /**
     * Attempts to start the gen-l10n process and returns a process handler.
     *
     * @return ColoredProcessHandler if the process starts successfully, null if an error occurs
     */
    private fun startProcessOrShowError(): ColoredProcessHandler? {
        try {
            val commandLine = createGeneralCommandLine()
            return ColoredProcessHandler(commandLine)
        } catch (e: ExecutionException) {
            FlutterMessages.showError(
                "Flutter gen-l10n",
                "Failed to execute flutter gen-l10n: ${e.message}",
                project
            )
            return null
        }
    }

    /**
     * Creates and configures the command line for the flutter gen-l10n command.
     *
     * @return GeneralCommandLine configured with the appropriate flutter command and parameters
     * @throws ExecutionException if the project base path cannot be determined
     */
    private fun createGeneralCommandLine(): GeneralCommandLine {
        val line = GeneralCommandLine()
        line.charset = StandardCharsets.UTF_8
        line.exePath = resolveFlutterExecutable()
        line.setWorkDirectory(resolveWorkingDirectory())
        line.addParameter("--no-color")
        line.addParameter("gen-l10n")

        return line
    }

    /**
     * Resolves the Flutter executable, honoring the OS-specific launcher name
     * (flutter.bat on Windows, flutter elsewhere). GeneralCommandLine does not
     * append the extension itself.
     */
    private fun resolveFlutterExecutable(): String {
        val executable = if (SystemInfo.isWindows) "flutter.bat" else "flutter"
        return FileUtil.toSystemDependentName("${flutterSdk.homePath}/bin/$executable")
    }

    /**
     * Resolves the working directory to the pub root of the triggering file when available,
     * so the command runs in the correct package (important for monorepos), falling back to
     * the project base path.
     */
    private fun resolveWorkingDirectory(): String {
        val pubRootPath = contextFile?.let { PubRoot.forFile(it)?.root?.path }
        return pubRootPath ?: project.basePath ?: throw ExecutionException("Cannot determine working directory")
    }
}