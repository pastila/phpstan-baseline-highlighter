package dev.baselinehighlighter.mcp

import com.intellij.mcpserver.McpToolCallResult
import com.intellij.mcpserver.McpToolset
import com.intellij.mcpserver.annotations.McpDescription
import com.intellij.mcpserver.annotations.McpTool
import com.intellij.mcpserver.project
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.psi.PsiDocumentManager
import dev.baselinehighlighter.core.BaselineMessageClassifier
import dev.baselinehighlighter.documentation.ErrorIdentifierDocumentation
import dev.baselinehighlighter.editor.BaselineNavigation
import dev.baselinehighlighter.index.BaselineIndexService
import dev.baselinehighlighter.php.PhpDeclarationProvider
import dev.baselinehighlighter.query.BaselineQueryService
import dev.baselinehighlighter.settings.BaselineSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

/** Loaded exclusively through the optional MCP Server descriptor. */
class BaselineMcpToolset : McpToolset {
    @McpTool
    @McpDescription("Find configured PHPStan baseline entries by PHP file, fully qualified class, method, named function or exact error identifier. Filters combine with AND. Specify filePath, className, functionName or identifier; an identifier alone searches all configured sources. functionName cannot be combined with className or methodName. Symbol searches use gutter marker declaration bindings. Returns paginated JSON including entryId, baseline location, PHP target, error group, offline explanation and a documentation URL for known identifiers. If state is updating, retry after indexing finishes. A baseline entry does not prove a current code violation.")
    suspend fun find_baseline_entries(
        @McpDescription("Absolute PHP file path or a path relative to the project root. Specify filePath, className, functionName or identifier.") filePath: String? = null,
        @McpDescription("Fully qualified PHP class name, e.g. App\\Service. Leading backslash and case differences are accepted.") className: String? = null,
        @McpDescription("Exact PHP method name without parentheses; requires className. Omit to include all entries bound to the class.") methodName: String? = null,
        @McpDescription("Number of matching entries to skip, starting at zero.") offset: Int = 0,
        @McpDescription("Maximum entries to return, from 1 to 200.") limit: Int = 50,
        @McpDescription("Exact case-sensitive error identifier, e.g. missingType.iterableValue. May be used alone across all sources or combined with other filters.") identifier: String? = null,
        @McpDescription("Fully qualified named PHP function without parentheses, e.g. App\\save. Leading backslash and case differences are accepted. Cannot be combined with className or methodName.") functionName: String? = null,
    ): McpToolCallResult {
        val project = currentCoroutineContext().project
        return checked {
            withContext(Dispatchers.EDT) { PsiDocumentManager.getInstance(project).commitAllDocuments() }
            val result = withContext(Dispatchers.Default) { project.service<BaselineQueryService>().find(filePath, className, methodName, offset, limit, identifier, functionName) }
            jsonResult(buildJsonObject {
                put("state", result.state)
                put("total", result.total)
                put("offset", result.offset)
                put("limit", result.limit)
                put("hasMore", result.offset.toLong() + result.entries.size < result.total)
                putJsonArray("entries") {
                    for (match in result.entries) add(buildJsonObject {
                        val entry = match.indexed.entry
                        put("entryId", match.entryId)
                        put("baselineFile", match.indexed.sourcePath)
                        put("line", entry.line)
                        put("startOffset", entry.range.start)
                        put("endOffset", entry.range.end)
                        put("targetFile", match.indexed.targetPath)
                        put("className", match.className?.let(::JsonPrimitive) ?: JsonNull)
                        put("methodName", match.methodName?.let(::JsonPrimitive) ?: JsonNull)
                        put("functionName", match.functionName?.let(::JsonPrimitive) ?: JsonNull)
                        put("binding", match.binding)
                        put("message", BaselineMessageClassifier.display(entry.message))
                        put("messagePattern", entry.message)
                        put("identifier", entry.identifier?.let(::JsonPrimitive) ?: JsonNull)
                        putIdentifierInfo(ErrorIdentifierDocumentation.lookup(entry.identifier))
                        put("count", entry.count?.let(::JsonPrimitive) ?: JsonNull)
                    })
                }
            })
        }
    }

    @McpTool
    @McpDescription("Get offline PHPStan error identifier information: group, knownIdentifier, an optional short explanation, and the official documentation URL when the identifier is in the bundled catalog. Custom and unknown identifiers have no assumed documentation URL. No PHP plugin, baseline configuration, network access or PHPStan execution is required. This is general guidance, not confirmation of an active violation.")
    suspend fun get_error_identifier_info(
        @McpDescription("Exact PHPStan error identifier, for example missingType.iterableValue.") identifier: String,
    ): McpToolCallResult = checked {
        require(ErrorIdentifierDocumentation.isValid(identifier)) { "Invalid error identifier. Use ASCII letters, digits and dot-separated nonempty parts." }
        jsonResult(buildJsonObject {
            val info = ErrorIdentifierDocumentation.lookup(identifier)
            put("identifier", identifier)
            putIdentifierInfo(info)
        })
    }

    @McpTool
    @McpDescription("Open and select a PHPStan baseline message in the IDE using an entryId returned by find_baseline_entries. Rechecks the current unsaved document and adjusts shifted offsets. Returns an error if the entry was removed, changed, expired or its source was disabled. Does not modify any file.")
    suspend fun open_baseline_entry(
        @McpDescription("entryId from find_baseline_entries in this project. Search again if the ID expired.") entryId: String,
    ): McpToolCallResult {
        val project = currentCoroutineContext().project
        return checked {
            val selected = project.service<BaselineQueryService>().selected(entryId)
            val target = withContext(Dispatchers.Default) { BaselineNavigation.resolve(selected) }
            if (target == null) {
                project.service<BaselineIndexService>().reload()
                return@checked McpToolCallResult.error("The baseline entry changed or was removed. Run find_baseline_entries again.")
            }
            val opened = withContext(Dispatchers.EDT) {
                // Settings may have changed while resolving the current baseline document.
                project.service<BaselineQueryService>().selected(entryId)
                BaselineNavigation.open(project, target)
            }
            if (!opened) return@checked McpToolCallResult.error("The baseline document changed again or could not be opened. Run find_baseline_entries again.")
            jsonResult(buildJsonObject {
                put("opened", true)
                put("baselineFile", target.file.path)
                put("line", target.entry.line)
                put("startOffset", target.entry.range.start)
                put("endOffset", target.entry.range.end)
            })
        }
    }

    @McpTool
    @McpDescription("Inspect PHPStan baseline configuration and index readiness for this project. Returns enabled state, PHP adapter availability, configured sources, parse issues and missing target counts. Diagnostics and missing paths are limited to 20 samples per source. Does not change settings or run PHPStan.")
    suspend fun get_baseline_status(): McpToolCallResult {
        val project = currentCoroutineContext().project
        val index = project.service<BaselineIndexService>()
        index.start()
        val settings = project.service<BaselineSettings>().state
        val snapshot = index.snapshot
        return jsonResult(buildJsonObject {
            put("enabled", settings.enabled)
            put("state", if (!settings.enabled) "disabled" else if (index.isUpdating) "updating" else "ready")
            put("phpSupportAvailable", PhpDeclarationProvider.EP.extensionList.isNotEmpty())
            put("entryCount", if (settings.enabled) snapshot.byFile.values.sumOf { it.size } else 0)
            putJsonArray("sources") {
                for (source in settings.sources) add(buildJsonObject {
                    val path = index.normalize(source.path)
                    val status = snapshot.sources[path]
                    put("path", path)
                    put("baseDirectory", if (source.baseDirectory.isBlank()) path.substringBeforeLast('/') else index.normalize(source.baseDirectory))
                    put("enabled", source.enabled)
                    put("state", when {
                        !settings.enabled || !source.enabled -> "disabled"
                        index.isUpdating -> "updating"
                        status == null -> "not_indexed"
                        status.unavailable -> "unavailable"
                        status.issues.isNotEmpty() -> "invalid"
                        else -> "ready"
                    })
                    put("entryCount", status?.entries?.size ?: 0)
                    put("issueCount", status?.issues?.size ?: 0)
                    putJsonArray("issues") {
                        status?.issues?.take(20)?.forEach { issue -> add(buildJsonObject {
                            put("line", issue.line)
                            put("code", issue.message)
                        }) }
                    }
                    put("missingTargetCount", status?.missingTargets?.size ?: 0)
                    putJsonArray("missingTargets") { status?.missingTargets?.take(20)?.forEach { add(JsonPrimitive(it)) } }
                })
            }
        })
    }

    private suspend fun checked(action: suspend () -> McpToolCallResult): McpToolCallResult = try {
        action()
    } catch (error: IllegalArgumentException) {
        McpToolCallResult.error(error.message ?: "Invalid baseline request.")
    }

    private fun jsonResult(data: JsonObject): McpToolCallResult = McpToolCallResult.text(data.toString(), data)

    private fun JsonObjectBuilder.putIdentifierInfo(info: ErrorIdentifierDocumentation.Info) {
        put("errorGroup", info.errorGroup?.let(::JsonPrimitive) ?: JsonNull)
        put("knownIdentifier", info.knownIdentifier)
        put("explanation", info.explanation?.let(::JsonPrimitive) ?: JsonNull)
        put("documentationUrl", info.documentationUrl?.let(::JsonPrimitive) ?: JsonNull)
    }
}
