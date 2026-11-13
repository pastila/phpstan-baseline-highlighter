package dev.baselinehighlighter.editor

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import dev.baselinehighlighter.core.BaselinePathResolver
import dev.baselinehighlighter.neon.BaselineNeonEntryLocator
import dev.baselinehighlighter.settings.BaselineSettings

/** Only Ctrl-hover / Go to Declaration; no reference collection, annotations or project scans. */
class BaselinePathDeclarationHandler : GotoDeclarationHandler, DumbAware {
    override fun getGotoDeclarationTargets(sourceElement: PsiElement?, offset: Int, editor: Editor): Array<PsiElement>? {
        val project = editor.project ?: return null
        val source = sourceElement?.containingFile ?: PsiDocumentManager.getInstance(project).getPsiFile(editor.document) ?: return null
        if (!source.name.endsWith(".neon", ignoreCase = true)) return null
        val entry = BaselineNeonEntryLocator.entryAt(editor.document, offset) ?: return null
        val range = entry.pathRange ?: return null
        if (offset < range.start || offset >= range.end) return null
        val file = source.virtualFile ?: return null
        val projectBase = project.basePath.orEmpty()
        fun normalize(path: String) = BaselinePathResolver.resolve(path, projectBase, SystemInfoRt.isFileSystemCaseSensitive)
        val sourcePath = normalize(file.path)
        val configuredBase = project.service<BaselineSettings>().state.sources.firstOrNull {
            it.path.isNotBlank() && normalize(it.path) == sourcePath
        }?.baseDirectory?.takeIf { it.isNotBlank() }
        val base = configuredBase?.let(::normalize) ?: file.parent?.path ?: return null
        val targetPath = BaselinePathResolver.resolve(entry.path, base, SystemInfoRt.isFileSystemCaseSensitive)
        // Ctrl-hover must not refresh the filesystem or probe an unavailable/mounted path.
        val target = LocalFileSystem.getInstance().findFileByPathIfCached(targetPath)?.takeIf { it.isValid && !it.isDirectory } ?: return null
        val psi = PsiManager.getInstance(project).findFile(target) ?: return null
        return arrayOf(psi)
    }
}
