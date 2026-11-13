package dev.baselinehighlighter.core

import java.util.Locale

object BaselinePathResolver {
    private val drive = Regex("^[A-Za-z]:/")
    fun resolve(path: String, base: String, caseSensitive: Boolean = true): String {
        val value = path.replace('\\', '/')
        val absolute = value.startsWith('/') || drive.containsMatchIn(value)
        val joined = if (absolute) value else "${base.replace('\\', '/').trimEnd('/')}/$value"
        val prefix = when {
            joined.startsWith("//") -> "//"
            joined.startsWith('/') -> "/"
            else -> ""
        }
        val parts = mutableListOf<String>()
        for (part in joined.split('/')) when (part) {
            "", "." -> Unit
            ".." -> if (parts.isNotEmpty() && parts.last() != ".." && !parts.last().endsWith(':')) parts.removeAt(parts.lastIndex)
            else -> parts += part
        }
        val normalized = prefix + parts.joinToString("/")
        return if (caseSensitive) normalized else normalized.lowercase(Locale.ROOT)
    }

    fun portable(path: String, projectBase: String): String {
        val normalized = resolve(path, projectBase)
        val base = resolve(projectBase, projectBase).trimEnd('/')
        return if (normalized.startsWith("$base/")) normalized.removePrefix("$base/") else normalized
    }
}
