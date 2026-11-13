package dev.baselinehighlighter.editor

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.awt.RelativePoint
import dev.baselinehighlighter.BaselineBundle.message
import dev.baselinehighlighter.core.*
import dev.baselinehighlighter.index.BaselineIndexService
import javax.swing.JList

object BaselineNavigation {
    fun choose(project: Project, entries: List<IndexedEntry>, location: RelativePoint? = null) {
        if (entries.isEmpty()) return
        if (entries.size == 1 && location != null) { navigate(project, entries.single()); return }
        val popup = createChooser(project, entries)
        if (location != null && location.component.isShowing) popup.show(location) else popup.showCenteredInCurrentWindow(project)
    }

    internal fun createChooser(project: Project, entries: List<IndexedEntry>): JBPopup {
        require(entries.isNotEmpty())
        val details = BaselineEntryDetailsPanel(project = project).apply { showEntry(entries.first()) }
        return JBPopupFactory.getInstance().createPopupChooserBuilder(entries)
            .setTitle(message("navigation.title"))
            .setMovable(true)
            .setResizable(true)
            .setFilterAlwaysVisible(true)
            .setAccessibleName(message("navigation.filter"))
            .setNamerForFiltering { searchText(it) }
            .setAdvertiser(details)
            .setItemSelectedCallback { details.showEntry(it) }
            .setRenderer(object : SimpleListCellRenderer<IndexedEntry>() {
                init { putClientProperty("html.disable", true) }
                override fun customize(list: JList<out IndexedEntry>, value: IndexedEntry?, index: Int, selected: Boolean, hasFocus: Boolean) {
                    text = value?.let { "${it.entry.identifier?.let { id -> "[$id] " }.orEmpty()}${BaselineMessageClassifier.display(it.entry.message).take(160)} — ${BaselinePathResolver.portable(it.sourcePath, project.basePath ?: "")}:${it.entry.line}" }.orEmpty()
                }
            })
            .setItemChosenCallback { navigate(project, it) }
            .createPopup()
    }

    internal fun searchText(entry: IndexedEntry): String = "${entry.entry.identifier.orEmpty()} ${BaselineMessageClassifier.display(entry.entry.message)} ${entry.sourcePath}"

    data class Target(val file: com.intellij.openapi.vfs.VirtualFile, val stamp: Long, val entry: BaselineEntry)

    /** Resolve against the current, possibly unsaved document, never just an indexed offset. */
    fun resolve(selected: IndexedEntry): Target? {
        val captured = ReadAction.compute<Triple<com.intellij.openapi.vfs.VirtualFile, String, Long>?, RuntimeException> {
            val file = LocalFileSystem.getInstance().findFileByPath(selected.sourcePath) ?: return@compute null
            val document = FileDocumentManager.getInstance().getDocument(file) ?: return@compute null
            Triple(file, document.immutableCharSequence.toString(), document.modificationStamp)
        } ?: return null
        val candidates = BaselineParser.parse(captured.second).entries.filter {
            it.message == selected.entry.message && it.path == selected.entry.path && it.identifier == selected.entry.identifier && it.count == selected.entry.count
        }
        val target = candidates.singleOrNull() ?: candidates.firstOrNull { it.range == selected.entry.range } ?: return null
        return Target(captured.first, captured.third, target)
    }

    /** Must run on the EDT; returns false if the document changed again or cannot be opened. */
    fun open(project: Project, target: Target): Boolean {
        ApplicationManager.getApplication().assertIsDispatchThread()
        if (project.isDisposed || !target.file.isValid) return false
        val document = FileDocumentManager.getInstance().getDocument(target.file) ?: return false
        if (document.modificationStamp != target.stamp) return false
        val editor = FileEditorManager.getInstance(project).openTextEditor(OpenFileDescriptor(project, target.file, target.entry.range.start), true) ?: return false
        editor.selectionModel.setSelection(target.entry.range.start, target.entry.range.end)
        return true
    }

    fun navigate(project: Project, selected: IndexedEntry) {
        ApplicationManager.getApplication().executeOnPooledThread {
            val target = resolve(selected)
            ApplicationManager.getApplication().invokeLater {
                if (project.isDisposed) return@invokeLater
                if (target == null || !open(project, target)) {
                    project.service<BaselineIndexService>().reload()
                    Messages.showInfoMessage(project, message("navigation.changed"), message("plugin.name"))
                }
            }
        }
    }
}
