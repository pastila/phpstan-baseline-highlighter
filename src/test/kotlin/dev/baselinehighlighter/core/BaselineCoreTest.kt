package dev.baselinehighlighter.core

import org.junit.Assert.*
import org.junit.Test

class BaselineCoreTest {
    private fun parse(fields: String) = BaselineParser.parse("parameters:\n  ignoreErrors:\n    -\n" + fields.prependIndent("      "))
    private fun pattern(message: String) = "#^" + message.map { if (it.isLetterOrDigit() || it in " _-:") "$it" else "\\$it" }.joinToString("") + "$#"
    private fun indexed(message: String, path: String = "/src/A.php") = IndexedEntry("/baseline.neon", path, BaselineEntry(pattern(message), "src/A.php", null, 1, SourceRange(0, 1), 1))

    @Test fun `single quoted NEON preserves PCRE slashes and source ranges`() {
        val source = "\uFEFFparameters:\r\n\tignoreErrors:\r\n\t\t-\r\n\t\t\tmessage: '#^Method App\\\\Service\\:\\:save\\(\\) has no return type specified\\.$#' # comment\r\n\t\t\tcount: 2\r\n\t\t\tpath: '../src/Service.php'\r\n"
        val result = BaselineParser.parse(source)
        assertEquals(emptyList<ParseIssue>(), result.issues)
        val entry = result.entries.single()
        assertEquals(4, entry.line)
        assertEquals(2, entry.count)
        assertEquals("App\\Service", BaselineMessageClassifier.owner(entry.message)?.className)
        assertTrue(source.substring(entry.range.start, entry.range.end).startsWith("'#^Method"))
        assertTrue(source.substring(entry.range.start, entry.range.end).endsWith("$#'"))
    }

    @Test fun `identifier source range preserves BOM CRLF and quoted escapes`() {
        val source = "\uFEFFparameters:\r\n  ignoreErrors:\r\n    -\r\n      message: '#^Message$#'\r\n      identifier: \"missingType.\\u0069terableValue\" # note\r\n      path: File.php\r\n"
        val result = BaselineParser.parse(source)
        assertTrue(result.issues.toString(), result.issues.isEmpty())
        val entry = result.entries.single()
        assertEquals("missingType.iterableValue", entry.identifier)
        val range = entry.identifierRange!!
        assertEquals("\"missingType.\\u0069terableValue\"", source.substring(range.start, range.end))
        assertEquals("'#^Message$#'", source.substring(entry.range.start, entry.range.end))
        assertEquals("File.php", source.substring(entry.pathRange!!.start, entry.pathRange.end))
        assertNull(parse("message: '#^X$#'\npath: A.php").entries.single().identifierRange)
    }

    @Test fun `double quoted scalars decode JSON escapes before PCRE`() {
        val result = parse("""
            message: "#^Method App\\\\Service\\:\\:save\\(\\) has no return type specified\\.$#"
            identifier: "missingType.return"
            path: "../src/\u0410.php"
        """.trimIndent())
        assertTrue(result.issues.toString(), result.issues.isEmpty())
        val entry = result.entries.single()
        assertEquals("../src/А.php", entry.path)
        assertEquals(OwnerHint("App\\Service", "save"), BaselineMessageClassifier.owner(entry.message))
        assertNull(entry.count)
    }

    @Test fun `single quote escaping and hash in a path`() {
        val result = parse("message: '#^It''s literal$#'\npath: 'src/a#b.php'")
        assertEquals("#^It's literal$#", result.entries.single().message)
        assertEquals("src/a#b.php", result.entries.single().path)
    }

    @Test fun `inline first field and empty baseline are accepted`() {
        val source = "parameters:\n  ignoreErrors:\n    - message: '#^Message$#'\n      path: src/A.php\n"
        assertEquals(1, BaselineParser.parse(source).entries.size)
        assertEquals(ParseResult(emptyList(), emptyList()), BaselineParser.parse("parameters:\n  ignoreErrors: []\n"))
    }

    @Test fun `unsupported structures never produce partial markers`() {
        for (fields in listOf("message: '#^X$#'\npaths: [src/A.php]", "message: '#^X$#'\npath: src/*.php", "message: '#^X$#'\npath: src/A.php\nrawMessage: X", "message: '#^X$#'\npath: src/A.php\ncount: -1")) {
            val result = parse(fields)
            assertTrue(fields, result.issues.isNotEmpty())
            assertTrue(fields, result.entries.isEmpty())
        }
    }

    @Test fun `malformed escaped strings and duplicate fields are diagnosed`() {
        for (fields in listOf("message: \"bad\\q\"\npath: A.php", "message: 'unclosed\npath: A.php", "message: '#^X$#'\nmessage: '#^Y$#'\npath: A.php")) {
            assertTrue(BaselineParser.parse("parameters:\n  ignoreErrors:\n    -\n" + fields.prependIndent("      ")).issues.isNotEmpty())
        }
    }

    @Test fun `wrong indentation missing path and missing section are diagnosed`() {
        assertTrue(parse("message: '#^X$#'").issues.isNotEmpty())
        assertTrue(BaselineParser.parse("parameters:\n  ignoreErrors:\n    -\n      message: '#^X$#'\n     path: A.php").issues.isNotEmpty())
        assertTrue(BaselineParser.parse("parameters:\n  level: 6").issues.isNotEmpty())
    }

    @Test fun `method definitions constructors properties and architecture identify owners`() {
        assertEquals(OwnerHint("App\\Service", "save"), BaselineMessageClassifier.owner(pattern("Method App\\Service::save() has no return type specified.")))
        assertEquals(OwnerHint("App\\Service", "__construct"), BaselineMessageClassifier.owner(pattern("Method App\\Service::__construct() has parameter missing type.")))
        assertEquals(OwnerHint("App\\Service", "save"), BaselineMessageClassifier.owner(pattern("Return type of method App\\Service::save() is invalid.")))
        assertEquals(OwnerHint("App\\Service"), BaselineMessageClassifier.owner(pattern("Property App\\Service::\$value is never read.")))
        assertEquals(OwnerHint("App\\Service"), BaselineMessageClassifier.owner(pattern("architectureRules: App\\Service should not depend on App\\Gateway")))
    }

    @Test fun `callee names and arbitrary patterns never identify declaration owners`() {
        assertNull(BaselineMessageClassifier.owner(pattern("Call to an undefined method App\\Other::run().")))
        assertNull(BaselineMessageClassifier.owner("#^Method App\\\\Service::.*$#"))
        assertNull(BaselineMessageClassifier.owner("Method App\\\\Service::save()"))
        assertNull(BaselineMessageClassifier.owner("#^Method A::save\\(\\) has .*|other message$#"))
        assertEquals("#^Method A::save\\(\\) has .*|other message$#", BaselineMessageClassifier.display("#^Method A::save\\(\\) has .*|other message$#"))
    }

    @Test fun `method matching is case insensitive and does not attach to another class`() {
        val entries = listOf(indexed("Method app\\service::SAVE() has no return type specified."), indexed("Method App\\Absent::run() has no return type specified."))
        val result = BaselineMatcher.bind(entries, listOf(Declaration("\\App\\Service", setOf("save")), Declaration("App\\Other", setOf("run"))))
        assertEquals(1, result.size)
        assertEquals(1, result.getValue("app\\service").methods.getValue("save").size)
    }

    @Test fun `unknown owner falls back only to sole class and missing method stays at class`() {
        val unknown = indexed("Unsafe usage of new static().")
        assertTrue(BaselineMatcher.bind(listOf(unknown), listOf(Declaration("A", emptySet()), Declaration("B", emptySet()))).isEmpty())
        assertEquals(BindingNote.FILE_ONLY, BaselineMatcher.bind(listOf(unknown), listOf(Declaration("A", emptySet()))).getValue("a").entries.single().note)
        val missing = indexed("Method App\\Service::gone() has no return type specified.")
        val result = BaselineMatcher.bind(listOf(missing), listOf(Declaration("App\\Service", emptySet()))).getValue("app\\service")
        assertEquals(BindingNote.METHOD_MISSING, result.entries.single().note)
        assertTrue(result.methods.isEmpty())
    }

    @Test fun `path normalization supports project relative paths and Windows drives`() {
        assertEquals("/repo/src/A.php", BaselinePathResolver.resolve("../src/A.php", "/repo/config"))
        assertEquals("/other/A.php", BaselinePathResolver.resolve("/other/A.php", "/repo"))
        assertEquals("c:/repo/src/a.php", BaselinePathResolver.resolve("..\\src\\A.php", "C:\\Repo\\config", false))
        assertEquals("//server/share/src/A.php", BaselinePathResolver.resolve("\\\\server\\share\\src\\A.php", "C:/Repo"))
        assertEquals("config/base.neon", BaselinePathResolver.portable("/repo/config/base.neon", "/repo"))
        assertEquals("/repository/base.neon", BaselinePathResolver.portable("/repository/base.neon", "/repo"))
    }
}
