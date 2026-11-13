package dev.baselinehighlighter.php.jetbrains

import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.php.lang.psi.PhpFile
import com.jetbrains.php.lang.psi.elements.PhpClass
import dev.baselinehighlighter.php.PhpDeclarationProvider
import dev.baselinehighlighter.php.PhpMethod
import dev.baselinehighlighter.php.PhpType

class JetBrainsPhpDeclarationProvider : PhpDeclarationProvider {
    override fun supports(file: PsiFile): Boolean = file is PhpFile
    override fun declarations(file: PsiFile): List<PhpType> =
        PsiTreeUtil.findChildrenOfType(file, PhpClass::class.java).mapNotNull { type ->
            if (type.isAnonymous || type.name.isEmpty()) return@mapNotNull null
            val identifier = type.nameIdentifier ?: return@mapNotNull null
            PhpType(type.fqn, identifier, type.ownMethods.mapNotNull { method ->
                method.nameIdentifier?.let { PhpMethod(method.name, it) }
            })
        }
}
