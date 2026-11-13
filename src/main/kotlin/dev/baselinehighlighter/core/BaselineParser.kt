package dev.baselinehighlighter.core

/** A deliberately restricted parser for generated NEON baselines, with UTF-16 source offsets. */
object BaselineParser {
    private data class Field(val value: String, val range: SourceRange, val line: Int)
    private data class Line(val body: String, val indent: Int, val offset: Int, val number: Int)

    fun parse(text: String, checkCanceled: () -> Unit = {}): ParseResult {
        val issues = mutableListOf<ParseIssue>()
        val entries = mutableListOf<BaselineEntry>()
        val fields = linkedMapOf<String, Field>()
        var parameters = false
        var ignores = false
        var ignoreIndent = -1
        var itemIndent = -1
        var fieldIndent = -1
        var itemLine = 0
        var activeItem = false
        fun issue(line: Int, message: String) { issues += ParseIssue(line, message) }
        fun finish() {
            if (!activeItem) return
            val message = fields["message"]
            val path = fields["path"]
            if (message == null || path == null || message.value.isEmpty() || path.value.isEmpty()) {
                issue(itemLine, "entry.requires.message.path")
            } else {
                val count = fields["count"]?.value?.toIntOrNull()
                if (fields.containsKey("count") && (count == null || count <= 0)) issue(itemLine, "entry.invalid.count")
                if (path.value.any { it in "*?[]%" || it == '\u0000' }) issue(path.line, "entry.unsupported.path")
                entries += BaselineEntry(message.value, path.value, fields["identifier"]?.value, count, message.range, message.line, fields["identifier"]?.range, path.range)
            }
            fields.clear()
            activeItem = false
        }
        var offset = 0
        text.splitToSequence('\n').forEachIndexed { index, original ->
            checkCanceled()
            val raw = original.removeSuffix("\r").let { if (index == 0) it.removePrefix("\uFEFF") else it }
            val prefix = raw.takeWhile { it == ' ' || it == '\t' }
            val body = raw.substring(prefix.length)
            val line = Line(body, prefix.sumOf { if (it == '\t') 4 else 1 }, offset + prefix.length + if (index == 0 && original.startsWith('\uFEFF')) 1 else 0, index + 1)
            offset += original.length + 1
            if (body.isBlank() || body.startsWith('#')) return@forEachIndexed
            if (line.indent == 0 && body.substringBefore('#').trim() == "parameters:") {
                if (parameters) issue(line.number, "duplicate.parameters")
                parameters = true
                return@forEachIndexed
            }
            if (parameters && !ignores && line.indent > 0 && body.substringBefore('#').trim() in listOf("ignoreErrors:", "ignoreErrors: []")) {
                ignores = true
                ignoreIndent = line.indent
                return@forEachIndexed
            }
            if (!ignores || line.indent <= ignoreIndent) {
                issue(line.number, "unsupported.structure")
                return@forEachIndexed
            }
            var content = body
            var contentOffset = line.offset
            val newItem = content == "-" || content.startsWith("- ") || content.startsWith("-\t")
            if (newItem) {
                if (itemIndent != -1 && line.indent != itemIndent) issue(line.number, "invalid.indentation")
                finish()
                activeItem = true
                itemLine = line.number
                itemIndent = line.indent
                fieldIndent = -1
                val remainder = content.drop(1).trimStart()
                contentOffset += content.length - remainder.length
                content = remainder
                if (content.isBlank() || content.startsWith('#')) return@forEachIndexed
            } else {
                if (!activeItem || line.indent <= itemIndent) {
                    issue(line.number, "invalid.entry")
                    return@forEachIndexed
                }
                if (fieldIndent == -1) fieldIndent = line.indent
                else if (fieldIndent != line.indent) issue(line.number, "invalid.indentation")
            }
            val colon = content.indexOf(':')
            if (colon <= 0) {
                issue(line.number, "unsupported.structure")
                return@forEachIndexed
            }
            val key = content.substring(0, colon).trim()
            if (key !in setOf("message", "identifier", "count", "path")) {
                issue(line.number, "unsupported.field:$key")
                return@forEachIndexed
            }
            if (fields.containsKey(key)) issue(line.number, "duplicate.field:$key")
            val tail = content.substring(colon + 1)
            val valueText = tail.trimStart()
            val start = contentOffset + colon + 1 + tail.length - valueText.length
            try {
                val (value, length) = scalar(valueText)
                fields[key] = Field(value, SourceRange(start, start + length), line.number)
            } catch (_: IllegalArgumentException) {
                issue(line.number, "invalid.scalar")
            }
        }
        finish()
        if (!parameters || !ignores) issue(1, "missing.ignoreErrors")
        // An invalid source never contributes misleading, partially parsed markers.
        return ParseResult(if (issues.isEmpty()) entries else emptyList(), issues)
    }

    private fun scalar(text: String): Pair<String, Int> {
        require(text.isNotEmpty() && !text.startsWith("'''") && !text.startsWith("\"\"\""))
        if (text[0] != '\'' && text[0] != '"') {
            val raw = text.substringBefore(" #").trimEnd()
            require(raw.isNotEmpty() && raw.first() !in "[{#" && raw.lowercase() !in listOf("null", "true", "false", "yes", "no", "~"))
            return raw to raw.length
        }
        val quote = text[0]
        val value = StringBuilder()
        var i = 1
        while (i < text.length) {
            val c = text[i++]
            if (c == quote) {
                if (quote == '\'' && i < text.length && text[i] == '\'') { value.append('\''); i++; continue }
                val rest = text.substring(i).trimStart()
                require(rest.isEmpty() || rest.startsWith('#'))
                return value.toString() to i
            }
            if (quote == '"' && c == '\\') {
                require(i < text.length)
                when (val escape = text[i++]) {
                    '\\', '/', '"' -> value.append(escape)
                    'n' -> value.append('\n')
                    'r' -> value.append('\r')
                    't' -> value.append('\t')
                    'b' -> value.append('\b')
                    'f' -> value.append('\u000c')
                    '_' -> value.append('\u00a0')
                    'u' -> {
                        require(i + 4 <= text.length)
                        value.append(text.substring(i, i + 4).toInt(16).toChar())
                        i += 4
                    }
                    else -> throw IllegalArgumentException("Unsupported escape")
                }
            } else value.append(c)
        }
        throw IllegalArgumentException("Unclosed string")
    }
}
