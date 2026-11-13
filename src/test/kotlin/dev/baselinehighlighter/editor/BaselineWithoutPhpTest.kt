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
        for (id in listOf("com.jetbrains.php", "ru.openide.openphp")) {
            assertTrue(com.intellij.ide.plugins.PluginManagerCore.getPlugin(com.intellij.openapi.extensions.PluginId.getId(id))?.isEnabled != true)
        }
        val source = myFixture.addFileToProject("baseline.neon", "parameters:\n  ignoreErrors:\n    -\n      message: '#^Message$#'\n      path: File.php\n")
        project.service<BaselineSettings>().replace(BaselineSettings.Data(sources = mutableListOf(BaselineSettings.Source(path = source.virtualFile.path))))
        val service = project.service<BaselineIndexService>()
        service.reload()
        PlatformTestUtil.waitWithEventsDispatching("Baseline was not indexed without PHP", { service.snapshot.byFile.values.flatten().size == 1 }, 15)
        val file = myFixture.configureByText("File.php", "<?php class Example {}")
        assertNull(PhpDeclarationProvider.forFile(file))
        assertTrue(PhpBaselineMatcher.bindings(file).isEmpty())
    }
}
