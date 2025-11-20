package dev.rutvik.flutter_developer_tools.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * Persistent settings state for Flutter Developer Tools breadcrumbs functionality.
 * Handles storage and retrieval of user preferences related to widget breadcrumbs display.
 *
 * Settings are stored in FlutterDeveloperToolsSettings.xml file.
 */
@State(
    name = "dev.rutvik.flutter_developer_tools.settings.BreadcrumbsSettingsState",
    storages = [Storage("FlutterDeveloperToolsSettings.xml")]
)
class BreadcrumbsSettingsState : PersistentStateComponent<BreadcrumbsSettingsState> {

    /**
     * Custom widget names will also be mapped to their standard counterparts (e.g. MyScaffold -> Scaffold).
     */
    var inferStandardWidgetNames: Boolean = true

    /**
     * Maps widget names to their corresponding icon identifiers in the IDE.
     * Example: "Column" -> "Actions.SplitVertically"
     */
    var widgetIconMappings: MutableMap<String, String> = mutableMapOf()

    /**
     * Set of custom widget names that should be recognized by the plugin.
     * These are in addition to the default recognized widgets.
     */
    var customRecognizedWidgets: MutableSet<String> = mutableSetOf()

    override fun getState(): BreadcrumbsSettingsState = this

    override fun loadState(state: BreadcrumbsSettingsState) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        fun getInstance(): BreadcrumbsSettingsState =
            ApplicationManager.getApplication().getService(BreadcrumbsSettingsState::class.java)

        /**
         * Default Flutter widgets that are recognized by the plugin
         */
        val DEFAULT_RECOGNIZED_WIDGETS = listOf(
            // Layout
            "Column", "Row", "Stack", "Flex", "Wrap", "Flow",
            "Container", "SizedBox", "AspectRatio", "ConstrainedBox", "LimitedBox",
            "Padding", "Center", "Align", "FittedBox",
            "Expanded", "Flexible", "Spacer",

            // Scrollable
            "ListView", "GridView", "CustomScrollView", "SingleChildScrollView", "PageView",
            "NestedScrollView", "ScrollView",

            // Interactive
            "GestureDetector", "InkWell", "Draggable", "DragTarget", "LongPressDraggable",
            "Dismissible", "InteractiveViewer",

            // Text
            "Text", "RichText", "SelectableText", "DefaultTextStyle",

            // Input
            "TextField", "TextFormField", "EditableText", "Form", "FormField",

            // Buttons
            "ElevatedButton", "TextButton", "OutlinedButton", "FilledButton",
            "IconButton", "FloatingActionButton", "MaterialButton", "CupertinoButton",

            // Icons & Images
            "Icon", "ImageIcon", "Image", "FadeInImage", "CircleAvatar",

            // App Structure
            "Scaffold", "AppBar", "BottomNavigationBar", "Drawer", "TabBar", "TabBarView",
            "NavigationBar", "NavigationRail", "BottomAppBar", "SliverAppBar",

            // Material App
            "MaterialApp", "CupertinoApp", "WidgetsApp",

            // Cards & Surfaces
            "Card", "Material", "Surface", "Paper",

            // Lists
            "ListTile", "ExpansionTile", "CheckboxListTile", "RadioListTile", "SwitchListTile",

            // Forms & Controls
            "Checkbox", "Radio", "Switch", "Slider", "DropdownButton", "DropdownMenu",
            "PopupMenuButton", "MenuBar",

            // Navigation
            "Navigator", "Route", "PageRoute", "MaterialPageRoute", "CupertinoPageRoute",

            // Animations
            "AnimatedContainer", "AnimatedOpacity", "AnimatedCrossFade", "AnimatedSwitcher",
            "Hero", "FadeTransition", "ScaleTransition", "SlideTransition", "RotationTransition",

            // State Management
            "Provider", "Consumer", "Selector", "ChangeNotifierProvider", "StreamProvider",
            "FutureProvider", "ValueListenableBuilder",

            // Builders
            "Builder", "LayoutBuilder", "FutureBuilder", "StreamBuilder", "StatefulBuilder",

            // Dialogs & Overlays
            "Dialog", "AlertDialog", "SimpleDialog", "BottomSheet", "ModalBottomSheet",
            "Snackbar", "Banner", "Tooltip",

            // Cupertino (iOS-style)
            "CupertinoNavigationBar", "CupertinoTabBar", "CupertinoTextField", "CupertinoSwitch",
            "CupertinoSlider", "CupertinoActivityIndicator", "CupertinoAlertDialog"
        ).sorted()
    }
}