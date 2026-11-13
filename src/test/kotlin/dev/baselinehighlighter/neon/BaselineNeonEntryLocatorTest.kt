package dev.baselinehighlighter.neon

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.lang.reflect.Proxy
import kotlin.system.measureNanoTime

class BaselineNeonEntryLocatorTest : BasePlatformTestCase() {
    private val header = "parameters:\n  ignoreErrors:\n"
    private val entry = "    -\n      message: '#^A diagnostic$#'\n      identifier: missingType.iterableValue\n      path: ../src/Target.php\n"

    fun testLargeBaselineLookupReadsABoundedWindow() {
        val source = header + entry.repeat(25000)
        val document = EditorFactory.getInstance().createDocument(source)
        val offset = source.lastIndexOf("../src/Target.php")
        var charsRead = 0
        val guardedText = object : CharSequence {
            private val delegate get() = document.immutableCharSequence
            override val length get() = delegate.length
            override fun get(index: Int): Char { charsRead++; return delegate[index] }
            override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
                charsRead += endIndex - startIndex
                check(charsRead < 100000) { "Lookup read beyond its bounded window" }
                return delegate.subSequence(startIndex, endIndex)
            }
            override fun toString(): String = error("Lookup copied the full document")
        }
        val guarded = Proxy.newProxyInstance(Document::class.java.classLoader, arrayOf(Document::class.java)) { _, method, args ->
            if (method.name == "getImmutableCharSequence" || method.name == "getCharsSequence") guardedText
            else method.invoke(document, *(args ?: emptyArray()))
        } as Document
        val result = BaselineNeonEntryLocator.entryAt(guarded, offset)!!
        assertEquals("../src/Target.php", result.path)
        assertEquals(offset, result.pathRange!!.start)
        assertEquals("missingType.iterableValue", result.identifier)
        assertTrue("Read $charsRead chars from ${source.length}", charsRead < 4096)
        repeat(10) { BaselineNeonEntryLocator.entryAt(document, offset - entry.length) }
        val samples = (0 until 100).map { index -> measureNanoTime {
            BaselineNeonEntryLocator.entryAt(document, offset - if (index % 2 == 0) 0 else entry.length)
        } }.sorted()
        println("Bounded NEON lookup: ${source.length} UTF-16 chars, 25000 entries, cold text read=$charsRead chars, warm alternating-entry p95=${samples[94] / 1_000_000.0} ms")
    }

    fun testOversizedHeadersLinesAndEntriesAreSkipped() {
        for (text in listOf(
            "# comment\n".repeat(70) + header + entry,
            header + entry.replace("#^A diagnostic$#", "#^" + "X".repeat(20000) + "$#"),
            header + entry.replace("      path:", "# comment\n".repeat(40) + "      path:"),
        )) {
            val document = EditorFactory.getInstance().createDocument(text)
            assertNull(BaselineNeonEntryLocator.entryAt(document, text.lastIndexOf("../src/Target.php")))
        }
    }

    fun testInlineEntriesAndLocalValidation() {
        val text = header + entry.replace("    -\n      message:", "    - message:") + entry.replace("path:", "paths:")
        val document = EditorFactory.getInstance().createDocument(text)
        val found = BaselineNeonEntryLocator.entryAt(document, text.indexOf("../src/Target.php"))!!
        assertEquals("../src/Target.php", found.path)
        assertNull(BaselineNeonEntryLocator.entryAt(document, text.lastIndexOf("../src/Target.php")))
    }
}
