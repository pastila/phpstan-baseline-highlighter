package dev.baselinehighlighter.editor

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement

/** Fallback for NEON PSI tokens; never let a coarse PSI leaf highlight multiple fields. */
class BaselinePathDeclarationHandler : GotoDeclarationHandler, DumbAware {
    override fun getGotoDeclarationTargets(sourceElement: PsiElement?, offset: Int, editor: Editor): Array<PsiElement>? {
        val project = editor.project ?: return null
        val source = sourceElement?.containingFile ?: PsiDocumentManager.getInstance(project).getPsiFile(editor.document) ?: return null
        val link = BaselinePathNavigation.linkAt(source, editor.document, offset) ?: return null
        val leafRange = sourceElement?.textRange ?: return null
        if (!link.rawRange.contains(leafRange)) return null
        val target = BaselinePathNavigation.resolve(source, link.path) ?: return null
        return arrayOf(target)
    }
}
