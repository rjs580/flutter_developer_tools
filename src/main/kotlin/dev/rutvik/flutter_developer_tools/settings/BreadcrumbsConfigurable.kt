package dev.rutvik.flutter_developer_tools.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.table.JBTable
import java.awt.Component
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer

class BreadcrumbsConfigurable : Configurable {

    private val settings = BreadcrumbsSettingsState.getInstance()

    private lateinit var inferStandardWidgetNamesCheckbox: JBCheckBox
    private lateinit var widgetIconTable: JBTable
    private lateinit var tableModel: WidgetIconTableModel

    override fun getDisplayName(): String = "Flutter Developer Tools"

    override fun createComponent(): JComponent {
        // Initialize table model with current settings
        val recognizedWidgets = (BreadcrumbsSettingsState.DEFAULT_RECOGNIZED_WIDGETS +
                settings.customRecognizedWidgets).distinct().sorted()

        tableModel = WidgetIconTableModel(
            recognizedWidgets,
            settings.widgetIconMappings.toMutableMap()
        )

        widgetIconTable = JBTable(tableModel).apply {
            setDefaultRenderer(Icon::class.java, IconTableCellRenderer())
            setDefaultRenderer(JButton::class.java, ButtonRenderer())
            setDefaultEditor(String::class.java, IconComboBoxEditor())
            rowHeight = 32
            columnModel.getColumn(0).preferredWidth = 200
            columnModel.getColumn(1).preferredWidth = 300
            columnModel.getColumn(2).preferredWidth = 80
            columnModel.getColumn(3).preferredWidth = 100

            // Handle reset button clicks
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    val row = rowAtPoint(e.point)
                    val col = columnAtPoint(e.point)
                    if (row >= 0 && col == 3) { // Reset column
                        tableModel.resetWidget(row)
                    }
                }
            })
        }

        return panel {
            group("Breadcrumbs Settings") {
                row {
                    inferStandardWidgetNamesCheckbox = checkBox("Infer standard widget names")
                        .bindSelected(settings::inferStandardWidgetNames)
                        .comment("Automatically detect Flutter widgets from custom names (e.g., MyScaffold → Scaffold)")
                        .component
                }
            }

            group("Widget Icon Mappings") {
                row {
                    comment(
                        "Configure which icon to display for each Flutter widget type in breadcrumbs. " +
                                "Click on the icon cell to select from ${AvailableIcons.getAllIcons().size}+ available IntelliJ icons. " +
                                "Modified defaults are shown in bold and can be reset individually."
                    )
                }

                row {
                    cell(JBScrollPane(widgetIconTable))
                        .align(AlignX.FILL)
                        .resizableColumn()
                }.resizableRow()

                row {
                    button("Reset All to Defaults") {
                        val result = JOptionPane.showConfirmDialog(
                            null,
                            "Reset all widget icons to default values?\nThis will remove all customizations.",
                            "Confirm Reset",
                            JOptionPane.YES_NO_OPTION
                        )
                        if (result == JOptionPane.YES_OPTION) {
                            tableModel.resetAllToDefaults()
                        }
                    }
                    button("Add Custom Widget") {
                        val widgetName = JOptionPane.showInputDialog(
                            null,
                            "Enter custom widget name:",
                            "Add Custom Widget",
                            JOptionPane.PLAIN_MESSAGE
                        )
                        if (!widgetName.isNullOrBlank()) {
                            tableModel.addWidget(widgetName.trim())
                        }
                    }
                    button("Remove Selected") {
                        val selectedRow = widgetIconTable.selectedRow
                        if (selectedRow >= 0) {
                            tableModel.removeWidget(selectedRow)
                        }
                    }
                }
            }

            group("How Widget Name Inference Works") {
                row {
                    comment(
                        """
                        When enabled, the plugin will match custom widget names to standard Flutter widgets:
                        <ul>
                        <li><b>Suffix matching:</b> MyScaffold → Scaffold</li>
                        <li><b>Prefix removal:</b> MyCustomButton → Button (if Button is in the list)</li>
                        <li><b>Case insensitive:</b> customTextField → TextField</li>
                        </ul>
                        Custom widgets that match will inherit the icon of the standard widget.
                        """.trimIndent(),
                        maxLineLength = 80
                    )
                }
            }
        }
    }

    override fun isModified(): Boolean {
        return settings.inferStandardWidgetNames != inferStandardWidgetNamesCheckbox.isSelected ||
                tableModel.isModified()
    }

    override fun apply() {
        settings.inferStandardWidgetNames = inferStandardWidgetNamesCheckbox.isSelected

        // Apply icon mappings from table
        settings.widgetIconMappings.clear()
        settings.widgetIconMappings.putAll(tableModel.getIconMappings())

        // Save custom widgets (those not in default list)
        settings.customRecognizedWidgets.clear()
        settings.customRecognizedWidgets.addAll(
            tableModel.getWidgetNames().filter {
                it !in BreadcrumbsSettingsState.DEFAULT_RECOGNIZED_WIDGETS
            }
        )

        tableModel.markAsUnmodified()
    }

    override fun reset() {
        inferStandardWidgetNamesCheckbox.isSelected = settings.inferStandardWidgetNames

        val recognizedWidgets = (BreadcrumbsSettingsState.DEFAULT_RECOGNIZED_WIDGETS +
                settings.customRecognizedWidgets).distinct().sorted()
        tableModel.reset(recognizedWidgets, settings.widgetIconMappings.toMutableMap())
    }
}

/**
 * Table model for widget icon mappings
 */
class WidgetIconTableModel(
    private var widgets: List<String>,
    private var iconMappings: MutableMap<String, String>
) : AbstractTableModel() {

    private var modified = false

    override fun getRowCount(): Int = widgets.size

    override fun getColumnCount(): Int = 4

    override fun getColumnName(column: Int): String = when (column) {
        0 -> "Widget Name"
        1 -> "Icon"
        2 -> "Preview"
        3 -> "Action"
        else -> ""
    }

    override fun getColumnClass(columnIndex: Int): Class<*> = when (columnIndex) {
        0 -> String::class.java
        1 -> String::class.java
        2 -> Icon::class.java
        3 -> JButton::class.java
        else -> Any::class.java
    }

    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = columnIndex == 1

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any? {
        val widget = widgets[rowIndex]
        return when (columnIndex) {
            0 -> widget
            1 -> {
                val iconId = iconMappings[widget] ?: DefaultWidgetIconMappings.mappings[widget]
                val iconDef = AvailableIcons.findIconById(iconId)
                iconDef?.displayName ?: "Generic Widget"
            }
            2 -> {
                val iconId = iconMappings[widget] ?: DefaultWidgetIconMappings.mappings[widget]
                AvailableIcons.getIconOrDefault(iconId)
            }
            3 -> {
                // Check if this widget has been customized
                val hasCustomIcon = iconMappings.containsKey(widget) &&
                        DefaultWidgetIconMappings.mappings[widget] != null &&
                        iconMappings[widget] != DefaultWidgetIconMappings.mappings[widget]
                if (hasCustomIcon) "Reset" else ""
            }
            else -> null
        }
    }

    override fun setValueAt(aValue: Any?, rowIndex: Int, columnIndex: Int) {
        if (columnIndex == 1 && aValue is String) {
            val widget = widgets[rowIndex]
            val iconDef = AvailableIcons.getAllIcons().find { it.displayName == aValue }
            if (iconDef != null) {
                iconMappings[widget] = iconDef.id
                modified = true
                fireTableCellUpdated(rowIndex, 1)
                fireTableCellUpdated(rowIndex, 2)
                fireTableCellUpdated(rowIndex, 3)
            }
        }
    }

    fun isModified(): Boolean = modified

    fun markAsUnmodified() {
        modified = false
    }

    fun getIconMappings(): Map<String, String> = iconMappings.toMap()

    fun getWidgetNames(): List<String> = widgets

    fun resetAllToDefaults() {
        iconMappings.clear()
        iconMappings.putAll(DefaultWidgetIconMappings.mappings)
        modified = true
        fireTableDataChanged()
    }

    fun resetWidget(rowIndex: Int) {
        if (rowIndex >= 0 && rowIndex < widgets.size) {
            val widget = widgets[rowIndex]
            val defaultIcon = DefaultWidgetIconMappings.mappings[widget]
            if (defaultIcon != null) {
                iconMappings[widget] = defaultIcon
                modified = true
                fireTableRowsUpdated(rowIndex, rowIndex)
            }
        }
    }

    fun addWidget(widgetName: String) {
        if (widgetName !in widgets) {
            widgets = (widgets + widgetName).sorted()
            iconMappings[widgetName] = "Nodes.Tag"
            modified = true
            fireTableDataChanged()
        }
    }

    fun removeWidget(rowIndex: Int) {
        if (rowIndex >= 0 && rowIndex < widgets.size) {
            val widget = widgets[rowIndex]
            // Only allow removing custom widgets, not defaults
            if (widget !in BreadcrumbsSettingsState.DEFAULT_RECOGNIZED_WIDGETS) {
                widgets = widgets.filterIndexed { index, _ -> index != rowIndex }
                iconMappings.remove(widget)
                modified = true
                fireTableDataChanged()
            } else {
                JOptionPane.showMessageDialog(
                    null,
                    "Cannot remove default Flutter widget '$widget'.\nYou can only remove custom widgets.",
                    "Cannot Remove",
                    JOptionPane.WARNING_MESSAGE
                )
            }
        }
    }

    fun reset(newWidgets: List<String>, newMappings: MutableMap<String, String>) {
        widgets = newWidgets
        iconMappings = newMappings
        modified = false
        fireTableDataChanged()
    }
}

/**
 * Cell renderer for icon preview column
 */
class IconTableCellRenderer : DefaultTableCellRenderer() {
    override fun getTableCellRendererComponent(
        table: JTable?,
        value: Any?,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int
    ): Component {
        val label = super.getTableCellRendererComponent(table, "", isSelected, hasFocus, row, column) as JLabel
        if (value is Icon) {
            label.icon = value
        }
        label.horizontalAlignment = SwingConstants.CENTER
        return label
    }
}

/**
 * Cell renderer for reset button column
 */
class ButtonRenderer : JButton(), javax.swing.table.TableCellRenderer {
    override fun getTableCellRendererComponent(
        table: JTable?,
        value: Any?,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int
    ): Component {
        text = value as? String ?: ""
        isEnabled = text.isNotEmpty()
        return this
    }
}

/**
 * Cell editor for icon selection with searchable dialog
 */
class IconComboBoxEditor : DefaultCellEditor(JBTextField()) {

    init {
        clickCountToStart = 1
    }

    override fun getTableCellEditorComponent(
        table: JTable?,
        value: Any?,
        isSelected: Boolean,
        row: Int,
        column: Int
    ): Component {
        // Create a dialog to select icon
        val currentValue = value as? String ?: "Generic Widget"

        SwingUtilities.invokeLater {
            val selectedIcon = showIconSelectionDialog(currentValue)
            if (selectedIcon != null) {
                fireEditingStopped()
                table?.setValueAt(selectedIcon, row, column)
            } else {
                fireEditingCanceled()
            }
        }

        return super.getTableCellEditorComponent(table, value, isSelected, row, column)
    }

    private fun showIconSelectionDialog(currentValue: String): String? {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)

        // Category filter
        val categoryFilter = ComboBox<String>().apply {
            addItem("All Categories")
            AvailableIcons.categories.forEach { addItem(it) }
        }

        // Icon list
        val iconListModel = DefaultListModel<IconDefinition>()
        AvailableIcons.getAllIcons().forEach { iconListModel.addElement(it) }

        val iconList = JBList(iconListModel).apply {
            selectionMode = ListSelectionModel.SINGLE_SELECTION
            cellRenderer = IconListCellRenderer()

            // Pre-select current icon
            val currentIconDef = AvailableIcons.getAllIcons().find { it.displayName == currentValue }
            if (currentIconDef != null) {
                val index = iconListModel.indexOf(currentIconDef)
                if (index >= 0) {
                    selectedIndex = index
                    ensureIndexIsVisible(index)
                }
            }
        }

        // Search field
        val searchField = JBTextField().apply {
            emptyText.text = "Search icons..."
        }

        // Update list based on category filter
        categoryFilter.addActionListener {
            val category = categoryFilter.selectedItem as? String
            iconListModel.clear()
            val filteredIcons = if (category == "All Categories") {
                AvailableIcons.getAllIcons()
            } else {
                AvailableIcons.getIconsByCategory(category ?: "")
            }

            val searchText = searchField.text.lowercase()
            filteredIcons
                .filter { searchText.isEmpty() || it.displayName.lowercase().contains(searchText) }
                .forEach { iconListModel.addElement(it) }
        }

        // Update list based on search
        searchField.addActionListener {
            categoryFilter.selectedIndex = 0
            categoryFilter.actionListeners.firstOrNull()?.actionPerformed(null)
        }

        panel.add(JLabel("Category:"))
        panel.add(categoryFilter)
        panel.add(Box.createVerticalStrut(5))
        panel.add(JLabel("Search:"))
        panel.add(searchField)
        panel.add(Box.createVerticalStrut(5))
        panel.add(JLabel("Select Icon:"))
        panel.add(JBScrollPane(iconList).apply {
            preferredSize = java.awt.Dimension(400, 300)
        })

        val result = JOptionPane.showConfirmDialog(
            null,
            panel,
            "Select Icon",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE
        )

        return if (result == JOptionPane.OK_OPTION && iconList.selectedValue != null) {
            (iconList.selectedValue as IconDefinition).displayName
        } else {
            null
        }
    }
}

/**
 * Custom list cell renderer for icons with preview
 */
class IconListCellRenderer : DefaultListCellRenderer() {
    override fun getListCellRendererComponent(
        list: JList<*>?,
        value: Any?,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean
    ): Component {
        val label = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus) as JLabel
        if (value is IconDefinition) {
            label.text = "${value.displayName} (${value.category})"
            label.icon = value.icon
        }
        return label
    }
}