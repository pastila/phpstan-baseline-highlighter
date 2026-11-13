package dev.baselinehighlighter.editor

import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.text.StringUtil
import dev.baselinehighlighter.BaselineBundle.message
import dev.baselinehighlighter.core.BaselineMessageClassifier
import dev.baselinehighlighter.core.BindingNote
import dev.baselinehighlighter.core.BoundEntry
import dev.baselinehighlighter.documentation.ErrorIdentifierDocumentation

object BaselinePresentation {
    val icon = IconLoader.getIcon("/icons/baseline.svg", BaselinePresentation::class.java)
    private fun escape(value: String) = StringUtil.escapeXmlEntities(value)

    fun tooltip(entries: List<BoundEntry>): String = buildString {
        append("<html><b>${escape(message("tooltip.title", entries.size))}</b><br>")
        append("${escape(message("tooltip.unverified"))}<br>")
        for (bound in entries.take(5)) {
            val entry = bound.indexed.entry
            append("<br><b>${escape(bound.indexed.owner?.methodName?.let { "$it()" } ?: message("tooltip.class.file"))}</b><br>")
            append(escape(BaselineMessageClassifier.display(entry.message).take(500)))
            entry.identifier?.let { append("<br>identifier: ${escape(it)}") }
            val info = ErrorIdentifierDocumentation.lookup(entry.identifier)
            info.errorGroup?.let { append("<br>${escape(message("documentation.group", it))}") }
            info.explanation?.let { append("<br>${escape(it)}") }
            info.documentationUrl?.let { append("<br><a href=\"${escape(it)}\">${escape(message("documentation.open"))}</a>") }
            entry.count?.let { append("<br>${escape(message("tooltip.count", it))}") }
            if (bound.note != BindingNote.NONE) append("<br><i>${escape(message("binding.${bound.note.name.lowercase()}"))}</i>")
            append("<br>${escape(bound.indexed.sourcePath)}:${entry.line}<br>")
        }
        if (entries.size > 5) append("<br>${escape(message("tooltip.more", entries.size - 5))}")
        append("<br>${escape(message("tooltip.navigate"))}</html>")
    }
}
