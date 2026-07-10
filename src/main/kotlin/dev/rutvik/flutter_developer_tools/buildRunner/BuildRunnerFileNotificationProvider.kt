
package dev.rutvik.flutter_developer_tools.buildRunner

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import com.intellij.ui.EditorNotifications
import com.intellij.ui.awt.RelativePoint
import icons.FlutterIcons
import io.flutter.pub.PubRoot
import io.flutter.sdk.FlutterSdk
import io.flutter.utils.UIUtils
import java.awt.Point
import java.util.function.Function
import javax.swing.JComponent


/**
 * Provides notification panel for files that require build_runner in Flutter projects.
 * This notification allows quick access to Flutter build_runner commands.
 */
class BuildRunnerFileNotificationProvider : EditorNotificationProvider {

    companion object {
        private val GENERATED_PART_REGEX = Regex("""part\s+['"][^'"]*\.(g|freezed)\.dart['"]""")
        private val CODE_GEN_ANNOTATIONS = listOf(
            "@JsonSerializable", "@freezed", "@Freezed", "@injectable", "@Injectable",
            "@RestApi", "@HiveType", "@Entity", "@CopyWith", "@GenerateMocks",
            "@embedded", "@Embedded", "@collection", "@Collection", "@DataClassName", "@DriftDatabase"
        )
    }

    /**
     * Checks if the file requires build_runner and Flutter SDK is available,
     * then provides a notification panel with build_runner actions.
     *
     * @param project Current project
     * @param file Virtual file being checked
     * @return Function that creates notification panel or null if conditions are not met
     */
    override fun collectNotificationData(
        project: Project,
        file: VirtualFile
    ): Function<in FileEditor, out JComponent?>? {
        // Check if Flutter SDK is available
        val flutterSdk = FlutterSdk.getFlutterSdk(project) ?: return null

        // Handle build_runner related files
        if (shouldShowBuildRunnerNotification(project, file)) {
            return Function { _ ->
                BuildRunnerActionsPanel(project, flutterSdk, file)
            }
        }

        return null
    }

    /**
     * Determines if build_runner notification should be shown for the given file.
     */
    private fun shouldShowBuildRunnerNotification(project: Project, file: VirtualFile): Boolean {
        // Check for build.yaml file
        if (file.name == "build.yaml") {
            return hasBuildRunnerInPubspec(project, file)
        }

        // Only check .dart files
        if (file.extension != "dart") {
            return false
        }

        // Check if build_runner is in dev_dependencies
        if (!hasBuildRunnerInPubspec(project, file)) {
            return false
        }

        // Check if this dart file uses code generation
        return usesCodeGeneration(file)
    }

    /**
     * Checks if the file's project has build_runner in dev_dependencies.
     */
    private fun hasBuildRunnerInPubspec(project: Project, file: VirtualFile): Boolean {
        val pubRoot = PubRoot.forFile(file) ?: return false
        val pubspecFile = pubRoot.pubspec

        try {
            val psiFile = com.intellij.psi.PsiManager.getInstance(project).findFile(pubspecFile) ?: return false

            if (psiFile !is org.jetbrains.yaml.psi.YAMLFile) return false

            val topLevelValue = psiFile.documents.firstOrNull()?.topLevelValue
            if (topLevelValue !is org.jetbrains.yaml.psi.YAMLMapping) return false

            // Get dev_dependencies section
            val devDependencies = topLevelValue.getKeyValueByKey("dev_dependencies")?.value
            if (devDependencies !is org.jetbrains.yaml.psi.YAMLMapping) return false

            // Check if build_runner exists
            return devDependencies.getKeyValueByKey("build_runner") != null
        } catch (e: com.intellij.openapi.progress.ProcessCanceledException) {
            throw e
        } catch (_: Exception) {
            return false
        }
    }

    /**
     * Checks if a Dart file uses code generation patterns.
     */
    private fun usesCodeGeneration(file: VirtualFile): Boolean {
        try {
            // Read the in-memory document instead of re-reading the file from disk on
            // every notification pass.
            val document = com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getDocument(file)
            val content = document?.charsSequence ?: return false

            if (GENERATED_PART_REGEX.containsMatchIn(content)) {
                return true
            }

            return CODE_GEN_ANNOTATIONS.any { content.contains(it) }
        } catch (e: com.intellij.openapi.progress.ProcessCanceledException) {
            throw e
        } catch (_: Exception) {
            return false
        }
    }

    /**
     * Panel that displays build_runner actions.
     * Provides buttons and dropdown for build_runner commands.
     */
    private class BuildRunnerActionsPanel(
        private val project: Project,
        private val flutterSdk: FlutterSdk,
        private val contextFile: VirtualFile
    ) : EditorNotificationPanel(UIUtils.getEditorNotificationBackgroundColor()) {
        private var moreLabel: com.intellij.ui.HyperlinkLabel? = null

        init {
            icon(FlutterIcons.Dart_16)
            text("Code generation available")

            // Show different buttons based on watch status
            if (BuildRunnerCommands.isWatchRunning()) {
                // Show stop button when watch is running
                val stopLabel = createActionLabel("Stop Watch") {
                    stopWatch()
                }
                stopLabel.toolTipText = "Stop the running build_runner watch process"
            } else {
                // Show build and watch buttons when watch is not running
                val buildLabel = createActionLabel("Build") {
                    runBuild()
                }
                buildLabel.toolTipText = "Run build_runner build --delete-conflicting-outputs"

                val watchLabel = createActionLabel("Watch") {
                    runWatch()
                }
                watchLabel.toolTipText = "Run build_runner watch --delete-conflicting-outputs"

                // Dropdown with more options
                moreLabel = createActionLabel("More...") {
                    showMoreOptions()
                }
                moreLabel?.toolTipText = "Show more build_runner options"
            }
        }

        /**
         * Refreshes the notification panel to reflect current watch state.
         */
        private fun refreshPanel() {
            EditorNotifications.getInstance(project).updateAllNotifications()
        }

        /**
         * Shows dropdown menu with additional build_runner options.
         */
        private fun showMoreOptions() {
            val group = com.intellij.openapi.actionSystem.DefaultActionGroup().apply {
                if (BuildRunnerCommands.isWatchRunning()) {
                    add(BuildRunnerAction("Stop Watch", ::stopWatch))
                    addSeparator()
                }
                add(BuildRunnerAction("Build (no delete conflicts)", ::runBuildNoDelete))
                add(BuildRunnerAction("Watch (no delete conflicts)", ::runWatchNoDelete))
                addSeparator()
                add(BuildRunnerAction("Build (verbose)", ::runBuildVerbose))
                add(BuildRunnerAction("Watch (verbose)", ::runWatchVerbose))
                addSeparator()
                add(BuildRunnerAction("Clean", ::runClean))
            }

            val context = com.intellij.openapi.actionSystem.DataContext.EMPTY_CONTEXT
            val popup = com.intellij.openapi.ui.popup.JBPopupFactory.getInstance()
                .createActionGroupPopup(
                    null,
                    group,
                    context,
                    com.intellij.openapi.ui.popup.JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
                    true
                )

            // Show popup below the "More..." label
            moreLabel?.let { label ->
                popup.show(RelativePoint(label, Point(0, label.height)))
            }
        }

        /**
         * Stops the running watch process.
         */
        private fun stopWatch() {
            if (BuildRunnerCommands.stopWatch()) {
                com.intellij.notification.NotificationGroupManager.getInstance()
                    .getNotificationGroup("Flutter Developer Tools")
                    .createNotification(
                        "Build runner watch stopped",
                        com.intellij.notification.NotificationType.INFORMATION
                    )
                    .notify(project)

                // Refresh the panel to show Build/Watch buttons again
                refreshPanel()
            }
        }

        /**
         * Executes build_runner build command.
         */
        private fun runBuild() {
            val runner = BuildRunnerCommands(project, flutterSdk, contextFile)
            runner.runBuild()
        }

        /**
         * Executes build_runner build command without delete-conflicting-outputs.
         */
        private fun runBuildNoDelete() {
            val runner = BuildRunnerCommands(project, flutterSdk, contextFile)
            runner.runBuildNoDelete()
        }

        /**
         * Executes build_runner watch command.
         */
        private fun runWatch() {
            val runner = BuildRunnerCommands(project, flutterSdk, contextFile)
            runner.runWatch()

            // Refresh the panel to show Stop button
            refreshPanel()
        }

        /**
         * Executes build_runner watch command without delete-conflicting-outputs.
         */
        private fun runWatchNoDelete() {
            val runner = BuildRunnerCommands(project, flutterSdk, contextFile)
            runner.runWatchNoDelete()

            // Refresh the panel to show Stop button
            refreshPanel()
        }

        /**
         * Executes build_runner build with verbose output.
         */
        private fun runBuildVerbose() {
            val runner = BuildRunnerCommands(project, flutterSdk, contextFile)
            runner.runBuildVerbose()
        }

        /**
         * Executes build_runner watch with verbose output.
         */
        private fun runWatchVerbose() {
            val runner = BuildRunnerCommands(project, flutterSdk, contextFile)
            runner.runWatchVerbose()

            // Refresh the panel to show Stop button
            refreshPanel()
        }

        /**
         * Executes build_runner clean command.
         */
        private fun runClean() {
            val runner = BuildRunnerCommands(project, flutterSdk, contextFile)
            runner.runClean()
        }

        /**
         * Action for build_runner commands in dropdown menu.
         */
        private inner class BuildRunnerAction(
            text: String,
            private val action: () -> Unit
        ) : com.intellij.openapi.actionSystem.AnAction(text) {
            override fun actionPerformed(e: com.intellij.openapi.actionSystem.AnActionEvent) {
                action()
            }
        }
    }
}