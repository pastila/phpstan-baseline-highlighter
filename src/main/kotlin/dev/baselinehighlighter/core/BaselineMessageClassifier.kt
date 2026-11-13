package dev.baselinehighlighter.core

object BaselineMessageClassifier {
    private val name = "[\\p{L}_\\u0080-\\uffff][\\p{L}\\p{N}_\\u0080-\\uffff]*"
    private val fqn = "\\\\?$name(?:\\\\$name)*"
    private val method = Regex("^(?:Method |Return type of method )($fqn)::($name)\\(\\) ")
    private val property = Regex("^Property ($fqn)::\\$$name ")
    private val architecture = Regex("^(?:[A-Za-z0-9_.]+: )?($fqn) should not depend on ")

    fun owner(pattern: String): OwnerHint? {
        val literal = literalPrefix(pattern) ?: return null
        method.find(literal)?.let { return OwnerHint(it.groupValues[1].trimStart('\\'), it.groupValues[2]) }
        property.find(literal)?.let { return OwnerHint(it.groupValues[1].trimStart('\\')) }
        architecture.find(literal)?.let { return OwnerHint(it.groupValues[1].trimStart('\\')) }
        return null
    }

    fun display(pattern: String): String = literalPrefix(pattern)?.takeIf { it.isNotEmpty() } ?: pattern

    /** Decode only anchored literal patterns, never execute a baseline's PCRE. */
    private fun literalPrefix(pattern: String): String? {
        if (pattern.length < 3 || pattern[1] != '^') return null
        val delimiter = pattern[0]
        if (delimiter.isLetterOrDigit() || delimiter == '\\') return null
        val result = StringBuilder()
        var i = 2
        while (i < pattern.length) {
            val c = pattern[i++]
            if (c == '$' && i == pattern.length - 1 && pattern[i] == delimiter) return result.toString()
            if (c == delimiter) return null
            if (c == '\\') {
                if (i >= pattern.length) return null
                val escaped = pattern[i++]
                if (escaped.isLetterOrDigit()) return null
                result.append(escaped)
            } else {
                if (c in ".*+?[]{}()|^$") return null
                result.append(c)
            }
        }
        return null
    }
}
