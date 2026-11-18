package dev.baselinehighlighter.php.raketa

import com.intellij.lang.ASTNode
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SyntaxTraverser
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.PhpClass
import dev.baselinehighlighter.php.PhpDeclarationProvider
import dev.baselinehighlighter.php.PhpFunction
import dev.baselinehighlighter.php.PhpMethod
import dev.baselinehighlighter.php.PhpType
import dev.baselinehighlighter.php.PhpatMethod
import dev.baselinehighlighter.phpat.PhpatRuleNames
import java.lang.reflect.InvocationTargetException
import java.util.Locale

class RaketaPhpDeclarationProvider : PhpDeclarationProvider {
    override fun supports(file: PsiFile): Boolean = file.language.javaClass.name == "ru.raketa.php.PhpLanguage"

    override fun declarations(file: PsiFile): List<PhpType> =
        PsiTreeUtil.findChildrenOfType(file, PhpClass::class.java).mapNotNull { type ->
            val identifier = type.identifier() ?: return@mapNotNull null
            val methods = type.methods().map { (name, _) -> PhpMethod(name.text, name) }.toList()
            PhpType(type.fqn, identifier, methods)
        }

    override fun functions(file: PsiFile): List<PhpFunction> =
        SyntaxTraverser.psiTraverser(file).asSequence().filter { it.kind == "FUNCTION_DECLARATION" }.mapNotNull { function ->
            val identifier = function.identifier() ?: return@mapNotNull null
            val namespace = generateSequence(function.parent) { it.parent }
                .firstOrNull { it.kind == "NAMESPACE_DECLARATION" }
                ?.childrenOfKind("NAMESPACE_NAME")?.firstOrNull()?.nameText().orEmpty()
            val fqn = if (namespace.isEmpty()) identifier.text else "$namespace\\${identifier.text}"
            PhpFunction(fqn, identifier)
        }.toList()

    override fun phpatMethods(project: Project, className: String): List<PhpatMethod> {
        val index = PhpIndex.getInstance(project)
        return index.getClassesByFQN(qualified(className)).flatMap { type ->
            val visited = mutableSetOf<PhpClass>()
            fun methods(current: PhpClass): Sequence<Pair<PsiElement, PsiElement>> = sequence {
                ProgressManager.checkCanceled()
                if (!visited.add(current)) return@sequence
                yieldAll(current.methods())
                for (parent in current.childrenOfKind("PARENT_NAME")) {
                    val fqn = resolveClassName(parent.nameText(), current.node) ?: continue
                    for (declaration in index.getClassesByFQN(fqn)) yieldAll(methods(declaration))
                }
            }
            val effective = methods(type).distinctBy { (name, _) -> name.text.lowercase(Locale.ROOT) }.toList()
            val paths = effective.firstOrNull { (name, _) -> name.text.equals("getYamlPaths", true) }?.second
            effective.mapNotNull { (identifier, method) ->
                val name = identifier.text
                val prefix = methodPrefix(method)
                if (prefix.nonPublic || !name.startsWith("test") && prefix.attributes.none {
                    resolveClassName(it, method.node).equals("\\PHPat\\Test\\Attributes\\TestRule", ignoreCase = true)
                }) return@mapNotNull null
                PhpatMethod(name, identifier, PhpatRuleNames.returnsCollection(prefix.docComment + "\n" + method.text), paths)
            }
        }
    }

    // Raketa PHP exposes PhpIndex and PhpClass through its compatibility API. The declaration
    // name leaves and the remaining declaration kinds come from its parser, whose PSI types are internal.
    private val PsiElement.kind: String? get() = node?.elementType?.toString()

    private fun PsiElement.childrenOfKind(kind: String): Sequence<PsiElement> =
        generateSequence(firstChild) { it.nextSibling }.filter { it.kind == kind }

    private fun PsiElement.identifier(): PsiElement? = childrenOfKind("IDENTIFIER").firstOrNull()

    private fun PsiElement.nameText(): String = generateSequence(firstChild) { it.nextSibling }
        .filter { it.kind == "IDENTIFIER" || it.kind == "BACKSLASH" }.joinToString("") { it.text }

    private fun PhpClass.methods(): Sequence<Pair<PsiElement, PsiElement>> =
        childrenOfKind("METHOD_DECLARATION").mapNotNull { method -> method.identifier()?.let { it to method } }

    private fun qualified(name: String): String = "\\" + name.trimStart('\\')

    // Use Raketa's namespace and import resolution for parent classes and PHP attributes.
    private val classNameResolver by lazy {
        try {
            javaClass.classLoader.loadClass("ru.raketa.php.PhpSymbolsKt")
                .getMethod("phpClassName", String::class.java, ASTNode::class.java)
        } catch (_: ReflectiveOperationException) {
            null
        }
    }

    private fun resolveClassName(name: String, context: ASTNode): String? {
        if (name.startsWith('\\')) return name
        return try {
            classNameResolver?.invoke(null, name, context) as? String
        } catch (exception: InvocationTargetException) {
            throw exception.targetException
        }
    }

    private data class MethodPrefix(val nonPublic: Boolean, val docComment: String, val attributes: List<String>)

    private fun methodPrefix(method: PsiElement): MethodPrefix {
        val attributes = mutableListOf<String>()
        var nonPublic = false
        var leaf = PsiTreeUtil.prevVisibleLeaf(method.firstChild ?: return MethodPrefix(false, "", emptyList()))
        while (leaf != null) {
            when (leaf.text.lowercase(Locale.ROOT)) {
                "protected", "private" -> nonPublic = true
                "public", "static", "final", "abstract" -> Unit
                "]" -> {
                    val start = attributeStart(leaf) ?: break
                    attributes += attributeNames(start, leaf)
                    leaf = PsiTreeUtil.prevVisibleLeaf(start) // Skip the opening bracket and the leading '#'.
                }
                else -> break
            }
            leaf = leaf?.let(PsiTreeUtil::prevVisibleLeaf)
        }
        val docComment = leaf?.text?.takeIf { it.startsWith("/**") }.orEmpty()
        return MethodPrefix(nonPublic, docComment, attributes)
    }

    private fun attributeStart(close: PsiElement): PsiElement? {
        var brackets = 0
        for (leaf in generateSequence(close, PsiTreeUtil::prevVisibleLeaf)) {
            when (leaf.kind) {
                "RBRACKET" -> brackets++
                "LBRACKET" -> if (--brackets == 0) {
                    return leaf.takeIf { PsiTreeUtil.prevVisibleLeaf(it)?.textMatches("#") == true }
                }
                "SEMICOLON", "LBRACE", "RBRACE" -> return null
            }
        }
        return null
    }

    private fun attributeNames(start: PsiElement, close: PsiElement): List<String> = buildList {
        var depth = 0
        var atName = true
        var name = StringBuilder()
        for (leaf in generateSequence(PsiTreeUtil.nextVisibleLeaf(start), PsiTreeUtil::nextVisibleLeaf)) {
            if (leaf == close) break
            val token = leaf.kind
            if (token == "COMMENT") continue
            if (depth == 0 && leaf.textMatches(",")) {
                if (name.isNotEmpty()) add(name.toString())
                name = StringBuilder()
                atName = true
                continue
            }
            if (atName && (token == "IDENTIFIER" || token == "BACKSLASH" || token == "NAMESPACE")) {
                name.append(leaf.text)
            } else {
                atName = false
            }
            when (token) {
                "LPAREN", "LBRACKET", "LBRACE" -> depth++
                "RPAREN", "RBRACKET", "RBRACE" -> depth--
            }
        }
        if (name.isNotEmpty()) add(name.toString())
    }
}
