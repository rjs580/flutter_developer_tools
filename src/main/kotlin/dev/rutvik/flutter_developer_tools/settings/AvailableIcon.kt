
package dev.rutvik.flutter_developer_tools.settings

import com.intellij.icons.AllIcons
import javax.swing.Icon

/**
 * Comprehensive catalog of all available IntelliJ icons
 * Based on https://intellij-icons.jetbrains.design/
 */
data class IconDefinition(
    val id: String,
    val displayName: String,
    val category: String,
    val icon: Icon
)

object AvailableIcons {

    private val allIconsDefinition: List<IconDefinition> by lazy {
        listOf(
            // Actions - Common
            IconDefinition("Actions.Execute", "Execute/Run", "Actions", AllIcons.Actions.Execute),
            IconDefinition("Actions.Compile", "Compile/Build", "Actions", AllIcons.Actions.Compile),
            IconDefinition("Actions.Cancel", "Cancel/Close", "Actions", AllIcons.Actions.Cancel),
            IconDefinition("Actions.Refresh", "Refresh/Reload", "Actions", AllIcons.Actions.Refresh),
            IconDefinition("Actions.Edit", "Edit", "Actions", AllIcons.Actions.Edit),
            IconDefinition("Actions.Find", "Find/Search", "Actions", AllIcons.Actions.Find),
            IconDefinition("Actions.Copy", "Copy", "Actions", AllIcons.Actions.Copy),
            IconDefinition("Actions.DeleteTag", "Delete", "Actions", AllIcons.Actions.DeleteTag),
            IconDefinition("Actions.Forward", "Forward/Next", "Actions", AllIcons.Actions.Forward),
            IconDefinition("Actions.Back", "Back/Previous", "Actions", AllIcons.Actions.Back),
            IconDefinition("Actions.MoveUp", "Move Up", "Actions", AllIcons.Actions.MoveUp),
            IconDefinition("Actions.MoveDown", "Move Down", "Actions", AllIcons.Actions.MoveDown),
            IconDefinition("Actions.Show", "Show/View", "Actions", AllIcons.Actions.Show),
            IconDefinition("Actions.Profile", "Profile", "Actions", AllIcons.Actions.Profile),
            IconDefinition("Actions.Preview", "Preview", "Actions", AllIcons.Actions.Preview),
            IconDefinition("Actions.Download", "Download", "Actions", AllIcons.Actions.Download),
            IconDefinition("Actions.Upload", "Upload", "Actions", AllIcons.Actions.Upload),
            IconDefinition("Actions.Suspend", "Suspend/Pause", "Actions", AllIcons.Actions.Suspend),
            IconDefinition("Actions.Resume", "Resume/Play", "Actions", AllIcons.Actions.Resume),
            IconDefinition("Actions.Restart", "Restart", "Actions", AllIcons.Actions.Restart),
            IconDefinition("Actions.GC", "Garbage Collect", "Actions", AllIcons.Actions.GC),
            IconDefinition("Actions.Install", "Install", "Actions", AllIcons.Actions.Install),
            IconDefinition("Actions.Lightning", "Lightning/Quick", "Actions", AllIcons.Actions.Lightning),
            IconDefinition("Actions.StartDebugger", "Debug", "Actions", AllIcons.Actions.StartDebugger),
            IconDefinition("Actions.Checked", "Checked/Success", "Actions", AllIcons.Actions.Checked),
            IconDefinition("Actions.Help", "Help/Info", "Actions", AllIcons.Actions.Help),
            IconDefinition("Actions.More", "More Options", "Actions", AllIcons.Actions.More),
            IconDefinition("Actions.AddList", "Add to List", "Actions", AllIcons.Actions.AddList),
            IconDefinition("Actions.BuildLoadChanges", "Build Changes", "Actions", AllIcons.Actions.BuildLoadChanges),
            IconDefinition("Actions.Close", "Close", "Actions", AllIcons.Actions.Close),
            IconDefinition("Actions.Colors", "Colors", "Actions", AllIcons.Actions.Colors),
            IconDefinition("Actions.Diff", "Diff/Compare", "Actions", AllIcons.Actions.Diff),
            IconDefinition("Actions.DiagramDiff", "Diagram Diff", "Actions", AllIcons.Actions.DiagramDiff),
            IconDefinition("Actions.ToggleSoftWrap", "Toggle/Switch", "Actions", AllIcons.Actions.ToggleSoftWrap),
            IconDefinition("Actions.ToggleVisibility", "Toggle Visibility", "Actions", AllIcons.Actions.ToggleVisibility),
            IconDefinition("Actions.Properties", "Properties/Settings", "Actions", AllIcons.Actions.Properties),
            IconDefinition("Actions.ListChanges", "List Changes", "Actions", AllIcons.Actions.ListChanges),
            IconDefinition("Actions.GroupBy", "Group By", "Actions", AllIcons.Actions.GroupBy),
            IconDefinition("Actions.PreviewDetails", "Preview Details", "Actions", AllIcons.Actions.PreviewDetails),
            IconDefinition("Actions.ForceRefresh", "Force Refresh", "Actions", AllIcons.Actions.ForceRefresh),
            IconDefinition("Actions.MenuOpen", "Menu Open", "Actions", AllIcons.Actions.MenuOpen),
            IconDefinition("Actions.MenuCut", "Menu Cut", "Actions", AllIcons.Actions.MenuCut),
            IconDefinition("Actions.MenuPaste", "Menu Paste", "Actions", AllIcons.Actions.MenuPaste),
            IconDefinition("Actions.MenuSaveall", "Save All", "Actions", AllIcons.Actions.MenuSaveall),
            IconDefinition("Actions.Share", "Share", "Actions", AllIcons.Actions.Share),
            IconDefinition("Actions.Stub", "Stub", "Actions", AllIcons.Actions.Stub),
            IconDefinition("Actions.Annotate", "Annotate", "Actions", AllIcons.Actions.Annotate),
            IconDefinition("Actions.InSelection", "In Selection", "Actions", AllIcons.Actions.InSelection),
            IconDefinition("Actions.IntentionBulb", "Intention Bulb", "Actions", AllIcons.Actions.IntentionBulb),
            IconDefinition("Actions.Search", "Search", "Actions", AllIcons.Actions.Search),
            IconDefinition("Actions.SearchNewLine", "Search New Line", "Actions", AllIcons.Actions.SearchNewLine),
            IconDefinition("Actions.SearchWithHistory", "Search With History", "Actions", AllIcons.Actions.SearchWithHistory),
            IconDefinition("Actions.Regex", "Regex", "Actions", AllIcons.Actions.Regex),
            IconDefinition("Actions.Words", "Words", "Actions", AllIcons.Actions.Words),
            IconDefinition("Actions.MatchCase", "Match Case", "Actions", AllIcons.Actions.MatchCase),
            IconDefinition("Actions.PreserveCase", "Preserve Case", "Actions", AllIcons.Actions.PreserveCase),

            // Actions - Layout & Positioning
            IconDefinition("Actions.SplitHorizontally", "Split Horizontal", "Actions - Layout", AllIcons.Actions.SplitHorizontally),
            IconDefinition("Actions.SplitVertically", "Split Vertical", "Actions - Layout", AllIcons.Actions.SplitVertically),
            IconDefinition("Actions.MoveToLeftBottom", "Align/Position", "Actions - Layout", AllIcons.Actions.MoveToLeftBottom),
            IconDefinition("Actions.MoveToLeftTop", "Move to Left Top", "Actions - Layout", AllIcons.Actions.MoveToLeftTop),
            IconDefinition("Actions.MoveToRightBottom", "Move to Right Bottom", "Actions - Layout", AllIcons.Actions.MoveToRightBottom),
            IconDefinition("Actions.MoveToRightTop", "Move to Right Top", "Actions - Layout", AllIcons.Actions.MoveToRightTop),
            IconDefinition("Actions.Expandall", "Expand All", "Actions - Layout", AllIcons.Actions.Expandall),
            IconDefinition("Actions.Collapseall", "Collapse All", "Actions - Layout", AllIcons.Actions.Collapseall),

            // Nodes - Code Elements
            IconDefinition("Nodes.Class", "Class", "Nodes - Code", AllIcons.Nodes.Class),
            IconDefinition("Nodes.AbstractClass", "Abstract Class", "Nodes - Code", AllIcons.Nodes.AbstractClass),
            IconDefinition("Nodes.Interface", "Interface", "Nodes - Code", AllIcons.Nodes.Interface),
            IconDefinition("Nodes.Method", "Method", "Nodes - Code", AllIcons.Nodes.Method),
            IconDefinition("Nodes.AbstractMethod", "Abstract Method", "Nodes - Code", AllIcons.Nodes.AbstractMethod),
            IconDefinition("Nodes.Function", "Function", "Nodes - Code", AllIcons.Nodes.Function),
            IconDefinition("Nodes.Field", "Field", "Nodes - Code", AllIcons.Nodes.Field),
            IconDefinition("Nodes.Property", "Property", "Nodes - Code", AllIcons.Nodes.Property),
            IconDefinition("Nodes.Variable", "Variable", "Nodes - Code", AllIcons.Nodes.Variable),
            IconDefinition("Nodes.Parameter", "Parameter", "Nodes - Code", AllIcons.Nodes.Parameter),
            IconDefinition("Nodes.Enum", "Enum", "Nodes - Code", AllIcons.Nodes.Enum),
            IconDefinition("Nodes.Static", "Static", "Nodes - Code", AllIcons.Nodes.Static),
            IconDefinition("Nodes.Constant", "Constant", "Nodes - Code", AllIcons.Nodes.Constant),
            IconDefinition("Nodes.Lambda", "Lambda", "Nodes - Code", AllIcons.Nodes.Lambda),
            IconDefinition("Nodes.Type", "Type", "Nodes - Code", AllIcons.Nodes.Type),
            IconDefinition("Nodes.Tag", "Tag/Element", "Nodes - Code", AllIcons.Nodes.Tag),
            IconDefinition("Nodes.Annotationtype", "Annotation", "Nodes - Code", AllIcons.Nodes.Annotationtype),
            IconDefinition("Nodes.AnonymousClass", "Anonymous Class", "Nodes - Code", AllIcons.Nodes.AnonymousClass),
            IconDefinition("Nodes.ExceptionClass", "Exception Class", "Nodes - Code", AllIcons.Nodes.ExceptionClass),
            IconDefinition("Nodes.Record", "Record", "Nodes - Code", AllIcons.Nodes.Record),

            // Nodes - Structure
            IconDefinition("Nodes.Folder", "Folder/Container", "Nodes - Structure", AllIcons.Nodes.Folder),
            IconDefinition("Nodes.Module", "Module/App", "Nodes - Structure", AllIcons.Nodes.Module),
            IconDefinition("Nodes.Package", "Package", "Nodes - Structure", AllIcons.Nodes.Package),
            IconDefinition("Nodes.Artifact", "Artifact/Asset", "Nodes - Structure", AllIcons.Nodes.Artifact),
            IconDefinition("Nodes.EmptyNode", "Empty/Container", "Nodes - Structure", AllIcons.Nodes.EmptyNode),
            IconDefinition("Nodes.Plugin", "Plugin/Extension", "Nodes - Structure", AllIcons.Nodes.Plugin),
            IconDefinition("Nodes.ResourceBundle", "Resource Bundle", "Nodes - Structure", AllIcons.Nodes.ResourceBundle),
            IconDefinition("Nodes.Controller", "Controller", "Nodes - Structure", AllIcons.Nodes.Controller),
            IconDefinition("Nodes.DataSchema", "Data Schema", "Nodes - Structure", AllIcons.Nodes.DataSchema),
            IconDefinition("Nodes.DataTables", "Data Tables", "Nodes - Structure", AllIcons.Nodes.DataTables),
            IconDefinition("Nodes.ModelClass", "Model Class", "Nodes - Structure", AllIcons.Nodes.ModelClass),
            IconDefinition("Nodes.Services", "Services", "Nodes - Structure", AllIcons.Nodes.Services),
            IconDefinition("Nodes.Template", "Template", "Nodes - Structure", AllIcons.Nodes.Template),
            IconDefinition("Nodes.ConfigFolder", "Config Folder", "Nodes - Structure", AllIcons.Nodes.ConfigFolder),
            IconDefinition("Nodes.WebFolder", "Web Folder", "Nodes - Structure", AllIcons.Nodes.WebFolder),

            // Nodes - UI & Display
            IconDefinition("Nodes.Editorconfig", "Editor Config", "Nodes - UI", AllIcons.Nodes.Editorconfig),
            IconDefinition("Nodes.Console", "Console/Terminal", "Nodes - UI", AllIcons.Nodes.Console),
            IconDefinition("Nodes.Toolbox", "Toolbox", "Nodes - UI", AllIcons.Nodes.Toolbox),
            IconDefinition("Nodes.UpLevel", "Up Level", "Nodes - UI", AllIcons.Nodes.UpLevel),
            IconDefinition("Nodes.HomeFolder", "Home Folder", "Nodes - UI", AllIcons.Nodes.HomeFolder),
            IconDefinition("Nodes.IdeaProject", "Project", "Nodes - UI", AllIcons.Nodes.IdeaProject),
            IconDefinition("Nodes.Bookmark", "Bookmark", "Nodes - UI", AllIcons.Nodes.Bookmark),
            IconDefinition("Nodes.Favorite", "Favorite", "Nodes - UI", AllIcons.Nodes.Favorite),
            IconDefinition("Nodes.TabAlert", "Tab Alert", "Nodes - UI", AllIcons.Nodes.TabAlert),
            IconDefinition("Nodes.TestSourceFolder", "Test Folder", "Nodes - UI", AllIcons.Nodes.TestSourceFolder),
            IconDefinition("Nodes.WarningIntroduction", "Warning", "Nodes - UI", AllIcons.Nodes.WarningIntroduction),
            IconDefinition("Nodes.ErrorIntroduction", "Error", "Nodes - UI", AllIcons.Nodes.ErrorIntroduction),
            IconDefinition("Nodes.Locked", "Locked", "Nodes - UI", AllIcons.Nodes.Locked),
            IconDefinition("Nodes.Related", "Related", "Nodes - UI", AllIcons.Nodes.Related),

            // FileTypes
            IconDefinition("FileTypes.Text", "Text File", "File Types", AllIcons.FileTypes.Text),
            IconDefinition("FileTypes.Unknown", "Unknown File", "File Types", AllIcons.FileTypes.Unknown),
            IconDefinition("FileTypes.Json", "JSON File", "File Types", AllIcons.FileTypes.Json),
            IconDefinition("FileTypes.Xml", "XML File", "File Types", AllIcons.FileTypes.Xml),
            IconDefinition("FileTypes.Html", "HTML File", "File Types", AllIcons.FileTypes.Html),
            IconDefinition("FileTypes.Css", "CSS File", "File Types", AllIcons.FileTypes.Css),
            IconDefinition("FileTypes.JavaScript", "JavaScript File", "File Types", AllIcons.FileTypes.JavaScript),
            IconDefinition("FileTypes.Java", "Java File", "File Types", AllIcons.FileTypes.Java),
            IconDefinition("FileTypes.Yaml", "YAML File", "File Types", AllIcons.FileTypes.Yaml),
            IconDefinition("FileTypes.Properties", "Properties File", "File Types", AllIcons.FileTypes.Properties),
            IconDefinition("FileTypes.Config", "Config File", "File Types", AllIcons.FileTypes.Config),
            IconDefinition("FileTypes.Archive", "Archive File", "File Types", AllIcons.FileTypes.Archive),
            IconDefinition("FileTypes.Any_type", "Any Type", "File Types", AllIcons.FileTypes.Any_type),
            IconDefinition("FileTypes.Custom", "Custom File", "File Types", AllIcons.FileTypes.Custom),
            IconDefinition("FileTypes.Diagram", "Diagram File", "File Types", AllIcons.FileTypes.Diagram),

            // General
            IconDefinition("General.Add", "Add/Plus", "General", AllIcons.General.Add),
            IconDefinition("General.Remove", "Remove/Minus", "General", AllIcons.General.Remove),
            IconDefinition("General.Settings", "Settings/Gear", "General", AllIcons.General.Settings),
            IconDefinition("General.Filter", "Filter", "General", AllIcons.General.Filter),
            IconDefinition("General.ArrowDown", "Arrow Down", "General", AllIcons.General.ArrowDown),
            IconDefinition("General.ArrowUp", "Arrow Up", "General", AllIcons.General.ArrowUp),
            IconDefinition("General.ArrowLeft", "Arrow Left", "General", AllIcons.General.ArrowLeft),
            IconDefinition("General.ArrowRight", "Arrow Right", "General", AllIcons.General.ArrowRight),
            IconDefinition("General.Balloon", "Balloon/Notification", "General", AllIcons.General.Balloon),
            IconDefinition("General.BalloonError", "Balloon Error", "General", AllIcons.General.BalloonError),
            IconDefinition("General.BalloonInformation", "Balloon Information", "General", AllIcons.General.BalloonInformation),
            IconDefinition("General.BalloonWarning", "Balloon Warning", "General", AllIcons.General.BalloonWarning),
            IconDefinition("General.Error", "Error", "General", AllIcons.General.Error),
            IconDefinition("General.Information", "Information", "General", AllIcons.General.Information),
            IconDefinition("General.Warning", "Warning", "General", AllIcons.General.Warning),
            IconDefinition("General.ChevronDown", "Chevron Down", "General", AllIcons.General.ChevronDown),
            IconDefinition("General.ChevronUp", "Chevron Up", "General", AllIcons.General.ChevronUp),
            IconDefinition("General.ChevronLeft", "Chevron Left", "General", AllIcons.General.ChevronLeft),
            IconDefinition("General.ChevronRight", "Chevron Right", "General", AllIcons.General.ChevronRight),
            IconDefinition("General.Divider", "Divider", "General", AllIcons.General.Divider),
            IconDefinition("General.Dropdown", "Dropdown", "General", AllIcons.General.Dropdown),
            IconDefinition("General.ExternalTools", "External Tools", "General", AllIcons.General.ExternalTools),
            IconDefinition("General.GearPlain", "Gear Plain", "General", AllIcons.General.GearPlain),
            IconDefinition("General.HideToolWindow", "Hide Tool Window", "General", AllIcons.General.HideToolWindow),
            IconDefinition("General.InheritedMethod", "Inherited Method", "General", AllIcons.General.InheritedMethod),
            IconDefinition("General.InspectionsError", "Inspections Error", "General", AllIcons.General.InspectionsError),
            IconDefinition("General.InspectionsWarning", "Inspections Warning", "General", AllIcons.General.InspectionsWarning),
            IconDefinition("General.Layout", "Layout", "General", AllIcons.General.Layout),
            IconDefinition("General.Locate", "Locate", "General", AllIcons.General.Locate),
            IconDefinition("General.Modified", "Modified", "General", AllIcons.General.Modified),
            IconDefinition("General.Mouse", "Mouse", "General", AllIcons.General.Mouse),
            IconDefinition("General.Note", "Note", "General", AllIcons.General.Note),
            IconDefinition("General.Pin_tab", "Pin Tab", "General", AllIcons.General.Pin_tab),
            IconDefinition("General.ProjectTab", "Project Tab", "General", AllIcons.General.ProjectTab),
            IconDefinition("General.QuestionDialog", "Question Dialog", "General", AllIcons.General.QuestionDialog),
            IconDefinition("General.ReaderMode", "Reader Mode", "General", AllIcons.General.ReaderMode),
            IconDefinition("General.Reset", "Reset", "General", AllIcons.General.Reset),
            IconDefinition("General.User", "User", "General", AllIcons.General.User),
            IconDefinition("General.Web", "Web", "General", AllIcons.General.Web),
            IconDefinition("General.ActualZoom", "ActualZoom", "General", AllIcons.General.ActualZoom),

            // Gutter
            IconDefinition("Gutter.Colors", "Gutter Colors", "Gutter", AllIcons.Gutter.Colors),
            IconDefinition("Gutter.DataSchema", "Gutter Data Schema", "Gutter", AllIcons.Gutter.DataSchema),
            IconDefinition("Gutter.ExtAnnotation", "External Annotation", "Gutter", AllIcons.Gutter.ExtAnnotation),
            IconDefinition("Gutter.ImplementedMethod", "Implemented Method", "Gutter", AllIcons.Gutter.ImplementedMethod),
            IconDefinition("Gutter.ImplementingMethod", "Implementing Method", "Gutter", AllIcons.Gutter.ImplementingMethod),
            IconDefinition("Gutter.OverridenMethod", "Overridden Method", "Gutter", AllIcons.Gutter.OverridenMethod),
            IconDefinition("Gutter.OverridingMethod", "Overriding Method", "Gutter", AllIcons.Gutter.OverridingMethod),
            IconDefinition("Gutter.ReadAccess", "Read Access", "Gutter", AllIcons.Gutter.ReadAccess),
            IconDefinition("Gutter.RecursiveMethod", "Recursive Method", "Gutter", AllIcons.Gutter.RecursiveMethod),
            IconDefinition("Gutter.SuggestedRefactoringBulb", "Refactoring Bulb", "Gutter", AllIcons.Gutter.SuggestedRefactoringBulb),
            IconDefinition("Gutter.Unique", "Unique", "Gutter", AllIcons.Gutter.Unique),
            IconDefinition("Gutter.WriteAccess", "Write Access", "Gutter", AllIcons.Gutter.WriteAccess),

            // RunConfigurations
            IconDefinition("RunConfigurations.TestState.Run", "Test Run", "Run Configurations", AllIcons.RunConfigurations.TestState.Run),
            IconDefinition("RunConfigurations.TestState.Run_run", "Test Run Run", "Run Configurations", AllIcons.RunConfigurations.TestState.Run_run),
            IconDefinition("RunConfigurations.TestState.Green2", "Test Passed", "Run Configurations", AllIcons.RunConfigurations.TestState.Green2),
            IconDefinition("RunConfigurations.TestState.Red2", "Test Failed", "Run Configurations", AllIcons.RunConfigurations.TestState.Red2),
            IconDefinition("RunConfigurations.TestState.Yellow2", "Test Warning", "Run Configurations", AllIcons.RunConfigurations.TestState.Yellow2),

            // Toolwindows
            IconDefinition("Toolwindows.ToolWindowChanges", "Changes", "Tool Windows", AllIcons.Toolwindows.ToolWindowChanges),
            IconDefinition("Toolwindows.ToolWindowDebugger", "Debugger", "Tool Windows", AllIcons.Toolwindows.ToolWindowDebugger),
            IconDefinition("Toolwindows.ToolWindowRun", "Run", "Tool Windows", AllIcons.Toolwindows.ToolWindowRun),
            IconDefinition("Toolwindows.ToolWindowStructure", "Structure", "Tool Windows", AllIcons.Toolwindows.ToolWindowStructure),
            IconDefinition("Toolwindows.ToolWindowTodo", "TODO", "Tool Windows", AllIcons.Toolwindows.ToolWindowTodo),
            IconDefinition("Toolwindows.ToolWindowMessages", "Messages", "Tool Windows", AllIcons.Toolwindows.ToolWindowMessages),
            IconDefinition("Toolwindows.ToolWindowHierarchy", "Hierarchy", "Tool Windows", AllIcons.Toolwindows.ToolWindowHierarchy),
            IconDefinition("Toolwindows.ToolWindowFind", "Find", "Tool Windows", AllIcons.Toolwindows.ToolWindowFind),
            IconDefinition("Toolwindows.ToolWindowBuild", "Build", "Tool Windows", AllIcons.Toolwindows.ToolWindowBuild),
            IconDefinition("Toolwindows.ToolWindowFavorites", "Favorites", "Tool Windows", AllIcons.Toolwindows.ToolWindowFavorites),
            IconDefinition("Toolwindows.ToolWindowProblems", "Problems", "Tool Windows", AllIcons.Toolwindows.ToolWindowProblems),

            // Vcs
            IconDefinition("Vcs.Branch", "Branch", "Version Control", AllIcons.Vcs.Branch),
            IconDefinition("Vcs.CommitNode", "Commit", "Version Control", AllIcons.Vcs.CommitNode),
            IconDefinition("Vcs.History", "History", "Version Control", AllIcons.Vcs.History),
            IconDefinition("Vcs.Merge", "Merge", "Version Control", AllIcons.Vcs.Merge),
            IconDefinition("Vcs.Push", "Push", "Version Control", AllIcons.Vcs.Push),
            IconDefinition("Vcs.Remove", "Remove", "Version Control", AllIcons.Vcs.Remove),

            // Debugger
            IconDefinition("Debugger.Console", "Debug Console", "Debugger", AllIcons.Debugger.Console),
            IconDefinition("Debugger.Db_set_breakpoint", "Set Breakpoint", "Debugger", AllIcons.Debugger.Db_set_breakpoint),
            IconDefinition("Debugger.EvaluateExpression", "Evaluate Expression", "Debugger", AllIcons.Debugger.EvaluateExpression),
            IconDefinition("Debugger.Frame", "Frame", "Debugger", AllIcons.Debugger.Frame),
            IconDefinition("Debugger.MuteBreakpoints", "Mute Breakpoints", "Debugger", AllIcons.Debugger.MuteBreakpoints),
            IconDefinition("Debugger.RestoreLayout", "Restore Layout", "Debugger", AllIcons.Debugger.RestoreLayout),
            IconDefinition("Debugger.ThreadStates.Idle", "Thread Idle", "Debugger", AllIcons.Debugger.ThreadStates.Idle),
            IconDefinition("Debugger.Value", "Value", "Debugger", AllIcons.Debugger.Value),
            IconDefinition("Debugger.Watch", "Watch", "Debugger", AllIcons.Debugger.Watch),

            // Hierarchy
            IconDefinition("Hierarchy.Class", "Class Hierarchy", "Hierarchy", AllIcons.Hierarchy.Class),
            IconDefinition("Hierarchy.Subtypes", "Subtypes", "Hierarchy", AllIcons.Hierarchy.Subtypes),
            IconDefinition("Hierarchy.Supertypes", "Supertypes", "Hierarchy", AllIcons.Hierarchy.Supertypes),

            // ObjectBrowser
            IconDefinition("ObjectBrowser.AbbreviatePackageNames", "Abbreviate", "Object Browser", AllIcons.ObjectBrowser.AbbreviatePackageNames),
            IconDefinition("ObjectBrowser.CompactEmptyPackages", "Compact", "Object Browser", AllIcons.ObjectBrowser.CompactEmptyPackages),
            IconDefinition("ObjectBrowser.FlattenPackages", "Flatten", "Object Browser", AllIcons.ObjectBrowser.FlattenPackages),
            IconDefinition("ObjectBrowser.ShowLibraryContents", "Show Library", "Object Browser", AllIcons.ObjectBrowser.ShowLibraryContents),
            IconDefinition("ObjectBrowser.ShowMembers", "Show Members", "Object Browser", AllIcons.ObjectBrowser.ShowMembers),
            IconDefinition("ObjectBrowser.Sorted", "Sorted", "Object Browser", AllIcons.ObjectBrowser.Sorted),
            IconDefinition("ObjectBrowser.VisibilitySort", "Visibility Sort", "Object Browser", AllIcons.ObjectBrowser.VisibilitySort),

            // Diff & Merge
            IconDefinition("Diff.ApplyNotConflicts", "Apply Not Conflicts", "Diff & Merge", AllIcons.Diff.ApplyNotConflicts),
            IconDefinition("Diff.Arrow", "Diff Arrow", "Diff & Merge", AllIcons.Diff.Arrow),
            IconDefinition("Diff.ArrowRight", "Arrow Right", "Diff & Merge", AllIcons.Diff.ArrowRight),
            IconDefinition("Diff.Compare3LeftMiddle", "Compare Left Middle", "Diff & Merge", AllIcons.Diff.Compare3LeftMiddle),
            IconDefinition("Diff.Compare3MiddleRight", "Compare Middle Right", "Diff & Merge", AllIcons.Diff.Compare3MiddleRight),
            IconDefinition("Diff.Compare4LeftMiddle", "Compare 4 Left Middle", "Diff & Merge", AllIcons.Diff.Compare4LeftMiddle),
            IconDefinition("Diff.Compare4MiddleRight", "Compare 4 Middle Right", "Diff & Merge", AllIcons.Diff.Compare4MiddleRight),
            IconDefinition("Diff.GutterCheckBox", "Gutter Checkbox", "Diff & Merge", AllIcons.Diff.GutterCheckBox),

            // Process
            IconDefinition("Process.Big.Step_1", "Process Step 1", "Process", AllIcons.Process.Big.Step_1),
            IconDefinition("Process.Big.Step_2", "Process Step 2", "Process", AllIcons.Process.Big.Step_2),
            IconDefinition("Process.Big.Step_3", "Process Step 3", "Process", AllIcons.Process.Big.Step_3),
            IconDefinition("Process.Big.Step_4", "Process Step 4", "Process", AllIcons.Process.Big.Step_4),

            // Providers
            IconDefinition("Providers.Eclipse", "Eclipse", "Providers", AllIcons.Providers.Eclipse),
            IconDefinition("Providers.Sqlite", "SQLite", "Providers", AllIcons.Providers.Sqlite),

            // Ide
            IconDefinition("Ide.Notification.ErrorEvents", "Error Events", "IDE", AllIcons.Ide.Notification.ErrorEvents),
            IconDefinition("Ide.Notification.InfoEvents", "Info Events", "IDE", AllIcons.Ide.Notification.InfoEvents),
            IconDefinition("Ide.Notification.WarningEvents", "Warning Events", "IDE", AllIcons.Ide.Notification.WarningEvents)
        )
    }

    val categories: List<String> by lazy {
        allIconsDefinition.map { it.category }.distinct().sorted()
    }

    fun getAllIcons(): List<IconDefinition> = allIconsDefinition

    fun getIconsByCategory(category: String): List<IconDefinition> {
        return allIconsDefinition.filter { it.category == category }.sortedBy { it.displayName }
    }

    fun findIconById(id: String?): IconDefinition? {
        return allIconsDefinition.find { it.id == id }
    }

    fun getIconOrDefault(id: String?): Icon {
        return findIconById(id)?.icon ?: AllIcons.Nodes.Tag
    }
}

/**
 * Default icon mappings for common Flutter widgets
 */
object DefaultWidgetIconMappings {
    val mappings = mapOf(
        // Layout
        "Column" to "Actions.SplitVertically",
        "Row" to "Actions.SplitHorizontally",
        "Stack" to "Actions.SplitVertically",
        "Flex" to "Actions.SplitVertically",
        "Wrap" to "Actions.GroupBy",
        "Flow" to "Actions.GroupBy",

        "Container" to "Nodes.Folder",
        "SizedBox" to "Nodes.Interface",
        "AspectRatio" to "General.ActualZoom",
        "ConstrainedBox" to "Actions.Stub",
        "LimitedBox" to "Actions.PreviewDetails",

        "Padding" to "Actions.MoveToLeftBottom",
        "Center" to "Actions.MoveToLeftBottom",
        "Align" to "Actions.MoveToLeftBottom",
        "FittedBox" to "Actions.MoveToLeftBottom",

        "Expanded" to "Actions.Expandall",
        "Flexible" to "Actions.Expandall",
        "Spacer" to "Actions.Expandall",

        // Scrollable
        "ListView" to "Actions.PreviewDetails",
        "GridView" to "Actions.PreviewDetails",
        "CustomScrollView" to "Actions.PreviewDetails",
        "SingleChildScrollView" to "Actions.PreviewDetails",
        "PageView" to "Actions.PreviewDetails",

        // Interactive
        "GestureDetector" to "Nodes.Editorconfig",
        "InkWell" to "Nodes.Editorconfig",
        "Draggable" to "Nodes.Editorconfig",
        "DragTarget" to "Nodes.Editorconfig",

        // Text
        "Text" to "FileTypes.Text",
        "RichText" to "FileTypes.Text",
        "SelectableText" to "FileTypes.Text",

        // Input
        "TextField" to "Actions.Edit",
        "TextFormField" to "Actions.Edit",
        "EditableText" to "Actions.Edit",

        // Buttons
        "ElevatedButton" to "Actions.Execute",
        "TextButton" to "Actions.Execute",
        "OutlinedButton" to "Actions.Execute",
        "FilledButton" to "Actions.Execute",
        "IconButton" to "Actions.Execute",
        "FloatingActionButton" to "Actions.Execute",
        "MaterialButton" to "Actions.Execute",

        // Icons & Images
        "Icon" to "Nodes.Artifact",
        "ImageIcon" to "Nodes.Artifact",
        "Image" to "FileTypes.Unknown",
        "FadeInImage" to "FileTypes.Unknown",
        "CircleAvatar" to "FileTypes.Unknown",

        // App Structure
        "Scaffold" to "Nodes.Folder",
        "AppBar" to "Nodes.Folder",
        "BottomNavigationBar" to "Nodes.Folder",
        "Drawer" to "Nodes.Folder",
        "TabBar" to "Nodes.Folder",

        "MaterialApp" to "Nodes.Module",
        "CupertinoApp" to "Nodes.Module",
        "WidgetsApp" to "Nodes.Module",

        // Cards
        "Card" to "Nodes.Static",
        "Material" to "Nodes.Static",
        "Surface" to "Nodes.Static",

        // Lists
        "ListTile" to "Actions.ListChanges",
        "ExpansionTile" to "Actions.ListChanges",
        "CheckboxListTile" to "Actions.ListChanges",
        "RadioListTile" to "Actions.ListChanges",

        // Forms
        "Form" to "Actions.Properties",
        "FormField" to "Actions.Properties",
        "Checkbox" to "Actions.ToggleSoftWrap",
        "Radio" to "Actions.ToggleSoftWrap",
        "Switch" to "Actions.ToggleSoftWrap",
        "Slider" to "Actions.ToggleSoftWrap",
        "DropdownButton" to "Actions.ToggleSoftWrap",

        // Navigation
        "Navigator" to "Actions.Forward",
        "Route" to "Actions.Forward",
        "PageRoute" to "Actions.Forward",

        // Animations
        "AnimatedContainer" to "Actions.Refresh",
        "AnimatedOpacity" to "Actions.Refresh",
        "Hero" to "Actions.Refresh",
        "FadeTransition" to "Actions.Refresh",
        "ScaleTransition" to "Actions.Refresh",

        // State Management
        "Provider" to "Nodes.DataSchema",
        "Consumer" to "Nodes.DataSchema",
        "Selector" to "Nodes.DataSchema",
        "ChangeNotifierProvider" to "Nodes.DataSchema",
        "StreamProvider" to "Nodes.DataSchema",

        // Builders
        "Builder" to "Actions.Show",
        "LayoutBuilder" to "Actions.Show",
        "FutureBuilder" to "Actions.Show",
        "StreamBuilder" to "Actions.Show",
        "ValueListenableBuilder" to "Actions.Show"
    )
}