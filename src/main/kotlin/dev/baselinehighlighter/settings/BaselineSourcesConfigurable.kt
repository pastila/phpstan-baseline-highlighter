package dev.baselinehighlighter.settings

import com.intellij.openapi.components.service
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.table.JBTable
import dev.baselinehighlighter.BaselineBundle.message
import dev.baselinehighlighter.core.BaselinePathResolver
import dev.baselinehighlighter.index.BaselineIndexListener
import dev.baselinehighlighter.index.BaselineIndexService
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JButton
import javax.swing.JPanel
import java.awt.FlowLayout
import javax.swing.table.AbstractTableModel

class BaselineSourcesConfigurable(private val project: Project) : SearchableConfigurable {
    private var lifetime: com.intellij.openapi.Disposable? = null
    private val enabled = JBCheckBox(message("settings.enabled"))
    private val rows = mutableListOf<BaselineSettings.Source>()
    private val model = object : AbstractTableModel() {
        override fun getRowCount(): Int = rows.size
        override fun getColumnCount(): Int = 3
        override fun getColumnName(column: Int): String = message(listOf("settings.source.enabled", "settings.source.path", "settings.source.base")[column])
        override fun getColumnClass(column: Int): Class<*> = if (column == 0) Boolean::class.javaObjectType else String::class.java
        override fun isCellEditable(row: Int, column: Int): Boolean = true
        override fun getValueAt(row: Int, column: Int): Any = when (column) { 0 -> rows[row].enabled; 1 -> rows[row].path; else -> rows[row].baseDirectory }
        override fun setValueAt(value: Any?, row: Int, column: Int) {
            when (column) { 0 -> rows[row].enabled = value == true; 1 -> rows[row].path = value?.toString().orEmpty(); else -> rows[row].baseDirectory = value?.toString().orEmpty() }
            fireTableRowsUpdated(row, row)
        }
    }
    private val table = JBTable(model)
    private val diagnostics = JBTextArea().apply { isEditable = false; lineWrap = true; wrapStyleWord = true }

    override fun getId(): String = "dev.baselinehighlighter.settings"
    override fun getDisplayName(): String = message("plugin.name")

    override fun createComponent(): JComponent {
        val uiLifetime = Disposer.newDisposable("Baseline settings UI")
        lifetime = uiLifetime
        val addButton = JButton("+").apply {
            toolTipText = message("settings.add")
            accessibleContext.accessibleName = toolTipText
            addActionListener {
                FileChooser.chooseFile(FileChooserDescriptorFactory.createSingleFileDescriptor("neon"), project, null)?.let { file ->
                    val path = BaselinePathResolver.portable(file.path, project.basePath ?: "")
                    rows += BaselineSettings.Source(path = path)
                    this@BaselineSourcesConfigurable.model.fireTableDataChanged()
                }
            }
        }
        val removeButton = JButton("−").apply {
            toolTipText = message("settings.remove")
            accessibleContext.accessibleName = toolTipText
            addActionListener {
                for (row in table.selectedRows.sortedDescending()) rows.removeAt(table.convertRowIndexToModel(row))
                this@BaselineSourcesConfigurable.model.fireTableDataChanged()
            }
        }
        val toolbar = JPanel(BorderLayout()).apply {
            add(JPanel(FlowLayout(FlowLayout.LEADING, 4, 0)).apply { add(addButton); add(removeButton) }, BorderLayout.NORTH)
            add(JBScrollPane(table).apply { setColumnHeaderView(table.tableHeader) }, BorderLayout.CENTER)
        }
        table.columnModel.getColumn(0).maxWidth = 90
        val header = JPanel(BorderLayout(0, 8)).apply {
            add(enabled, BorderLayout.NORTH)
            add(JLabel(message("settings.help")), BorderLayout.CENTER)
        }
        val bottom = JPanel(BorderLayout(0, 5)).apply {
            add(JLabel(message("settings.diagnostics")), BorderLayout.NORTH)
            add(JBScrollPane(diagnostics).apply { preferredSize = Dimension(700, 200) }, BorderLayout.CENTER)
            add(JButton(message("settings.reload")).apply { addActionListener { project.service<BaselineIndexService>().reload() } }, BorderLayout.SOUTH)
        }
        project.messageBus.connect(uiLifetime).subscribe(BaselineIndexService.TOPIC, BaselineIndexListener { updateDiagnostics() })
        reset()
        return JPanel(BorderLayout(0, 12)).apply {
            preferredSize = Dimension(800, 500)
            add(header, BorderLayout.NORTH)
            add(toolbar, BorderLayout.CENTER)
            add(bottom, BorderLayout.SOUTH)
        }
    }

    private fun stopEditing() { if (table.isEditing) table.cellEditor.stopCellEditing() }
    override fun isModified(): Boolean {
        stopEditing()
        val state = project.service<BaselineSettings>().state
        return enabled.isSelected != state.enabled || rows != state.sources
    }

    override fun apply() {
        stopEditing()
        val index = project.service<BaselineIndexService>()
        if (rows.any { it.path.isBlank() }) throw ConfigurationException(message("settings.empty.path"))
        if (rows.map { index.normalize(it.path) }.distinct().size != rows.size) throw ConfigurationException(message("settings.duplicate"))
        project.service<BaselineSettings>().replace(BaselineSettings.Data(enabled.isSelected, rows.map { it.copy(path = it.path.trim(), baseDirectory = it.baseDirectory.trim()) }.toMutableList()))
        index.reload()
    }

    override fun reset() {
        val state = project.service<BaselineSettings>().state
        enabled.isSelected = state.enabled
        rows.clear()
        rows.addAll(state.sources.map { it.copy() })
        model.fireTableDataChanged()
        updateDiagnostics()
    }

    private fun updateDiagnostics() {
        val snapshot = project.service<BaselineIndexService>().snapshot
        diagnostics.text = if (snapshot.sources.isEmpty()) message("settings.no.sources") else buildString {
            for ((path, status) in snapshot.sources) {
                appendLine(path)
                when {
                    status.unavailable -> appendLine(message("source.unavailable"))
                    status.issues.isNotEmpty() -> {
                        for (issue in status.issues.take(20)) {
                            val key = issue.message.substringBefore(':')
                            val argument = issue.message.substringAfter(':', "")
                            appendLine("  ${issue.line}: ${message("parser.$key", argument)}")
                        }
                        if (status.issues.size > 20) appendLine(message("source.more.issues", status.issues.size - 20))
                    }
                    else -> {
                        appendLine(message("source.ready", status.entries.size))
                        for (entry in status.entries.distinctBy { it.targetPath }.take(3)) appendLine("  ${entry.entry.path} → ${entry.targetPath}")
                        if (status.missingTargets.isNotEmpty()) {
                            appendLine(message("source.missing.targets", status.missingTargets.size))
                            status.missingTargets.take(3).forEach { appendLine("  $it") }
                        }
                    }
                }
                appendLine()
            }
        }
        diagnostics.caretPosition = 0
    }

    override fun disposeUIResources() { lifetime?.let(Disposer::dispose); lifetime = null }
}
