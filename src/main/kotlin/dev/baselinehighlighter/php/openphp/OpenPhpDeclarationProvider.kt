package dev.baselinehighlighter.php.openphp

import com.intellij.psi.PsiFile
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import dev.baselinehighlighter.php.PhpatMethod
import dev.baselinehighlighter.phpat.PhpatRuleNames
import ru.openide.openphp.lang.psi.symbols.PhpIndex
import ru.openide.openphp.lang.psi.symbols.PhpAttributes
import ru.openide.openphp.lang.psi.symbols.PhpVisibility
import ru.openide.openphp.lang.psi.symbols.PhpHierarchy
import com.intellij.openapi.progress.ProgressManager
import java.util.Locale
import com.intellij.psi.PsiNameIdentifierOwner
import dev.baselinehighlighter.php.PhpDeclarationProvider
import dev.baselinehighlighter.php.PhpMethod
import dev.baselinehighlighter.php.PhpType
import dev.baselinehighlighter.php.PhpFunction
import ru.openide.openphp.lang.psi.PhpFile
import ru.openide.openphp.lang.psi.symbols.PhpDeclarations
import ru.openide.openphp.lang.psi.symbols.valueOrNull

class OpenPhpDeclarationProvider : PhpDeclarationProvider {
    override fun phpatMethods(project: Project, className: String): List<PhpatMethod> =
        PhpIndex.classesByFqn(className, project, GlobalSearchScope.allScope(project)).flatMap { type ->
            val visited = mutableSetOf<com.intellij.psi.PsiElement>()
            fun methods(current: com.intellij.psi.PsiElement): List<com.intellij.psi.PsiElement> {
                ProgressManager.checkCanceled()
                if (!visited.add(current)) return emptyList()
                return PhpDeclarations.classOf(current).valueOrNull()?.ownMethods.orEmpty() +
                    PhpHierarchy.superTypes(current).types.flatMap(::methods)
            }
            methods(type).distinctBy { (it as? PsiNameIdentifierOwner)?.name?.lowercase(Locale.ROOT) }.mapNotNull { element ->
                val method = PhpDeclarations.methodOf(element).valueOrNull() ?: return@mapNotNull null
                if (method.flags.visibility != PhpVisibility.PUBLIC) return@mapNotNull null
                if (!method.name.startsWith("test") && PhpAttributes.of(element).valueOrNull().orEmpty().none {
                    it.fqn.trimStart('\\') == "PHPat\\Test\\Attributes\\TestRule"
                }) return@mapNotNull null
                val identifier = (element as? PsiNameIdentifierOwner)?.nameIdentifier ?: return@mapNotNull null
                PhpatMethod(method.name, identifier, PhpatRuleNames.returnsCollection(element.text))
            }
        }

    override fun supports(file: PsiFile): Boolean = file is PhpFile
    override fun functions(file: PsiFile): List<PhpFunction> =
        PhpDeclarations.inFile(file, false).mapNotNull { element ->
            val info = PhpDeclarations.functionOf(element).valueOrNull() ?: return@mapNotNull null
            if (info.name.isEmpty()) return@mapNotNull null
            val identifier = (element as? PsiNameIdentifierOwner)?.nameIdentifier ?: return@mapNotNull null
            PhpFunction(info.fqn.trimStart('\\'), identifier)
        }

    override fun declarations(file: PsiFile): List<PhpType> =
        PhpDeclarations.inFile(file, false).mapNotNull { element ->
            val type = element as? PsiNameIdentifierOwner ?: return@mapNotNull null
            val identifier = type.nameIdentifier ?: return@mapNotNull null
            if (type.name.isNullOrEmpty()) return@mapNotNull null
            val info = PhpDeclarations.classOf(type).valueOrNull() ?: return@mapNotNull null
            PhpType(info.fqn, identifier, info.ownMethods.mapNotNull { element ->
                val method = element as? PsiNameIdentifierOwner ?: return@mapNotNull null
                val name = method.name ?: return@mapNotNull null
                method.nameIdentifier?.let { PhpMethod(name, it) }
            })
        }
}
