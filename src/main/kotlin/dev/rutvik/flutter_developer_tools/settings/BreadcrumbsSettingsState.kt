package dev.rutvik.flutter_developer_tools.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

@State(
    name = "dev.rutvik.flutter_developer_tools.settings.BreadcrumbsSettingsState",
    storages = [Storage("FlutterDeveloperToolsSettings.xml")]
)
class BreadcrumbsSettingsState : PersistentStateComponent<BreadcrumbsSettingsState> {

    var inferStandardWidgetNames: Boolean = true

    // Map of widget name to icon identifier (e.g., "Column" -> "Actions.SplitVertically")
    var widgetIconMappings: MutableMap<String, String> = mutableMapOf()

    // Custom widget names that user wants to recognize (in addition to defaults)
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