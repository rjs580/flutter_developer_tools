package dev.rutvik.flutter_developer_tools.arb

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import icons.FlutterIcons
import io.flutter.sdk.FlutterSdk
import io.flutter.utils.UIUtils
import java.util.function.Function
import javax.swing.JComponent


/**
 * Provides notification panel for .arb files in Flutter projects.
 * This notification allows quick access to Flutter localization generation commands.
 */
class ArbFileNotificationProvider : EditorNotificationProvider {
    /**
     * Checks if the file is an .arb file and Flutter SDK is available,
     * then provides a notification panel with localization generation actions.
     *
     * @param project Current project
     * @param file Virtual file being checked
     * @return Function that creates notification panel or null if conditions are not met
     */
    override fun collectNotificationData(
        project: Project,
        file: VirtualFile
    ): Function<in FileEditor, out JComponent?>? {
        // Check if the file is an .arb file or l10n.yaml file
        if (file.extension != "arb" && file.name != "l10n.yaml") {
            return null
        }

        // Check if Flutter SDK is available
        val flutterSdk = FlutterSdk.getFlutterSdk(project) ?: return null

        return Function { fileEditor ->
            FlutterArbActionsPanel(project, flutterSdk)
        }
    }

    /**
     * Panel that displays Flutter localization actions for .arb files.
     * Provides a button to generate localizations using flutter gen-l10n command.
     */
    private class FlutterArbActionsPanel(
        private val project: Project,
        private val flutterSdk: FlutterSdk
    ) : EditorNotificationPanel(UIUtils.getEditorNotificationBackgroundColor()) {
        init {
            icon(FlutterIcons.Flutter)
            text("Flutter localization")

            // "flutter gen-l10n"
            val label = createActionLabel("Generate localizations") {
                runGenL10n()
            }
            label.toolTipText = "Generate Flutter localizations from .arb files"
        }

        /**
         * Executes the Flutter gen-l10n command to generate localizations from .arb files.
         */
        private fun runGenL10n() {
            val runner = FlutterGenL10nRunner(project, flutterSdk)
            runner.runGenL10n()
        }
    }
}