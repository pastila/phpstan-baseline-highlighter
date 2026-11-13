package dev.baselinehighlighter.core

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.FileVisitResult
import java.nio.file.attribute.BasicFileAttributes
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.system.measureNanoTime

object BaselineBenchmark {
    @JvmStatic fun main(args: Array<String>) {
        require(args.size == 1) { "Pass -PbaselineDirectory=/path/to/baselines" }
        val files = mutableListOf<Path>()
        Files.walkFileTree(Path.of(args.single()), object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult =
                if (dir.name in setOf("vendor", "node_modules", ".git", ".idea", "storage", "build", "tasks")) FileVisitResult.SKIP_SUBTREE else FileVisitResult.CONTINUE
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (attrs.isRegularFile && file.name.contains("baseline") && file.name.endsWith(".neon")) files.add(file)
                return FileVisitResult.CONTINUE
            }
        })
        val texts = files.map { it to it.readText() }
        repeat(3) { texts.forEach { (_, text) -> BaselineParser.parse(text) } }
        val runtime = Runtime.getRuntime()
        System.gc()
        Thread.sleep(150)
        val before = runtime.totalMemory() - runtime.freeMemory()
        var retained: List<IndexedEntry> = emptyList()
        var issues = 0
        val elapsed = measureNanoTime {
            retained = texts.flatMap { (file, text) ->
                val parsed = BaselineParser.parse(text)
                issues += parsed.issues.size
                if (parsed.issues.isNotEmpty()) println("Issues in $file: ${parsed.issues.take(3)}")
                parsed.entries.map { IndexedEntry(file.toString(), BaselinePathResolver.resolve(it.path, file.parent.toString()), it) }
            }
        }
        val index = retained.groupBy { it.targetPath }
        System.gc()
        Thread.sleep(150)
        val after = runtime.totalMemory() - runtime.freeMemory()
        println("JDK=${System.getProperty("java.version")}; files=${files.size}; bytes=${files.sumOf { Files.size(it) }}; entries=${retained.size}; targets=${index.size}; issues=$issues")
        println("Parse + normalize + classify + build entries: ${elapsed / 1_000_000.0} ms; approximate retained heap delta=${(after - before) / 1024.0 / 1024.0} MiB (texts already loaded)")
        val declarations = listOf(Declaration("Example\\LargeService", (0 until 2000).map { "method$it" }.toSet()))
        val records = (0 until 1000).map {
            IndexedEntry("baseline.neon", "LargeService.php", BaselineEntry("", "LargeService.php", null, 1, SourceRange(0, 0), 1), OwnerHint("Example\\LargeService", "method$it"))
        }
        repeat(10) { BaselineMatcher.bind(records, declarations) }
        val samples = (0 until 100).map { measureNanoTime { BaselineMatcher.bind(records, declarations) } }.sorted()
        println("Local binding, 2000 methods / 1000 records, warm 100 samples: p95=${samples[94] / 1_000_000.0} ms")
        check(issues == 0) { "Some baselines are unsupported or invalid" }
    }
}
