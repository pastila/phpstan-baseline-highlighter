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
import com.intellij.psi.PsiManager
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.Segment
import com.intellij.psi.SmartPointerManager
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
    data class Configuration(val file: PsiFile, val match: ArchitectureRuleConfiguration.Match)
    data class Target(val className: String, val method: PhpatMethod, val configuration: Configuration? = null) {
        val element: PsiElement get() = configuration?.file ?: method.identifier
        val range: TextRange get() = configuration?.match?.let { TextRange(it.start, it.end) } ?: element.textRange
        val offset: Int get() = range.startOffset
        val generator: Boolean get() = configuration == null && method.collection
        val hintKey: String get() = when { configuration != null -> "phpat.configuration.hint"; generator -> "phpat.generator.hint"; else -> "phpat.source.hint" }
        val kindKey: String get() = when { configuration != null -> "phpat.configuration"; generator -> "phpat.generator"; else -> "phpat.declaration" }
        val label: String get() = configuration?.let {
            "${it.file.virtualFile.path.removePrefix(it.file.project.basePath.orEmpty() + "/")}:${it.file.viewProvider.document!!.getLineNumber(offset) + 1} — ${it.match.path}"
        } ?: "$className::${method.name}()"
        fun pointer() = TargetPointer(this)
    }
    class TargetPointer(target: Target) {
        private val declaration = if (target.configuration == null) SmartPointerManager.createPointer(target.method.identifier) else null
        private val location = target.configuration?.let {
            SmartPointerManager.getInstance(it.file.project).createSmartPsiFileRangePointer(it.file, target.range)
        }
        val declarationElement: PsiElement? get() = declaration?.element
        val element: PsiFile? get() = if (location != null) location.element else declarationElement?.containingFile
        val range: Segment? get() = if (location != null) location.range else declarationElement?.textRange
    }
    private data class Node(val includes: List<VirtualFile>, val classes: List<String>)
    private data class Snapshot(val nodes: Map<VirtualFile, Node>, val methods: Map<String, List<PhpatMethod>>)
    private val key = Key.create<CachedValue<Snapshot>>("baseline.phpat.rules")
    private data class ArchitectureSnapshot(val files: List<VirtualFile>, val rules: Map<String, List<Pair<Int, ArchitectureRuleConfiguration.Match>>>)
    private val architectureKey = Key.create<CachedValue<ArchitectureSnapshot>>("baseline.phpat.architecture")
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
        }.flatMap { method ->
            val fallback = Target(clazz, method)
            configurationTargets(fallback, identifier).ifEmpty { listOf(fallback) }
        } }.distinctBy { it.element.containingFile.virtualFile to it.range }
    }

    private fun configurationTargets(target: Target, identifier: String): List<Target> {
        if (!target.method.collection) return emptyList()
        val provider = target.method.architecturePathProvider?.takeIf { it.isValid } ?: return emptyList()
        if (provider.textLength > MAX_CONFIG_CHARS) return emptyList()
        val project = provider.project
        val providerDocument = provider.containingFile.viewProvider.document ?: return emptyList()
        if (!PsiDocumentManager.getInstance(project).isCommitted(providerDocument)) return emptyList()
        val snapshot = CachedValuesManager.getManager(project).getCachedValue(provider, architectureKey, {
            val dependencies = mutableListOf<Any>(PsiModificationTracker.MODIFICATION_COUNT, providerDocument,
                VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS, ProjectRootModificationTracker.getInstance(project))
            fun load(): ArchitectureSnapshot {
                val paths = ArchitectureRuleConfiguration.paths(provider.text) ?: return ArchitectureSnapshot(emptyList(), emptyMap())
                val files = paths.map { (relative, path) ->
                    val file = if (relative) provider.containingFile.virtualFile.parent.findFileByRelativePath(path.removePrefix("/"))
                        else LocalFileSystem.getInstance().findFileByPathIfCached(path)
                    file?.let(dependencies::add)
                    file?.takeIf { it.isValid && !it.isDirectory && it.length <= MAX_CONFIG_CHARS * 4L }
                        ?: return ArchitectureSnapshot(emptyList(), emptyMap())
                }
                val documents = files.map {
                    ProgressManager.checkCanceled()
                    val document = FileDocumentManager.getInstance().getDocument(it)
                    document?.let(dependencies::add)
                    document?.takeIf { doc -> doc.textLength <= MAX_CONFIG_CHARS } ?: return ArchitectureSnapshot(emptyList(), emptyMap())
                }
                if (documents.sumOf { it.textLength.toLong() } > MAX_CONFIG_CHARS * 4L) return ArchitectureSnapshot(emptyList(), emptyMap())
                return ArchitectureSnapshot(files, ArchitectureRuleConfiguration.index(documents.map { it.immutableCharSequence.toString() }, ""))
            }
            val result = load()
            CachedValueProvider.Result.create(result, *dependencies.toTypedArray())
        }, false)
        val suffix = identifier.removePrefix("phpat." + PhpatRuleNames.normalize(target.method.name))
        return snapshot.rules["phpat.$suffix"].orEmpty().mapNotNull { (index, match) ->
            PsiManager.getInstance(project).findFile(snapshot.files[index])?.let { target.copy(configuration = Configuration(it, match)) }
        }
    }
}
