package dev.baselinehighlighter.core

object BaselineMessageClassifier {
    private val name = "[\\p{L}_\\u0080-\\uffff][\\p{L}\\p{N}_\\u0080-\\uffff]*"
    private val fqn = "\\\\?$name(?:\\\\$name)*"
    private val methodPatterns = listOf(
        Regex("^(?:Method |Return type of method )($fqn)::($name)\\(\\) "),
        Regex("^Public method \"($fqn)::($name)\\(\\)\" is never used\\.?$"),
        Regex("^Return type \\(.+?\\) of method ($fqn)::($name)\\(\\) should be compatible with return type "),
    )
    private val classDeclarationPatterns = listOf(
        Regex("^Class ($fqn) (?:implements generic interface|extends generic class) $fqn but does not specify its types: .+$"),
        Regex("^Class ($fqn) has PHPDoc tag @[A-Za-z][A-Za-z0-9-]* .+$"),
    )
    private val functionDeclaration = Regex("^Function ($fqn)\\(\\) (?:has (?:parameter |no return type specified|invalid return type )|return type has |should return )")
    private val property = Regex("^Property ($fqn)::\\$$name (.+)$")
    private val propertyDeclarationDetails = listOf(
        Regex("has no type specified\\.?"),
        Regex("type has no value type specified in iterable type .+"),
        Regex("with generic (?:class|interface) .+ does not specify its types: .+"),
        Regex("has unknown class .+ as its type\\.?"),
        Regex("(?:\\(.+\\) )?is never (?:read(?:, only written)?|written(?:, only read)?)\\.?"),
        Regex("(?:\\(.+\\) )?is never assigned .+ so it can be removed from the property type\\.?"),
        Regex("(?:\\(.+\\) )?does not accept default value of type .+"),
    )
    private val architecture = Regex("^(?:[A-Za-z0-9_.]+: )?($fqn) should not depend on ")
    private val architectureDeclaration = Regex(
        "^(?:[A-Za-z0-9_.]+: )?($fqn) should (?:" +
            "(?:not exist|have only one public method)\\.?|(?:be named matching the regex|implement|extend) .+)$"
    )

    fun owner(pattern: String): OwnerHint? {
        val literal = literalPrefix(pattern) ?: return null
        for (method in methodPatterns) {
            method.find(literal)?.let { return OwnerHint(it.groupValues[1].trimStart('\\'), it.groupValues[2]) }
        }
        for (declaration in classDeclarationPatterns) {
            declaration.find(literal)?.let { return OwnerHint(it.groupValues[1].trimStart('\\')) }
        }
        functionDeclaration.find(literal)?.let { return OwnerHint(functionName = it.groupValues[1].trimStart('\\')) }
        property.find(literal)?.let { match ->
            // Access diagnostics name a property's declaring class, not the owner of the access.
            if (propertyDeclarationDetails.any { it.matches(match.groupValues[2]) }) {
                return OwnerHint(match.groupValues[1].trimStart('\\'))
            }
        }
        architecture.find(literal)?.let { return OwnerHint(it.groupValues[1].trimStart('\\')) }
        architectureDeclaration.find(literal)?.let { return OwnerHint(it.groupValues[1].trimStart('\\')) }
        return null
    }

    fun display(pattern: String): String = literalPrefix(pattern)?.takeIf { it.isNotEmpty() } ?: pattern

    /** Decode only start-anchored literal patterns with an optional end anchor; never execute PCRE. */
    private fun literalPrefix(pattern: String): String? {
        if (pattern.length < 3 || pattern[1] != '^') return null
        val delimiter = pattern[0]
        if (delimiter.isLetterOrDigit() || delimiter == '\\') return null
        val result = StringBuilder()
        var i = 2
        while (i < pattern.length) {
            val c = pattern[i++]
            if (c == '$' && i == pattern.length - 1 && pattern[i] == delimiter) return result.toString()
            if (c == delimiter) return if (i == pattern.length) result.toString() else null
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
