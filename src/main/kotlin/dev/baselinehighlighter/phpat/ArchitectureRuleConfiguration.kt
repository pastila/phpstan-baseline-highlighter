package dev.baselinehighlighter.phpat

/** Declarative architecture YAML convention, not a PHP/YAML evaluator. Unsupported syntax fails closed. */
internal object ArchitectureRuleConfiguration {
    data class Match(val path: String, val start: Int, val end: Int)
    private data class Node(val key: String, val value: String, val start: Int, val end: Int,
        val list: Boolean = false, val indent: Int = -1, val plainScalar: Boolean = false,
        val children: MutableList<Node> = mutableListOf()) {
        fun child(key: String) = children.firstOrNull { !it.list && it.key == key }
    }

    /** The complete return body must be a literal array. Dynamic paths and partial arrays are rejected. */
    fun paths(method: String): List<Pair<Boolean, String>>? {
        val body = method.substringAfter('{', "").substringBeforeLast('}', "").trim()
        val array = Regex("return\\s*\\[([\\s\\S]*)]\\s*;").matchEntire(body)?.groupValues?.get(1) ?: return null
        val result = mutableListOf<Pair<Boolean, String>>()
        var offset = 0
        fun space() { while (offset < array.length && array[offset].isWhitespace()) offset++ }
        while (true) {
            space()
            if (offset == array.length) break
            val relative = array.startsWith("__DIR__", offset)
            if (relative) {
                offset += 7
                space()
                if (array.getOrNull(offset++) != '.') return null
                space()
            }
            if (array.getOrNull(offset++) != '\'') return null
            val literal = StringBuilder()
            while (offset < array.length && array[offset] != '\'') {
                val char = array[offset++]
                if (char == '\\' && array.getOrNull(offset) in listOf('\\', '\'')) literal.append(array[offset++])
                else literal.append(char)
            }
            if (array.getOrNull(offset++) != '\'') return null
            val path = literal.toString()
            if (path.isBlank() || path.any { it in "\u0000\n\r*?%" } || !path.endsWith(".yaml") && !path.endsWith(".yml")) return null
            if (!path.startsWith('/')) return null
            result += relative to path
            if (result.size > 32) return null
            space()
            if (offset == array.length) break
            if (array[offset++] != ',') return null
        }
        return result.takeIf { it.isNotEmpty() }
    }

    /** Later YAML files replace whole sections, matching the architecture format's merge convention. */
    fun find(texts: List<String>, method: String, identifier: String): List<Pair<Int, Match>> =
        index(texts, method)[identifier].orEmpty()

    fun index(texts: List<String>, method: String): Map<String, List<Pair<Int, Match>>> {
        val roots = texts.map { parse(it) ?: return emptyMap() }
        val sections = linkedMapOf<String, Pair<Int, Node>>()
        roots.forEachIndexed { index, root -> root.children.forEach { sections[it.key] = index to it } }
        val contexts = sections["contexts"]?.second ?: return emptyMap()
        val contextNames = contexts.children.flatMap { flatten(it) }
        if (contextNames.isEmpty()) return emptyMap()
        val layerCount = sections["layer_templates"]?.second?.children?.sumOf { 4 + (it.child("dependencies")?.child("special")?.children?.size ?: 0) } ?: 0
        val groupCount = sections["context_group_rules"]?.second?.children?.size ?: 0
        if (contextNames.size.toLong() * (layerCount + groupCount + 1) > 50000) return emptyMap()
        val found = linkedMapOf<String, MutableList<Pair<Int, Match>>>()
        fun candidate(rule: String, section: String, node: Node, path: String) {
            found.getOrPut("phpat." + PhpatRuleNames.normalize("$method:$rule")) { mutableListOf() }
                .add(sections.getValue(section).first to Match(path, node.start, node.end))
        }
        sections["layer_templates"]?.second?.children?.forEach { layer ->
            if (layer.list) return@forEach
            contextNames.forEach { context ->
                val prefix = context.replace('\\', '_') + "_" + layer.key.replace('.', '_').replace('\\', '_')
                listOf("onePublicMethod", "naming", "dependencies", "implements").forEach { field ->
                    layer.child(field)?.takeIf {
                        if (field == "onePublicMethod") it.value.equals("true", true)
                        else it.value.isEmpty() && it.children.isNotEmpty()
                    }?.let {
                        candidate("${prefix}_$field", "layer_templates", it, "layer_templates.${layer.key}.$field")
                    }
                }
                var special = 0
                layer.child("dependencies")?.child("special")?.children?.forEach { entry ->
                    if (entry.child("type")?.value == "canImplementButShouldNotDependOn") {
                        candidate("${prefix}_special_${special++}", "layer_templates", entry,
                            "layer_templates.${layer.key}.dependencies.special.${special - 1}")
                    }
                }
            }
        }
        sections["context_structure"]?.second?.takeIf { it.children.isNotEmpty() }?.let { structure ->
            contextNames.forEach { context -> candidate("contextStructure_${context.replace('\\', '_')}",
                "context_structure", structure, "context_structure") }
        }
        sections["context_group_rules"]?.second?.children?.forEach { rule ->
            val group = rule.child("context_group")?.value ?: return@forEach
            if (rule.child("subject")?.value.isNullOrBlank() || rule.child("targets")?.children.isNullOrEmpty()) return@forEach
            contexts.children.filter { it.key == group }.flatMap { flatten(it) }.forEach { context ->
                candidate("contextGroupRule_${rule.key}_${context.replace('\\', '_')}", "context_group_rules", rule,
                    "context_group_rules.${rule.key}")
            }
        }
        return found.mapValues { it.value.distinct() }
    }

    private fun flatten(node: Node, prefix: String = node.key): List<String> = when {
        node.children.isNotEmpty() -> node.children.flatMap { child ->
            if (child.list) listOf("$prefix\\${child.value}") else flatten(child, "$prefix\\${child.key}")
        }
        node.value in listOf("", "null", "~") -> listOf(prefix)
        node.value.startsWith('[') || node.value.startsWith('{') -> emptyList()
        else -> listOf("$prefix\\${node.value}")
    }

    private fun parse(text: String): Node? {
        val root = Node("", "", 0, 0)
        val stack = mutableListOf(-1 to root)
        var offset = 0
        for (raw in text.splitToSequence('\n')) {
            val line = if (offset == 0) raw.removePrefix("\uFEFF") else raw
            val indent = line.takeWhile { it == ' ' }.length
            val body = uncomment(line.substring(indent))?.trimEnd() ?: return null
            if (body.isBlank()) { offset += raw.length + 1; continue }
            if ('\t' in line.takeWhile { it.isWhitespace() } || body in listOf("---", "...") ||
                Regex("(^|[\\s:\\[,])[&*!|>]").containsMatchIn(body)) return null
            while (stack.last().first >= indent) stack.removeAt(stack.lastIndex)
            if (stack.size > 64) return null
            val parent = stack.last().second
            if (parent !== root && parent.value.isNotEmpty()) return null
            val start = offset + indent + if (offset == 0 && raw.startsWith('\uFEFF')) 1 else 0
            val list = body == "-" || body.startsWith("- ")
            val content = if (list) body.removePrefix("-").trimStart() else body
            val colon = mappingColon(content)
            val plainScalar = !list && colon < 0
            val key = if (colon >= 0) scalar(content.substring(0, colon).trim()) ?: return null else ""
            if (!list && !plainScalar && key.isEmpty() || plainScalar && parent === root) return null
            val value = if (colon >= 0) scalar(content.substring(colon + 1).trim()) ?: return null
                else scalar(content) ?: return null
            if (parent.children.isNotEmpty() && (parent.children.first().plainScalar || plainScalar)) return null
            if (parent.children.isNotEmpty() && parent.children.first().list != (list || plainScalar)) return null
            if (!list && !plainScalar && parent.children.any { it.key == key }) return null
            if (parent.children.isNotEmpty() && parent.children.first().indent != indent) return null
            val node = Node(if (list || plainScalar) "" else key, if (list && colon >= 0) "" else value,
                start, start + if (list) 1 else content.substringBefore(':').trimEnd().length, list || plainScalar, indent, plainScalar)
            parent.children += node
            stack += indent to node
            if (list && colon >= 0) {
                if (value.isEmpty()) return null
                val field = Node(key, value, start + 2, start + 2 + content.substringBefore(':').trimEnd().length, indent = indent + 2)
                node.children += field
                // Further mapping fields belong to this list item, not its first field.
            }
            offset += raw.length + 1
        }
        return root
    }

    private fun mappingColon(text: String): Int {
        var quote: Char? = null
        var escaped = false
        text.forEachIndexed { index, char ->
            if (escaped) escaped = false
            else if (quote == '"' && char == '\\') escaped = true
            else if (quote != null) { if (char == quote) quote = null }
            else if (char in "'\"") quote = char
            else if (char == ':' && (index == text.lastIndex || text[index + 1].isWhitespace())) return index
        }
        return -1
    }

    private fun uncomment(text: String): String? {
        var quote: Char? = null
        var escaped = false
        text.forEachIndexed { index, char ->
            if (escaped) escaped = false
            else if (quote == '"' && char == '\\') escaped = true
            else if (quote != null) { if (char == quote) quote = null }
            else if (char in "'\"") quote = char
            else if (char == '#' && (index == 0 || text[index - 1].isWhitespace())) return text.substring(0, index)
        }
        return text.takeIf { quote == null }
    }

    private fun scalar(text: String): String? {
        if (text.isEmpty()) return ""
        if (text.first() == '\'') {
            if (text.length < 2 || text.last() != '\'') return null
            val decoded = StringBuilder()
            var index = 1
            while (index < text.lastIndex) {
                val char = text[index++]
                if (char == '\'') {
                    if (index >= text.lastIndex || text[index++] != '\'') return null
                }
                decoded.append(char)
            }
            return decoded.toString()
        }
        if (text.first() == '"') {
            if (text.length < 2 || text.last() != '"') return null
            val body = text.substring(1, text.length - 1)
            val decoded = StringBuilder()
            var index = 0
            while (index < body.length) {
                val char = body[index++]
                if (char == '\\') {
                    if (index >= body.length || body[index] !in "\\\"") return null
                    decoded.append(body[index++])
                } else if (char == '"') return null else decoded.append(char)
            }
            return decoded.toString()
        }
        return text
    }
}
