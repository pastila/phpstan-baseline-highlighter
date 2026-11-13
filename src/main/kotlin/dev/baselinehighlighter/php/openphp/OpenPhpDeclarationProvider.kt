package dev.baselinehighlighter.php.openphp

import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNameIdentifierOwner
import dev.baselinehighlighter.php.PhpDeclarationProvider
import dev.baselinehighlighter.php.PhpMethod
import dev.baselinehighlighter.php.PhpType
import ru.openide.openphp.lang.psi.PhpFile
import ru.openide.openphp.lang.psi.symbols.PhpDeclarations
import ru.openide.openphp.lang.psi.symbols.valueOrNull

class OpenPhpDeclarationProvider : PhpDeclarationProvider {
    override fun supports(file: PsiFile): Boolean = file is PhpFile
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
