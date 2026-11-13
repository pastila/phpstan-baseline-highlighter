package dev.baselinehighlighter.phpat

/** PHPat 0.12 StatementBuilder::extractRuleName; the class part is discarded. */
internal object PhpatRuleNames {
    fun normalize(name: String): String = name.replace(Regex("_([a-zA-Z])")) { it.groupValues[1].uppercase() }
        .replace(Regex("[^a-zA-Z0-9.]"), "").replace(Regex("\\.+"), ".").trim('.')

    fun matches(identifier: String, method: String, collection: Boolean): Boolean {
        val name = identifier.removePrefix("phpat.")
        val normalized = normalize(method)
        return identifier.startsWith("phpat.") && normalized.isNotEmpty() &&
            if (collection) name.startsWith(normalized) else name == normalized
    }

    /** Only declared collection returns qualify for the conservative generator fallback. */
    fun returnsCollection(methodText: String): Boolean {
        val signature = methodText.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "").substringBefore('{')
        return Regex("(?i)\\)\\s*:\\s*\\\\?(?:array|iterable|Generator|Traversable|Iterator)\\b").containsMatchIn(signature) ||
            Regex("@return\\s+(?:array(?:[<{\\s]|$)|iterable[<\\s]|\\\\?(?:Generator|Traversable|Iterator)\\b|[^\\s]+\\[\\])").containsMatchIn(methodText.substringBefore("function"))
    }
}
