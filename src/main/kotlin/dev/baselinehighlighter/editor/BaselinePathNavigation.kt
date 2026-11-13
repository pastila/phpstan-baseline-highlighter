package dev.baselinehighlighter.editor

import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import dev.baselinehighlighter.core.BaselinePathResolver
import dev.baselinehighlighter.neon.BaselineNeonEntryLocator
import dev.baselinehighlighter.settings.BaselineSettings

internal object BaselinePathNavigation {
    data class Link(val path: String, val range: TextRange, val rawRange: TextRange)

    fun linkAt(source: PsiFile, document: Document, offset: Int): Link? {
        if (!source.name.endsWith(".neon", ignoreCase = true)) return null
        val entry = BaselineNeonEntryLocator.entryAt(document, offset) ?: return null
        val range = entry.pathRange ?: return null
        var start = range.start
        var end = range.end
        val text = document.immutableCharSequence
        if (end - start >= 2 && text[start] in "\"'" && text[end - 1] == text[start]) { start++; end-- }
        if (offset < start || offset >= end) return null
        return Link(entry.path, TextRange(start, end), TextRange(range.start, range.end))
    }

    fun resolve(source: PsiFile, path: String): PsiFile? = resolveFile(source, path)?.let { PsiManager.getInstance(source.project).findFile(it) }

    fun resolveFile(source: PsiFile, path: String): VirtualFile? {
        val file = source.virtualFile ?: return null
        val project = source.project
        val projectBase = project.basePath.orEmpty()
        fun normalize(value: String) = BaselinePathResolver.resolve(value, projectBase, SystemInfoRt.isFileSystemCaseSensitive)
        val sourcePath = normalize(file.path)
        val configuredBase = project.service<BaselineSettings>().state.sources.firstOrNull {
            it.path.isNotBlank() && normalize(it.path) == sourcePath
        }?.baseDirectory?.takeIf { it.isNotBlank() }
        val base = configuredBase?.let(::normalize) ?: file.parent?.path ?: return null
        val targetPath = BaselinePathResolver.resolve(path, base, SystemInfoRt.isFileSystemCaseSensitive)
        return LocalFileSystem.getInstance().findFileByPathIfCached(targetPath)?.takeIf { it.isValid && !it.isDirectory }
    }
}
