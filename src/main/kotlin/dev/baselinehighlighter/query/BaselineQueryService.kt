package dev.baselinehighlighter.query

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiManager
import dev.baselinehighlighter.core.*
import dev.baselinehighlighter.documentation.ErrorIdentifierDocumentation
import dev.baselinehighlighter.index.BaselineIndexService
import dev.baselinehighlighter.php.PhpDeclarationProvider
import dev.baselinehighlighter.settings.BaselineSettings
import dev.baselinehighlighter.BaselineBundle.message
import java.security.MessageDigest

/** Shared queries contain no MCP API types, so they also work without MCP Server. */
@Service(Service.Level.PROJECT)
class BaselineQueryService(private val project: Project) {
    data class Match(val entryId: String, val indexed: IndexedEntry, val className: String?, val methodName: String?, val binding: String, val functionName: String? = null)
    data class SearchResult(val state: String, val entries: List<Match>, val total: Int, val offset: Int, val limit: Int)

    private data class Candidate(val indexed: IndexedEntry, val className: String?, val methodName: String?, val binding: String, val functionName: String? = null)

    private data class Remembered(val entry: IndexedEntry, val settings: BaselineSettings.Data)
    private val remembered = object : LinkedHashMap<String, Remembered>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Remembered>?): Boolean = size > 1000
    }

    fun find(filePath: String? = null, className: String? = null, methodName: String? = null, offset: Int = 0, limit: Int = 50, identifier: String? = null, functionName: String? = null): SearchResult {
        require(filePath == null || filePath.isNotBlank()) { "filePath must not be blank." }
        require(className == null || className.trimStart('\\').isNotBlank()) { "className must be a fully qualified class name." }
        require(methodName == null || methodName.isNotBlank()) { "methodName must not be blank." }
        require(functionName == null || functionName.trimStart('\\').isNotBlank()) { message("query.function.blank") }
        require(functionName == null || (className == null && methodName == null)) { message("query.function.exclusive") }
        require(identifier == null || ErrorIdentifierDocumentation.isValid(identifier)) { "identifier must contain only ASCII letters, digits and dot-separated nonempty parts." }
        require(filePath != null || className != null || functionName != null || identifier != null) { message("query.selector.required") }
        require(methodName == null || className != null) { "methodName requires className." }
        require(limit in 1..200 && offset >= 0) { "limit must be between 1 and 200; offset must be non-negative." }
        val index = project.service<BaselineIndexService>()
        index.start()
        val settings = project.service<BaselineSettings>().state
        if (!settings.enabled) return SearchResult("disabled", emptyList(), 0, offset, limit)
        if (index.isUpdating) return SearchResult("updating", emptyList(), 0, offset, limit)
        val snapshot = index.snapshot
        val revision = index.modificationCount
        val matches = ReadAction.compute<List<Candidate>, RuntimeException> {
            if (className != null || functionName != null) require(PhpDeclarationProvider.EP.extensionList.isNotEmpty()) {
                message("query.symbol.php")
            }
            val files = if (filePath == null) snapshot.byFile else {
                val path = index.normalize(filePath)
                mapOf(path to snapshot.byFile[path].orEmpty())
            }
            val result = mutableListOf<Candidate>()
            for ((path, fileEntries) in files.toSortedMap()) {
                ProgressManager.checkCanceled()
                val entries = if (identifier == null) fileEntries else fileEntries.filter { it.entry.identifier == identifier }
                if (entries.isEmpty()) continue
                if (className == null && functionName == null) {
                    for (entry in entries) result += Candidate(entry, entry.owner?.className, entry.owner?.methodName, "file", entry.owner?.functionName)
                    continue
                }
                if (functionName != null) {
                    val functionKey = BaselineMatcher.key(functionName)
                    if (entries.none { it.owner?.functionName?.let(BaselineMatcher::key) == functionKey }) continue
                    val file = LocalFileSystem.getInstance().findFileByPath(path)?.let { PsiManager.getInstance(project).findFile(it) } ?: continue
                    val functions = PhpDeclarationProvider.forFile(file)?.functions(file).orEmpty()
                    val function = functions.firstOrNull { BaselineMatcher.key(it.fqn) == functionKey } ?: continue
                    val binding = BaselineMatcher.bindFunctions(entries, functions.map { it.fqn }.toSet())[functionKey].orEmpty()
                    for (bound in binding) result += Candidate(bound.indexed, null, null, "none", function.fqn)
                    continue
                }
                checkNotNull(className)
                val classKey = BaselineMatcher.key(className)
                // Unknown owners may be attached to the only class in a file, just like gutter markers.
                if (entries.none { it.owner == null || it.owner.className?.let(BaselineMatcher::key) == classKey }) continue
                val file = LocalFileSystem.getInstance().findFileByPath(path)?.let { PsiManager.getInstance(project).findFile(it) } ?: continue
                val types = PhpDeclarationProvider.forFile(file)?.declarations(file).orEmpty()
                val type = types.firstOrNull { BaselineMatcher.key(it.fqn) == classKey } ?: continue
                val binding = BaselineMatcher.bind(entries, types.map { Declaration(it.fqn, it.methods.map { method -> method.name }.toSet()) })[classKey] ?: continue
                val selected = if (methodName == null) binding.entries else binding.methods[BaselineMatcher.key(methodName)].orEmpty()
                for (bound in selected) result += Candidate(bound.indexed, type.fqn, bound.indexed.owner?.methodName, bound.note.name.lowercase())
            }
            result
        }
        if (!project.service<BaselineSettings>().state.enabled) return SearchResult("disabled", emptyList(), 0, offset, limit)
        if (index.isUpdating || index.modificationCount != revision || project.service<BaselineSettings>().state !== settings) return SearchResult("updating", emptyList(), 0, offset, limit)
        val page = matches.asSequence().drop(offset).take(limit).map { match(it.indexed, it.className, it.methodName, it.binding, it.functionName) }.toList()
        synchronized(remembered) { page.forEach { remembered[it.entryId] = Remembered(it.indexed, settings) } }
        return SearchResult("ready", page, matches.size, offset, limit)
    }

    fun selected(entryId: String): IndexedEntry {
        val settings = project.service<BaselineSettings>().state
        require(settings.enabled) { "Baseline highlighting is disabled." }
        val record = synchronized(remembered) { remembered[entryId] }
        require(record != null) { "Unknown or expired entryId. Run find_baseline_entries again." }
        require(record.settings === settings) { message("query.profile.changed") }
        val selected = record.entry
        val index = project.service<BaselineIndexService>()
        require(settings.activeSources().any { it.enabled && it.path.isNotBlank() && index.normalize(it.path) == selected.sourcePath }) {
            "The baseline source is no longer enabled. Run find_baseline_entries again."
        }
        return selected
    }

    private fun match(entry: IndexedEntry, className: String?, methodName: String?, binding: String, functionName: String?): Match {
        val fields = listOf(entry.sourcePath, entry.entry.path, entry.entry.message, entry.entry.identifier.orEmpty(), entry.entry.count.toString(), entry.entry.range.toString())
        val identity = fields.joinToString("") { "${it.length}:$it" }
        val id = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        return Match(id, entry, className, methodName, binding, functionName)
    }
}
