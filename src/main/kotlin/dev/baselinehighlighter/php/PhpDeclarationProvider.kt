package dev.baselinehighlighter.php

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.openapi.project.Project

data class PhpMethod(val name: String, val identifier: PsiElement)
data class PhpType(val fqn: String, val identifier: PsiElement, val methods: List<PhpMethod>)
data class PhpFunction(val fqn: String, val identifier: PsiElement)
data class PhpatMethod(val name: String, val identifier: PsiElement, val collection: Boolean,
    val architecturePathProvider: PsiElement? = null)

/** Only optional integrations may depend on a PHP vendor's PSI classes. */
interface PhpDeclarationProvider {
    fun supports(file: PsiFile): Boolean
    fun declarations(file: PsiFile): List<PhpType>
    fun functions(file: PsiFile): List<PhpFunction> = emptyList()
    fun phpatMethods(project: Project, className: String): List<PhpatMethod> = emptyList()

    companion object {
        val EP: ExtensionPointName<PhpDeclarationProvider> = ExtensionPointName.create("dev.baselinehighlighter.phpDeclarationProvider")
        fun forFile(file: PsiFile): PhpDeclarationProvider? = EP.extensionList.firstOrNull { it.supports(file) }
    }
}
