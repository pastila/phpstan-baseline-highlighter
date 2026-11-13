package dev.baselinehighlighter.editor

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiElement
import com.intellij.ui.awt.RelativePoint
import dev.baselinehighlighter.BaselineBundle.message
import javax.swing.Icon

class BaselineLineMarkerProvider : LineMarkerProviderDescriptor() {
    override fun getName(): String = message("plugin.name")
    override fun getIcon(): Icon = BaselinePresentation.icon

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        if (element.firstChild != null) return null
        val entries = PhpBaselineMatcher.bindings(element.containingFile)[element.textOffset].orEmpty()
        if (entries.isEmpty()) return null
        return LineMarkerInfo(
            element, element.textRange, icon,
            { BaselinePresentation.tooltip(entries) },
            { event, target -> BaselineNavigation.choose(target.project, entries.map { it.indexed }, RelativePoint(event)) },
            GutterIconRenderer.Alignment.RIGHT,
            { message("tooltip.title", entries.size) },
        )
    }
}
