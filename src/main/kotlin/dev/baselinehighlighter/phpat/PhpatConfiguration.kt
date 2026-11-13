package dev.baselinehighlighter.phpat

import dev.baselinehighlighter.core.BaselineParser

/** Reads the declarative subset of PHPStan NEON. Never evaluates factories or parameters. */
internal object PhpatConfiguration {
    data class Config(val includes: List<String>, val testClasses: List<String>)
    private data class Line(val indent: Int, val body: String)
    private val className = Regex("\\\\?[A-Za-z_\\x80-\\xff][A-Za-z0-9_\\x80-\\xff]*(?:\\\\[A-Za-z_\\x80-\\xff][A-Za-z0-9_\\x80-\\xff]*)*")
    private val header = Regex("(?m)^(?:includes|services):[ \\t]*(?:#.*)?\\r?$")

    fun parse(text: String): Config {
        val content = text.removePrefix("\uFEFF")
        if (!header.containsMatchIn(content)) return Config(emptyList(), emptyList())
        val lines = content.lineSequence().mapNotNull { raw ->
            val prefix = raw.takeWhile { it == ' ' || it == '\t' }
            val body = raw.substring(prefix.length).trimEnd()
            if (body.isBlank() || body.startsWith('#')) null
            else Line(prefix.sumOf { if (it == '\t') 4 else 1 }, body)
        }.toList()
        val includes = mutableListOf<String>()
        val classes = mutableListOf<String>()
        var index = 0
        while (index < lines.size) {
            val root = lines[index++]
            if (root.indent != 0) continue
            val end = (index until lines.size).firstOrNull { lines[it].indent == 0 } ?: lines.size
            val children = lines.subList(index, end)
            when (root.body.substringBefore('#').trim()) {
                "includes:" -> {
                    val indent = children.firstOrNull()?.indent
                    children.filter { it.indent == indent && it.body.startsWith("- ") }.forEach {
                        scalar(it.body.removePrefix("- ").trimStart())?.let(includes::add)
                    }
                }
                "services:" -> {
                    val indent = children.firstOrNull()?.indent
                    val starts = children.indices.filter { children[it].indent == indent }
                    starts.forEachIndexed { n, start ->
                        val block = children.subList(start, starts.getOrNull(n + 1) ?: children.size)
                        service(block)?.let(classes::add)
                    }
                }
            }
            index = end
        }
        return Config(includes.distinct(), classes.distinct())
    }

    private fun service(block: List<Line>): String? {
        val header = block.first()
        val fields = if (header.body.startsWith("- ")) listOf(Line(header.indent + 2, header.body.removePrefix("- "))) + block.drop(1) else block.drop(1)
        val fieldIndent = fields.firstOrNull()?.indent ?: return null
        var tagged = false
        var clazz: String? = null
        fields.forEachIndexed { index, line ->
            if (line.indent != fieldIndent) return@forEachIndexed
            if (line.body.startsWith("class:")) clazz = scalar(line.body.substringAfter(':').trimStart())
            if (line.body.startsWith("tags:")) {
                val value = line.body.substringAfter(':').substringBefore('#').trim()
                if (value.startsWith('[') && value.endsWith(']')) {
                    tagged = value.substring(1, value.length - 1).split(',').any { scalar(it.trim()) == "phpat.test" }
                } else if (value.isEmpty()) {
                    val tags = fields.drop(index + 1).takeWhile { it.indent > fieldIndent }
                    val tagIndent = tags.firstOrNull()?.indent
                    tagged = tags.filter { it.indent == tagIndent }.any {
                        val tag = it.body.removePrefix("- ").trim().substringBefore(':').trim()
                        scalar(tag) == "phpat.test"
                    }
                }
            }
        }
        if (!tagged) return null
        val headerBody = header.body
        // Named services can use the class FQN as their key, or Class(args) as their value.
        if (clazz == null && !headerBody.startsWith('-')) {
            clazz = headerBody.substringAfter(":", "").trim().takeIf { it.isNotEmpty() }
                ?: headerBody.substringBefore(':').trim()
        }
        return clazz?.substringBefore('(')?.trim()?.takeIf(className::matches)?.trimStart('\\')
    }

    private fun scalar(text: String): String? = try { BaselineParser.scalar(text).first } catch (_: IllegalArgumentException) { null }
}
