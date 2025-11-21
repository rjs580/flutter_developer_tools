
package dev.rutvik.flutter_developer_tools.buildRunner

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ColoredProcessHandler
import com.intellij.openapi.project.Project
import io.flutter.FlutterMessages
import io.flutter.console.FlutterConsoles
import io.flutter.sdk.FlutterSdk
import java.nio.charset.StandardCharsets


/**
 * Runner class for executing Dart's build_runner commands.
 *
 * This class handles the execution of the 'dart run build_runner' commands, which processes
 * code generation in Dart/Flutter projects.
 *
 * @property project The IntelliJ project instance
 * @property flutterSdk The Flutter SDK instance used for command execution
 */
class BuildRunnerCommands(
    private val project: Project,
    private val flutterSdk: FlutterSdk
) {

    /**
     * Executes the 'dart run build_runner build' command and displays the process output in the Flutter console.
     *
     * This method starts the build process and shows its output in a dedicated Flutter console window.
     * If the process fails to start, an error message is displayed to the user.
     */
    fun runBuild() {
        val handler = startProcessOrShowError("build", "build_runner build", "--delete-conflicting-outputs")
        if (handler != null) {
            FlutterConsoles.displayProcessLater(handler, project, null) {
                handler.startNotify()
            }
        }
    }

    /**
     * Executes the 'dart run build_runner build' command without delete-conflicting-outputs.
     */
    fun runBuildNoDelete() {
        val handler = startProcessOrShowError("build", "build_runner build")
        if (handler != null) {
            FlutterConsoles.displayProcessLater(handler, project, null) {
                handler.startNotify()
            }
        }
    }

    /**
     * Executes the 'dart run build_runner watch' command and displays the process output in the Flutter console.
     *
     * This method starts the watch process and shows its output in a dedicated Flutter console window.
     * The console will have a stop button to terminate the watch process.
     * If the process fails to start, an error message is displayed to the user.
     */
    fun runWatch() {
        val handler = startProcessOrShowError("watch", "build_runner watch", "--delete-conflicting-outputs")
        if (handler != null) {
            FlutterConsoles.displayProcessLater(handler, project, null) {
                handler.startNotify()
            }
        }
    }

    /**
     * Executes the 'dart run build_runner watch' command without delete-conflicting-outputs.
     */
    fun runWatchNoDelete() {
        val handler = startProcessOrShowError("watch", "build_runner watch")
        if (handler != null) {
            FlutterConsoles.displayProcessLater(handler, project, null) {
                handler.startNotify()
            }
        }
    }

    /**
     * Executes the 'dart run build_runner build --verbose' command and displays the process output in the Flutter console.
     */
    fun runBuildVerbose() {
        val handler = startProcessOrShowError("build", "build_runner build (verbose)", "--delete-conflicting-outputs", "--verbose")
        if (handler != null) {
            FlutterConsoles.displayProcessLater(handler, project, null) {
                handler.startNotify()
            }
        }
    }

    /**
     * Executes the 'dart run build_runner watch --verbose' command and displays the process output in the Flutter console.
     */
    fun runWatchVerbose() {
        val handler = startProcessOrShowError("watch", "build_runner watch (verbose)", "--delete-conflicting-outputs", "--verbose")
        if (handler != null) {
            FlutterConsoles.displayProcessLater(handler, project, null) {
                handler.startNotify()
            }
        }
    }

    /**
     * Executes the 'dart run build_runner clean' command and displays the process output in the Flutter console.
     */
    fun runClean() {
        val handler = startProcessOrShowError("clean", "build_runner clean")
        if (handler != null) {
            FlutterConsoles.displayProcessLater(handler, project, null) {
                handler.startNotify()
            }
        }
    }

    /**
     * Attempts to start the build_runner process and returns a process handler.
     *
     * @param command The build_runner command to execute (build, watch, or clean)
     * @param presentableName The name to display in the console tab
     * @param additionalArgs Additional arguments to pass to the command
     * @return ColoredProcessHandler if the process starts successfully, null if an error occurs
     */
    private fun startProcessOrShowError(command: String, presentableName: String, vararg additionalArgs: String): ColoredProcessHandler? {
        try {
            val commandLine = createGeneralCommandLine(command, *additionalArgs)
            val handler = ColoredProcessHandler(commandLine)
            // Set the presentable command line to hide the full path
            handler.setShouldDestroyProcessRecursively(true)
            return handler
        } catch (e: ExecutionException) {
            FlutterMessages.showError(
                "Dart build_runner",
                "Failed to execute dart run build_runner $command: ${e.message}",
                project
            )
            return null
        }
    }

    /**
     * Creates and configures the command line for the dart build_runner command.
     *
     * @param command The build_runner command to execute (build, watch, or clean)
     * @param additionalArgs Additional arguments to pass to the command
     * @return GeneralCommandLine configured with the appropriate dart command and parameters
     * @throws ExecutionException if the project base path cannot be determined
     */
    private fun createGeneralCommandLine(command: String, vararg additionalArgs: String): GeneralCommandLine {
        val projectBasePath = project.basePath ?: throw ExecutionException("Cannot determine project base path")

        val line = GeneralCommandLine()
        line.charset = StandardCharsets.UTF_8
        line.exePath = flutterSdk.homePath + "/bin/dart"
        line.setWorkDirectory(projectBasePath)
        line.addParameter("run")
        line.addParameter("build_runner")
        line.addParameter(command)
        additionalArgs.forEach { line.addParameter(it) }

        return line
    }
}