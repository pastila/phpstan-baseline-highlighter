package dev.baselinehighlighter.documentation

import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.model.Pointer
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.DocumentationTargetProvider
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import dev.baselinehighlighter.BaselineBundle.message
import dev.baselinehighlighter.core.BaselineEntry
import dev.baselinehighlighter.core.BaselineMessageClassifier
import dev.baselinehighlighter.core.SourceRange
import dev.baselinehighlighter.neon.BaselineNeonEntryLocator
import dev.baselinehighlighter.phpat.PhpatRuleResolver
import dev.baselinehighlighter.phpat.PhpatDocumentationLinkHandler
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.pom.Navigatable

/** Uses text ranges instead of vendor-specific NEON PSI, including plain-text .neon files. */
class BaselineDocumentationTargetProvider : DocumentationTargetProvider {
    override fun documentationTargets(file: PsiFile, offset: Int): List<DocumentationTarget> {
        if (!file.name.endsWith(".neon", ignoreCase = true)) return emptyList()
        val document = file.viewProvider.document ?: return emptyList()
        val entry = BaselineNeonEntryLocator.entryAt(document, offset) ?: return emptyList()
        if (entry.identifier == null || !(entry.range.contains(offset) || entry.identifierRange?.contains(offset) == true)) return emptyList()
        return listOf(BaselineDocumentationTarget(file, entry))
    }

    private fun SourceRange.contains(offset: Int): Boolean = offset >= start && offset < end
}

internal class BaselineDocumentationTarget(private val file: PsiFile, private val entry: BaselineEntry) : DocumentationTarget {
    private val anchor = SmartPointerManager.getInstance(file.project).createSmartPsiFileRangePointer(file, TextRange(entry.range.start, entry.range.end))

    override fun createPointer(): Pointer<out DocumentationTarget> {
        val rangePointer = anchor
        val selected = entry
        return Pointer {
            val currentFile = rangePointer.element ?: return@Pointer null
            val range = rangePointer.range ?: return@Pointer null
            val currentEntry = currentFile.viewProvider.document?.let { BaselineNeonEntryLocator.entryAt(it, range.startOffset) } ?: return@Pointer null
            if (!sameEntry(currentEntry, selected)) return@Pointer null
            BaselineDocumentationTarget(currentFile, currentEntry)
        }
    }

    override fun computePresentation(): TargetPresentation = TargetPresentation.builder(entry.identifier.orEmpty()).presentation()

    internal fun ruleTargets(): List<PhpatRuleResolver.Target> = currentEntry()?.let { PhpatRuleResolver.resolve(file, it.identifier) }.orEmpty()

    override val navigatable: Navigatable?
        get() = ruleTargets().singleOrNull()?.element?.let { OpenFileDescriptor(it.project, it.containingFile.virtualFile, it.textOffset) }

    override fun computeDocumentationHint(): String? = currentEntry()?.let(::html)

    override fun computeDocumentation(): DocumentationResult? {
        val current = currentEntry() ?: return null
        val result = DocumentationResult.documentation(html(current))
        return ErrorIdentifierDocumentation.lookup(current.identifier).documentationUrl?.let { result.externalUrl(it) } ?: result
    }

    private fun currentEntry(): BaselineEntry? {
        if (!file.isValid) return null
        val range = anchor.range ?: return null
        val document = file.viewProvider.document ?: return null
        return BaselineNeonEntryLocator.entryAt(document, range.startOffset)?.takeIf { sameEntry(it, entry) }
    }

    private companion object {
        fun sameEntry(first: BaselineEntry, second: BaselineEntry): Boolean = first.identifier == second.identifier &&
            first.message == second.message && first.path == second.path && first.count == second.count
    }

    private fun html(current: BaselineEntry): String = buildString {
        val info = ErrorIdentifierDocumentation.lookup(current.identifier)
        append(DocumentationMarkup.DEFINITION_START)
        append(StringUtil.escapeXmlEntities(current.identifier.orEmpty()))
        append(DocumentationMarkup.DEFINITION_END)
        append(DocumentationMarkup.CONTENT_START)
        info.errorGroup?.let { append("<p>${escape(message("documentation.group", it))}</p>") }
        append("<p>${escape(info.explanation ?: message("documentation.unavailable"))}</p>")
        append("<p>${escape(BaselineMessageClassifier.display(current.message).take(500))}</p>")
        append("<p><i>${escape(message("tooltip.unverified"))}</i></p>")
        info.documentationUrl?.let { append("<p><a href=\"${escape(it)}\">${escape(message("documentation.open"))}</a></p>") }
        PhpatRuleResolver.resolve(file, current.identifier).forEach { target ->
            append("<p><a href=\"${escape(PhpatDocumentationLinkHandler.url(target.label))}\">${escape(message("phpat.declaration"))}: ${escape(target.label)}</a>")
            if (target.method.collection) append("<br>${escape(message("phpat.generator.hint"))}")
            append("</p>")
        }
        append(DocumentationMarkup.CONTENT_END)
    }

    private fun escape(text: String): String = StringUtil.escapeXmlEntities(text)
}
