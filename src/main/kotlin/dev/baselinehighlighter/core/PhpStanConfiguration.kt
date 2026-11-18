package dev.baselinehighlighter.core

/** Reads only static root includes and detects a direct parameters.ignoreErrors section. */
internal object PhpStanConfiguration {
    data class Include(val path: String, val line: Int)
    data class Config(val includes: List<Include>, val hasIgnoreErrors: Boolean, val issues: List<ParseIssue>, val issueCount: Int)
    private const val MAX_INCLUDES = 512

    fun parse(text: String, checkCanceled: () -> Unit = {}): Config {
        val includes = mutableListOf<Include>()
        val issues = mutableListOf<ParseIssue>()
        var section = ""
        var includesSeen = false
        var duplicateIncludes = false
        var includeIndent = -1
        var parameterIndent = -1
        var hasIgnoreErrors = false
        var limited = false
        var issueCount = 0
        val syntax = LexicalState(checkCanceled)
        fun issue(line: Int, key: String) {
            issueCount++
            if (issues.size < MAX_INCLUDES) issues += ParseIssue(line, key)
        }
        fun include(value: String, line: Int) {
            if (includes.size >= MAX_INCLUDES) {
                if (!limited) issue(line, "includes.limit")
                limited = true
                return
            }
            try {
                val quoted = value.firstOrNull() in listOf('\'', '"')
                val scalar = if (quoted) value else value.takeWhileIndexed { index, char ->
                    char != '#' || index > 0 && !value[index - 1].isWhitespace()
                }
                val path = BaselineParser.scalar(scalar).first
                require(path.isNotBlank() && (quoted || !path.contains(Regex(":\\s"))))
                includes += Include(path, line)
            } catch (_: IllegalArgumentException) { issue(line, "includes.invalid") }
        }
        text.lineSequence().forEachIndexed { index, original ->
            checkCanceled()
            val raw = original.removeSuffix("\r").let { if (index == 0) it.removePrefix("\uFEFF") else it }
            val prefix = raw.takeWhile { it == ' ' || it == '\t' }
            val body = raw.substring(prefix.length).trimEnd()
            val indent = prefix.sumOf { if (it == '\t') 4 else 1 }
            val line = index + 1
            val continued = syntax.continued
            syntax.scan(raw)
            if (continued) return@forEachIndexed
            if (body.isBlank() || body.startsWith('#')) return@forEachIndexed
            if (indent == 0) {
                val rawKey = body.substringBefore(':').trim()
                val key = try { BaselineParser.scalar(rawKey).first } catch (_: IllegalArgumentException) { rawKey }
                val value = body.substringAfter(':', "").trimStart()
                section = key
                includeIndent = -1
                parameterIndent = -1
                if (key == "includes") {
                    if (includesSeen) { duplicateIncludes = true; issue(line, "includes.duplicate") }
                    includesSeen = true
                    when {
                        value.isBlank() || value.startsWith('#') -> Unit
                        value.startsWith('[') -> {
                            val items = inlineList(value)
                            if (items == null) issue(line, "includes.invalid")
                            else items.forEach { include(it, line) }
                            section = ""
                        }
                        else -> { issue(line, "includes.invalid"); section = "" }
                    }
                }
                if (key == "parameters" && value.isNotBlank() && !value.startsWith('#')) {
                    issue(line, "parameters.unsupported")
                    section = ""
                }
            } else when (section) {
                "includes" -> {
                    if (includeIndent == -1) includeIndent = indent
                    if (indent != includeIndent || !(body.startsWith("- ") || body.startsWith("-\t"))) issue(line, "includes.invalid")
                    else include(body.drop(1).trimStart(), line)
                }
                "parameters" -> {
                    if (parameterIndent == -1) parameterIndent = indent
                    if (indent < parameterIndent) issue(line, "parameters.unsupported")
                    if (indent == parameterIndent && body.substringBefore(':').trim() == "ignoreErrors") hasIgnoreErrors = true
                }
            }
        }
        val invalidSyntax = syntax.continued || syntax.invalid
        if (invalidSyntax) issue(1, "syntax.unsupported")
        return Config(if (duplicateIncludes || invalidSyntax) emptyList() else includes, hasIgnoreErrors, issues, issueCount)
    }

    /** Sections inside strings or continued flow collections are data, not root configuration keys. */
    private class LexicalState(private val checkCanceled: () -> Unit) {
        private var quote: Char? = null
        private var triple: String? = null
        private val flow = mutableListOf<Char>()
        var invalid = false
            private set
        val continued: Boolean get() = quote != null || triple != null || flow.isNotEmpty()

        fun scan(text: String) {
            if (invalid) return
            var index = 0
            while (index < text.length) {
                if (index and 8191 == 0) checkCanceled()
                val char = text[index]
                val delimiter = triple
                when {
                    delimiter != null -> {
                        if (text.startsWith(delimiter, index)) { triple = null; index += 3; continue }
                        if (delimiter == "\"\"\"" && char == '\\') index++
                    }
                    quote != null -> {
                        if (quote == '"' && char == '\\') index++
                        else if (char == quote) {
                            if (quote == '\'' && text.getOrNull(index + 1) == '\'') index++ else quote = null
                        }
                    }
                    char == '#' && (index == 0 || text[index - 1].isWhitespace()) -> return
                    char in "'\"" && boundary(text, index) -> {
                        val token = char.toString().repeat(3)
                        if (text.startsWith(token, index)) { triple = token; index += 3; continue }
                        quote = char
                    }
                    char in "[{" && boundary(text, index) -> {
                        if (flow.size >= 64) { invalid = true; return }
                        flow += char
                    }
                    char in "]}" && flow.isNotEmpty() -> {
                        if (flow.removeAt(flow.lastIndex) != if (char == ']') '[' else '{') invalid = true
                    }
                }
                index++
            }
        }

        private fun boundary(text: String, index: Int): Boolean = index == 0 || text[index - 1].isWhitespace() || text[index - 1] in ":,[{(="
    }

    private inline fun String.takeWhileIndexed(predicate: (Int, Char) -> Boolean): String {
        val end = indices.firstOrNull { !predicate(it, this[it]) } ?: length
        return substring(0, end)
    }

    private fun inlineList(text: String): List<String>? {
        val items = mutableListOf<String>()
        var quote: Char? = null
        var escaped = false
        var start = 1
        var index = 1
        while (index < text.length) {
            val char = text[index]
            when {
                escaped -> escaped = false
                quote == '"' && char == '\\' -> escaped = true
                quote != null -> {
                    if (char == quote) {
                        if (quote == '\'' && text.getOrNull(index + 1) == '\'') index++ else quote = null
                    }
                }
                char in "'\"" -> quote = char
                char == '[' || char == '{' || char == '#' -> return null
                char == ',' || char == ']' -> {
                    val item = text.substring(start, index).trim()
                    if (item.isNotEmpty()) items += item
                    else if (char == ',') return null
                    if (items.size > MAX_INCLUDES) return null
                    start = index + 1
                    if (char == ']') return items.takeIf { text.substring(index + 1).trimStart().let { it.isEmpty() || it.startsWith('#') } }
                }
            }
            index++
        }
        return null
    }
}
