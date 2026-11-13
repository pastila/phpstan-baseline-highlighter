package dev.baselinehighlighter.neon

import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.Key
import dev.baselinehighlighter.core.BaselineEntry
import dev.baselinehighlighter.core.BaselineParser
import dev.baselinehighlighter.core.SourceRange

/** Bounded, on-demand reads for hover/navigation. Never copies or parses the whole document. */
internal object BaselineNeonEntryLocator {
    private const val MAX_HEADER_LINES = 64
    private const val MAX_HEADER_CHARS = 8192
    private const val MAX_ENTRY_LINES = 32
    private const val MAX_ENTRY_CHARS = 32768
    private const val MAX_LINE_CHARS = 16384
    private data class Line(val start: Int, val end: Int, val indent: Int, val body: String) {
        val ignored: Boolean get() = body.isBlank() || body.startsWith('#')
        val listItem: Boolean get() = body == "-" || body.startsWith("- ") || body.startsWith("-\t")
    }
    private data class Header(val line: Int, val indent: Int, val entryIndent: Int)
    private data class CachedHeader(val stamp: Long, val header: Header?)
    private data class CachedEntry(val stamp: Long, val start: Int, val end: Int, val entry: BaselineEntry?)
    private val headerKey = Key.create<CachedHeader>("baseline.neon.local.header")
    private val entryKey = Key.create<CachedEntry>("baseline.neon.local.entry")

    fun entryAt(document: Document, offset: Int): BaselineEntry? {
        if (offset < 0 || offset >= document.textLength) return null
        val currentLine = document.getLineNumber(offset)
        val line = line(document, currentLine) ?: return null
        val field = line.body.removePrefix("-").trimStart().substringBefore(':')
        if (field !in setOf("message", "identifier", "path")) return null
        val stamp = document.modificationStamp
        document.getUserData(entryKey)?.takeIf { it.stamp == stamp && offset >= it.start && offset < it.end }?.let { return it.entry }
        val header = header(document, stamp) ?: return null
        if (currentLine <= header.line) return null
        var first = -1
        for (index in currentLine downTo maxOf(header.line + 1, currentLine - MAX_ENTRY_LINES + 1)) {
            if (line.end - document.getLineStartOffset(index) > MAX_ENTRY_CHARS) return null
            val candidate = line(document, index) ?: return null
            if (candidate.ignored) continue
            if (candidate.indent == header.entryIndent && candidate.listItem) { first = index; break }
            if (candidate.indent <= header.entryIndent) return null
        }
        if (first < 0) return null
        val start = document.getLineStartOffset(first)
        var end = -1
        for (index in first + 1..minOf(document.lineCount, first + MAX_ENTRY_LINES)) {
            if (index == document.lineCount) { end = document.textLength; break }
            if (document.getLineEndOffset(index) - start > MAX_ENTRY_CHARS) return null
            val candidate = line(document, index) ?: return null
            if (!candidate.ignored && candidate.indent <= header.entryIndent) { end = candidate.start; break }
            if (candidate.end - start > MAX_ENTRY_CHARS) return null
        }
        if (end < 0 || end - start > MAX_ENTRY_CHARS) return null
        val prefix = "parameters:\n${" ".repeat(header.indent)}ignoreErrors:\n"
        val block = document.immutableCharSequence.subSequence(start, end).toString()
        val parsed = BaselineParser.parse(prefix + block).entries.singleOrNull()
        val shift = start - prefix.length
        fun SourceRange.shifted() = SourceRange(this.start + shift, this.end + shift)
        val entry = parsed?.let {
            val range = it.range.shifted()
            it.copy(range = range, line = document.getLineNumber(range.start) + 1,
                identifierRange = it.identifierRange?.shifted(), pathRange = it.pathRange?.shifted())
        }
        document.putUserData(entryKey, CachedEntry(stamp, start, end, entry))
        return entry
    }

    private fun header(document: Document, stamp: Long): Header? {
        document.getUserData(headerKey)?.takeIf { it.stamp == stamp }?.let { return it.header }
        val found = readHeader(document)
        document.putUserData(headerKey, CachedHeader(stamp, found))
        return found
    }

    private fun readHeader(document: Document): Header? {
        var parameters = false
        var ignoreLine = -1
        var ignoreIndent = -1
        for (index in 0 until minOf(document.lineCount, MAX_HEADER_LINES)) {
            if (document.getLineEndOffset(index) > MAX_HEADER_CHARS) return null
            val line = line(document, index) ?: return null
            if (line.ignored) continue
            val body = line.body.substringBefore('#').trimEnd()
            when {
                !parameters && line.indent == 0 && body == "parameters:" -> parameters = true
                parameters && ignoreLine < 0 && line.indent in 1..128 && body == "ignoreErrors:" -> {
                    ignoreLine = index
                    ignoreIndent = line.indent
                }
                ignoreLine >= 0 && line.indent > ignoreIndent && line.listItem -> return Header(ignoreLine, ignoreIndent, line.indent)
                else -> return null
            }
        }
        return null
    }

    private fun line(document: Document, index: Int): Line? {
        val start = document.getLineStartOffset(index)
        val end = document.getLineEndOffset(index)
        if (end - start > MAX_LINE_CHARS) return null
        val raw = document.immutableCharSequence.subSequence(start, end).toString().let { if (index == 0) it.removePrefix("\uFEFF") else it }
        val prefix = raw.takeWhile { it == ' ' || it == '\t' }
        return Line(start, end, prefix.sumOf { if (it == '\t') 4 else 1 }, raw.substring(prefix.length))
    }
}
