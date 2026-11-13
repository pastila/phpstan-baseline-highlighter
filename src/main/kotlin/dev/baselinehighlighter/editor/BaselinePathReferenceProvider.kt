package dev.baselinehighlighter.editor

import com.intellij.model.Symbol
import com.intellij.model.Pointer
import com.intellij.model.psi.PsiExternalReferenceHost
import com.intellij.model.psi.PsiSymbolReference
import com.intellij.model.psi.PsiSymbolReferenceHints
import com.intellij.model.psi.PsiSymbolReferenceProvider
import com.intellij.model.search.SearchRequest
import com.intellij.navigation.NavigatableSymbol
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.navigation.NavigationRequest
import com.intellij.platform.backend.navigation.NavigationTarget
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.PsiFile

/** A single offset-hinted reference; requests to enumerate the whole file do no work. */
class BaselinePathReferenceProvider : PsiSymbolReferenceProvider {
    override fun getReferences(element: PsiExternalReferenceHost, hints: PsiSymbolReferenceHints): Collection<PsiSymbolReference> {
        val offset = hints.offsetInElement
        if (offset < 0) return emptyList()
        val source = element as? PsiFile ?: return emptyList()
        if (!source.name.endsWith(".neon", ignoreCase = true)) return emptyList()
        val document = source.viewProvider.document ?: return emptyList()
        val link = BaselinePathNavigation.linkAt(source, document, offset) ?: return emptyList()
        return listOf(BaselinePathSymbolReference(source, link, document.modificationStamp))
    }

    override fun getSearchRequests(project: Project, target: Symbol): Collection<SearchRequest> = emptyList()
}

internal class BaselinePathSymbolReference(
    private val source: PsiFile,
    private val link: BaselinePathNavigation.Link,
    private val stamp: Long,
) : PsiSymbolReference {
    override fun getElement(): PsiFile = source
    override fun getRangeInElement(): TextRange = link.range
    override fun resolveReference(): Collection<Symbol> {
        if (!source.isValid || source.viewProvider.document?.modificationStamp != stamp) return emptyList()
        val target = BaselinePathNavigation.resolveFile(source, link.path) ?: return emptyList()
        return listOf(BaselinePathSymbol(target))
    }
}

/** Public file navigation APIs avoid loading the PHP PSI during Ctrl-hover. */
private data class BaselinePathSymbol(val file: VirtualFile) : NavigatableSymbol, DocumentationTarget {
    override fun createPointer(): Pointer<BaselinePathSymbol> = Pointer { takeIf { file.isValid } }
    override fun getNavigationTargets(project: Project): Collection<NavigationTarget> =
        if (project.isDisposed || !file.isValid) emptyList() else listOf(BaselineFileNavigationTarget(project, file))
    override fun computePresentation(): TargetPresentation = TargetPresentation.builder(file.name).locationText(file.presentableUrl).presentation()
    override fun computeDocumentationHint(): String? = if (!file.isValid) null else
        "<html><code>${StringUtil.escapeXmlEntities(file.name)}</code><br>${StringUtil.escapeXmlEntities(file.presentableUrl)}</html>"
    override fun computeDocumentation(): DocumentationResult? = computeDocumentationHint()?.let { DocumentationResult.documentation(it) }
}

private data class BaselineFileNavigationTarget(val project: Project, val file: VirtualFile) : NavigationTarget {
    override fun createPointer(): Pointer<BaselineFileNavigationTarget> = Pointer { takeIf { !project.isDisposed && file.isValid } }
    override fun computePresentation(): TargetPresentation = TargetPresentation.builder(file.name).locationText(file.presentableUrl).presentation()
    override fun navigationRequest(): NavigationRequest? =
        if (project.isDisposed || !file.isValid) null else NavigationRequest.sourceNavigationRequest(project, file, 0)
}
