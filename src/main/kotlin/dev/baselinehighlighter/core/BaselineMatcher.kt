package dev.baselinehighlighter.core

import java.util.Locale

data class Declaration(val className: String, val methods: Set<String>)
enum class BindingNote { NONE, FILE_ONLY, METHOD_MISSING }
data class BoundEntry(val indexed: IndexedEntry, val note: BindingNote = BindingNote.NONE)
data class ClassBindings(val entries: List<BoundEntry>, val methods: Map<String, List<BoundEntry>>)

object BaselineMatcher {
    fun key(name: String): String = name.trimStart('\\').lowercase(Locale.ROOT)

    fun bind(entries: List<IndexedEntry>, declarations: List<Declaration>): Map<String, ClassBindings> {
        val types = declarations.associateBy { key(it.className) }
        val methodKeys = types.mapValues { (_, declaration) -> declaration.methods.map(::key).toSet() }
        val classes = mutableMapOf<String, MutableList<BoundEntry>>()
        val methods = mutableMapOf<String, MutableMap<String, MutableList<BoundEntry>>>()
        for (entry in entries) {
            val owner = entry.owner
            if (owner?.functionName != null) continue
            val classKey = owner?.className?.let(::key) ?: types.keys.singleOrNull() ?: continue
            if (classKey !in types) continue
            val method = owner?.methodName?.let(::key)
            val found = method != null && method in methodKeys.getValue(classKey)
            val note = when {
                owner == null -> BindingNote.FILE_ONLY
                method != null && !found -> BindingNote.METHOD_MISSING
                else -> BindingNote.NONE
            }
            val bound = BoundEntry(entry, note)
            classes.getOrPut(classKey) { mutableListOf() } += bound
            if (found) methods.getOrPut(classKey) { mutableMapOf() }.getOrPut(method) { mutableListOf() } += bound
        }
        return classes.mapValues { (name, records) -> ClassBindings(records, methods[name] ?: emptyMap()) }
    }

    fun bindFunctions(entries: List<IndexedEntry>, declarations: Set<String>): Map<String, List<BoundEntry>> {
        val functions = declarations.map(::key).toSet()
        val bound = mutableMapOf<String, MutableList<BoundEntry>>()
        for (entry in entries) {
            val function = entry.owner?.functionName?.let(::key) ?: continue
            if (function in functions) bound.getOrPut(function) { mutableListOf() } += BoundEntry(entry)
        }
        return bound.mapValues { (_, records) -> records.toList() }
    }
}
