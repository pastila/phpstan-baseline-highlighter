package dev.baselinehighlighter.settings

import com.intellij.openapi.components.service
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.table.JBTable
import dev.baselinehighlighter.BaselineBundle.message
import dev.baselinehighlighter.core.BaselinePathResolver
import dev.baselinehighlighter.core.BaselineConfigurationImporter
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
import java.util.concurrent.Future

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
    private val importButton = JButton(message("settings.import"))
    private val cancelImportButton = JButton(message("settings.import.cancel")).apply { isVisible = false }
    private val importReport = JBTextArea().apply { isEditable = false; lineWrap = true; wrapStyleWord = true }
    private val importScroll = JBScrollPane(importReport).apply { preferredSize = Dimension(700, 110); isVisible = false }
    private var currentImport: ProgressIndicatorBase? = null
    internal val isImporting: Boolean get() = currentImport != null
    internal val importSummary: String get() = importReport.text

    override fun getId(): String = "dev.baselinehighlighter.settings"
    override fun getDisplayName(): String = message("plugin.name")

    internal fun importDescriptor(): FileChooserDescriptor = FileChooserDescriptorFactory.singleFile()
        .withFileFilter { it.name.endsWith(".neon", true) || it.name.endsWith(".neon.dist", true) }
        .withShowHiddenFiles(true)
        .withTitle(message("settings.import.choose"))

    override fun createComponent(): JComponent {
        val uiLifetime = Disposer.newDisposable("Baseline settings UI")
        lifetime = uiLifetime
        importButton.addActionListener {
            FileChooser.chooseFile(importDescriptor(), project, null)?.let(::importConfiguration)
        }
        cancelImportButton.addActionListener { cancelImport(); showImportReport(message("settings.import.cancelled")) }
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
            add(JPanel(FlowLayout(FlowLayout.LEADING, 4, 0)).apply {
                add(addButton); add(removeButton); add(importButton); add(cancelImportButton)
            }, BorderLayout.NORTH)
            add(JBScrollPane(table).apply { setColumnHeaderView(table.tableHeader) }, BorderLayout.CENTER)
            add(importScroll, BorderLayout.SOUTH)
        }
        table.columnModel.getColumn(0).maxWidth = 90
        val header = JPanel(BorderLayout(0, 8)).apply {
            add(enabled, BorderLayout.NORTH)
            add(JLabel(message("settings.help")), BorderLayout.CENTER)
        }
        val bottom = JPanel(BorderLayout(0, 5)).apply {
            add(JLabel(message("settings.diagnostics")), BorderLayout.NORTH)
            add(JBScrollPane(diagnostics).apply { preferredSize = Dimension(700, 150) }, BorderLayout.CENTER)
            add(JButton(message("settings.reload")).apply { addActionListener { project.service<BaselineIndexService>().reload() } }, BorderLayout.SOUTH)
        }
        project.messageBus.connect(uiLifetime).subscribe(BaselineIndexService.TOPIC, BaselineIndexListener { updateDiagnostics() })
        reset()
        return JPanel(BorderLayout(0, 12)).apply {
            preferredSize = Dimension(800, 600)
            add(header, BorderLayout.NORTH)
            add(toolbar, BorderLayout.CENTER)
            add(bottom, BorderLayout.SOUTH)
        }
    }

    private fun stopEditing() { if (table.isEditing) table.cellEditor.stopCellEditing() }

    internal fun importConfiguration(file: VirtualFile): Future<*>? {
        val owner = lifetime ?: return null
        stopEditing()
        cancelImport()
        val indicator = ProgressIndicatorBase()
        currentImport = indicator
        importButton.isEnabled = false
        cancelImportButton.isVisible = true
        showImportReport(message("settings.import.running"))
        val modality = ModalityState.stateForComponent(table)
        return ApplicationManager.getApplication().executeOnPooledThread {
            var result: BaselineConfigurationImporter.Result? = null
            var failed = false
            try { result = BaselineSourceImport.discover(file, indicator::checkCanceled) }
            catch (_: ProcessCanceledException) { /* Settings were reset, closed, or the import was cancelled. */ }
            catch (error: Exception) {
                Logger.getInstance(BaselineSourcesConfigurable::class.java).warn("Could not import PHPStan baseline sources", error)
                failed = true
            }
            ApplicationManager.getApplication().invokeLater({
                if (project.isDisposed || lifetime !== owner || currentImport !== indicator) return@invokeLater
                currentImport = null
                importButton.isEnabled = true
                cancelImportButton.isVisible = false
                if (failed) showImportReport(message("settings.import.failed"))
                else if (result != null) mergeImport(result)
                else showImportReport(message("settings.import.cancelled"))
            }, modality)
        }
    }

    private fun mergeImport(result: BaselineConfigurationImporter.Result) {
        stopEditing()
        val index = project.service<BaselineIndexService>()
        val existing = rows.mapTo(mutableSetOf()) { index.normalize(it.path) }
        var added = 0
        for (path in result.sources) {
            if (existing.add(index.normalize(path))) {
                rows += BaselineSettings.Source(path = BaselinePathResolver.portable(path, project.basePath.orEmpty()))
                added++
            }
        }
        model.fireTableDataChanged()
        showImportReport(buildString {
            appendLine(message("settings.import.summary", result.sources.size, added, result.sources.size - added, result.filesRead))
            appendLine(message(if (result.sources.isEmpty()) "settings.import.none" else "settings.import.apply"))
            result.issues.take(20).forEach { issue ->
                val explanation = if (issue.key == "baseline.invalid") {
                    message("import.baseline.invalid", message("parser.${issue.detail.substringBefore(':')}", issue.detail.substringAfter(':', "")))
                } else message("import.${issue.key}", issue.detail)
                appendLine("${issue.path}:${issue.line}: $explanation")
            }
            if (result.issueCount > 20) appendLine(message("settings.import.more", result.issueCount - 20))
        })
    }

    private fun showImportReport(text: String) {
        importReport.text = text
        importReport.caretPosition = 0
        importScroll.isVisible = text.isNotEmpty()
        importScroll.parent?.revalidate()
        importScroll.parent?.repaint()
    }

    private fun cancelImport() {
        currentImport?.cancel()
        currentImport = null
        importButton.isEnabled = true
        cancelImportButton.isVisible = false
    }
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
        cancelImport()
        showImportReport("")
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

    override fun disposeUIResources() {
        cancelImport()
        importButton.actionListeners.forEach(importButton::removeActionListener)
        cancelImportButton.actionListeners.forEach(cancelImportButton::removeActionListener)
        lifetime?.let(Disposer::dispose)
        lifetime = null
    }
}
