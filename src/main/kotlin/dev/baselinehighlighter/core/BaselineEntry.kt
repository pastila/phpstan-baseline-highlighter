package dev.baselinehighlighter.core

data class SourceRange(val start: Int, val end: Int)
data class BaselineEntry(
    val message: String,
    val path: String,
    val identifier: String?,
    val count: Int?,
    val range: SourceRange,
    val line: Int,
    val identifierRange: SourceRange? = null,
    val pathRange: SourceRange? = null,
)
data class ParseIssue(val line: Int, val message: String)
data class ParseResult(val entries: List<BaselineEntry>, val issues: List<ParseIssue>)
data class OwnerHint(val className: String? = null, val methodName: String? = null, val functionName: String? = null) {
    init {
        require((className == null) != (functionName == null))
        require(methodName == null || className != null)
    }
}
data class IndexedEntry(
    val sourcePath: String,
    val targetPath: String,
    val entry: BaselineEntry,
    val owner: OwnerHint? = BaselineMessageClassifier.owner(entry.message),
)
