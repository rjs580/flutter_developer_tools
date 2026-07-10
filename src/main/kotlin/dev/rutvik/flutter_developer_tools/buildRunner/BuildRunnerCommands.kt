
package dev.rutvik.flutter_developer_tools.buildRunner

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ColoredProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
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
    private val flutterSdk: FlutterSdk,
    private val contextFile: VirtualFile? = null
) {
    companion object {
        // Store the currently running watch process handler
        private var activeWatchHandler: ColoredProcessHandler? = null

        /**
         * Checks if a watch process is currently running.
         */
        fun isWatchRunning(): Boolean {
            return activeWatchHandler?.isProcessTerminated == false
        }

        /**
         * Stops the currently running watch process.
         */
        fun stopWatch(): Boolean {
            val handler = activeWatchHandler
            if (handler != null && !handler.isProcessTerminated) {
                handler.destroyProcess()
                activeWatchHandler = null
                return true
            }
            return false
        }
    }

    /**
     * Executes the 'dart run build_runner build' command and displays the process output in the Flutter console.
     *
     * This method starts the build process and shows its output in a dedicated Flutter console window.
     * If the process fails to start, an error message is displayed to the user.
     */
    fun runBuild() {
        // Stop any existing watch process
        if (isWatchRunning()) {
            FlutterMessages.showWarning(
                "Build Runner Watch",
                "A watch process is already running. Please stop it first.",
                project
            )
            return
        }

        val handler = startProcessOrShowError("build", "--delete-conflicting-outputs")
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
        // Stop any existing watch process
        if (isWatchRunning()) {
            FlutterMessages.showWarning(
                "Build Runner Watch",
                "A watch process is already running. Please stop it first.",
                project
            )
            return
        }

        val handler = startProcessOrShowError("build")
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
        // Stop any existing watch process
        if (isWatchRunning()) {
            FlutterMessages.showWarning(
                "Build Runner Watch",
                "A watch process is already running. Please stop it first.",
                project
            )
            return
        }

        val handler = startProcessOrShowError("watch", "--delete-conflicting-outputs")
        if (handler != null) {
            activeWatchHandler = handler

            // Add listener to clean up when process terminates
            handler.addProcessListener(object : ProcessListener {
                override fun processTerminated(event: ProcessEvent) {
                    if (activeWatchHandler == handler) {
                        activeWatchHandler = null
                    }
                }
            })

            FlutterConsoles.displayProcessLater(handler, project, null) {
                handler.startNotify()
            }
        }
    }

    /**
     * Executes the 'dart run build_runner watch' command without delete-conflicting-outputs.
     */
    fun runWatchNoDelete() {
        // Stop any existing watch process
        if (isWatchRunning()) {
            FlutterMessages.showWarning(
                "Build Runner Watch",
                "A watch process is already running. Please stop it first.",
                project
            )
            return
        }

        val handler = startProcessOrShowError("watch")
        if (handler != null) {
            activeWatchHandler = handler

            // Add listener to clean up when process terminates
            handler.addProcessListener(object : ProcessListener {
                override fun processTerminated(event: ProcessEvent) {
                    if (activeWatchHandler == handler) {
                        activeWatchHandler = null
                    }
                }
            })

            FlutterConsoles.displayProcessLater(handler, project, null) {
                handler.startNotify()
            }
        }
    }

    /**
     * Executes the 'dart run build_runner build --verbose' command and displays the process output in the Flutter console.
     */
    fun runBuildVerbose() {
        // Stop any existing watch process
        if (isWatchRunning()) {
            FlutterMessages.showWarning(
                "Build Runner Watch",
                "A watch process is already running. Please stop it first.",
                project
            )
            return
        }

        val handler = startProcessOrShowError("build", "--delete-conflicting-outputs", "--verbose")
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
        // Stop any existing watch process
        if (isWatchRunning()) {
            FlutterMessages.showWarning(
                "Build Runner Watch",
                "A watch process is already running. Please stop it first.",
                project
            )
            return
        }

        val handler = startProcessOrShowError("watch", "--delete-conflicting-outputs", "--verbose")
        if (handler != null) {
            activeWatchHandler = handler

            // Add listener to clean up when process terminates
            handler.addProcessListener(object : ProcessListener {
                override fun processTerminated(event: ProcessEvent) {
                    if (activeWatchHandler == handler) {
                        activeWatchHandler = null
                    }
                }
            })

            FlutterConsoles.displayProcessLater(handler, project, null) {
                handler.startNotify()
            }
        }
    }

    /**
     * Executes the 'dart run build_runner clean' command and displays the process output in the Flutter console.
     */
    fun runClean() {
        // Stop any existing watch process
        if (isWatchRunning()) {
            FlutterMessages.showWarning(
                "Build Runner Watch",
                "A watch process is already running. Please stop it first.",
                project
            )
            return
        }

        val handler = startProcessOrShowError("clean")
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
     * @param additionalArgs Additional arguments to pass to the command
     * @return ColoredProcessHandler if the process starts successfully, null if an error occurs
     */
    private fun startProcessOrShowError(command: String, vararg additionalArgs: String): ColoredProcessHandler? {
        try {
            val commandLine = createGeneralCommandLine(command, *additionalArgs)
            val handler = ColoredProcessHandler(commandLine)
            // Ensure the process and all child processes are killed when stop is pressed
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
        val line = GeneralCommandLine()
        line.charset = StandardCharsets.UTF_8
        line.exePath = resolveDartExecutable()
        line.setWorkDirectory(resolveWorkingDirectory())
        line.addParameter("run")
        line.addParameter("build_runner")
        line.addParameter(command)
        additionalArgs.forEach { line.addParameter(it) }

        return line
    }

    /**
     * Resolves the Dart executable, honoring the OS-specific launcher name
     * (dart.bat on Windows, dart elsewhere). GeneralCommandLine does not append
     * the extension itself.
     */
    private fun resolveDartExecutable(): String {
        val executable = if (SystemInfo.isWindows) "dart.bat" else "dart"
        return FileUtil.toSystemDependentName("${flutterSdk.homePath}/bin/$executable")
    }

    /**
     * Resolves the working directory to the pub root of the triggering file when available
     * (so build_runner runs in the correct package in a monorepo), falling back to the project base path.
     */
    private fun resolveWorkingDirectory(): String {
        val pubRootPath = contextFile?.let { PubRoot.forFile(it)?.root?.path }
        return pubRootPath ?: project.basePath ?: throw ExecutionException("Cannot determine working directory")
    }
}