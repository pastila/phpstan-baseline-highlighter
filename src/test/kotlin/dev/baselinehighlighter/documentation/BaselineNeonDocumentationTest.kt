package dev.baselinehighlighter.documentation

import com.intellij.lang.documentation.ide.IdeDocumentationTargetProvider
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl
import dev.baselinehighlighter.core.BaselineParser

class BaselineNeonDocumentationTest : BasePlatformTestCase() {
    override fun createTempDirTestFixture() = TempDirTestFixtureImpl()

    private fun source(identifier: String? = "missingType.iterableValue"): String = """
        # missingType.iterableValue in a comment is not a documentation target
        parameters:
          ignoreErrors:
            -
              message: '#^Message mentioning argument.type$#'
    """.trimIndent() + "\n" + (identifier?.let { "      identifier: '$it'\n" } ?: "") + "      count: 1\n      path: ../src/File.php\n"

    private fun configure(text: String = source(), name: String = "custom-baseline.neon"): PsiFile {
        val file = myFixture.addFileToProject("config/$name", text)
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        return myFixture.file
    }

    private fun targets(file: PsiFile, offset: Int): List<DocumentationTarget> = BaselineDocumentationTargetProvider().documentationTargets(file, offset)

    fun testRegisteredProviderSuppliesMessageAndIdentifierDocumentation() {
        val file = configure()
        val entry = BaselineParser.parse(file.text).entries.single()
        val dispatcher = IdeDocumentationTargetProvider.getInstance(project)
        for (offset in listOf(entry.range.start, entry.identifierRange!!.start + 2)) {
            val target = dispatcher.documentationTargets(myFixture.editor, file, offset).filterIsInstance<BaselineDocumentationTarget>().single()
            assertEquals("missingType.iterableValue", target.computePresentation().presentableText)
            val hint = target.computeDocumentationHint()!!
            assertTrue(hint.contains("array&lt;User&gt;"))
            assertTrue(hint.contains("href=\"https://phpstan.org/error-identifiers/missingType.iterableValue\""))
            assertTrue(hint.contains("missingType.*"))
            assertTrue(target.computeDocumentation() is DocumentationResult.Documentation)
            assertNotNull(target.createPointer().dereference())
        }
    }

    fun testCommentsPathsAndOtherIdentifierMentionsDoNotBecomeTargets() {
        val file = configure()
        assertTrue(targets(file, file.text.indexOf("missingType.iterableValue")).isEmpty()) // Comment.
        assertTrue(targets(file, file.text.indexOf("../src/File.php")).isEmpty())
        val entry = BaselineParser.parse(file.text).entries.single()
        assertTrue(targets(file, entry.range.end).isEmpty())
        val messageTarget = targets(file, file.text.indexOf("argument.type")).single()
        assertEquals("missingType.iterableValue", messageTarget.computePresentation().presentableText)
        assertFalse(messageTarget.computeDocumentationHint()!!.contains("error-identifiers/argument.type"))
    }

    fun testUnsavedEditsRefreshTargetsAndInvalidateChangedEntryPointers() {
        val file = configure()
        val document = myFixture.editor.document
        val entry = BaselineParser.parse(file.text).entries.single()
        val target = targets(file, entry.range.start).single()
        val pointer = target.createPointer()
        WriteCommandAction.runWriteCommandAction(project) { document.insertString(0, "# inserted comment\n") }
        val moved = pointer.dereference()!!
        assertTrue(moved.computeDocumentationHint()!!.contains("missingType.iterableValue"))
        WriteCommandAction.runWriteCommandAction(project) {
            document.setText(document.text.replace("identifier: 'missingType.iterableValue'", "identifier: 'missingType.return'"))
        }
        assertNull(pointer.dereference())
        assertNull(target.computeDocumentationHint())
        val changed = targets(file, document.text.indexOf("identifier: 'missingType.return'") + 15).single()
        assertEquals("missingType.return", changed.computePresentation().presentableText)
        assertTrue(changed.computeDocumentationHint()!!.contains("error-identifiers/missingType.return"))
        val changedPointer = changed.createPointer()
        WriteAction.run<RuntimeException> { file.virtualFile.delete(this) }
        assertNull(changedPointer.dereference())
        assertNull(changed.computeDocumentation())
    }

    fun testUnknownIdentifiersHaveNoAssumedLinkAndMissingIdentifiersHaveNoTarget() {
        val custom = configure(source("phpat.projectRule"))
        val entry = BaselineParser.parse(custom.text).entries.single()
        val hint = targets(custom, entry.range.start).single().computeDocumentationHint()!!
        assertTrue(hint.contains("phpat.*"))
        assertFalse(hint.contains("href="))
        assertFalse(hint.contains("error-identifiers/phpat"))
        val withoutId = configure(source(null), "without-id.neon")
        assertTrue(targets(withoutId, withoutId.text.indexOf("Message")).isEmpty())
        val unsafe = configure(source("<img src=x>"), "unsafe-id.neon")
        val unsafeHint = targets(unsafe, unsafe.text.indexOf("Message")).single().computeDocumentationHint()!!
        assertFalse(unsafeHint.contains("<img"))
        assertTrue(unsafeHint.contains("&lt;img"))
        assertFalse(unsafeHint.contains("href="))
    }

    fun testUnrelatedAndMalformedFilesAreIgnoredButPlainTextNeonWorks() {
        val textFile = configure(source(), "baseline.txt")
        assertTrue(targets(textFile, textFile.text.indexOf("Message")).isEmpty())
        val config = configure("parameters:\n  identifier: missingType.iterableValue\n", "config.neon")
        assertTrue(targets(config, config.text.indexOf("missingType")).isEmpty())
        val invalid = configure(source().replace("path: ../src/File.php", "paths: [../src/File.php]"), "invalid.neon")
        assertTrue(targets(invalid, invalid.text.indexOf("Message")).isEmpty())
        val plainNeon = PsiFileFactory.getInstance(project).createFileFromText("plain.neon", PlainTextFileType.INSTANCE, source())
        assertEquals("TEXT", plainNeon.language.id)
        assertEquals(1, targets(plainNeon, plainNeon.text.indexOf("Message")).size)
    }
}
