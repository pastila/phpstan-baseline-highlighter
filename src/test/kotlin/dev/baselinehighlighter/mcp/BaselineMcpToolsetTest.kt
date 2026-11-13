package dev.baselinehighlighter.mcp

import com.intellij.mcpserver.*
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl
import dev.baselinehighlighter.index.BaselineIndexService
import dev.baselinehighlighter.settings.BaselineSettings
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import java.util.concurrent.CompletableFuture
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor

class BaselineMcpToolsetTest : BasePlatformTestCase() {
    override fun createTempDirTestFixture() = TempDirTestFixtureImpl()

    private val messages = listOf(
        "Method App\\Service::save() has no return type specified.",
        "rule: App\\Service should not depend on App\\Other",
        "Unsafe usage of new static().",
        "Method App\\Service::gone() has no return type specified.",
    )

    private fun baseline(): String = "parameters:\n  ignoreErrors:\n" + messages.joinToString("") { text ->
        val pattern = "#^" + text.map { if (it.isLetterOrDigit() || it in " _-:") "$it" else "\\$it" }.joinToString("") + "$#"
        "    -\n      message: '$pattern'\n      identifier: test.identifier\n      count: 1\n      path: ../src/Service.php\n"
    }

    private fun configure(): Pair<com.intellij.openapi.vfs.VirtualFile, com.intellij.openapi.vfs.VirtualFile> {
        val php = myFixture.addFileToProject("src/Service.php", "<?php namespace App; class Service { public function save() {} }").virtualFile
        val neon = myFixture.addFileToProject("config/baseline.neon", baseline()).virtualFile
        project.service<BaselineSettings>().replace(BaselineSettings.Data(sources = mutableListOf(BaselineSettings.Source(path = neon.path))))
        project.service<BaselineIndexService>().reload()
        awaitIndex { !it.isUpdating && it.entries(php.path).size == 4 }
        return php to neon
    }

    private fun awaitIndex(condition: (BaselineIndexService) -> Boolean) {
        val index = project.service<BaselineIndexService>()
        PlatformTestUtil.waitWithEventsDispatching("Baseline index did not update", { condition(index) }, 15)
    }

    private fun tools(): List<McpTool> = McpToolsProvider.EP.extensionList.flatMap { it.getTools() }
        .filter { it.descriptor.name in setOf("find_baseline_entries", "open_baseline_entry", "get_baseline_status", "get_error_identifier_info") }

    private fun call(name: String, args: JsonObject = buildJsonObject {}): McpToolCallResult {
        val tool = tools().single { it.descriptor.name == name }
        // The server added context fields between platform 252 and 262. Use its runtime
        // constructor defaults so this test exercises the same built plugin on both SDKs.
        val optionsConstructor = Class.forName("com.intellij.mcpserver.impl.McpServerService\$McpSessionOptions").kotlin.primaryConstructor!!
        val options = optionsConstructor.callBy(optionsConstructor.parameters.filterNot { it.isOptional }.associateWith {
            (it.type.classifier as KClass<*>).java.enumConstants.first()
        })
        val fields = mapOf(
            "callId" to 1, "clientInfo" to ClientInfo("baseline-test", "1"), "project" to project,
            "mcpToolDescriptor" to tool.descriptor, "rawArguments" to args, "meta" to buildJsonObject {},
            "mcpSessionOptions" to options, "headers" to emptyMap<String, List<String>>(), "sessionId" to "baseline-test",
        )
        val constructor = McpCallInfo::class.primaryConstructor!!
        val info = constructor.callBy(constructor.parameters.filter { fields.containsKey(it.name) }.associateWith { fields[it.name] })
        val result = CompletableFuture.supplyAsync { runBlocking(McpCallAdditionalDataElement(info)) { tool.call(args) } }
        PlatformTestUtil.waitWithEventsDispatching("MCP call did not complete", { result.isDone }, 30)
        return result.get()
    }

    private fun json(result: McpToolCallResult): JsonObject {
        assertFalse(result.toString(), result.isError)
        return result.structuredContent!!
    }

    fun testToolDiscoveryAndFindByFileClassMethodAndPagination() {
        val (php, _) = configure()
        assertEquals(4, tools().size)
        val fileResult = json(call("find_baseline_entries", buildJsonObject { put("filePath", php.path); put("limit", 2) }))
        assertEquals(4, fileResult["total"]!!.jsonPrimitive.int)
        assertTrue(fileResult["hasMore"]!!.jsonPrimitive.boolean)
        assertEquals(2, fileResult["entries"]!!.jsonArray.size)
        val entry = fileResult["entries"]!!.jsonArray.first().jsonObject
        assertEquals(php.path, entry["targetFile"]!!.jsonPrimitive.content)
        assertEquals(64, entry["entryId"]!!.jsonPrimitive.content.length)
        assertEquals(1, entry["count"]!!.jsonPrimitive.int)
        assertTrue(entry["startOffset"]!!.jsonPrimitive.int < entry["endOffset"]!!.jsonPrimitive.int)
        val byClass = json(call("find_baseline_entries", buildJsonObject { put("className", "\\app\\SERVICE") }))
        assertEquals(4, byClass["total"]!!.jsonPrimitive.int)
        val method = json(call("find_baseline_entries", buildJsonObject { put("className", "App\\Service"); put("methodName", "SAVE") }))
        assertEquals(1, method["total"]!!.jsonPrimitive.int)
        val missingMethod = json(call("find_baseline_entries", buildJsonObject { put("className", "App\\Service"); put("methodName", "gone") }))
        assertEquals(0, missingMethod["total"]!!.jsonPrimitive.int)
        val callee = json(call("find_baseline_entries", buildJsonObject { put("className", "App\\Other") }))
        assertEquals(0, callee["total"]!!.jsonPrimitive.int)
        val nextPage = json(call("find_baseline_entries", buildJsonObject { put("filePath", php.path); put("offset", 2); put("limit", 2) }))
        assertFalse(nextPage["hasMore"]!!.jsonPrimitive.boolean)
    }

    fun testIdentifierInfoAndExactFilteringAcrossSources() {
        val (php, neon) = configure()
        val info = json(call("get_error_identifier_info", buildJsonObject { put("identifier", "missingType.iterableValue") }))
        assertTrue(info["knownIdentifier"]!!.jsonPrimitive.boolean)
        assertEquals("missingType.*", info["errorGroup"]!!.jsonPrimitive.content)
        assertEquals("https://phpstan.org/error-identifiers/missingType.iterableValue", info["documentationUrl"]!!.jsonPrimitive.content)
        assertTrue(info["explanation"]!!.jsonPrimitive.content.contains("array<User>"))
        val custom = json(call("get_error_identifier_info", buildJsonObject { put("identifier", "phpat.projectRule") }))
        assertFalse(custom["knownIdentifier"]!!.jsonPrimitive.boolean)
        assertEquals(JsonNull, custom["documentationUrl"])
        assertEquals(JsonNull, custom["explanation"])
        assertTrue(call("get_error_identifier_info", buildJsonObject { put("identifier", "../bad") }).isError)
        assertTrue(call("find_baseline_entries", buildJsonObject { put("identifier", "missingType..return") }).isError)

        val document = FileDocumentManager.getInstance().getDocument(neon)!!
        WriteCommandAction.runWriteCommandAction(project) {
            document.setText(baseline().replaceFirst("identifier: test.identifier", "identifier: missingType.iterableValue"))
        }
        awaitIndex { !it.isUpdating && it.entries(php.path).first().entry.identifier == "missingType.iterableValue" }
        val filtered = json(call("find_baseline_entries", buildJsonObject { put("identifier", "missingType.iterableValue") }))
        assertEquals(1, filtered["total"]!!.jsonPrimitive.int)
        val match = filtered["entries"]!!.jsonArray.single().jsonObject
        assertEquals(info["documentationUrl"], match["documentationUrl"])
        assertEquals(info["explanation"], match["explanation"])
        assertEquals(info["errorGroup"], match["errorGroup"])
        val combined = json(call("find_baseline_entries", buildJsonObject {
            put("filePath", php.path); put("className", "App\\Service"); put("identifier", "test.identifier"); put("offset", 1); put("limit", 1)
        }))
        assertEquals(3, combined["total"]!!.jsonPrimitive.int)
        assertEquals(1, combined["entries"]!!.jsonArray.size)
        assertTrue(combined["hasMore"]!!.jsonPrimitive.boolean)
        val wrongCase = json(call("find_baseline_entries", buildJsonObject { put("identifier", "missingType.IterableValue") }))
        assertEquals(0, wrongCase["total"]!!.jsonPrimitive.int)

        myFixture.addFileToProject("src/Other.php", "<?php namespace App; class Other {}")
        val second = myFixture.addFileToProject("config/other.neon", baseline().replace("../src/Service.php", "../src/Other.php").replace("test.identifier", "missingType.iterableValue")).virtualFile
        val settings = project.service<BaselineSettings>()
        settings.replace(settings.state.copy(sources = (settings.state.sources + BaselineSettings.Source(path = second.path)).toMutableList()))
        project.service<BaselineIndexService>().reload()
        awaitIndex { !it.isUpdating && it.snapshot.sources.size == 2 }
        val allSources = json(call("find_baseline_entries", buildJsonObject { put("identifier", "missingType.iterableValue") }))
        assertEquals(5, allSources["total"]!!.jsonPrimitive.int)
        val type = json(call("find_baseline_entries", buildJsonObject { put("className", "App\\Service"); put("identifier", "missingType.iterableValue") }))
        assertEquals(1, type["total"]!!.jsonPrimitive.int)
    }

    fun testNavigationRechecksUnsavedOffsetsAndRejectsChangedEntries() {
        val (php, neon) = configure()
        val entry = json(call("find_baseline_entries", buildJsonObject { put("filePath", php.path) }))["entries"]!!.jsonArray.first().jsonObject
        val id = entry["entryId"]!!.jsonPrimitive.content
        val document = FileDocumentManager.getInstance().getDocument(neon)!!
        WriteCommandAction.runWriteCommandAction(project) { document.insertString(0, "# shifted\n") }
        val opened = json(call("open_baseline_entry", buildJsonObject { put("entryId", id) }))
        assertTrue(opened["opened"]!!.jsonPrimitive.boolean)
        assertTrue(opened["startOffset"]!!.jsonPrimitive.int > entry["startOffset"]!!.jsonPrimitive.int)
        val editor = FileEditorManager.getInstance(project).selectedTextEditor!!
        assertSame(document, editor.document)
        assertTrue(editor.selectionModel.selectedText!!.contains("save"))
        WriteCommandAction.runWriteCommandAction(project) { document.setText("parameters:\n  ignoreErrors: []\n") }
        assertTrue(call("open_baseline_entry", buildJsonObject { put("entryId", id) }).isError)
    }

    fun testUnsavedPhpEditsAffectSymbolBindingsImmediately() {
        val (php, _) = configure()
        myFixture.configureFromExistingVirtualFile(php)
        WriteCommandAction.runWriteCommandAction(project) { myFixture.editor.document.setText("<?php namespace App; class Service { public function renamed() {} } class Other {}") }
        val method = json(call("find_baseline_entries", buildJsonObject { put("className", "App\\Service"); put("methodName", "save") }))
        assertEquals(0, method["total"]!!.jsonPrimitive.int)
        val type = json(call("find_baseline_entries", buildJsonObject { put("className", "App\\Service") }))
        assertEquals(3, type["total"]!!.jsonPrimitive.int) // Unknown owner no longer falls back in a multi-class file.
    }

    fun testUpdatingStateAndDistinctIdsForMultipleSources() {
        val (php, _) = configure()
        val second = myFixture.addFileToProject("config/second.neon", baseline()).virtualFile
        val settings = project.service<BaselineSettings>()
        settings.replace(settings.state.copy(sources = (settings.state.sources + BaselineSettings.Source(path = second.path)).toMutableList()))
        val index = project.service<BaselineIndexService>()
        index.reload()
        val pending = project.service<dev.baselinehighlighter.query.BaselineQueryService>().find(filePath = php.path)
        assertEquals("updating", pending.state)
        assertTrue(pending.entries.isEmpty())
        awaitIndex { !it.isUpdating && it.entries(php.path).size == 8 }
        val result = json(call("find_baseline_entries", buildJsonObject { put("filePath", php.path) }))
        val entries = result["entries"]!!.jsonArray.map { it.jsonObject }
        assertEquals(8, entries.map { it["entryId"]!!.jsonPrimitive.content }.toSet().size)
        assertEquals(2, entries.map { it["baselineFile"]!!.jsonPrimitive.content }.toSet().size)
    }

    fun testInvalidArgumentsAndDisabledSourcesReturnErrorsOrExplicitState() {
        val (php, _) = configure()
        assertTrue(call("find_baseline_entries").isError)
        assertTrue(call("find_baseline_entries", buildJsonObject { put("filePath", php.path); put("limit", 0) }).isError)
        assertTrue(call("find_baseline_entries", buildJsonObject { put("filePath", php.path); put("methodName", "save") }).isError)
        assertTrue(call("open_baseline_entry", buildJsonObject { put("entryId", "unknown") }).isError)
        val entry = json(call("find_baseline_entries", buildJsonObject { put("filePath", php.path) }))["entries"]!!.jsonArray.first().jsonObject
        val settings = project.service<BaselineSettings>()
        settings.replace(settings.state.copy(enabled = false))
        assertTrue(call("open_baseline_entry", buildJsonObject { put("entryId", entry["entryId"]!!) }).isError)
        assertEquals("disabled", json(call("find_baseline_entries", buildJsonObject { put("filePath", php.path) }))["state"]!!.jsonPrimitive.content)
        assertEquals("disabled", json(call("get_baseline_status"))["state"]!!.jsonPrimitive.content)
        settings.replace(BaselineSettings.Data())
        assertTrue(call("open_baseline_entry", buildJsonObject { put("entryId", entry["entryId"]!!) }).isError)
    }

    fun testStatusReportsInvalidUnavailableAndMissingTargets() {
        val (_, neon) = configure()
        var status = json(call("get_baseline_status"))
        assertEquals("ready", status["state"]!!.jsonPrimitive.content)
        assertTrue(status["phpSupportAvailable"]!!.jsonPrimitive.boolean)
        val document = FileDocumentManager.getInstance().getDocument(neon)!!
        WriteCommandAction.runWriteCommandAction(project) { document.setText("invalid") }
        awaitIndex { !it.isUpdating && it.snapshot.sources.values.single().issues.isNotEmpty() }
        status = json(call("get_baseline_status"))
        val source = status["sources"]!!.jsonArray.single().jsonObject
        assertEquals("invalid", source["state"]!!.jsonPrimitive.content)
        assertTrue(source["issueCount"]!!.jsonPrimitive.int > 0)
        WriteCommandAction.runWriteCommandAction(project) { document.setText(baseline().replace("../src/Service.php", "../src/Missing.php")) }
        awaitIndex { !it.isUpdating && it.snapshot.sources.values.single().missingTargets.isNotEmpty() }
        status = json(call("get_baseline_status"))
        assertEquals(1, status["sources"]!!.jsonArray.single().jsonObject["missingTargetCount"]!!.jsonPrimitive.int)
        val settings = project.service<BaselineSettings>()
        settings.replace(BaselineSettings.Data(sources = mutableListOf(BaselineSettings.Source(path = neon.parent.path + "/absent.neon"))))
        project.service<BaselineIndexService>().reload()
        awaitIndex { !it.isUpdating && it.snapshot.sources.values.single().unavailable }
        status = json(call("get_baseline_status"))
        assertEquals("unavailable", status["sources"]!!.jsonArray.single().jsonObject["state"]!!.jsonPrimitive.content)
    }
}
