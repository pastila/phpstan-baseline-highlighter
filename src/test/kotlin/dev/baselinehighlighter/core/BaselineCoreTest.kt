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

    @Test fun `unused public methods identify the quoted declaration owner`() {
        assertEquals(OwnerHint("App\\Service", "save"), BaselineMessageClassifier.owner(pattern("Public method \"App\\Service::save()\" is never used")))
        assertEquals(OwnerHint("Пример\\Сервис", "__construct"), BaselineMessageClassifier.owner(pattern("Public method \"\\Пример\\Сервис::__construct()\" is never used.")))
        assertNull(BaselineMessageClassifier.owner(pattern("Call to public method \"App\\Other::save()\" is not allowed.")))
        assertNull(BaselineMessageClassifier.owner("#^Public method \"App\\\\Service::.*\" is never used$#"))
    }

    @Test fun `incompatible return types identify the child method instead of the parent`() {
        assertEquals(OwnerHint("App\\Service", "execute"), BaselineMessageClassifier.owner(pattern(
            "Return type (void) of method App\\Service::execute() should be compatible with return type (int|null) of method App\\ParentService::execute()"
        )))
        assertEquals(OwnerHint("App\\Service", "callback"), BaselineMessageClassifier.owner(pattern(
            "Return type (Closure(int): (string|null)) of method \\App\\Service::callback() should be compatible with return type (callable(int): string) of method App\\ParentService::callback()"
        )))
        assertNull(BaselineMessageClassifier.owner(pattern(
            "Return type (void) of function execute() should be compatible with return type (int|null) of method App\\ParentService::execute()"
        )))
        assertNull(BaselineMessageClassifier.owner("#^Return type \\(.*\\) of method App\\\\Service::execute\\(\\) should be compatible with return type \\(int\\) of method App\\\\ParentService::execute\\(\\)$#"))
    }

    @Test fun `method matching is case insensitive and does not attach to another class`() {
        val entries = listOf(indexed("Method app\\service::SAVE() has no return type specified."), indexed("Method App\\Absent::run() has no return type specified."))
        val result = BaselineMatcher.bind(entries, listOf(Declaration("\\App\\Service", setOf("save")), Declaration("App\\Other", setOf("run"))))
        assertEquals(1, result.size)
        assertEquals(1, result.getValue("app\\service").methods.getValue("save").size)
    }

    @Test fun `architecture declaration rules identify the class before the requirement`() {
        for (requirement in listOf(
            "not exist",
            "not exist.",
            "have only one public method",
            "have only one public method.",
            "be named matching the regex /.*Service$/",
            "implement App\\Contract",
            "implement /^App\\Domain\\.*Interface$/",
            "extend App\\BaseService",
            "be named matching the regex /[/",
        )) {
            for (prefix in listOf("", "architectureRules.contextStructure: ")) {
                assertEquals(requirement, OwnerHint("Пример\\Сервис"), BaselineMessageClassifier.owner(pattern("${prefix}\\Пример\\Сервис should $requirement")))
            }
        }
    }

    @Test fun `architecture requirements do not identify referenced classes or execute message patterns`() {
        for (text in listOf(
            "Dependency App\\Other of App\\Service should not exist",
            "App\\Service should implement ",
            "App\\Service should extend ",
            "App\\Service should be named matching the regex ",
            "App\\Service should not exist anymore",
        )) assertNull(text, BaselineMessageClassifier.owner(pattern(text)))
        assertNull(BaselineMessageClassifier.owner("#^rule: App\\\\Service should be named matching the regex /.*Service\\$/$#"))
        val entries = listOf(
            indexed("rule: App\\Service should implement App\\Contract"),
            indexed("App\\Service should extend App\\BaseService"),
            indexed("App\\Absent should implement App\\Contract"),
        )
        val bindings = BaselineMatcher.bind(entries, listOf(
            Declaration("App\\Service", setOf("save")),
            Declaration("App\\Contract", emptySet()),
            Declaration("App\\BaseService", emptySet()),
        ))
        assertEquals(setOf("app\\service"), bindings.keys)
        assertEquals(2, bindings.getValue("app\\service").entries.size)
        assertTrue(bindings.getValue("app\\service").entries.all { it.note == BindingNote.NONE })
        assertTrue(bindings.getValue("app\\service").methods.isEmpty())
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

    @Test fun `property declaration diagnostics retain the declaring class`() {
        for (detail in listOf(
            "has no type specified.",
            "type has no value type specified in iterable type array.",
            "with generic class ArrayIterator does not specify its types: TKey, TValue",
            "with generic interface IteratorAggregate does not specify its types: TKey, TValue",
            "has unknown class App\\Other as its type.",
            "is never read.",
            "is never read, only written.",
            "is never written, only read.",
            "(array<string>|null) is never assigned null so it can be removed from the property type.",
            "(Closure(int): string) does not accept default value of type false.",
        )) assertEquals(detail, OwnerHint("Пример\\Сервис"), BaselineMessageClassifier.owner(pattern("Property \\Пример\\Сервис::\$value $detail")))
    }

    @Test fun `property access diagnostics never identify the referenced declaring class`() {
        for (detail in listOf(
            "(string) does not accept null.",
            "(Closure(int): string) does not accept int.",
            "(string) in isset() is not nullable.",
            "in isset() is not nullable nor uninitialized.",
            "(string) in empty() is not falsy.",
            "(string) on left side of ?? is not nullable.",
            "on left side of ?? is not nullable nor uninitialized.",
            "has an unfamiliar diagnostic.",
        )) {
            val access = indexed("Property App\\Dependency::\$value $detail")
            assertNull(detail, access.owner)
            val single = BaselineMatcher.bind(listOf(access), listOf(Declaration("App\\Service", setOf("save"))))
            assertEquals(detail, BindingNote.FILE_ONLY, single.getValue("app\\service").entries.single().note)
            val multiple = BaselineMatcher.bind(listOf(access), listOf(Declaration("App\\Service", emptySet()), Declaration("App\\Dependency", emptySet())))
            assertTrue(detail, multiple.isEmpty())
            assertTrue(detail, BaselineMatcher.bind(listOf(access), emptyList()).isEmpty())
        }
    }

    @Test fun `class generic and PHPDoc diagnostics identify the declaring class`() {
        for (detail in listOf(
            "implements generic interface App\\Contract but does not specify its types: TKey, TValue",
            "extends generic class \\App\\BaseService but does not specify its types: T",
            "has PHPDoc tag @method for method findBy() parameter #1 \$criteria with no value type specified in iterable type array.",
            "has PHPDoc tag @property for property \$items with no value type specified in iterable type array.",
            "has PHPDoc tag @template-covariant T with invalid bound type App\\Other.",
        )) assertEquals(detail, OwnerHint("Пример\\Сервис"), BaselineMessageClassifier.owner(pattern("Class \\Пример\\Сервис $detail")))
    }

    @Test fun `class references and documented methods never become declaration owners`() {
        for (text in listOf(
            "Class App\\Other constructor invoked with 4 parameters, 3 required.",
            "Class App\\Other not found.",
            "Class App\\Other referenced with incorrect case: App\\OTHER.",
            "Call to method App\\Other::findBy() with incompatible PHPDoc type.",
            "Class App\\Service has PHPDoc tag @method ",
            "Class App\\Service implements generic interface App\\Contract but does not specify its types: ",
        )) assertNull(text, BaselineMessageClassifier.owner(pattern(text)))
        assertNull(BaselineMessageClassifier.owner("#^Class App\\\\Service.* has PHPDoc tag @method for method findBy\\(\\)$#"))
        val entries = listOf(
            indexed("Class App\\Service has PHPDoc tag @method for method findBy() parameter #1 \$criteria with no value type specified in iterable type array."),
            indexed("Class App\\Absent extends generic class App\\BaseService but does not specify its types: T"),
        )
        val bound = BaselineMatcher.bind(entries, listOf(Declaration("App\\Service", setOf("findBy")), Declaration("App\\BaseService", emptySet())))
        assertEquals(setOf("app\\service"), bound.keys)
        assertEquals(BindingNote.NONE, bound.getValue("app\\service").entries.single().note)
        assertTrue(bound.getValue("app\\service").methods.isEmpty())
    }

    @Test fun `start anchored literal messages retain owners without an end anchor`() {
        for ((message, expected) in listOf(
            "Method App\\Service::save() has no return type specified." to OwnerHint("App\\Service", "save"),
            "Public method \"App\\Service::save()\" is never used" to OwnerHint("App\\Service", "save"),
            "Class \\Пример\\Сервис has PHPDoc tag @method for method findBy() with invalid return type." to OwnerHint("Пример\\Сервис"),
            "Property App\\Service::\$value has no type specified." to OwnerHint("App\\Service"),
            "rule: App\\Service should not depend on App\\Dependency" to OwnerHint("App\\Service"),
            "rule: App\\Service should not exist" to OwnerHint("App\\Service"),
        )) {
            val startOnly = pattern(message).removeSuffix("$#") + "#"
            assertEquals(message, expected, BaselineMessageClassifier.owner(startOnly))
            assertEquals(message, BaselineMessageClassifier.display(startOnly))
        }
        val escaped = "Literal #, /, Unicode Ж and a final \$"
        assertEquals(escaped, BaselineMessageClassifier.display(pattern(escaped).removeSuffix("$#") + "#"))
        assertEquals(OwnerHint("App\\Service"), BaselineMessageClassifier.owner("~^rule: App\\\\Service should not depend on App\\\\Dependency~"))
    }

    @Test fun `missing end anchors never permit partial regex decoding or malformed delimiters`() {
        val prefix = pattern("rule: App\\Service should not depend on App\\Dependency").removeSuffix("$#")
        for (pattern in listOf(
            "$prefix.*#", "$prefix|Other#", "$prefix(?:suffix)#", "$prefix[abc]#",
            "$prefix+#", "$prefix?#", "$prefix{2}#", "${prefix}\\d#",
            "$prefix#i", "$prefix#extra#", "${prefix}\\#", prefix,
            ("$prefix#").removeRange(1, 2),
        )) {
            assertNull(pattern, BaselineMessageClassifier.owner(pattern))
            assertEquals(pattern, BaselineMessageClassifier.display(pattern))
        }
    }

    @Test fun `named function diagnostics identify functions instead of classes or methods`() {
        for (detail in listOf(
            "has parameter \$items with no type specified.",
            "has parameter \$items with no value type specified in iterable type array.",
            "has no return type specified.",
            "has invalid return type App\\Other.",
            "return type has no value type specified in iterable type array.",
            "should return App\\Other but returns int.",
        )) assertEquals(detail, OwnerHint(functionName = "Пример\\обработать"), BaselineMessageClassifier.owner(pattern("Function \\Пример\\обработать() $detail")))
        val records = listOf(indexed("Function App\\run() has no return type specified."), indexed("Function run() has no return type specified."), indexed("Function App\\missing() has no return type specified."))
        val bound = BaselineMatcher.bindFunctions(records, setOf("\\APP\\RUN", "run", "Other\\run"))
        assertEquals(setOf("app\\run", "run"), bound.keys)
        assertTrue(bound.values.all { it.size == 1 && it.single().note == BindingNote.NONE })
        assertTrue(BaselineMatcher.bind(records, listOf(Declaration("App\\run", setOf("run")))).isEmpty())
    }

    @Test fun `function calls missing names and arbitrary regexes have no function owner`() {
        for (text in listOf(
            "Function App\\run not found.",
            "Function App\\run() invoked with 3 parameters, 1 required.",
            "Parameter #1 \$value of function App\\run expects string, int given.",
            "Call to function App\\run() has no effect.",
            "Anonymous function has no return type specified.",
        )) {
            val entry = indexed(text)
            assertNull(text, entry.owner)
            assertTrue(BaselineMatcher.bindFunctions(listOf(entry), setOf("App\\run")).isEmpty())
        }
        assertNull(BaselineMessageClassifier.owner("#^Function App\\\\run\\(\\) has .*$#"))
        assertTrue(BaselineMatcher.bindFunctions(listOf(indexed("Function run() has no return type specified.")), setOf("App\\run")).isEmpty())
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
