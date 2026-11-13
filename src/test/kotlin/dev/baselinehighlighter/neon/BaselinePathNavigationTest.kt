package dev.baselinehighlighter.neon

import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction
import com.intellij.codeInsight.navigation.CtrlMouseData
import com.intellij.model.psi.PsiExternalReferenceHost
import com.intellij.model.psi.PsiSymbolReferenceHints
import com.intellij.model.psi.PsiSymbolReferenceService
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl
import dev.baselinehighlighter.editor.BaselinePathDeclarationHandler
import dev.baselinehighlighter.editor.BaselinePathNavigation
import dev.baselinehighlighter.editor.BaselinePathSymbolReference
import dev.baselinehighlighter.editor.BaselinePathReferenceProvider
import com.intellij.openapi.util.TextRange
import dev.baselinehighlighter.settings.BaselineSettings
import java.util.concurrent.CompletableFuture

class BaselinePathNavigationTest : BasePlatformTestCase() {
    override fun createTempDirTestFixture() = TempDirTestFixtureImpl()

    private fun source(path: String = "../src/Target.php") = """
        parameters:
          ignoreErrors:
            -
              message: '#^An old diagnostic$#'
              identifier: missingType.return
              path: $path
    """.trimIndent() + "\n"

    private fun configure(text: String = source(), name: String = "baseline.neon") {
        myFixture.addFileToProject("src/Target.php", "<?php class Target {}")
        val source = myFixture.addFileToProject("config/$name", text)
        myFixture.configureFromExistingVirtualFile(source.virtualFile)
    }

    private fun targets(offset: Int) = BaselinePathNavigation.linkAt(myFixture.file, myFixture.editor.document, offset)
        ?.let { BaselinePathNavigation.resolve(myFixture.file, it.path) }?.let { arrayOf(it) }

    private fun hoverAt(offset: Int): CtrlMouseData? {
        val action = GotoDeclarationAction()
        val result = CompletableFuture.supplyAsync {
            ReadAction.compute<CtrlMouseData?, RuntimeException> {
                action.getCtrlMouseData(myFixture.editor, myFixture.file, offset)
            }
        }
        PlatformTestUtil.waitWithEventsDispatching("Ctrl-hover lookup did not complete", { result.isDone }, 15)
        return result.get()
    }

    fun testRegisteredCtrlNavigationOpensTheTargetFile() {
        configure()
        val offset = myFixture.editor.document.text.indexOf("../src/Target.php") + 3
        val target = targets(offset)!!.single()
        assertEquals("Target.php", target.containingFile.name)
        val hover = hoverAt(offset)!!
        assertTrue(hover.isNavigatable)
        assertTrue(hover.hintText.orEmpty().contains("Target.php"))
        val pathStart = myFixture.editor.document.text.indexOf("../src/Target.php")
        assertEquals(listOf(TextRange(pathStart, pathStart + "../src/Target.php".length)), hover.ranges)
        assertNull(BaselinePathDeclarationHandler().getGotoDeclarationTargets(myFixture.file.findElementAt(offset), offset, myFixture.editor))
        assertNull(targets(myFixture.editor.document.text.indexOf("message:")))
        myFixture.editor.caretModel.moveToOffset(offset)
        myFixture.performEditorAction(IdeActions.ACTION_GOTO_DECLARATION)
        PlatformTestUtil.waitWithEventsDispatching("Target file did not open", {
            FileEditorManager.getInstance(project).selectedFiles.singleOrNull()?.name == "Target.php"
        }, 15)
    }

    fun testQuotedAndEscapedPathsHighlightOnlyTheValue() {
        for ((index, quoted) in listOf("'../src/Target.php'", "\"../src/Target.php\"", "\"../src/\\u0054arget.php\"").withIndex()) {
            configure(source("$quoted # comment"), "quoted-$index.neon")
            val text = myFixture.editor.document.text
            val start = text.indexOf("../src/")
            val rawValue = quoted.substring(1, quoted.length - 1)
            val hover = hoverAt(start + 3)!!
            assertTrue(hover.isNavigatable)
            assertEquals(listOf(TextRange(start, start + rawValue.length)), hover.ranges)
            assertEquals(rawValue, text.substring(hover.ranges.single().startOffset, hover.ranges.single().endOffset))
        }
    }

    fun testMultipleEntriesDoNotHighlightAdjacentFieldsOrEntries() {
        val second = source("../src/Other.php").substringAfter("  ignoreErrors:\n")
        configure(source() + second)
        myFixture.addFileToProject("src/Other.php", "<?php class Other {}")
        val text = myFixture.editor.document.text
        for (path in listOf("../src/Target.php", "../src/Other.php")) {
            val start = text.indexOf(path)
            assertEquals(listOf(TextRange(start, start + path.length)), hoverAt(start + 3)!!.ranges)
        }
    }

    fun testUnhintedReferenceRequestsAreEmptyAndStaleReferencesCannotNavigate() {
        configure()
        val service = PsiSymbolReferenceService.getService()
        val host = myFixture.file as PsiExternalReferenceHost
        val unhinted = service.getExternalReferences(host, PsiSymbolReferenceHints.referenceClassHint(BaselinePathSymbolReference::class.java))
        assertTrue(unhinted.isEmpty())
        val offset = myFixture.editor.document.text.indexOf("../src/Target.php")
        val reference = service.getExternalReferences(host, PsiSymbolReferenceHints.offsetHint(offset)).filterIsInstance<BaselinePathSymbolReference>().single()
        assertFalse(reference.resolveReference().isEmpty())
        WriteCommandAction.runWriteCommandAction(project) { myFixture.editor.document.insertString(0, "# shifted\n") }
        assertTrue(reference.resolveReference().isEmpty())
        // A bulk request must return before accessing file contents or the document.
        val untouched = java.lang.reflect.Proxy.newProxyInstance(javaClass.classLoader, arrayOf(com.intellij.psi.PsiPlainTextFile::class.java)) { _, method, _ ->
            error("Unhinted request accessed the file: ${method.name}")
        } as PsiExternalReferenceHost
        assertTrue(BaselinePathReferenceProvider().getReferences(untouched, PsiSymbolReferenceHints.referenceClassHint(BaselinePathSymbolReference::class.java)).isEmpty())
    }

    fun testConfiguredBaseDirectoryIsUsedWithoutIndexLookup() {
        configure(source("Target.php"))
        val target = myFixture.findFileInTempDir("src/Target.php")
        val source = myFixture.file.virtualFile
        project.service<BaselineSettings>().replace(BaselineSettings.Data(sources = mutableListOf(
            BaselineSettings.Source(path = source.path, baseDirectory = target.parent.path),
        )))
        assertEquals(target, targets(myFixture.editor.document.text.indexOf("Target.php"))!!.single().containingFile.virtualFile)
        project.service<BaselineSettings>().replace(BaselineSettings.Data())
        assertNull(targets(myFixture.editor.document.text.indexOf("Target.php")))
    }

    fun testQuotesTabsBomCrLfAndUnsavedPathChanges() {
        configure(source("'../src/Target.php' # a comment").replace("  ", "\t").replace("\n", "\r\n").let { "\uFEFF$it" })
        val document = myFixture.editor.document
        assertEquals("Target.php", targets(document.text.indexOf("../src/Target.php"))!!.single().containingFile.name)
        assertNull(targets(document.text.indexOf("a comment")))
        myFixture.addFileToProject("src/Other.php", "<?php class Other {}")
        WriteCommandAction.runWriteCommandAction(project) { document.setText(source("\"../src/Other.php\"")) }
        assertEquals("Other.php", targets(document.text.indexOf("../src/Other.php"))!!.single().containingFile.name)
        WriteCommandAction.runWriteCommandAction(project) { document.setText(source("../src/Absent.php")) }
        assertNull(targets(document.text.indexOf("../src/Absent.php")))
    }

    fun testUnrelatedFilesCommentsDirectoriesAndWildcardPathsAreIgnored() {
        configure(source(), "baseline.txt")
        assertNull(targets(myFixture.editor.document.text.indexOf("../src/Target.php")))
        configure("parameters:\n  path: ../src/Target.php\n", "unrelated.neon")
        assertNull(targets(myFixture.editor.document.text.indexOf("../src/Target.php")))
        configure(source("../src/*.php"), "wildcard.neon")
        assertNull(targets(myFixture.editor.document.text.indexOf("../src/*.php")))
        configure(source("../src"), "directory.neon")
        assertNull(targets(myFixture.editor.document.text.indexOf("../src")))
        configure("# path: ../src/Target.php\n" + source(), "comment.neon")
        assertNull(targets(myFixture.editor.document.text.indexOf("../src/Target.php")))
    }
}
