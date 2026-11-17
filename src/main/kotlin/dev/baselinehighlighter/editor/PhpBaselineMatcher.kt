package dev.baselinehighlighter.editor

import com.intellij.openapi.components.service
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiTreeUtil
import dev.baselinehighlighter.php.PhpDeclarationProvider
import dev.baselinehighlighter.core.*
import dev.baselinehighlighter.index.BaselineIndexService

object PhpBaselineMatcher {
    private val cacheKey = Key.create<CachedValue<Map<Int, List<BoundEntry>>>>("baseline.declaration.bindings")

    fun bindings(file: PsiFile): Map<Int, List<BoundEntry>> {
        val index = file.project.service<BaselineIndexService>()
        return CachedValuesManager.getManager(file.project).getCachedValue(file, cacheKey, {
            val entries = file.virtualFile?.let { index.entries(it.path) }.orEmpty()
            val provider = if (entries.isEmpty()) null else PhpDeclarationProvider.forFile(file)
            val types = provider?.declarations(file).orEmpty()
            val bound = BaselineMatcher.bind(entries, types.map { Declaration(it.fqn, it.methods.map { method -> method.name }.toSet()) })
            val byOffset = mutableMapOf<Int, List<BoundEntry>>()
            for (type in types) {
                val bindings = bound[BaselineMatcher.key(type.fqn)] ?: continue
                byOffset[PsiTreeUtil.getDeepestFirst(type.identifier).textOffset] = bindings.entries
                for (method in type.methods) {
                    val records = bindings.methods[BaselineMatcher.key(method.name)] ?: continue
                    byOffset[PsiTreeUtil.getDeepestFirst(method.identifier).textOffset] = records
                }
            }
            if (entries.any { it.owner?.functionName != null }) {
                val functions = provider?.functions(file).orEmpty()
                val functionBindings = BaselineMatcher.bindFunctions(entries, functions.map { it.fqn }.toSet())
                for (function in functions) {
                    val records = functionBindings[BaselineMatcher.key(function.fqn)] ?: continue
                    byOffset[PsiTreeUtil.getDeepestFirst(function.identifier).textOffset] = records
                }
            }
            CachedValueProvider.Result.create(byOffset.toMap(), file, index)
        }, false)
    }
}
