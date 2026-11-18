package dev.baselinehighlighter.index

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.psi.PsiManager
import com.intellij.util.Alarm
import com.intellij.util.messages.Topic
import dev.baselinehighlighter.core.*
import dev.baselinehighlighter.settings.BaselineSettings
import dev.baselinehighlighter.php.PhpDeclarationProvider
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.InvalidPathException
import java.util.concurrent.CancellationException

data class SourceStatus(
    val sourcePath: String,
    val entries: List<IndexedEntry>,
    val issues: List<ParseIssue>,
    val unavailable: Boolean = false,
    val missingTargets: List<String> = emptyList(),
)
data class IndexSnapshot(val byFile: Map<String, List<IndexedEntry>> = emptyMap(), val sources: Map<String, SourceStatus> = emptyMap(),
    val settings: BaselineSettings.Data? = null)
fun interface BaselineIndexListener { fun updated() }

@Service(Service.Level.PROJECT)
class BaselineIndexService(private val project: Project, private val coroutineScope: CoroutineScope) : Disposable, ModificationTracker {
    private val alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, this)
    private val started = AtomicBoolean()
    private val generation = AtomicLong()
    private val version = AtomicLong()
    @Volatile private var publishedGeneration = -1L
    private val lock = Any()
    private val pending = mutableSetOf<String>()
    private var refreshAll = false
    @Volatile var snapshot = IndexSnapshot()
        private set

    override fun getModificationCount(): Long = version.get()
    fun normalize(path: String): String = BaselinePathResolver.resolve(path, project.basePath ?: "", SystemInfoRt.isFileSystemCaseSensitive)
    fun entries(path: String): List<IndexedEntry> {
        val current = snapshot
        return if (current.settings === project.service<BaselineSettings>().state) current.byFile[normalize(path)].orEmpty() else emptyList()
    }
    val isUpdating: Boolean get() = publishedGeneration != generation.get() || snapshot.settings !== project.service<BaselineSettings>().state

    fun start() {
        if (!started.compareAndSet(false, true)) return
        project.service<BaselineSettings>().addChangeListener(this) { if (!project.isDisposed) reload() }
        PhpDeclarationProvider.EP.addChangeListener(coroutineScope, Runnable { reload() })
        EditorFactory.getInstance().eventMulticaster.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) { changed(event.document) }
        }, this)
        ApplicationManager.getApplication().messageBus.connect(this).subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: List<VFileEvent>) {
                val sources = configuredSources().map { it.first }.toSet()
                if (sources.isEmpty()) return
                val affected = mutableSetOf<String>()
                for (event in events) {
                    val paths = listOfNotNull(event.path, event.file?.path?.takeIf { it != event.path })
                    for (rawPath in paths) {
                        val path = normalize(rawPath)
                        if (path in sources) affected += path
                        if (event.file?.isDirectory == true) affected += sources.filter { it.startsWith("${path.trimEnd('/')}/") }
                    }
                }
                if (affected.isNotEmpty()) request(affected)
            }
        })
        reload()
    }

    private fun changed(document: Document) {
        val file = FileDocumentManager.getInstance().getFile(document) ?: return
        val path = normalize(file.path)
        if (configuredSources().any { it.first == path }) request(setOf(path))
    }

    fun reload() {
        if (!started.get()) { start(); return }
        request(emptySet(), all = true)
    }

    private fun configuredSources(settings: BaselineSettings.Data = project.service<BaselineSettings>().state): List<Pair<String, String>> {
        if (!settings.enabled) return emptyList()
        return settings.activeSources().filter { it.enabled && it.path.isNotBlank() }.map { source ->
            val path = normalize(source.path)
            val base = if (source.baseDirectory.isBlank()) path.substringBeforeLast('/') else normalize(source.baseDirectory)
            path to base
        }.distinctBy { it.first }
    }

    private fun request(paths: Set<String>, all: Boolean = false) {
        synchronized(lock) {
            pending += paths
            refreshAll = refreshAll || all
            val ticket = generation.incrementAndGet()
            alarm.cancelAllRequests()
            alarm.addRequest({ try { rebuild(ticket) } catch (_: CancellationException) { /* Superseded by a newer request. */ } }, 350)
        }
    }

    private fun rebuild(ticket: Long) {
        if (project.isDisposed) return
        val (dirty, all) = synchronized(lock) { pending.toSet() to refreshAll }
        val previous = snapshot
        val settings = project.service<BaselineSettings>().state
        val sources = linkedMapOf<String, SourceStatus>()
        for ((path, base) in configuredSources(settings)) {
            if (generation.get() != ticket || project.isDisposed) return
            sources[path] = if (!all && previous.settings === settings && path !in dirty && path in previous.sources) previous.sources.getValue(path) else readSource(path, base, ticket)
        }
        val byFile = sources.values.flatMap { it.entries }.groupBy { it.targetPath }
        synchronized(lock) {
            if (generation.get() != ticket || project.isDisposed || project.service<BaselineSettings>().state !== settings) return
            snapshot = IndexSnapshot(byFile, sources, settings)
            publishedGeneration = ticket
            version.incrementAndGet()
            pending.clear()
            refreshAll = false
        }
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) {
                project.messageBus.syncPublisher(TOPIC).updated()
                val manager = PsiManager.getInstance(project)
                for (file in FileEditorManager.getInstance(project).openFiles) {
                    if (normalize(file.path) in previous.byFile || normalize(file.path) in byFile) {
                        manager.findFile(file)?.let { DaemonCodeAnalyzer.getInstance(project).restart(it) }
                    }
                }
            }
        }
    }

    private fun readSource(path: String, base: String, ticket: Long): SourceStatus {
        val text = ReadAction.compute<String?, RuntimeException> {
            val file = LocalFileSystem.getInstance().findFileByPath(path) ?: return@compute null
            if (file.isDirectory || !file.isValid) return@compute null
            FileDocumentManager.getInstance().getDocument(file)?.immutableCharSequence?.toString()
        } ?: return SourceStatus(path, emptyList(), emptyList(), unavailable = true)
        val result = BaselineParser.parse(text) {
            ProgressManager.checkCanceled()
            if (generation.get() != ticket || project.isDisposed) throw CancellationException()
        }
        val entries = result.entries.map {
            IndexedEntry(path, BaselinePathResolver.resolve(it.path, base, SystemInfoRt.isFileSystemCaseSensitive), it)
        }
        val missing = entries.map { it.targetPath }.distinct().filter {
            try { !Files.isRegularFile(Path.of(it)) } catch (_: InvalidPathException) { true }
        }
        return SourceStatus(path, entries, result.issues, missingTargets = missing)
    }

    override fun dispose() = Unit

    companion object {
        val TOPIC: Topic<BaselineIndexListener> = Topic.create("PHPStan baseline snapshot", BaselineIndexListener::class.java)
    }
}

class BaselineStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) { project.service<BaselineIndexService>().start() }
}
