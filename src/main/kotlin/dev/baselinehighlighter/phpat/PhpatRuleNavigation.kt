package dev.baselinehighlighter.phpat

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.model.Pointer
import com.intellij.model.Symbol
import com.intellij.model.psi.PsiExternalReferenceHost
import com.intellij.model.psi.PsiSymbolReference
import com.intellij.model.psi.PsiSymbolReferenceHints
import com.intellij.model.psi.PsiSymbolReferenceProvider
import com.intellij.model.search.SearchRequest
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiManager
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.platform.backend.navigation.NavigationRequest
import com.intellij.platform.backend.navigation.NavigationTarget
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import dev.baselinehighlighter.BaselineBundle.message
import dev.baselinehighlighter.neon.BaselineNeonEntryLocator
import javax.swing.JList

internal object PhpatRuleNavigation {
    data class Link(val identifier: String, val range: TextRange, val rawRange: TextRange)

    fun open(project: Project, sourcePath: String, identifier: String) {
        ReadAction.nonBlocking<List<PhpatRuleResolver.Target>> {
            val file = LocalFileSystem.getInstance().findFileByPath(sourcePath) ?: return@nonBlocking emptyList()
            val source = PsiManager.getInstance(project).findFile(file) ?: return@nonBlocking emptyList()
            PhpatRuleResolver.resolve(source, identifier)
        }.inSmartMode(project).expireWith(project).finishOnUiThread(ModalityState.nonModal()) { targets ->
            val current = targets.filter { it.element.isValid }
            fun navigate(target: PhpatRuleResolver.Target) {
                val element = target.element.takeIf { it.isValid } ?: return
                OpenFileDescriptor(project, element.containingFile.virtualFile, target.offset).navigate(true)
            }
            when (current.size) {
                0 -> Messages.showInfoMessage(project, message("phpat.not.found"), message("phpat.declaration"))
                1 -> navigate(current.single())
                else -> JBPopupFactory.getInstance().createPopupChooserBuilder(current)
                    .setTitle(message("phpat.declaration"))
                    .setRenderer(object : SimpleListCellRenderer<PhpatRuleResolver.Target>() {
                        init { putClientProperty("html.disable", true) }
                        override fun customize(list: JList<out PhpatRuleResolver.Target>, value: PhpatRuleResolver.Target?, index: Int, selected: Boolean, hasFocus: Boolean) {
                            text = value?.let { it.label + " — ${message(it.kindKey)}" }.orEmpty()
                        }
                    })
                    .setItemChosenCallback(::navigate).createPopup().showCenteredInCurrentWindow(project)
            }
        }.submit(AppExecutorUtil.getAppExecutorService())
    }

    fun linkAt(source: PsiFile, offset: Int): Link? {
        if (!source.name.endsWith(".neon", true)) return null
        val document = source.viewProvider.document ?: return null
        val entry = BaselineNeonEntryLocator.entryAt(document, offset) ?: return null
        val identifier = entry.identifier?.takeIf { it.startsWith("phpat.") } ?: return null
        val raw = entry.identifierRange ?: return null
        val text = document.immutableCharSequence
        val quoted = raw.end - raw.start >= 2 && text[raw.start] in "\"'" && text[raw.end - 1] == text[raw.start]
        val range = TextRange(raw.start + if (quoted) 1 else 0, raw.end - if (quoted) 1 else 0)
        return if (range.containsOffset(offset) && offset < range.endOffset) Link(identifier, range, TextRange(raw.start, raw.end)) else null
    }
}

class PhpatRuleReferenceProvider : PsiSymbolReferenceProvider {
    override fun getReferences(element: PsiExternalReferenceHost, hints: PsiSymbolReferenceHints): Collection<PsiSymbolReference> {
        if (hints.offsetInElement < 0) return emptyList()
        val source = element as? PsiFile ?: return emptyList()
        val link = PhpatRuleNavigation.linkAt(source, hints.offsetInElement) ?: return emptyList()
        val stamp = source.viewProvider.document?.modificationStamp ?: return emptyList()
        return listOf(PhpatRuleReference(source, link, stamp))
    }

    override fun getSearchRequests(project: Project, target: Symbol): Collection<SearchRequest> = emptyList()
}

internal class PhpatRuleReference(private val source: PsiFile, private val link: PhpatRuleNavigation.Link, private val stamp: Long) : PsiSymbolReference {
    override fun getElement(): PsiFile = source
    override fun getRangeInElement(): TextRange = link.range
    override fun resolveReference(): Collection<Symbol> =
        if (!source.isValid || source.viewProvider.document?.modificationStamp != stamp) emptyList()
        else PhpatRuleResolver.resolve(source, link.identifier).map { PhpatRuleSymbol(it) }
}

private class PhpatRuleSymbol(target: PhpatRuleResolver.Target) : Symbol, NavigationTarget {
    private val pointer = target.pointer()
    private val label = target.label
    private val kindKey = target.kindKey
    override fun createPointer(): Pointer<PhpatRuleSymbol> = Pointer { takeIf { pointer.element != null } }
    override fun computePresentation(): TargetPresentation = TargetPresentation.builder(label)
        .locationText(message(kindKey)).presentation()
    override fun navigationRequest(): NavigationRequest? {
        val element = pointer.element ?: return null
        val range = pointer.range ?: return null
        return NavigationRequest.sourceNavigationRequest(element.project, element.virtualFile, range.startOffset)
    }
}

/** Fine-grained NEON PSI fallback; plain-text files use the offset-hinted symbol reference. */
class PhpatRuleDeclarationHandler : GotoDeclarationHandler {
    override fun getGotoDeclarationTargets(sourceElement: PsiElement?, offset: Int, editor: Editor): Array<PsiElement>? {
        val project = editor.project ?: return null
        val source = sourceElement?.containingFile ?: PsiDocumentManager.getInstance(project).getPsiFile(editor.document) ?: return null
        val link = PhpatRuleNavigation.linkAt(source, offset) ?: return null
        if (sourceElement?.textRange?.let(link.rawRange::contains) != true) return null
        return PhpatRuleResolver.resolve(source, link.identifier).map { target ->
            if (target.configuration == null) target.element else ArchitectureNavigationElement(target)
        }.takeIf { it.isNotEmpty() }?.toTypedArray()
    }
}

/** NEON PSI navigation needs a positioned element even when YAML is plain text. */
private class ArchitectureNavigationElement(target: PhpatRuleResolver.Target) : com.intellij.psi.impl.FakePsiElement() {
    private val pointer = target.pointer()
    private val label = target.label
    override fun getParent(): PsiElement? = pointer.element
    override fun getName(): String = label
    override fun getTextRange(): TextRange = pointer.range?.let { TextRange(it.startOffset, it.endOffset) } ?: TextRange.EMPTY_RANGE
    override fun getTextOffset(): Int = textRange.startOffset
    override fun isValid(): Boolean = pointer.element?.isValid == true && pointer.range != null
    override fun navigate(requestFocus: Boolean) {
        val file = pointer.element ?: return
        val range = pointer.range ?: return
        OpenFileDescriptor(file.project, file.virtualFile, range.startOffset).navigate(requestFocus)
    }
}
