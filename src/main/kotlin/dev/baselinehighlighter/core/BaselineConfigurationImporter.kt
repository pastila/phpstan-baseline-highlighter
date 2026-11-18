package dev.baselinehighlighter.core

/** Explicit includes graph only. No project scan, configuration execution or message regex evaluation. */
internal object BaselineConfigurationImporter {
    const val MAX_FILE_CHARS = 16 * 1024 * 1024
    private const val MAX_TOTAL_CHARS = 64L * 1024 * 1024
    private const val MAX_FILES = 512
    private const val MAX_DEPTH = 64
    data class Content(val text: String? = null, val problem: String = "unavailable")
    data class Issue(val path: String, val line: Int, val key: String, val detail: String = "")
    data class Result(val sources: List<String>, val issues: List<Issue>, val issueCount: Int, val filesRead: Int)

    fun discover(root: String, read: (String) -> Content, caseSensitive: Boolean = true,
        checkCanceled: () -> Unit = {}): Result {
        val sources = mutableListOf<String>()
        val issues = mutableListOf<Issue>()
        val visited = mutableSetOf<String>()
        val active = mutableSetOf<String>()
        var issueCount = 0
        var filesRead = 0
        var chars = 0L
        var filesLimited = false
        var totalLimited = false
        fun issue(path: String, line: Int, key: String, detail: String = "") {
            issueCount++
            if (issues.size < 100) issues += Issue(path, line, key, detail.take(1000))
        }
        fun visit(path: String, depth: Int, parent: String, line: Int) {
            checkCanceled()
            val key = BaselinePathResolver.resolve(path, "", caseSensitive)
            if (key in active) { issue(parent, line, "cycle", path); return }
            if (key in visited) return
            if (depth > MAX_DEPTH) { issue(parent, line, "depth.limit", path); return }
            if (visited.size >= MAX_FILES) {
                if (!filesLimited) issue(parent, line, "files.limit")
                filesLimited = true
                return
            }
            if (totalLimited) return
            visited += key
            if (!(path.endsWith(".neon", true) || path.endsWith(".neon.dist", true))) {
                issue(parent, line, "unsupported.file", path)
                return
            }
            val content = read(path)
            val text = content.text
            if (text == null) { issue(path, 1, content.problem); return }
            filesRead++
            if (text.length > MAX_FILE_CHARS) { issue(path, 1, "file.limit"); return }
            chars += text.length
            if (chars > MAX_TOTAL_CHARS) { issue(path, 1, "total.limit"); totalLimited = true; return }
            val config = PhpStanConfiguration.parse(text, checkCanceled)
            config.issues.forEach { issue(path, it.line, it.message) }
            issueCount += config.issueCount - config.issues.size
            if (config.hasIgnoreErrors) {
                val baseline = BaselineParser.parse(text, checkCanceled)
                if (baseline.issues.isEmpty()) sources += path
                else baseline.issues.first().let { issue(path, it.line, "baseline.invalid", it.message) }
            }
            active += key
            for (include in config.includes) {
                checkCanceled()
                if (include.path.length > 32768) {
                    issue(path, include.line, "path.limit")
                    continue
                }
                if (include.path.any { it in "%*?[]\u0000\r\n" } || "://" in include.path) {
                    issue(path, include.line, "unsupported.path", include.path)
                    continue
                }
                val child = BaselinePathResolver.resolve(include.path, path.substringBeforeLast('/'))
                visit(child, depth + 1, path, include.line)
            }
            active -= key
        }
        val path = BaselinePathResolver.resolve(root, "")
        visit(path, 0, path, 1)
        return Result(sources, issues, issueCount, filesRead)
    }
}
