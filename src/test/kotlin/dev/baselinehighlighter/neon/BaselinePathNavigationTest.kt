package dev.baselinehighlighter.neon

import com.intellij.codeInsight.navigation.action.GotoDeclarationUtil
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl
import dev.baselinehighlighter.editor.BaselinePathDeclarationHandler
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

    private fun targets(offset: Int) = BaselinePathDeclarationHandler().getGotoDeclarationTargets(myFixture.file, offset, myFixture.editor)

    fun testRegisteredCtrlNavigationOpensTheTargetFile() {
        configure()
        val offset = myFixture.editor.document.text.indexOf("../src/Target.php") + 3
        val target = GotoDeclarationUtil.findTargetElementsFromProviders(myFixture.editor, offset, myFixture.file)!!.single()
        assertEquals("Target.php", target.containingFile.name)
        val action = GotoDeclarationAction()
        val hoverResult = CompletableFuture.supplyAsync {
            ReadAction.compute<com.intellij.codeInsight.navigation.CtrlMouseData?, RuntimeException> {
                action.getCtrlMouseData(myFixture.editor, myFixture.file, offset)
            }
        }
        PlatformTestUtil.waitWithEventsDispatching("Ctrl-hover lookup did not complete", { hoverResult.isDone }, 15)
        val hover = hoverResult.get()!!
        assertTrue(hover.isNavigatable)
        assertTrue(hover.ranges.any { it.contains(offset) })
        assertNull(targets(myFixture.editor.document.text.indexOf("message:")))
        myFixture.editor.caretModel.moveToOffset(offset)
        myFixture.performEditorAction(IdeActions.ACTION_GOTO_DECLARATION)
        PlatformTestUtil.waitWithEventsDispatching("Target file did not open", {
            FileEditorManager.getInstance(project).selectedFiles.singleOrNull()?.name == "Target.php"
        }, 15)
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
