package dev.baselinehighlighter.documentation

import dev.baselinehighlighter.BaselineBundle.message

/** Offline metadata; creating a tooltip or MCP response never makes a network request. */
object ErrorIdentifierDocumentation {
    data class Info(
        val identifier: String?,
        val errorGroup: String?,
        val knownIdentifier: Boolean,
        val explanation: String?,
        val documentationUrl: String?,
    )

    private val syntax = Regex("[A-Za-z0-9]+(?:\\.[A-Za-z0-9]+)*")
    private val explained = setOf(
        "missingType.iterableValue", "missingType.generics", "missingType.return", "missingType.parameter",
        "argument.type", "return.type", "method.notFound",
    )
    private val identifiers: Set<String> by lazy {
        requireNotNull(javaClass.getResourceAsStream("/documentation/phpstan-identifiers.txt")) {
            "Missing bundled PHPStan identifier catalog"
        }.bufferedReader(Charsets.UTF_8).use { reader ->
            reader.readLines().map(String::trim).filter { it.isNotEmpty() && !it.startsWith('#') }.toSet()
        }
    }

    fun isValid(identifier: String): Boolean = syntax.matches(identifier)

    fun lookup(identifier: String?): Info {
        if (identifier == null || !isValid(identifier)) return Info(identifier, null, false, null, null)
        val known = identifier in identifiers
        return Info(
            identifier,
            identifier.substringBefore('.', "").takeIf { it.isNotEmpty() }?.let { "$it.*" },
            known,
            if (known && identifier in explained) message("error.$identifier") else null,
            if (known) "https://phpstan.org/error-identifiers/$identifier" else null,
        )
    }
}
