package dev.baselinehighlighter.core

import org.junit.Assert.*
import org.junit.Test

class BaselineConfigurationImporterTest {
    private val empty = "parameters:\n  ignoreErrors: []\n"
    private val baseline = """
        parameters:
          ignoreErrors:
            -
              message: '#^Method App\\Service\:\:save\(\) has no return type specified\.$#'
              path: ../src/Service.php
    """.trimIndent()

    @Test fun `reads root includes with NEON quoting BOM tabs CRLF and inline lists`() {
        val config = PhpStanConfiguration.parse("\uFEFFincludes:\r\n\t- 'dir/it''s #1.neon' # comment\r\n\t- \"dir/\\u0054est.neon\"\r\n\t- plain.neon\t# comment\r\nparameters:\r\n\tignoreErrors: []\r\n")
        assertEquals(listOf("dir/it's #1.neon", "dir/Test.neon", "plain.neon"), config.includes.map { it.path })
        assertTrue(config.hasIgnoreErrors)
        assertTrue(config.issues.isEmpty())
        val inline = PhpStanConfiguration.parse("includes: ['a,b.neon', \"C:/a: b.neon\", file.neon,] # comment\n")
        assertEquals(listOf("a,b.neon", "C:/a: b.neon", "file.neon"), inline.includes.map { it.path })
        assertTrue(inline.issues.isEmpty())
        assertTrue(PhpStanConfiguration.parse("includes: []\n").includes.isEmpty())
        val nested = PhpStanConfiguration.parse("parameters:\n  nested:\n    ignoreErrors: []\n    includes:\n      - hidden.neon\nservices:\n  x:\n    includes: [also-hidden.neon]\n")
        assertFalse(nested.hasIgnoreErrors)
        assertTrue(nested.includes.isEmpty())
        val literals = listOf(
            "parameters:\n  text: '''\nincludes: [fake.neon]\n'''\n",
            "parameters:\n  text: \"\"\"\nincludes: [fake.neon]\n\"\"\"\n",
            "parameters:\n  text: '\nincludes: [fake.neon]\n'\n",
            "parameters: {\n  nested: {\nincludes: [fake.neon]\n  }\n}\n"
        )
        literals.forEach {
            assertEquals(it, listOf("actual.neon"), PhpStanConfiguration.parse(it + "includes: [actual.neon]\n").includes.map { include -> include.path })
        }
    }

    @Test fun `reports unsupported includes without treating duplicate sections as an active graph`() {
        val duplicate = PhpStanConfiguration.parse("includes:\n  - a.neon\nincludes: [b.neon]\n")
        assertTrue(duplicate.includes.isEmpty())
        assertEquals("includes.duplicate", duplicate.issues.single().message)
        listOf("includes: 'file.neon'", "includes: [a.neon,,b.neon]", "includes: ['unclosed.neon]",
            "includes:\n  -\n    file: a.neon", "includes:\n  - good.neon\n    - nested.neon").forEach {
            assertTrue(it, PhpStanConfiguration.parse(it).issues.isNotEmpty())
        }
        val quoted = PhpStanConfiguration.parse("'includes': [file.neon]\n")
        assertEquals("file.neon", quoted.includes.single().path)
        assertEquals("parameters.unsupported", PhpStanConfiguration.parse("parameters: {ignoreErrors: []}").issues.single().message)
    }

    @Test fun `follows only explicit includes and imports baselines regardless of name`() {
        val files = mapOf(
            "/project/phpstan.neon.dist" to "includes:\n  - config/nested.neon\n  - config/../config/custom.neon\n  - /shared/empty.neon\nservices:\n  x: Factory()\n",
            "/project/config/nested.neon" to "includes: [custom.neon, ../phpstan.neon.dist]\n",
            "/project/config/custom.neon" to baseline,
            "/shared/empty.neon" to empty,
            "/project/unreferenced.neon" to baseline
        )
        val reads = mutableListOf<String>()
        val result = BaselineConfigurationImporter.discover("/project/phpstan.neon.dist", {
            reads += it
            BaselineConfigurationImporter.Content(files[it])
        })
        assertEquals(listOf("/project/config/custom.neon", "/shared/empty.neon"), result.sources)
        assertEquals(4, result.filesRead)
        assertEquals(reads.distinct(), reads)
        assertFalse(reads.contains("/project/unreferenced.neon"))
        assertEquals("cycle", result.issues.single().key)
        assertEquals(1, result.issueCount)
        assertEquals(listOf("/project/config/custom.neon"), BaselineConfigurationImporter.discover("/project/config/custom.neon", {
            BaselineConfigurationImporter.Content(files[it])
        }).sources)
    }

    @Test fun `reports missing dynamic PHP and invalid baselines while retaining valid siblings`() {
        val root = """
            includes:
              - good.neon
              - missing.neon
              - config.php
              - '%rootDir%/dynamic.neon'
              - '*.neon'
              - https://example.test/config.neon
              - bad.neon
        """.trimIndent()
        val files = mapOf("/p/phpstan.neon" to root + "\nparameters:\n  level: 8\n  ignoreErrors:\n    - '#^Global ignore$#'\n", "/p/good.neon" to baseline,
            "/p/bad.neon" to "parameters:\n  ignoreErrors:\n    - identifier: missingType.return\n")
        val reads = mutableListOf<String>()
        val result = BaselineConfigurationImporter.discover("/p/phpstan.neon", {
            reads += it
            BaselineConfigurationImporter.Content(files[it])
        })
        assertEquals(listOf("/p/good.neon"), result.sources)
        assertEquals(7, result.issueCount)
        assertEquals(3, result.issues.count { it.key == "unsupported.path" })
        assertTrue(result.issues.any { it.key == "unavailable" && it.path == "/p/missing.neon" })
        assertTrue(result.issues.any { it.key == "baseline.invalid" && it.detail == "entry.requires.message.path" })
        assertFalse(reads.contains("/p/config.php"))
        assertEquals(4, reads.size)
    }

    @Test fun `bounds graph depth file count and diagnostics and checks cancellation`() {
        val deep = BaselineConfigurationImporter.discover("/p/0.neon", { path ->
            val number = path.substringAfterLast('/').substringBefore('.').toInt()
            BaselineConfigurationImporter.Content("includes: [${number + 1}.neon]\n")
        })
        assertEquals(65, deep.filesRead)
        assertEquals("depth.limit", deep.issues.single().key)
        val wide = BaselineConfigurationImporter.discover("/p/root.neon", { path ->
            if (path.endsWith("root.neon")) BaselineConfigurationImporter.Content("includes:\n" + (1..513).joinToString("") { "  - $it.neon\n" })
            else BaselineConfigurationImporter.Content()
        })
        assertEquals(100, wide.issues.size)
        assertTrue(wide.issueCount > wide.issues.size)
        assertTrue(wide.issues.any { it.key == "includes.limit" })
        assertEquals(1, wide.filesRead)
        val noisy = BaselineConfigurationImporter.discover("/p/root.neon", {
            BaselineConfigurationImporter.Content("includes:\n" + "  -\n".repeat(1000))
        })
        assertEquals(1000, noisy.issueCount)
        assertEquals(100, noisy.issues.size)
        var checks = 0
        try {
            BaselineConfigurationImporter.discover("/p/root.neon", { BaselineConfigurationImporter.Content("includes:\n  - a.neon\n") }, checkCanceled = {
                if (++checks == 4) throw java.util.concurrent.CancellationException()
            })
            fail("Import should be cancellable")
        } catch (_: java.util.concurrent.CancellationException) { assertEquals(4, checks) }
    }

    @Test fun `deduplicates case insensitive paths and accepts Windows absolute includes`() {
        val reads = mutableListOf<String>()
        val result = BaselineConfigurationImporter.discover("C:/Project/phpstan.neon", { path ->
            reads += path
            BaselineConfigurationImporter.Content(if (path.endsWith("phpstan.neon")) "includes: [config/a.neon, CONFIG/A.neon, 'D:\\Shared\\base.neon']\n" else empty)
        }, caseSensitive = false)
        assertEquals(listOf("C:/Project/config/a.neon", "D:/Shared/base.neon"), result.sources)
        assertEquals(3, reads.size)
        assertTrue(result.issues.isEmpty())
    }
}
