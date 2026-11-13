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

/** Details follow the selected row; only a user click opens the browser. */
internal class BaselineEntryDetailsPanel(private val openUrl: (String) -> Unit = { BrowserUtil.browse(it) }) : JPanel(BorderLayout(0, JBUI.scale(5))) {
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
    private var documentationUrl: String? = null

    init {
        border = JBUI.Borders.empty(8)
        add(groupLabel, BorderLayout.NORTH)
        add(explanation, BorderLayout.CENTER)
        add(documentationLink, BorderLayout.SOUTH)
        documentationLink.addHyperlinkListener { documentationUrl?.let(openUrl) }
        showEntry(null)
    }

    fun showEntry(entry: IndexedEntry?) {
        val info = ErrorIdentifierDocumentation.lookup(entry?.entry?.identifier)
        groupLabel.text = info.errorGroup?.let { message("documentation.group", it) }.orEmpty()
        groupLabel.isVisible = info.errorGroup != null
        explanation.text = info.explanation ?: message(if (info.identifier == null) "documentation.no.identifier" else "documentation.unavailable")
        explanation.caretPosition = 0
        documentationUrl = info.documentationUrl
        documentationLink.isVisible = documentationUrl != null
        documentationLink.isEnabled = documentationUrl != null
        revalidate()
        repaint()
    }
}
