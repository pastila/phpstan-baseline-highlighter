package dev.baselinehighlighter.phpat

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import dev.baselinehighlighter.php.PhpatMethod
import dev.baselinehighlighter.php.PhpDeclarationProvider

/** Project NEON index and PHP symbol indices only; no directory walk or PHP execution. Read action required. */
internal object PhpatRuleResolver {
    data class Target(val className: String, val method: PhpatMethod) {
        val element: PsiElement get() = method.identifier
        val label: String get() = "$className::${method.name}()"
    }
    private data class Node(val includes: List<VirtualFile>, val classes: List<String>)
    private data class Snapshot(val nodes: Map<VirtualFile, Node>, val methods: Map<String, List<PhpatMethod>>)
    private val key = Key.create<CachedValue<Snapshot>>("baseline.phpat.rules")
    private const val MAX_CONFIG_CHARS = 262144

    fun resolve(source: PsiFile, identifier: String?): List<Target> {
        if (identifier == null || !identifier.startsWith("phpat.") || DumbService.isDumb(source.project)) return emptyList()
        val baseline = source.virtualFile ?: return emptyList()
        if (PhpDeclarationProvider.EP.extensionList.isEmpty()) return emptyList()
        val project = source.project
        val snapshot = CachedValuesManager.getManager(project).getCachedValue(project, key, {
            val dependencies = mutableListOf<Any>(PsiModificationTracker.MODIFICATION_COUNT,
                VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS, ProjectRootModificationTracker.getInstance(project))
            val nodes = linkedMapOf<VirtualFile, Node>()
            val documents = FileDocumentManager.getInstance()
            fun load(file: VirtualFile) {
                if (file in nodes || !file.isValid || file.isDirectory) return
                dependencies += file
                if (file.length > MAX_CONFIG_CHARS * 4L) return
                ProgressManager.checkCanceled()
                val document = documents.getDocument(file) ?: return
                dependencies += document
                if (document.textLength > MAX_CONFIG_CHARS) return
                val config = PhpatConfiguration.parse(document.immutableCharSequence.toString())
                val includes = config.includes.mapNotNull { path ->
                    when {
                        path.any { it in "%*?" } -> null
                        path.startsWith('/') || Regex("^[A-Za-z]:[/\\\\]").containsMatchIn(path) -> LocalFileSystem.getInstance().findFileByPathIfCached(path)
                        else -> file.parent?.findFileByRelativePath(path)
                    }
                }
                nodes[file] = Node(includes, config.testClasses)
                includes.filter { it.name.endsWith(".neon") || it.name.endsWith(".neon.dist") }.forEach { load(it) }
            }
            val scope = GlobalSearchScope.projectScope(project)
            val candidates = FilenameIndex.getAllFilesByExt(project, "neon", scope) +
                FilenameIndex.getAllFilesByExt(project, "dist", scope).filter { it.name.endsWith(".neon.dist") }
            candidates.filter { "/vendor/" !in it.path && "/node_modules/" !in it.path }.forEach { load(it) }
            val methods = nodes.values.flatMap { it.classes }.distinct().associateWith { clazz ->
                PhpDeclarationProvider.EP.extensionList.flatMap { it.phpatMethods(project, clazz) }.onEach {
                    documents.getDocument(it.identifier.containingFile.virtualFile)?.let(dependencies::add)
                }
            }
            CachedValueProvider.Result.create(Snapshot(nodes, methods), *dependencies.toTypedArray())
        }, false)
        fun closure(root: VirtualFile): Set<VirtualFile> {
            val visited = linkedSetOf<VirtualFile>()
            fun visit(file: VirtualFile) {
                if (visited.add(file)) snapshot.nodes[file]?.includes?.forEach(::visit)
            }
            visit(root)
            return visited
        }
        val active = snapshot.nodes.keys.map(::closure).filter { baseline in it }.flatten().toSet()
        val classes = active.flatMap { snapshot.nodes[it]?.classes.orEmpty() }.distinct()
        return classes.flatMap { clazz -> snapshot.methods[clazz].orEmpty().filter { method ->
            val element = method.identifier
            val document = element.containingFile?.viewProvider?.document
            element.isValid && document != null && PsiDocumentManager.getInstance(project).isCommitted(document) &&
                PhpatRuleNames.matches(identifier, method.name, method.collection)
        }.map { Target(clazz, it) } }.distinctBy { it.element }
    }
}
