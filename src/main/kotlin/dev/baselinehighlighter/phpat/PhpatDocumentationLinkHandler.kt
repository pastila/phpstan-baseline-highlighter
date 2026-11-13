package dev.baselinehighlighter.phpat

import com.intellij.model.Pointer
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.backend.documentation.DocumentationLinkHandler
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.LinkResolveResult
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.pom.Navigatable
import com.intellij.psi.SmartPointerManager
import dev.baselinehighlighter.BaselineBundle.message
import dev.baselinehighlighter.documentation.BaselineDocumentationTarget
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class PhpatDocumentationLinkHandler : DocumentationLinkHandler {
    override fun resolveLink(target: DocumentationTarget, url: String): LinkResolveResult? {
        if (!url.startsWith(PREFIX)) return null
        val baseline = target as? BaselineDocumentationTarget ?: return null
        val rule = baseline.ruleTargets().firstOrNull { url(it.label) == url } ?: return null
        return LinkResolveResult.resolvedTarget(PhpatDeclarationDocumentationTarget(rule))
    }

    companion object {
        private const val PREFIX = "psi_element://baseline-phpat/"
        internal fun url(label: String): String = PREFIX + URLEncoder.encode(label, StandardCharsets.UTF_8)
    }
}

private class PhpatDeclarationDocumentationTarget(rule: PhpatRuleResolver.Target) : DocumentationTarget {
    private val pointer = SmartPointerManager.createPointer(rule.element)
    private val label = rule.label
    private val generator = rule.method.collection
    override fun createPointer(): Pointer<out DocumentationTarget> = Pointer { takeIf { pointer.element != null } }
    override fun computePresentation(): TargetPresentation = TargetPresentation.builder(label).presentation()
    override val navigatable: Navigatable?
        get() = pointer.element?.let { OpenFileDescriptor(it.project, it.containingFile.virtualFile, it.textOffset) }
    override fun computeDocumentation(): DocumentationResult? {
        val element = pointer.element ?: return null
        val explanation = message(if (generator) "phpat.generator.hint" else "phpat.source.hint")
        return DocumentationResult.documentation("<div class='definition'>${StringUtil.escapeXmlEntities(label)}</div>" +
            "<div class='content'><p>${StringUtil.escapeXmlEntities(explanation)}</p>" +
            "<pre>${StringUtil.escapeXmlEntities(element.parent.text.take(4000))}</pre></div>")
    }
}
