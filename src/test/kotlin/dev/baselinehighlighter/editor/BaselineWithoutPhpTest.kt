package dev.baselinehighlighter.editor

import com.intellij.openapi.components.service
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.fixtures.TempDirTestFixture
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl
import dev.baselinehighlighter.index.BaselineIndexService
import dev.baselinehighlighter.php.PhpDeclarationProvider
import dev.baselinehighlighter.settings.BaselineSettings

class BaselineWithoutPhpTest : BasePlatformTestCase() {
    override fun createTempDirTestFixture(): TempDirTestFixture = TempDirTestFixtureImpl()

    fun testPluginLoadsAndIndexesWithoutEitherPhpPlugin() {
        assertTrue(PhpDeclarationProvider.EP.extensionList.isEmpty())
        val phpat = myFixture.addFileToProject("phpat.neon", "parameters:\n  ignoreErrors:\n    -\n      message: '#^Architecture$#'\n      identifier: phpat.testRule\n      path: File.php\n")
        myFixture.addFileToProject("phpstan.neon", "includes:\n  - phpat.neon\nservices:\n  -\n    class: Tests\\Architecture\n    tags: [phpat.test]\n")
        assertTrue(dev.baselinehighlighter.phpat.PhpatRuleResolver.resolve(phpat, "phpat.testRule").isEmpty())
        for (id in listOf("com.jetbrains.php", "ru.openide.openphp")) {
            assertTrue(com.intellij.ide.plugins.PluginManagerCore.getPlugin(com.intellij.openapi.extensions.PluginId.getId(id))?.isEnabled != true)
        }
        val source = myFixture.addFileToProject("baseline.neon", "parameters:\n  ignoreErrors:\n    -\n      message: '#^Message$#'\n      identifier: missingType.return\n      path: File.php\n")
        project.service<BaselineSettings>().replace(BaselineSettings.Data(sources = mutableListOf(BaselineSettings.Source(path = source.virtualFile.path))))
        val service = project.service<BaselineIndexService>()
        service.reload()
        PlatformTestUtil.waitWithEventsDispatching("Baseline was not indexed without PHP", { service.snapshot.byFile.values.flatten().size == 1 }, 15)
        val file = myFixture.configureByText("File.php", "<?php class Example {}")
        assertNull(PhpDeclarationProvider.forFile(file))
        assertTrue(PhpBaselineMatcher.bindings(file).isEmpty())
        val query = project.service<dev.baselinehighlighter.query.BaselineQueryService>()
        val result = query.find(filePath = service.snapshot.byFile.keys.single())
        assertEquals("ready", result.state)
        assertEquals(1, result.entries.size)
        assertEquals(1, query.find(identifier = "missingType.return").total)
        try {
            query.find(className = "Example")
            fail("Symbol lookup should require a PHP adapter")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("PHP plugin"))
        }
        if (System.getProperty("baseline.mcpServerEnabled") == "false") {
            assertTrue(com.intellij.ide.plugins.PluginManagerCore.getPlugin(com.intellij.openapi.extensions.PluginId.getId("com.intellij.mcpServer"))?.isEnabled != true)
        }
    }
}
