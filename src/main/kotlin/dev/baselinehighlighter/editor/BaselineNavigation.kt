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
        if (entries.size == 1) { navigate(project, entries.single()); return }
        val popup = JBPopupFactory.getInstance().createPopupChooserBuilder(entries)
            .setTitle(message("navigation.title"))
            .setMovable(true)
            .setRenderer(object : SimpleListCellRenderer<IndexedEntry>() {
                init { putClientProperty("html.disable", true) }
                override fun customize(list: JList<out IndexedEntry>, value: IndexedEntry?, index: Int, selected: Boolean, hasFocus: Boolean) {
                    text = value?.let { "${BaselineMessageClassifier.display(it.entry.message).take(160)} — ${BaselinePathResolver.portable(it.sourcePath, project.basePath ?: "")}:${it.entry.line}" }.orEmpty()
                }
            })
            .setItemChosenCallback { navigate(project, it) }
            .createPopup()
        if (location != null && location.component.isShowing) popup.show(location) else popup.showCenteredInCurrentWindow(project)
    }

    fun navigate(project: Project, selected: IndexedEntry) {
        // Re-parse the current document in the background; offsets from the marker may be stale.
        ApplicationManager.getApplication().executeOnPooledThread {
            val captured = ReadAction.compute<Triple<com.intellij.openapi.vfs.VirtualFile, String, Long>?, RuntimeException> {
                val file = LocalFileSystem.getInstance().findFileByPath(selected.sourcePath) ?: return@compute null
                val document = FileDocumentManager.getInstance().getDocument(file) ?: return@compute null
                Triple(file, document.immutableCharSequence.toString(), document.modificationStamp)
            }
            val parsed = captured?.let { BaselineParser.parse(it.second) }
            val candidates = parsed?.entries?.filter {
                it.message == selected.entry.message && it.path == selected.entry.path && it.identifier == selected.entry.identifier && it.count == selected.entry.count
            }.orEmpty()
            val target = candidates.singleOrNull() ?: candidates.firstOrNull { it.range == selected.entry.range }
            ApplicationManager.getApplication().invokeLater {
                if (project.isDisposed) return@invokeLater
                val document = captured?.first?.takeIf { it.isValid }?.let { FileDocumentManager.getInstance().getDocument(it) }
                if (target == null || captured == null || document == null || document.modificationStamp != captured.third) {
                    project.service<BaselineIndexService>().reload()
                    Messages.showInfoMessage(project, message("navigation.changed"), message("plugin.name"))
                    return@invokeLater
                }
                FileEditorManager.getInstance(project).openTextEditor(OpenFileDescriptor(project, captured.first, target.range.start), true)?.let { editor ->
                    editor.selectionModel.setSelection(target.range.start, target.range.end)
                }
            }
        }
    }
}
