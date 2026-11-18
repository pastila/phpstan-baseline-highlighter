package dev.baselinehighlighter.editor

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl
import dev.baselinehighlighter.BaselineBundle
import dev.baselinehighlighter.core.BaselineMatcher
import dev.baselinehighlighter.index.BaselineIndexService
import dev.baselinehighlighter.php.PhpDeclarationProvider
import dev.baselinehighlighter.query.BaselineQueryService
import dev.baselinehighlighter.settings.BaselineSettings

class BaselineFunctionIntegrationTest : BasePlatformTestCase() {
    override fun createTempDirTestFixture() = TempDirTestFixtureImpl()

    private val phpText = """
        <?php
        namespace App {
            function run(${'$'}values) {}
            class Service { public function run() {} }
            class Holder {}
        }
        namespace Other { function run() {} }
    """.trimIndent()

    private fun configure(): Pair<VirtualFile, VirtualFile> {
        val php = myFixture.addFileToProject("src/Functions.php", phpText).virtualFile
        val messages = listOf(
            "Function App\\run() has no return type specified.",
            "Function App\\run() has parameter \$values with no type specified.",
            "Function Other\\run() has no return type specified.",
            "Function App\\missing() has no return type specified.",
            "Parameter #1 \$values of function App\\run expects array, int given.",
        )
        val text = "parameters:\n  ignoreErrors:\n" + messages.joinToString("") { message ->
            val pattern = "#^" + message.map { if (it.isLetterOrDigit() || it in " _-:") "$it" else "\\$it" }.joinToString("") + "$#"
            "    -\n      message: '$pattern'\n      identifier: test.function\n      path: ../src/Functions.php\n"
        }
        val neon = myFixture.addFileToProject("config/baseline.neon", text).virtualFile
        project.service<BaselineSettings>().replace(BaselineSettings.Data(sources = mutableListOf(BaselineSettings.Source(path = neon.path))))
        val index = project.service<BaselineIndexService>()
        index.reload()
        PlatformTestUtil.waitWithEventsDispatching("Function entries did not load", { !index.isUpdating && index.entries(php.path).size == 5 }, 15)
        myFixture.configureFromExistingVirtualFile(php)
        return php to neon
    }

    fun testBehaviorAdaptersDiscoverNamedFunctionsWithoutMethodsOrClosures() {
        val file = myFixture.configureByText("Functions.php", """
            <?php
            namespace App {
                function run() {}
                class Service { public function run() {} }
                ${'$'}closure = function () {};
                ${'$'}arrow = fn() => 1;
                if (true) { function conditional() {} }
                function outer() { function inner() {} }
            }
            namespace Other { function run() {} }
            namespace { function globalRun() {} }
        """.trimIndent())
        val functions = PhpDeclarationProvider.forFile(file)!!.functions(file)
        val expected = mutableSetOf("app\\run", "app\\outer", "other\\run", "globalrun")
        // Raketa PHP 262 does not emit function declaration nodes from statement blocks.
        if (System.getProperty("baseline.phpBackend") != "raketa") expected += setOf("app\\conditional", "app\\inner")
        assertEquals(expected, functions.map { BaselineMatcher.key(it.fqn) }.toSet())
        assertEquals(expected.size, functions.size)
        assertTrue(functions.all { it.identifier.text.isNotBlank() })
    }

    fun testBehaviorFunctionGuttersAndNavigationIgnoreMethodsAndOtherNamespaces() {
        val (php, neon) = configure()
        val functions = PhpDeclarationProvider.forFile(myFixture.file)!!.functions(myFixture.file)
        val bindings = PhpBaselineMatcher.bindings(myFixture.file)
        val expected = functions.associate { PsiTreeUtil.getDeepestFirst(it.identifier).textOffset to if (BaselineMatcher.key(it.fqn) == "app\\run") 2 else 1 }
        assertEquals(expected.keys, bindings.keys)
        for ((offset, count) in expected) assertEquals(count, bindings.getValue(offset).size)
        assertTrue(bindings.values.flatten().all { it.indexed.owner!!.className == null && it.indexed.owner.functionName != null })
        val gutters = myFixture.findAllGutters().filter { it.tooltipText?.contains("PHPStan baseline") == true }
        assertEquals(2, gutters.size)
        assertTrue(gutters.any { it.tooltipText!!.contains(BaselineBundle.message("tooltip.function", "App\\run")) })
        val entry = project.service<BaselineIndexService>().entries(php.path).first { it.owner?.functionName == "App\\run" }
        BaselineNavigation.navigate(project, entry)
        val document = FileDocumentManager.getInstance().getDocument(neon)!!
        PlatformTestUtil.waitWithEventsDispatching("Function entry did not open", {
            val editor = FileEditorManager.getInstance(project).selectedTextEditor
            editor?.document === document && editor.selectionModel.selectedText?.contains(entry.entry.message) == true
        }, 15)
    }

    fun testBehaviorUnsavedFunctionRenameAndRemovalLeaveEntriesOnlyAtFileLevel() {
        val (php, _) = configure()
        val index = project.service<BaselineIndexService>()
        val revision = index.modificationCount
        WriteCommandAction.runWriteCommandAction(project) {
            myFixture.editor.document.setText(phpText.replace("function run(\$values)", "function renamed(\$values)"))
        }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        val remaining = PhpBaselineMatcher.bindings(myFixture.file).values.flatten()
        assertEquals(1, remaining.size)
        assertEquals("Other\\run", remaining.single().indexed.owner!!.functionName)
        assertEquals(0, project.service<BaselineQueryService>().find(functionName = "App\\run").total)
        WriteCommandAction.runWriteCommandAction(project) {
            myFixture.editor.document.setText(myFixture.editor.document.text.replace("namespace Other { function run() {} }", "namespace Other {}"))
        }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        assertTrue(PhpBaselineMatcher.bindings(myFixture.file).isEmpty())
        assertEquals(5, project.service<BaselineQueryService>().find(filePath = php.path).total)
        assertEquals(revision, index.modificationCount)
    }
}
