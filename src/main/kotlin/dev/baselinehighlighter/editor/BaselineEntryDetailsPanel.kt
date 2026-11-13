package dev.baselinehighlighter.editor

import com.intellij.ide.BrowserUtil
import com.intellij.ui.HyperlinkLabel
import com.intellij.util.ui.JBUI
import dev.baselinehighlighter.BaselineBundle.message
import dev.baselinehighlighter.core.IndexedEntry
import dev.baselinehighlighter.documentation.ErrorIdentifierDocumentation
import java.awt.BorderLayout
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextArea
import com.intellij.openapi.project.Project
import dev.baselinehighlighter.phpat.PhpatRuleNavigation

/** Details follow the selected row; only a user click opens the browser. */
internal class BaselineEntryDetailsPanel(private val project: Project? = null, private val openUrl: (String) -> Unit = { BrowserUtil.browse(it) }) : JPanel(BorderLayout(0, JBUI.scale(5))) {
    val groupLabel = JLabel()
    val explanation = JTextArea(3, 65).apply {
        isEditable = false
        isFocusable = false
        lineWrap = true
        wrapStyleWord = true
        isOpaque = false
        font = groupLabel.font
    }
    val documentationLink = HyperlinkLabel(message("documentation.open"))
    val ruleLink = HyperlinkLabel(message("phpat.declaration"))
    private var documentationUrl: String? = null
    private var selectedEntry: IndexedEntry? = null

    init {
        border = JBUI.Borders.empty(8)
        add(groupLabel, BorderLayout.NORTH)
        add(explanation, BorderLayout.CENTER)
        add(JPanel(java.awt.GridLayout(0, 1)).apply { isOpaque = false; add(documentationLink); add(ruleLink) }, BorderLayout.SOUTH)
        documentationLink.addHyperlinkListener { documentationUrl?.let(openUrl) }
        ruleLink.addHyperlinkListener {
            val entry = selectedEntry ?: return@addHyperlinkListener
            val currentProject = project ?: return@addHyperlinkListener
            entry.entry.identifier?.let { PhpatRuleNavigation.open(currentProject, entry.sourcePath, it) }
        }
        showEntry(null)
    }

    fun showEntry(entry: IndexedEntry?) {
        selectedEntry = entry
        val info = ErrorIdentifierDocumentation.lookup(entry?.entry?.identifier)
        groupLabel.text = info.errorGroup?.let { message("documentation.group", it) }.orEmpty()
        groupLabel.isVisible = info.errorGroup != null
        explanation.text = info.explanation ?: message(when {
            info.identifier == null -> "documentation.no.identifier"
            info.identifier.startsWith("phpat.") && project != null -> "phpat.lookup.hint"
            else -> "documentation.unavailable"
        })
        explanation.caretPosition = 0
        documentationUrl = info.documentationUrl
        documentationLink.isVisible = documentationUrl != null
        documentationLink.isEnabled = documentationUrl != null
        ruleLink.isVisible = project != null && entry?.entry?.identifier?.startsWith("phpat.") == true
        revalidate()
        repaint()
    }
}
