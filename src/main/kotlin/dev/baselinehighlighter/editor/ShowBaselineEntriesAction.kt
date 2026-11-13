package dev.baselinehighlighter.editor

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.Messages
import dev.baselinehighlighter.BaselineBundle.message
import dev.baselinehighlighter.index.BaselineIndexService

class ShowBaselineEntriesAction : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible = event.project != null && event.getData(CommonDataKeys.VIRTUAL_FILE)?.extension == "php"
    }
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val file = event.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val index = project.service<BaselineIndexService>()
        val entries = index.entries(file.path)
        if (entries.isNotEmpty()) BaselineNavigation.choose(project, entries)
        else Messages.showInfoMessage(project, message("action.empty"), message("plugin.name"))
    }
}
